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

import com.github.adamantcheese.chan.core.database.DatabaseSavedReplyManager;
import com.github.adamantcheese.chan.core.manager.FilterEngine;
import com.github.adamantcheese.chan.core.model.Post;
import com.github.adamantcheese.chan.core.model.orm.Filter;

import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;

// Called concurrently to parse the post html and the filters on it
// belong to ChanReaderRequest
class PostParseCallable implements Callable<Post> {
    private static final String TAG = "PostParseCallable";

    private FilterEngine filterEngine;
    private List<Filter> filters;
    private DatabaseSavedReplyManager savedReplyManager;
    private Post.Builder post;
    private ChanReader reader;
    private Set<Long> internalIds;

    public PostParseCallable(
            FilterEngine filterEngine,
            List<Filter> filters,
            DatabaseSavedReplyManager savedReplyManager,
            Post.Builder post,
            ChanReader reader,
            Set<Long> internalIds
    ) {
        this.filterEngine = filterEngine;
        this.filters = filters;
        this.savedReplyManager = savedReplyManager;
        this.post = post;
        this.reader = reader;
        this.internalIds = internalIds;
    }

    @Override
    public Post call() {
        // needed for "Apply to own posts" to work correctly
        post.isSavedReply(savedReplyManager.isSaved(post.board, post.id));

        // Process the filters before finish, because parsing the html is dependent on filter matches
        processPostFilter(post);

        return reader.getParser().parse(null, post, new PostParser.Callback() {
            @Override
            public boolean isSaved(int postNo) {
                return savedReplyManager.isSaved(post.board, postNo);
            }

            @Override
            public boolean isInternal(long postNo) {
                return internalIds.contains(postNo);
            }
        });
    }

    private void processPostFilter(Post.Builder post) {
        for (Filter f : filters) {
            if (filterEngine.matches(f, post)) {
                FilterEngine.FilterAction action = FilterEngine.FilterAction.forId(f.action);
                switch (action) {
                    case COLOR:
                        post.filter(f.color, false, false, false, f.applyToReplies, f.onlyOnOP, f.applyToSaved);
                        break;
                    case HIDE:
                        post.filter(0, true, false, false, f.applyToReplies, f.onlyOnOP, false);
                        break;
                    case REMOVE:
                        post.filter(0, false, true, false, f.applyToReplies, f.onlyOnOP, false);
                        break;
                    case WATCH:
                        post.filter(0, false, false, true, false, true, false);
                }
            }
        }
    }
}
