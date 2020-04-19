package com.github.adamantcheese.chan.core.database;

import android.annotation.SuppressLint;

import androidx.annotation.Nullable;

import com.github.adamantcheese.chan.Chan;
import com.github.adamantcheese.chan.core.model.Post;
import com.github.adamantcheese.chan.core.model.orm.PostHide;
import com.github.adamantcheese.chan.core.site.Site;
import com.github.adamantcheese.chan.utils.Logger;
import com.github.adamantcheese.chan.utils.PostUtils;
import com.j256.ormlite.stmt.DeleteBuilder;
import com.j256.ormlite.table.TableUtils;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;

import javax.inject.Inject;

import static com.github.adamantcheese.chan.Chan.inject;

public class DatabaseHideManager {
    private static final String TAG = "DatabaseHideManager";

    private static final long POST_HIDE_TRIM_TRIGGER = 25000;
    private static final long POST_HIDE_TRIM_COUNT = 5000;

    @Inject
    DatabaseHelper helper;

    public DatabaseHideManager() {
        inject(this);
    }

    public Callable<Void> load() {
        return () -> {
            Chan.instance(DatabaseManager.class)
                    .trimTable(helper.postHideDao, "posthide", POST_HIDE_TRIM_TRIGGER, POST_HIDE_TRIM_COUNT);

            return null;
        };
    }

    /**
     * Searches for hidden posts in the PostHide table then checks whether there are posts with a reply
     * to already hidden posts and if there are hides them as well.
     */
    public List<Post> filterHiddenPosts(List<Post> posts, int siteId, String board) {
        return Chan.instance(DatabaseManager.class).runTask(() -> {
            List<Long> postNoList = new ArrayList<>(posts.size());
            for (Post post : posts) {
                postNoList.add(post.no);
            }

            @SuppressLint("UseSparseArrays")
            Map<Long, Post> postsFastLookupMap = new LinkedHashMap<>();
            for (Post post : posts) {
                postsFastLookupMap.put(post.no, post);
            }

            applyFiltersToReplies(posts, postsFastLookupMap);

            Map<Long, PostHide> hiddenPostsLookupMap = getHiddenPosts(siteId, board, postNoList);

            // find replies to hidden posts and add them to the PostHide table in the database
            // and to the hiddenPostsLookupMap
            hideRepliesToAlreadyHiddenPosts(postsFastLookupMap, hiddenPostsLookupMap);

            List<Post> resultList = new ArrayList<>();

            // filter out hidden posts
            for (Post post : postsFastLookupMap.values()) {
                if (post.getPostFilter().getFilterRemove()) {
                    // this post is already filtered by some custom filter
                    continue;
                }

                PostHide hiddenPost = findHiddenPost(hiddenPostsLookupMap, post, siteId, board);
                if (hiddenPost != null) {
                    if (hiddenPost.hide) {
                        // hide post
                        Post newPost = rebuildPostWithCustomFilter(post,
                                0,
                                true,
                                false,
                                false,
                                hiddenPost.hideRepliesToThisPost,
                                false
                        );

                        resultList.add(newPost);
                    } else {
                        // remove post
                        if (post.isOP) {
                            // hide OP post only if the user hid the whole thread
                            if (!hiddenPost.wholeThread) {
                                resultList.add(post);
                            }
                        }
                    }
                } else {
                    // no record of hidden post in the DB
                    resultList.add(post);
                }
            }
            //return posts that are NOT hidden
            return resultList;
        });
    }

    private void hideRepliesToAlreadyHiddenPosts(
            Map<Long, Post> postsFastLookupMap,
            Map<Long, PostHide> hiddenPostsLookupMap
    ) throws SQLException {
        List<PostHide> newHiddenPosts = new ArrayList<>();

        for (Post post : postsFastLookupMap.values()) {
            if (hiddenPostsLookupMap.containsKey(post.no)) {
                continue;
            }

            for (Long replyNo : post.getRepliesTo()) {
                if (hiddenPostsLookupMap.containsKey(replyNo)) {
                    PostHide parentHiddenPost = hiddenPostsLookupMap.get(replyNo);
                    Post parentPost = postsFastLookupMap.get(replyNo);

                    if (
                            (parentPost == null || !parentPost.getPostFilter().getFilterRemove())
                                    || (parentHiddenPost == null  || !parentHiddenPost.hideRepliesToThisPost)
                    ) {
                        continue;
                    }

                    PostHide newHiddenPost = PostHide.hidePost(post,
                            false,
                            parentHiddenPost.hide,
                            true
                    );
                    hiddenPostsLookupMap.put((long) newHiddenPost.no, newHiddenPost);
                    newHiddenPosts.add(newHiddenPost);

                    //post is already hidden no need to check other replies
                    break;
                }
            }
        }

        if (newHiddenPosts.isEmpty()) {
            return;
        }

        for (PostHide postHide : newHiddenPosts) {
            helper.postHideDao.createIfNotExists(postHide);
        }
    }

    private void applyFiltersToReplies(List<Post> posts, Map<Long, Post> postsFastLookupMap) {
        for (Post post : posts) {
            if (post.isOP) continue; //skip the OP

            if (post.hasFilterParameters()) {
                if (post.getPostFilter().getFilterRemove() && post.getPostFilter().getFilterStub()) {
                    // wtf?
                    Logger.w(TAG, "Post has both filterRemove and filterStub flags");
                    continue;
                }

                applyPostFilterActionToChildPosts(post, postsFastLookupMap);
            }
        }
    }

    private Map<Long, PostHide> getHiddenPosts(int siteId, String board, List<Long> postNoList)
            throws SQLException {

        Set<PostHide> hiddenInDatabase = new HashSet<>(helper.postHideDao.queryBuilder()
                .where()
                .in("no", postNoList)
                .and()
                .eq("site", siteId)
                .and()
                .eq("board", board)
                .query());

        @SuppressLint("UseSparseArrays")
        Map<Long, PostHide> hiddenMap = new HashMap<>();

        for (PostHide postHide : hiddenInDatabase) {
            hiddenMap.put((long) postHide.no, postHide);
        }

        return hiddenMap;
    }

    /**
     * Takes filter parameters from the post and assigns them to all posts in the current reply chain.
     * If some post already has another filter's parameters - does not overwrite them.
     * Returns a chain of hidden posts.
     */
    private void applyPostFilterActionToChildPosts(Post parentPost, Map<Long, Post> postsFastLookupMap) {
        if (postsFastLookupMap.isEmpty() || !parentPost.getPostFilter().getFilterReplies()) {
            // do nothing with replies if filtering is disabled for replies
            return;
        }

        // find all replies to the post recursively
        Set<Post> postWithAllReplies =
                PostUtils.findPostWithReplies(parentPost.no, new ArrayList<>(postsFastLookupMap.values()));

        Set<Long> postNoWithAllReplies = new HashSet<>(postWithAllReplies.size());

        for (Post p : postWithAllReplies) {
            postNoWithAllReplies.add(p.no);
        }

        for (Long no : postNoWithAllReplies) {
            if (no == parentPost.no) {
                // do nothing with the parent post
                continue;
            }

            Post childPost = postsFastLookupMap.get(no);
            if (childPost == null) {
                // cross-thread post
                continue;
            }

            // do not overwrite filter parameters from another filter
            if (!childPost.hasFilterParameters()) {
                Post newPost = rebuildPostWithCustomFilter(childPost,
                        parentPost.getPostFilter().getFilterHighlightedColor(),
                        parentPost.getPostFilter().getFilterStub(),
                        parentPost.getPostFilter().getFilterRemove(),
                        parentPost.getPostFilter().getFilterWatch(),
                        true,
                        parentPost.getPostFilter().getFilterSaved()
                );

                // assign the filter parameters to the child post
                postsFastLookupMap.put(no, newPost);

                postWithAllReplies.remove(childPost);
                postWithAllReplies.add(newPost);
            }
        }
    }

    /**
     * Rebuilds a child post with custom filter parameters
     */
    private Post rebuildPostWithCustomFilter(
            Post childPost,
            int filterHighlightedColor,
            boolean filterStub,
            boolean filterRemove,
            boolean filterWatch,
            boolean filterReplies,
            boolean filterSaved
    ) {
        return new Post.Builder().board(childPost.board)
                .posterId(childPost.posterId)
                .opId(childPost.opNo)
                .id(childPost.no)
                .op(childPost.isOP)
                .replies(childPost.getTotalRepliesCount())
                .threadImagesCount(childPost.getThreadImagesCount())
                .uniqueIps(childPost.getUniqueIps())
                .sticky(childPost.isSticky())
                .archived(childPost.isArchived())
                .lastModified(childPost.getLastModified())
                .closed(childPost.isClosed())
                .subject(childPost.subject)
                .name(childPost.name)
                .comment(childPost.getComment())
                .tripcode(childPost.tripcode)
                .setUnixTimestampSeconds(childPost.time)
                .postImages(childPost.getPostImages())
                .moderatorCapcode(childPost.capcode)
                .setHttpIcons(childPost.httpIcons)
                .filter(filterHighlightedColor,
                        filterStub,
                        filterRemove,
                        filterWatch,
                        filterReplies,
                        false,
                        filterSaved
                )
                .isSavedReply(childPost.isSavedReply)
                .linkables(childPost.getLinkables())
                .repliesTo(childPost.getRepliesTo())
                .build();
    }

    @Nullable
    private PostHide findHiddenPost(Map<Long, PostHide> hiddenPostsLookupMap, Post post, int siteId, String board) {
        if (hiddenPostsLookupMap.isEmpty()) {
            return null;
        }

        PostHide maybeHiddenPost = hiddenPostsLookupMap.get(post.no);
        if (maybeHiddenPost != null && maybeHiddenPost.site == siteId && maybeHiddenPost.board.equals(board)) {
            return maybeHiddenPost;
        }

        return null;
    }

    public Callable<Void> addThreadHide(PostHide hide) {
        return () -> {
            if (contains(hide)) {
                return null;
            }

            helper.postHideDao.createIfNotExists(hide);

            return null;
        };
    }

    public Callable<Void> addPostsHide(List<PostHide> hideList) {
        return () -> {
            for (PostHide postHide : hideList) {
                if (contains(postHide)) continue;
                helper.postHideDao.createIfNotExists(postHide);
            }

            return null;
        };
    }

    public Callable<Void> removePostHide(PostHide hide) {
        return removePostsHide(Collections.singletonList(hide));
    }

    public Callable<Void> removePostsHide(List<PostHide> hideList) {
        return () -> {
            for (PostHide postHide : hideList) {
                DeleteBuilder<PostHide, Integer> deleteBuilder = helper.postHideDao.deleteBuilder();

                deleteBuilder.where()
                        .eq("no", postHide.no)
                        .and()
                        .eq("site", postHide.site)
                        .and()
                        .eq("board", postHide.board);

                deleteBuilder.delete();
            }

            return null;
        };
    }

    private boolean contains(PostHide hide)
            throws SQLException {
        PostHide inDb = helper.postHideDao.queryBuilder()
                .where()
                .eq("no", hide.no)
                .and()
                .eq("site", hide.site)
                .and()
                .eq("board", hide.board)
                .queryForFirst();

        //if this thread is already hidden - do nothing
        return inDb != null;
    }

    public Callable<Void> clearAllThreadHides() {
        return () -> {
            TableUtils.clearTable(helper.getConnectionSource(), PostHide.class);

            return null;
        };
    }

    public List<PostHide> getRemovedPostsWithThreadNo(long threadNo)
            throws SQLException {
        return helper.postHideDao.queryBuilder().where().eq("thread_no", threadNo).and().eq("hide", false).query();
    }

    public Callable<Void> deleteThreadHides(Site site) {
        return () -> {
            DeleteBuilder<PostHide, Integer> builder = helper.postHideDao.deleteBuilder();
            builder.where().eq("site", site.id());
            builder.delete();

            return null;
        };
    }
}
