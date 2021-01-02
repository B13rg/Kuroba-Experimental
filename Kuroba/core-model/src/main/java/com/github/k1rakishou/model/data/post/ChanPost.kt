package com.github.k1rakishou.model.data.post

import com.github.k1rakishou.model.data.descriptor.BoardDescriptor
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.descriptor.PostDescriptor
import java.util.*

open class ChanPost(
  val chanPostId: Long,
  val postDescriptor: PostDescriptor,
  val postImages: List<ChanPostImage>,
  val postIcons: List<ChanPostHttpIcon>,
  val repliesTo: Set<Long>,
  val timestamp: Long = -1L,
  val postComment: PostComment,
  val subject: CharSequence?,
  val tripcode: CharSequence?,
  val name: String? = null,
  val posterId: String? = null,
  val moderatorCapcode: String? = null,
  val isSavedReply: Boolean = false,
  repliesFrom: Set<Long>? = null
) {
  /**
   * We use this map to avoid infinite loops when binding posts since after all post content
   * loaders have done their jobs we update the post via notifyItemChange, which triggers
   * onPostBind() again.
   */
  private val onDemandContentLoadedMap = HashMap<LoaderType, LoaderContentLoadState>()

  @get:Synchronized
  var deleted: Boolean = false
    private set

  @get:Synchronized
  val repliesFrom = mutableSetOf<Long>()

  @Synchronized
  fun postNo(): Long = postDescriptor.postNo
  @Synchronized
  fun firstImage(): ChanPostImage? = postImages.firstOrNull()

  @Synchronized
  fun setPostDeleted(isDeleted: Boolean) {
    deleted = isDeleted
  }

  @Synchronized
  fun isOP(): Boolean = postDescriptor.isOP()

  @get:Synchronized
  val repliesFromCount: Int
    get() = repliesFrom.size
  @get:Synchronized
  val postImagesCount: Int
    get() = postImages.size

  @get:Synchronized
  open val catalogRepliesCount: Int
    get() = 0
  @get:Synchronized
  open val catalogImagesCount: Int
    get() = 0

  val boardDescriptor: BoardDescriptor
    get() = postDescriptor.boardDescriptor()

  init {
    onDemandContentLoadedMap.clear()

    for (loaderType in LoaderType.values()) {
      onDemandContentLoadedMap[loaderType] = when (isOP()) {
        true -> OriginalPostContentLoadState()
        false -> PostContentLoadState()
      }
    }

    repliesFrom?.let { replies -> this.repliesFrom.addAll(replies) }
  }

  @Synchronized
  open fun isContentLoadedForLoader(chanDescriptor: ChanDescriptor, loaderType: LoaderType): Boolean {
    return onDemandContentLoadedMap[loaderType]
      ?.isContentLoadedFor(chanDescriptor)
      ?: false
  }

  @Synchronized
  open fun setContentLoadedForLoader(chanDescriptor: ChanDescriptor, loaderType: LoaderType) {
    onDemandContentLoadedMap[loaderType]?.setContentLoadedFor(chanDescriptor)
  }

  @Synchronized
  fun setContentLoaded(loaderType: LoaderType) {
    onDemandContentLoadedMap[loaderType]?.setContentLoaded()
  }

  @Synchronized
  open fun allLoadersCompletedLoading(): Boolean {
    return onDemandContentLoadedMap.values.all { loaderContentLoadState ->
      loaderContentLoadState.everythingLoaded()
    }
  }

  @Synchronized
  fun copyOnDemandContentLoadedMap(): Map<LoaderType, LoaderContentLoadState> {
    val newMap = mutableMapOf<LoaderType, LoaderContentLoadState>()

    onDemandContentLoadedMap.forEach { (loaderType, state) ->
      newMap[loaderType] = state
    }

    return newMap
  }

  @Synchronized
  fun replaceOnDemandContentLoadedMap(newMap: Map<LoaderType, LoaderContentLoadState>) {
    onDemandContentLoadedMap.clear()
    onDemandContentLoadedMap.putAll(newMap)

    LoaderType.values().forEach { loaderType ->
      check(onDemandContentLoadedMap.containsKey(loaderType)) {
        "No loaderType (${loaderType}) was found in input map!"
      }
    }
  }

  @Synchronized
  open fun updatePostImageSize(fileUrl: String, fileSize: Long) {
    for (postImage in postImages) {
      if (postImage.imageUrl != null && postImage.imageUrl.toString() == fileUrl) {
        postImage.setSize(fileSize)
        return
      }
    }
  }

  @Synchronized
  open fun iterateRepliesFrom(iterator: (Long) -> Unit) {
    for (replyNo in repliesFrom) {
      iterator.invoke(replyNo)
    }
  }

  @Synchronized
  fun iteratePostImages(iterator: (ChanPostImage) -> Unit) {
    for (postImage in postImages) {
      iterator.invoke(postImage)
    }
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is ChanPost) return false

    if (postDescriptor != other.postDescriptor) {
      return false
    }
    if (!arePostImagesTheSame(other)) {
      return false
    }
    if (postComment != other.postComment) {
      return false
    }
    if (subject != other.subject) {
      return false
    }
    if (name != other.name) {
      return false
    }
    if (tripcode != other.tripcode) {
      return false
    }
    if (posterId != other.posterId) {
      return false
    }
    if (moderatorCapcode != other.moderatorCapcode) {
      return false
    }

    return true
  }

  private fun arePostImagesTheSame(other: ChanPost): Boolean {
    if (postImages.size != other.postImages.size) {
      return false
    }

    return postImages.indices.none { postImages[it] != other.postImages[it] }
  }

  override fun hashCode(): Int {
    var result = chanPostId.hashCode()
    result = 31 * result + postDescriptor.hashCode()
    result = 31 * result + repliesTo.hashCode()
    result = 31 * result + postImages.hashCode()
    result = 31 * result + postComment.hashCode()
    result = 31 * result + subject.hashCode()
    result = 31 * result + (name?.hashCode() ?: 0)
    result = 31 * result + tripcode.hashCode()
    result = 31 * result + (posterId?.hashCode() ?: 0)
    result = 31 * result + (moderatorCapcode?.hashCode() ?: 0)
    result = 31 * result + isSavedReply.hashCode()
    return result
  }

  override fun toString(): String {
    return "ChanPost{" +
      "chanPostId=" + chanPostId +
      ", postDescriptor=" + postDescriptor +
      ", postImages=" + postImages.size +
      ", subject='" + subject + '\'' +
      ", postComment=" + postComment.originalComment().take(64) +
      '}'
  }

}