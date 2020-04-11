/*
 * Kuroba - *chan browser https://github.com/Adamantcheese/Kuroba/
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.github.adamantcheese.chan.core.site.parser;

import android.util.JsonReader;

import com.github.adamantcheese.chan.core.database.DatabaseManager;
import com.github.adamantcheese.chan.core.database.DatabaseSavedReplyManager;
import com.github.adamantcheese.chan.core.manager.FilterEngine;
import com.github.adamantcheese.chan.core.mapper.ChanPostUnparsedMapper;
import com.github.adamantcheese.chan.core.model.Post;
import com.github.adamantcheese.chan.core.model.orm.Filter;
import com.github.adamantcheese.chan.core.model.orm.Loadable;
import com.github.adamantcheese.chan.core.net.JsonReaderRequest;
import com.github.adamantcheese.chan.core.site.loader.ChanLoaderRequestParams;
import com.github.adamantcheese.chan.core.site.loader.ChanLoaderResponse;
import com.github.adamantcheese.chan.utils.DescriptorUtils;
import com.github.adamantcheese.chan.utils.Logger;
import com.github.adamantcheese.model.data.descriptor.ChanDescriptor;
import com.github.adamantcheese.model.data.descriptor.PostDescriptor;
import com.github.adamantcheese.model.data.post.ChanPostUnparsed;
import com.github.adamantcheese.model.repository.ChanPostRepository;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import javax.inject.Inject;

import kotlin.Unit;
import kotlin.system.TimingKt;
import okhttp3.HttpUrl;

import static com.github.adamantcheese.chan.Chan.inject;

/**
 * Process a typical imageboard json response.<br>
 * This class is highly multithreaded, take good care to not access models that are to be only
 * changed on the main thread.
 */
public class ChanReaderRequest
        extends JsonReaderRequest<ChanLoaderResponse> {
    private static final String TAG = "ChanReaderRequest";
    private static final int THREAD_COUNT;
    private static final ExecutorService EXECUTOR;
    private static final String threadFactoryName = "post_parser_thread_%d";
    private static final AtomicInteger threadIndex = new AtomicInteger(0);

    static {
        THREAD_COUNT = Runtime.getRuntime().availableProcessors();
        Logger.d(TAG, "Thread count: " + THREAD_COUNT);
        EXECUTOR = Executors.newFixedThreadPool(THREAD_COUNT, r -> {
            String threadName = String.format(Locale.ENGLISH, threadFactoryName, threadIndex.getAndIncrement());
            return new Thread(r, threadName);
        });
    }

    @Inject
    DatabaseManager databaseManager;
    @Inject
    FilterEngine filterEngine;
    @Inject
    ChanPostRepository chanPostRepository;

    private Loadable loadable;
    private List<Post> cached;
    private ChanReader reader;
    private DatabaseSavedReplyManager databaseSavedReplyManager;

    private List<Filter> filters;

    public ChanReaderRequest(ChanLoaderRequestParams request) {
        super(getChanUrl(request.loadable).toString(), request.listener, request.errorListener);
        inject(this);

        // Copy the loadable and cached list. The cached array may changed/cleared by other threads.
        loadable = request.loadable.clone();
        cached = new ArrayList<>(request.cached);
        reader = request.chanReader;

        filters = new ArrayList<>();
        List<Filter> enabledFilters = filterEngine.getEnabledFilters();
        for (Filter filter : enabledFilters) {
            if (filterEngine.matchesBoard(filter, loadable.board)) {
                // copy the filter because it will get used on other threads
                filters.add(filter.clone());
            }
        }

        databaseSavedReplyManager = databaseManager.getDatabaseSavedReplyManager();
    }

    private static HttpUrl getChanUrl(Loadable loadable) {
        HttpUrl url;

        if (loadable.site == null) {
            throw new NullPointerException("Loadable.site == null");
        }

        if (loadable.board == null) {
            throw new NullPointerException("Loadable.board == null");
        }

        if (loadable.isThreadMode()) {
            url = loadable.site.endpoints().thread(loadable.board, loadable);
        } else if (loadable.isCatalogMode()) {
            url = loadable.site.endpoints().catalog(loadable.board);
        } else {
            throw new IllegalArgumentException("Unknown mode");
        }
        return url;
    }

    @Override
    public Priority getPriority() {
        return Priority.HIGH;
    }

    @Override
    public ChanLoaderResponse readJson(JsonReader reader)
            throws Exception {
        ChanReaderProcessingQueue processing = new ChanReaderProcessingQueue(cached, loadable);

        if (loadable.isThreadMode()) {
            this.reader.loadThread(reader, processing);
            storePostsInDatabase(processing.getToParse());
        } else if (loadable.isCatalogMode()) {
            this.reader.loadCatalog(reader, processing);
        } else {
            throw new IllegalArgumentException("Unknown mode");
        }

        List<Post> list = parsePosts(processing);
        return processPosts(processing.getOp(), list);
    }

    private void storePostsInDatabase(List<Post.Builder> toParse) {
        if (toParse.isEmpty()) {
            return;
        }

        List<ChanPostUnparsed> unparsedPosts = new ArrayList<>(toParse.size());

        for (Post.Builder postBuilder : toParse) {
            PostDescriptor postDescriptor = PostDescriptor.create(
                    postBuilder.board.site.name(),
                    postBuilder.board.code,
                    postBuilder.getOpId(),
                    postBuilder.id
            );

            unparsedPosts.add(
                    ChanPostUnparsedMapper.fromPostBuilder(postDescriptor, postBuilder)
            );
        }

        long timeMs = TimingKt.measureTimeMillis(() -> {
            chanPostRepository.insertManyBlocking(unparsedPosts).unwrap();
            return Unit.INSTANCE;
        });

        Logger.d(TAG, "Successfully inserted " + unparsedPosts.size() +
                " posts into the database, took " + timeMs + "ms");
    }

    // Concurrently parses the new posts with an executor
    private List<Post> parsePosts(ChanReaderProcessingQueue queue)
            throws InterruptedException, ExecutionException {
        List<Post> cached = queue.getToReuse();
        List<Post> total = new ArrayList<>(cached);
        List<Post.Builder> toParse = queue.getToParse();
        ChanDescriptor descriptor = DescriptorUtils.getDescriptor(queue.getLoadable());

        List<Callable<Post>> tasks = new ArrayList<>(toParse.size());
        for (Post.Builder post : toParse) {
            tasks.add(new PostParseCallable(filterEngine,
                    filters,
                    databaseSavedReplyManager,
                    chanPostRepository,
                    post,
                    reader,
                    descriptor
            ));
        }

        if (!tasks.isEmpty()) {
            List<Future<Post>> futures = EXECUTOR.invokeAll(tasks);
            for (Future<Post> future : futures) {
                Post parsedPost = future.get();
                if (parsedPost != null) {
                    total.add(parsedPost);
                }
            }
        }

        return total;
    }

    private ChanLoaderResponse processPosts(Post.Builder op, List<Post> allPost) {
        ChanLoaderResponse response = new ChanLoaderResponse(op, new ArrayList<>(allPost.size()));

        List<Post> cachedPosts = new ArrayList<>();
        List<Post> newPosts = new ArrayList<>();
        if (cached.size() > 0) {
            // Add all posts that were parsed before
            cachedPosts.addAll(cached);

            Map<Integer, Post> cachedPostsByNo = new HashMap<>();
            for (Post post : cachedPosts) {
                cachedPostsByNo.put(post.no, post);
            }

            Map<Integer, Post> serverPostsByNo = new HashMap<>();
            for (Post post : allPost) {
                serverPostsByNo.put(post.no, post);
            }

            // If there's a cached post but it's not in the list received from the server, mark it as deleted
            if (loadable.isThreadMode()) {
                for (Post cachedPost : cachedPosts) {
                    cachedPost.deleted.set(!serverPostsByNo.containsKey(cachedPost.no));
                }
            }

            // If there's a post in the list from the server, that's not in the cached list, add it.
            for (Post serverPost : allPost) {
                if (!cachedPostsByNo.containsKey(serverPost.no)) {
                    newPosts.add(serverPost);
                }
            }
        } else {
            newPosts.addAll(allPost);
        }

        List<Post> allPosts = new ArrayList<>(cachedPosts.size() + newPosts.size());
        allPosts.addAll(cachedPosts);
        allPosts.addAll(newPosts);

        if (loadable.isThreadMode()) {
            Map<Integer, Post> postsByNo = new HashMap<>();
            for (Post post : allPosts) {
                postsByNo.put(post.no, post);
            }

            // Maps post no's to a list of no's that that post received replies from
            Map<Integer, List<Integer>> replies = new HashMap<>();

            for (Post sourcePost : allPosts) {
                for (int replyTo : sourcePost.getRepliesTo()) {
                    List<Integer> value = replies.get(replyTo);
                    if (value == null) {
                        value = new ArrayList<>(3);
                        replies.put(replyTo, value);
                    }
                    value.add(sourcePost.no);
                }
            }

            for (Map.Entry<Integer, List<Integer>> entry : replies.entrySet()) {
                int key = entry.getKey();
                List<Integer> value = entry.getValue();

                Post subject = postsByNo.get(key);
                // Sometimes a post replies to a ghost, a post that doesn't exist.
                if (subject != null) {
                    subject.setRepliesFrom(value);
                }
            }
        }

        response.posts.addAll(allPosts);

        return response;
    }

    public Loadable getLoadable() {
        return loadable;
    }
}
