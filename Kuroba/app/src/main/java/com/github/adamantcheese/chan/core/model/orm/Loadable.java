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
package com.github.adamantcheese.chan.core.model.orm;

import android.os.Parcel;
import android.text.TextUtils;

import com.github.adamantcheese.chan.core.model.Post;
import com.github.adamantcheese.chan.core.settings.ChanSettings;
import com.github.adamantcheese.chan.core.site.Site;
import com.github.adamantcheese.model.data.descriptor.BoardDescriptor;
import com.github.adamantcheese.model.data.descriptor.ChanDescriptor;
import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;

import java.util.Locale;

import static com.github.adamantcheese.chan.utils.StringUtils.maskPostNo;

/**
 * Something that can be loaded, like a board or thread.
 * Used instead of {@link Board} or {@link Post} because of the unique things a loadable can do and save in the database:<br>
 * - It keeps track of the list index where the user last viewed.<br>
 * - It keeps track of what post was last seen and loaded.<br>
 * - It keeps track of the title the toolbar should show, generated from the first post (so after loading).<br>
 * <p>Obtain Loadables through {@link com.github.adamantcheese.chan.core.database.DatabaseLoadableManager} to make sure everyone
 * references the same loadable and that the loadable is properly saved in the database.
 */
@DatabaseTable(tableName = "loadable")
public class Loadable
        implements Cloneable {
    @DatabaseField(generatedId = true)
    public int id;

    @DatabaseField(columnName = "site")
    public int siteId;

    public transient Site site;

    /**
     * Mode for the loadable. Either thread or catalog.
     */
    @DatabaseField
    public int mode = Mode.INVALID;

    @DatabaseField(columnName = "board", canBeNull = false, index = true)
    public String boardCode = "";

    public Board board;

    /**
     * Thread number.
     */
    @DatabaseField(index = true)
    public int no = -1;

    @DatabaseField(canBeNull = false)
    public String title = "";

    @DatabaseField
    public int listViewIndex;

    @DatabaseField
    public int listViewTop;

    @DatabaseField
    public int lastViewed = -1;

    @DatabaseField
    public int lastLoaded = -1;

    public int markedNo = -1;

    // when the title, listViewTop, listViewIndex or lastViewed were changed
    public boolean dirty = false;

    private ChanDescriptor chanDescriptor;
    private BoardDescriptor boardDescriptor;

    public synchronized ChanDescriptor getChanDescriptor() {
        if (this.chanDescriptor != null) {
            return this.chanDescriptor;
        }

        switch (mode) {
            case Mode.CATALOG:
                this.chanDescriptor = new ChanDescriptor.CatalogDescriptor(board.boardDescriptor());
                break;
            case Mode.THREAD:
                this.chanDescriptor = new ChanDescriptor.ThreadDescriptor(board.boardDescriptor(), no);
                break;
            default:
                throw new IllegalStateException("Unknown mode: " + mode);
        }

        return this.chanDescriptor;
    }

    public synchronized BoardDescriptor getBoardDescriptor() {
        if (this.boardDescriptor != null) {
            return this.boardDescriptor;
        }

        this.boardDescriptor = new BoardDescriptor(site.siteDescriptor(), boardCode);
        return this.boardDescriptor;
    }

    /**
     * Constructs an empty loadable. The mode is INVALID.
     */
    protected Loadable() {
    }

    public static Loadable importLoadable(
            int siteId,
            int mode,
            String boardCode,
            int no,
            String title,
            int listViewIndex,
            int listViewTop,
            int lastViewed,
            int lastLoaded
    ) {
        Loadable loadable = new Loadable();
        loadable.siteId = siteId;
        loadable.mode = mode;
        loadable.boardCode = boardCode;
        loadable.no = no;
        loadable.title = title;
        loadable.listViewIndex = listViewIndex;
        loadable.listViewTop = listViewTop;
        loadable.lastViewed = lastViewed;
        loadable.lastLoaded = lastLoaded;

        return loadable;
    }

    public static Loadable emptyLoadable() {
        return new Loadable();
    }

    public static Loadable forCatalog(Board board) {
        Loadable loadable = new Loadable();
        loadable.siteId = board.siteId;
        loadable.site = board.site;
        loadable.board = board;
        loadable.boardCode = board.code;
        loadable.mode = Mode.CATALOG;
        return loadable;
    }

    public static Loadable forThread(Site site, Board board, long no, String title) {
        Loadable loadable = new Loadable();
        loadable.siteId = site.id();
        loadable.site = site;
        loadable.mode = Mode.THREAD;
        loadable.board = board;
        loadable.boardCode = board.code;
        loadable.no = (int) no;
        loadable.title = title;
        return loadable;
    }

    public Site getSite() {
        return site;
    }

    public Board getBoard() {
        return board;
    }

    public void setTitle(String title) {
        if (!TextUtils.equals(this.title, title)) {
            this.title = title;
            dirty = true;
        }
    }

    public void setLastViewed(long lastViewed) {
        if (this.lastViewed != lastViewed) {
            this.lastViewed = (int) lastViewed;
            dirty = true;
        }
    }

    public void setLastLoaded(long lastLoaded) {
        if (this.lastLoaded != lastLoaded) {
            this.lastLoaded = (int) lastLoaded;
            dirty = true;
        }
    }

    public void setListViewTop(int listViewTop) {
        if (this.listViewTop != listViewTop) {
            this.listViewTop = listViewTop;
            dirty = true;
        }
    }

    public void setListViewIndex(int listViewIndex) {
        if (this.listViewIndex != listViewIndex) {
            this.listViewIndex = listViewIndex;
            dirty = true;
        }
    }

    /**
     * Compares the mode, site, board and no.
     */
    @Override
    public boolean equals(Object object) {
        if (!(object instanceof Loadable)) return false;

        Loadable other = (Loadable) object;

        if (site.id() != other.siteId) {
            return false;
        }

        if (mode == other.mode) {
            switch (mode) {
                case Mode.INVALID:
                    return true;
                case Mode.CATALOG:
                    return boardCode.equals(other.boardCode);
                case Mode.THREAD:
                    return boardCode.equals(other.boardCode) && no == other.no;
                default:
                    throw new IllegalArgumentException();
            }
        } else {
            return false;
        }
    }

    @Override
    public int hashCode() {
        int result = mode;

        if (mode == Mode.THREAD || mode == Mode.CATALOG) {
            result = 31 * result + boardCode.hashCode();
        }
        if (mode == Mode.THREAD) {
            result = 31 * result + no;
        }
        return result;
    }

    @Override
    public String toString() {
        return "Loadable{id=" + id + ", mode=" + mode + ", board='" + boardCode + '\'' + ", no=" + maskPostNo(no) + '\''
                + ", listViewIndex=" + listViewIndex + ", listViewTop=" + listViewTop + ", lastViewed=" + maskPostNo(
                lastViewed) + ", lastLoaded=" + maskPostNo(lastLoaded) + ", markedNo=" + maskPostNo(markedNo)
                + ", dirty=" + dirty + '}';
    }

    public boolean isThreadMode() {
        return mode == Mode.THREAD;
    }

    public boolean isCatalogMode() {
        return mode == Mode.CATALOG;
    }

    // TODO(multi-site) remove
    public boolean isFromDatabase() {
        return id > 0;
    }

    /**
     * Extracts and converts to a string only the info that we are interested in from this loadable
     */
    public String toShortString() {
        return String.format(Locale.ENGLISH, "[%s, %s, %s]", site.name(), boardCode, maskPostNo(no));
    }

    public String desktopUrl() {
        return site.resolvable().desktopUrl(this, (long) no);
    }

    public boolean isSuitableForPrefetch() {
        if (!ChanSettings.autoLoadThreadImages.get()) {
            // Prefetching disabled
            return false;
        }

        return true;
    }

    public static Loadable readFromParcel(Parcel parcel) {
        Loadable loadable = new Loadable();
        /*loadable.id = */
        parcel.readInt();
        loadable.siteId = parcel.readInt();
        loadable.mode = parcel.readInt();
        loadable.boardCode = parcel.readString();
        loadable.no = parcel.readInt();
        loadable.title = parcel.readString();
        loadable.listViewIndex = parcel.readInt();
        loadable.listViewTop = parcel.readInt();
        return loadable;
    }

    public void writeToParcel(Parcel parcel) {
        parcel.writeInt(id);
        // TODO(multi-site)
        parcel.writeInt(siteId);
        parcel.writeInt(mode);
        // TODO(multi-site)
        parcel.writeString(boardCode);
        parcel.writeInt(no);
        parcel.writeString(title);
        parcel.writeInt(listViewIndex);
        parcel.writeInt(listViewTop);
    }

    @SuppressWarnings("MethodDoesntCallSuperMethod")
    public Loadable clone() {
        Loadable copy = new Loadable();
        copy.id = id;
        copy.siteId = siteId;
        copy.site = site;
        copy.mode = mode;
        // TODO: for empty loadables
        if (board != null) copy.board = board.clone();
        copy.boardCode = boardCode;
        copy.no = no;
        copy.title = title;
        copy.listViewIndex = listViewIndex;
        copy.listViewTop = listViewTop;
        copy.lastViewed = lastViewed;
        copy.lastLoaded = lastLoaded;

        return copy;
    }

    public static class Mode {
        public static final int INVALID = -1;
        public static final int THREAD = 0;
        public static final int CATALOG = 1;
    }
}
