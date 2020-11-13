package com.github.k1rakishou.model.data.post;

import android.text.SpannableString;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.github.k1rakishou.common.MurmurHashUtils;
import com.github.k1rakishou.core_spannable.PostLinkable;
import com.github.k1rakishou.model.data.descriptor.BoardDescriptor;
import com.github.k1rakishou.model.data.descriptor.PostDescriptor;
import com.github.k1rakishou.model.mapper.ChanPostMapper;
import com.github.k1rakishou.model.util.ChanPostUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import kotlin.Lazy;
import kotlin.LazyKt;

public class ChanPostBuilder {
    @Nullable
    public BoardDescriptor boardDescriptor;
    public long id = -1;
    public long opId = -1;
    public boolean op;
    public int totalRepliesCount = -1;
    public int threadImagesCount = -1;
    public int uniqueIps = -1;
    /**
     * When in rolling sticky thread this parameter means the maximum amount of posts in the
     * thread. Once the capacity is exceeded old posts are deleted right away (except the OP).
     * <p>
     * Be really careful with this param since we use it in the database query when selecting
     * thread's posts.
     */
    public int stickyCap = -1;
    public boolean sticky;
    public boolean closed;
    public boolean archived;
    public boolean deleted;
    public long lastModified = -1L;
    public String name = "";
    public PostCommentBuilder postCommentBuilder = PostCommentBuilder.create();
    public long unixTimestampSeconds = -1L;
    @NonNull
    public List<ChanPostImage> postImages = new ArrayList<>();
    @NonNull
    public List<ChanPostHttpIcon> httpIcons = new ArrayList<>();
    public String posterId = "";
    public String moderatorCapcode = "";
    public int idColor;
    public boolean isSavedReply;
    public Set<Long> repliesToIds = new HashSet<>();
    @Nullable
    public CharSequence tripcode;
    @Nullable
    public CharSequence subject;
    private PostDescriptor postDescriptor;

    private final Lazy<MurmurHashUtils.Murmur3Hash> commentHash = LazyKt.lazy(
            this,
            () -> ChanPostUtils.getPostHash(this)
    );

    public ChanPostBuilder() {
    }

    public synchronized MurmurHashUtils.Murmur3Hash getGetPostHash() {
        int commentUpdateCounter = postCommentBuilder.getCommentUpdateCounter();
        if (commentUpdateCounter > 1) {
            throw new IllegalStateException("Bad commentUpdateCounter: " + commentUpdateCounter);
        }

        return commentHash.getValue();
    }

    public synchronized boolean hasPostDescriptor() {
        if (boardDescriptor == null) {
            return false;
        }

        if (getOpId() < 0L) {
            return false;
        }

        if (id < 0L) {
            return false;
        }

        return true;
    }

    public synchronized PostDescriptor getPostDescriptor() {
        if (postDescriptor != null) {
            return postDescriptor;
        }

        Objects.requireNonNull(boardDescriptor);

        long opId = getOpId();
        if (opId < 0L) {
            throw new IllegalArgumentException("Bad opId: " + opId);
        }

        if (id < 0L) {
            throw new IllegalArgumentException("Bad post id: " + id);
        }

        postDescriptor = PostDescriptor.create(
                boardDescriptor.siteName(),
                boardDescriptor.getBoardCode(),
                opId,
                id
        );

        return postDescriptor;
    }

    public ChanPostBuilder boardDescriptor(BoardDescriptor boardDescriptor) {
        this.boardDescriptor = boardDescriptor;
        return this;
    }

    public ChanPostBuilder id(long id) {
        this.id = id;
        return this;
    }

    public ChanPostBuilder opId(long opId) {
        this.opId = opId;
        return this;
    }

    public ChanPostBuilder op(boolean op) {
        this.op = op;
        return this;
    }

    public ChanPostBuilder replies(int replies) {
        this.totalRepliesCount = replies;
        return this;
    }

    public ChanPostBuilder threadImagesCount(int imagesCount) {
        this.threadImagesCount = imagesCount;
        return this;
    }

    public ChanPostBuilder uniqueIps(int uniqueIps) {
        this.uniqueIps = uniqueIps;
        return this;
    }

    public ChanPostBuilder stickyCap(int cap) {
        this.stickyCap = cap;

        if (this.stickyCap == 0) {
            this.stickyCap = -1;
        }

        return this;
    }

    public ChanPostBuilder sticky(boolean sticky) {
        this.sticky = sticky;
        return this;
    }

    public ChanPostBuilder archived(boolean archived) {
        this.archived = archived;
        return this;
    }

    public ChanPostBuilder deleted(boolean deleted) {
        this.deleted = deleted;
        return this;
    }

    public ChanPostBuilder lastModified(long lastModified) {
        this.lastModified = lastModified;
        return this;
    }

    public ChanPostBuilder closed(boolean closed) {
        this.closed = closed;
        return this;
    }

    public ChanPostBuilder subject(CharSequence subject) {
        this.subject = subject;
        return this;
    }

    public ChanPostBuilder name(String name) {
        this.name = name;
        return this;
    }

    public ChanPostBuilder comment(CharSequence comment) {
        this.postCommentBuilder.setComment(new SpannableString(comment));
        return this;
    }

    public ChanPostBuilder tripcode(@Nullable CharSequence tripcode) {
        this.tripcode = tripcode;
        return this;
    }

    public ChanPostBuilder setUnixTimestampSeconds(long unixTimestampSeconds) {
        this.unixTimestampSeconds = unixTimestampSeconds;
        return this;
    }

    public ChanPostBuilder postImages(List<ChanPostImage> images, PostDescriptor ownerPostDescriptor) {
        synchronized (this) {
            this.postImages.addAll(images);

            for (ChanPostImage postImage : this.postImages) {
                postImage.setPostDescriptor(ownerPostDescriptor);
            }
        }

        return this;
    }

    public ChanPostBuilder posterId(String posterId) {
        this.posterId = posterId;

        // Stolen from the 4chan extension
        int hash = this.posterId.hashCode();

        int r = (hash >> 24) & 0xff;
        int g = (hash >> 16) & 0xff;
        int b = (hash >> 8) & 0xff;

        this.idColor = (0xff << 24) + (r << 16) + (g << 8) + b;
        return this;
    }

    public ChanPostBuilder moderatorCapcode(String moderatorCapcode) {
        this.moderatorCapcode = moderatorCapcode;
        return this;
    }

    public ChanPostBuilder addHttpIcon(ChanPostHttpIcon httpIcon) {
        httpIcons.add(httpIcon);
        return this;
    }

    public long getOpId() {
        if (!op) {
            return opId;
        }

        return id;
    }

    public ChanPostBuilder isSavedReply(boolean isSavedReply) {
        this.isSavedReply = isSavedReply;
        return this;
    }

    public ChanPostBuilder addLinkable(PostLinkable linkable) {
        synchronized (this) {
            this.postCommentBuilder.addPostLinkable(linkable);
            return this;
        }
    }

    public List<PostLinkable> getLinkables() {
        synchronized (this) {
            return postCommentBuilder.getAllLinkables();
        }
    }

    public ChanPostBuilder addReplyTo(long postId) {
        repliesToIds.add(postId);
        return this;
    }

    public ChanPost build() {
        if (boardDescriptor == null
                || id < 0
                || opId < 0
                || unixTimestampSeconds < 0
                || !postCommentBuilder.hasComment()
        ) {
            throw new IllegalArgumentException("Post data not complete" + toString());
        }

        return ChanPostMapper.fromPostBuilder(this);
    }

    @Override
    public String toString() {
        return "Builder{" +
                "id=" + id +
                ", opId=" + opId +
                ", op=" + op +
                ", postDescriptor=" + postDescriptor +
                ", unixTimestampSeconds=" + unixTimestampSeconds +
                ", subject='" + subject + '\'' +
                ", postCommentBuilder=" + postCommentBuilder +
                '}';
    }
}
