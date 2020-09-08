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
package com.github.adamantcheese.chan.core.site.loader;

import com.github.adamantcheese.chan.core.model.Post;
import com.github.adamantcheese.chan.core.site.parser.ChanReader;
import com.github.adamantcheese.model.data.descriptor.ChanDescriptor;

import java.util.List;

public class ChanLoaderRequestParams {
    public final ChanDescriptor chanDescriptor;
    public final ChanReader chanReader;
    public final List<Post> cached;

    public ChanLoaderRequestParams(
            ChanDescriptor chanDescriptor,
            ChanReader chanReader,
            List<Post> cached
    ) {
        this.chanDescriptor = chanDescriptor;
        this.chanReader = chanReader;
        this.cached = cached;
    }

    @Override
    public String toString() {
        return "ChanLoaderRequestParams{" +
                "chanDescriptor=" + chanDescriptor +
                ", chanReader=" + chanReader.getClass().getSimpleName() +
                ", cached.size=" + cached.size() +
                '}';
    }
}
