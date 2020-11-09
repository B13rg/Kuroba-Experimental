package com.github.k1rakishou.model.data.serializable;

import androidx.annotation.Nullable;

import com.github.k1rakishou.core_spannable.serializable.SerializableSpannableString;
import com.google.gson.annotations.SerializedName;

import java.util.List;
import java.util.Objects;
import java.util.Set;

public class SerializablePost {
    @SerializedName("board_id")
    private String boardId;
    @SerializedName("serializable_board")
    private SerializableBoard board;
    @SerializedName("no")
    private Long no;
    @SerializedName("is_op")
    private boolean isOP;
    @SerializedName("name")
    private String name;
    @SerializedName("comment")
    private SerializableSpannableString comment;
    @SerializedName("subject")
    private SerializableSpannableString subject;
    @SerializedName("time")
    private long time;
    @SerializedName("images")
    private List<SerializablePostImage> images;
    @SerializedName("tripcode_with_spans")
    private SerializableSpannableString tripcode;
    @SerializedName("id")
    private String id;
    @SerializedName("op_id")
    private Long opId;
    @SerializedName("capcode")
    private String capcode;
    @SerializedName("is_saved_reply")
    private boolean isSavedReply;
    @SerializedName("filter_highlighted_color")
    private int filterHighlightedColor;
    @SerializedName("filter_stub")
    private boolean filterStub;
    @SerializedName("filter_remove")
    private boolean filterRemove;
    @SerializedName("filter_watch")
    private boolean filterWatch;
    @SerializedName("filter_replies")
    private boolean filterReplies;
    @SerializedName("filter_only_op")
    private boolean filterOnlyOP;
    @SerializedName("filter_saved")
    private boolean filterSaved;
    @SerializedName("replies_to")
    private Set<Long> repliesTo;
    @SerializedName("deleted")
    private Boolean deleted;
    @SerializedName("replies_from")
    private List<Long> repliesFrom;
    @SerializedName("sticky")
    private boolean sticky;
    @SerializedName("closed")
    private boolean closed;
    @SerializedName("archived")
    private boolean archived;
    @SerializedName("replies")
    private int replies;
    @SerializedName("images_count")
    private int imagesCount;
    @SerializedName("unique_ips")
    private int uniqueIps;
    @SerializedName("last_modified")
    private long lastModified;
    @SerializedName("title")
    private String title;

    public SerializablePost(
            String boardId,
            SerializableBoard board,
            long no,
            boolean isOP,
            String name,
            SerializableSpannableString comment,
            SerializableSpannableString subject,
            long time,
            List<SerializablePostImage> images,
            SerializableSpannableString tripcode,
            String id,
            long opId,
            String capcode,
            boolean isSavedReply,
            int filterHighlightedColor,
            boolean filterStub,
            boolean filterRemove,
            boolean filterWatch,
            boolean filterReplies,
            boolean filterOnlyOP,
            boolean filterSaved,
            Set<Long> repliesTo,
            Boolean deleted,
            List<Long> repliesFrom,
            boolean sticky,
            boolean closed,
            boolean archived,
            int replies,
            int imagesCount,
            int uniqueIps,
            long lastModified,
            String title
    ) {
        this.boardId = boardId;
        this.board = board;
        this.no = no;
        this.isOP = isOP;
        this.name = name;
        this.comment = comment;
        this.subject = subject;
        this.time = time;
        this.images = images;
        this.tripcode = tripcode;
        this.id = id;
        this.opId = opId;
        this.capcode = capcode;
        this.isSavedReply = isSavedReply;
        this.filterHighlightedColor = filterHighlightedColor;
        this.filterStub = filterStub;
        this.filterRemove = filterRemove;
        this.filterWatch = filterWatch;
        this.filterReplies = filterReplies;
        this.filterOnlyOP = filterOnlyOP;
        this.filterSaved = filterSaved;
        this.repliesTo = repliesTo;
        this.deleted = deleted;
        this.repliesFrom = repliesFrom;
        this.sticky = sticky;
        this.closed = closed;
        this.archived = archived;
        this.replies = replies;
        this.imagesCount = imagesCount;
        this.uniqueIps = uniqueIps;
        this.lastModified = lastModified;
        this.title = title;
    }

    public String getBoardId() {
        return boardId;
    }

    public long getNo() {
        return no;
    }

    public boolean isOP() {
        return isOP;
    }

    public String getName() {
        return name;
    }

    public SerializableSpannableString getComment() {
        return comment;
    }

    public SerializableSpannableString getSubject() {
        return subject;
    }

    public long getTime() {
        return time;
    }

    public List<SerializablePostImage> getImages() {
        return images;
    }

    public SerializableSpannableString getTripcode() {
        return tripcode;
    }

    public String getId() {
        return id;
    }

    public long getOpId() {
        return opId;
    }

    public String getCapcode() {
        return capcode;
    }

    public boolean isSavedReply() {
        return isSavedReply;
    }

    public int getFilterHighlightedColor() {
        return filterHighlightedColor;
    }

    public boolean isFilterStub() {
        return filterStub;
    }

    public boolean isFilterRemove() {
        return filterRemove;
    }

    public boolean isFilterWatch() {
        return filterWatch;
    }

    public boolean isFilterReplies() {
        return filterReplies;
    }

    public boolean isFilterOnlyOP() {
        return filterOnlyOP;
    }

    public boolean isFilterSaved() {
        return filterSaved;
    }

    public Set<Long> getRepliesTo() {
        return repliesTo;
    }

    public Boolean getDeleted() {
        return deleted;
    }

    public List<Long> getRepliesFrom() {
        return repliesFrom;
    }

    public boolean isSticky() {
        return sticky;
    }

    public boolean isClosed() {
        return closed;
    }

    public boolean isArchived() {
        return archived;
    }

    public int getReplies() {
        return replies;
    }

    public int getThreadImagesCount() {
        return imagesCount;
    }

    public int getUniqueIps() {
        return uniqueIps;
    }

    public long getLastModified() {
        return lastModified;
    }

    public String getTitle() {
        return title;
    }

    @Override
    public int hashCode() {
        return 31 * Objects.hashCode(no) + 31 * board.code.hashCode() + 31 * board.siteId;
    }

    @Override
    public boolean equals(@Nullable Object other) {
        if (other == null) {
            return false;
        }

        if (other == this) {
            return true;
        }

        if (this.getClass() != other.getClass()) {
            return false;
        }

        SerializablePost otherPost = (SerializablePost) other;

        return this.no.equals(otherPost.no)
                && this.board.code.equals(otherPost.board.code)
                && this.board.siteId == otherPost.board.siteId;
    }
}
