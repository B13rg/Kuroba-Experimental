package com.github.k1rakishou.chan.features.reply.data

import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.common.ModularResult.Companion.Try
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.google.gson.Gson
import java.io.File
import java.io.IOException

class ReplyFile(
  private val gson: Gson,
  val fileOnDisk: File,
  val fileMetaOnDisk: File,
  val previewFileOnDisk: File
) {
  private var replyFileMeta: ReplyFileMeta? = null

  @Synchronized
  fun getReplyFileMeta(): ModularResult<ReplyFileMeta> {
    if (replyFileMeta != null) {
      return ModularResult.value(replyFileMeta!!)
    }

    if (!fileMetaOnDisk.exists() || !fileMetaOnDisk.canRead()) {
      return ModularResult.error(IOException("File meta does not exist or cannot be read"))
    }

    val result = Try {
      return@Try gson.fromJson<ReplyFileMeta>(
        fileMetaOnDisk.readText(),
        ReplyFileMeta::class.java
      )
    }

    if (result is ModularResult.Error) {
      return result
    }

    result as ModularResult.Value
    replyFileMeta = result.value

    return result
  }

  @Synchronized
  fun storeFileMetaInfo(newReplyFileMeta: ReplyFileMeta? = null): ModularResult<Unit> {
    if (newReplyFileMeta != null) {
      replyFileMeta = newReplyFileMeta
    }

    checkNotNull(replyFileMeta) { "replyFileMeta is null!" }

    return Try {
      if (!fileMetaOnDisk.exists()) {
        if (!fileMetaOnDisk.canWrite()) {
          throw IOException("Cannot write to fileMetaOnDisk: ${fileMetaOnDisk.absolutePath}")
        }

        if (!fileMetaOnDisk.createNewFile()) {
          throw IOException("Failed to create fileMetaOnDisk: ${fileMetaOnDisk.absolutePath}")
        }
      }

      val json = gson.toJson(replyFileMeta)
      fileMetaOnDisk.writeText(json)
    }
  }

  @Synchronized
  fun markFileAsTaken(chanDescriptor: ChanDescriptor): ModularResult<Boolean> {
    return Try {
      val replyFileMeta = getReplyFileMeta().unwrap()

      if (replyFileMeta.fileTakenBy != null) {
        // File already taken
        return@Try false
      }

      replyFileMeta.fileTakenBy = ReplyChanDescriptor.fromChanDescriptor(chanDescriptor)
      storeFileMetaInfo().unwrap()

      return@Try true
    }
  }

  @Synchronized
  fun updateFileSelection(selected: Boolean): ModularResult<Boolean> {
    return Try {
      val replyFileMeta = getReplyFileMeta().unwrap()
      if (replyFileMeta.selected == selected) {
        return@Try true
      }

      replyFileMeta.selected = selected
      storeFileMetaInfo().unwrap()

      return@Try true
    }
  }

  @Synchronized
  fun clearSelection(): ModularResult<Unit> {
    return Try {
      val replyFileMeta = getReplyFileMeta().unwrap()
      replyFileMeta.selected = false
      storeFileMetaInfo().unwrap()
    }
  }

  @Synchronized
  fun markFilesAsNotTaken(): ModularResult<Unit> {
    return Try {
      val replyFileMeta = getReplyFileMeta().unwrap()

      if (replyFileMeta.fileTakenBy == null) {
        // File is not taken
        return@Try
      }

      replyFileMeta.fileTakenBy = null
      storeFileMetaInfo().unwrap()

      return@Try
    }
  }

  @Synchronized
  fun deleteFromDisk() {
    if (fileOnDisk.exists() && !fileOnDisk.delete()) {
      Logger.e("ReplyFile", "Failed to delete: ${fileOnDisk.absolutePath}")
    }

    if (fileMetaOnDisk.exists() && !fileMetaOnDisk.delete()) {
      Logger.e("ReplyFile", "Failed to delete: ${fileMetaOnDisk.absolutePath}")
    }

    if (previewFileOnDisk.exists() && !previewFileOnDisk.delete()) {
      Logger.e("ReplyFile", "Failed to delete: ${previewFileOnDisk.absolutePath}")
    }
  }

  @Synchronized
  fun shortState(): String {
    return buildString {
      append("fileOnDisk=")
      append("'")
      append(fileOnDisk.absolutePath)
      append("'")
      append(", ")

      append("fileMetaOnDisk=")
      append("'")
      append(fileMetaOnDisk.absolutePath)
      append("'")
      append(", ")

      append("replyFileMeta=")
      append("'")
      append(replyFileMeta)
      append("'")
    }
  }

}