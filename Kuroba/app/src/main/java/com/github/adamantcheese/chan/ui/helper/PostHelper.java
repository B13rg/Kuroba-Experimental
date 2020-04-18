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
package com.github.adamantcheese.chan.ui.helper;

import android.graphics.Bitmap;
import android.text.SpannableString;
import android.text.TextUtils;
import android.text.style.ImageSpan;

import androidx.annotation.Nullable;

import com.github.adamantcheese.chan.core.model.Post;
import com.github.adamantcheese.chan.core.model.orm.Loadable;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import static com.github.adamantcheese.chan.utils.AndroidUtils.getAppContext;

public class PostHelper {
    public static CharSequence prependIcon(CharSequence total, Bitmap bitmap, int height) {
        SpannableString string = new SpannableString("  ");
        ImageSpan imageSpan = new ImageSpan(getAppContext(), bitmap);

        int width = (int) (height / (bitmap.getHeight() / (float) bitmap.getWidth()));

        imageSpan.getDrawable().setBounds(0, 0, width, height);
        string.setSpan(imageSpan, 0, 1, 0);
        if (total == null) {
            return string;
        } else {
            return TextUtils.concat(string, " ", total);
        }
    }

    public static String getTitle(@Nullable Post post, @Nullable Loadable loadable) {
        if (post != null) {
            if (!TextUtils.isEmpty(post.subject)) {
                return post.subject.toString();
            } else if (!TextUtils.isEmpty(post.getComment())) {
                int length = Math.min(post.getComment().length(), 200);
                return "/" + post.boardId + "/ - " + post.getComment().subSequence(0, length);
            } else {
                return "/" + post.boardId + "/" + post.no;
            }
        } else if (loadable != null) {
            if (loadable.mode == Loadable.Mode.CATALOG) {
                return "/" + loadable.boardCode + "/";
            } else {
                return "/" + loadable.boardCode + "/" + loadable.no;
            }
        } else {
            return "";
        }
    }

    private static DateFormat dateFormat =
            SimpleDateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM, Locale.ENGLISH);
    private static Date tmpDate = new Date();

    public static String getLocalDate(Post post) {
        tmpDate.setTime(post.time * 1000L);
        return dateFormat.format(tmpDate);
    }
}
