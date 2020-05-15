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
package com.github.adamantcheese.chan.ui.toolbar;

import java.util.ArrayList;
import java.util.List;

public class ToolbarMenu {
    public static final int OVERFLOW_ID = 1000000;

    public final List<ToolbarMenuItem> items = new ArrayList<>();

    public void addItem(ToolbarMenuItem item) {
        items.add(item);
    }

    public ToolbarMenuItem findItem(int id) {
        for (ToolbarMenuItem item : items) {
            if (item.id == id) {
                return item;
            }
        }
        return null;
    }

    public ToolbarMenuSubItem findSubItem(int id) {
        ToolbarMenuItem overflow = findItem(OVERFLOW_ID);
        if (overflow != null) {
            for (ToolbarMenuSubItem subItem : overflow.subItems) {
                if (subItem.id == id) {
                    return subItem;
                }
            }
        }

        return null;
    }
}
