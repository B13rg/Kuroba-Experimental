package com.github.k1rakishou.chan.features.bookmarks.data

import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import okhttp3.HttpUrl

data class ThreadBookmarkItemView(
  val threadDescriptor: ChanDescriptor.ThreadDescriptor,
  val title: String,
  val thumbnailUrl: HttpUrl?,
  val highlight: Boolean,
  val threadBookmarkStats: ThreadBookmarkStats
)