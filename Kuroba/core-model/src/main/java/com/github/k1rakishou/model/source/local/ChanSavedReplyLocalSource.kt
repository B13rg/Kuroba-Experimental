package com.github.k1rakishou.model.source.local

import com.github.k1rakishou.model.KurobaDatabase
import com.github.k1rakishou.model.common.Logger
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.descriptor.PostDescriptor
import com.github.k1rakishou.model.data.post.ChanSavedReply
import com.github.k1rakishou.model.mapper.ChanSavedReplyMapper

class ChanSavedReplyLocalSource(
  database: KurobaDatabase,
  loggerTag: String,
  private val isDevFlavor: Boolean,
  private val logger: Logger
) : AbstractLocalSource(database) {
  private val chanSavedReplyDao = database.chanSavedReplyDao()

  suspend fun preloadForThread(threadDescriptor: ChanDescriptor.ThreadDescriptor): List<ChanSavedReply> {
    ensureInTransaction()

    return chanSavedReplyDao.loadAllForThread(
      threadDescriptor.siteName(),
      threadDescriptor.boardCode(),
      threadDescriptor.threadNo
    ).map { chanSavedReplyEntity -> ChanSavedReplyMapper.fromEntity(chanSavedReplyEntity) }
  }

  suspend fun unsavePost(postDescriptor: PostDescriptor) {
    ensureInTransaction()

    chanSavedReplyDao.delete(
      postDescriptor.threadDescriptor().siteName(),
      postDescriptor.threadDescriptor().boardDescriptor.boardCode,
      postDescriptor.threadDescriptor().threadNo,
      postDescriptor.postNo,
      postDescriptor.postSubNo
    )
  }

  suspend fun savePost(chanSavedReply: ChanSavedReply) {
    ensureInTransaction()

    chanSavedReplyDao.insertOrIgnore(
      ChanSavedReplyMapper.toEntity(chanSavedReply)
    )
  }

  private val TAG = "$loggerTag ChanSavedReplyLocalSource"
}