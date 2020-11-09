/*
 * KurobaEx - *chan browser https://github.com/K1rakishou/Kuroba-Experimental/
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
package com.github.k1rakishou.chan.core.site.common.taimaba;

import androidx.annotation.NonNull;

import com.github.k1rakishou.chan.core.site.parser.CommentParser;
import com.github.k1rakishou.chan.core.site.parser.ICommentParser;
import com.github.k1rakishou.chan.core.site.parser.MockReplyManager;
import com.github.k1rakishou.chan.core.site.parser.style.StyleRule;
import com.github.k1rakishou.core_themes.ChanThemeColorId;

import java.util.regex.Pattern;

import static com.github.k1rakishou.common.AndroidUtils.sp;

public class TaimabaCommentParser extends CommentParser implements ICommentParser {
    private static final Pattern QUOTE_PATTERN = Pattern.compile("#(\\d+)");
    private static final Pattern FULL_QUOTE_PATTERN = Pattern.compile("/(\\w+)/thread/(\\d+)#(\\d+)");

    public TaimabaCommentParser(MockReplyManager mockReplyManager) {
        super(mockReplyManager);
        addDefaultRules();

        rule(StyleRule.tagRule("strike").strikeThrough());
        rule(StyleRule.tagRule("pre").monospace().size(sp(12f)));

        rule(StyleRule.tagRule("blockquote")
                .cssClass("unkfunc")
                .foregroundColorId(ChanThemeColorId.PostInlineQuoteColor)
                .linkify());
    }

    @NonNull
    @Override
    public Pattern getQuotePattern() {
        return QUOTE_PATTERN;
    }

    @NonNull
    @Override
    public Pattern getFullQuotePattern() {
        return FULL_QUOTE_PATTERN;
    }
}