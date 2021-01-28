package com.github.k1rakishou.chan.core.site.sites.foolfuuka

import com.github.k1rakishou.chan.core.site.common.CommonClientException
import com.github.k1rakishou.chan.core.site.common.CommonSite
import com.github.k1rakishou.chan.core.site.parser.ChanReaderProcessor
import com.github.k1rakishou.chan.utils.extractFileNameExtension
import com.github.k1rakishou.chan.utils.fixImageUrlIfNecessary
import com.github.k1rakishou.chan.utils.removeExtensionIfPresent
import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.common.jsonObject
import com.github.k1rakishou.common.nextStringOrNull
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.model.data.archive.ArchivePost
import com.github.k1rakishou.model.data.archive.ArchivePostMedia
import com.github.k1rakishou.model.data.bookmark.ThreadBookmarkInfoObject
import com.github.k1rakishou.model.data.descriptor.BoardDescriptor
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.filter.FilterWatchCatalogInfoObject
import com.github.k1rakishou.model.mapper.ArchiveThreadMapper
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import okhttp3.Request
import okhttp3.ResponseBody

class FoolFuukaApi(
  site: CommonSite
) : CommonSite.CommonApi(site) {

  override suspend fun loadThread(
    request: Request,
    responseBody: ResponseBody,
    chanReaderProcessor: ChanReaderProcessor
  ) {
    readBodyJson(responseBody) { jsonReader ->
      val chanDescriptor = chanReaderProcessor.chanDescriptor

      val threadDescriptor = chanDescriptor.threadDescriptorOrNull()
        ?: throw CommonClientException("chanDescriptor is not thread descriptor: ${chanDescriptor}")

      jsonReader.jsonObject {
        if (!hasNext()) {
          return@jsonObject
        }

        val jsonKey = nextName()
        if (jsonKey == "error") {
          val errorMessage = nextStringOrNull()
            ?: "No error message"
          throw ArchivesApiException(errorMessage)
        }

        val parsedThreadNo = jsonKey.toLongOrNull()
        if (parsedThreadNo == null || parsedThreadNo != threadDescriptor.threadNo) {
          Logger.e(TAG, "Bad parsedThreadNo: ${parsedThreadNo}, expected ${threadDescriptor.threadNo}")
          return@jsonObject
        }

        jsonObject {
          while (hasNext()) {
            when (nextName()) {
              "op" -> readOriginalPost(this, chanReaderProcessor)
              "posts" -> readRegularPosts(this, chanReaderProcessor)
              else -> skipValue()
            }
          }
        }
      }

      chanReaderProcessor.applyChanReadOptions()
    }
  }

  private suspend fun readOriginalPost(
    reader: JsonReader,
    chanReaderProcessor: ChanReaderProcessor
  ) {
    reader.jsonObject { readPostObject(reader, chanReaderProcessor, true) }
  }

  private suspend fun readRegularPosts(
    reader: JsonReader,
    chanReaderProcessor: ChanReaderProcessor
  ) {
    if (!reader.hasNext()) {
      return
    }

    reader.jsonObject {
      while (hasNext()) {
        // skip the json key
        nextName()

        reader.jsonObject { readPostObject(reader, chanReaderProcessor, false) }
      }
    }
  }

  private suspend fun readPostObject(
    reader: JsonReader,
    chanReaderProcessor: ChanReaderProcessor,
    expectedOp: Boolean
  ) {
    val chanDescriptor = chanReaderProcessor.chanDescriptor
    val boardDescriptor = chanDescriptor.boardDescriptor()

    val archivePost = reader.readPost(boardDescriptor)
    if (expectedOp != archivePost.isOP) {
      Logger.e(TAG, "Invalid archive post OP flag (expected: ${expectedOp}, actual: ${archivePost.isOP})")
      return
    }

    if (!archivePost.isValid()) {
      Logger.e(TAG, "Invalid archive post: ${archivePost}")
      return
    }

    val postBuilder = ArchiveThreadMapper.fromPost(
      boardDescriptor,
      archivePost
    )

    chanReaderProcessor.addPost(postBuilder)

    if (postBuilder.op) {
      chanReaderProcessor.setOp(postBuilder)
    }
  }

  private fun JsonReader.readPost(boardDescriptor: BoardDescriptor): ArchivePost {
    val archivePost = ArchivePost(boardDescriptor)

    while (hasNext()) {
      when (nextName()) {
        "num" -> archivePost.postNo = nextInt().toLong()
        "subnum" -> archivePost.postSubNo = nextInt().toLong()
        "thread_num" -> archivePost.threadNo = nextInt().toLong()
        "op" -> archivePost.isOP = nextInt() == 1
        "timestamp" -> archivePost.unixTimestampSeconds = nextInt().toLong()
        "capcode" -> archivePost.moderatorCapcode = nextStringOrNull() ?: ""
        "name_processed" -> archivePost.name = nextStringOrNull() ?: ""
        "title_processed" -> archivePost.subject = nextStringOrNull() ?: ""
        "comment_processed" -> archivePost.comment = nextStringOrNull() ?: ""
        "sticky" -> archivePost.sticky = nextInt() == 1
        "locked" -> archivePost.closed = nextInt() == 1
        "deleted" -> archivePost.archived = nextInt() == 1
        "trip_processed" -> archivePost.tripcode = nextStringOrNull() ?: ""
        "media" -> {
          if (hasNext()) {
            if (peek() == JsonToken.NULL) {
              skipValue()
            } else {
              jsonObject {
                val archivePostMedia = readPostMedia()

                if (!archivePostMedia.isValid()) {
                  Logger.e(TAG, "Invalid archive post media: ${archivePostMedia}")
                  return@jsonObject
                }

                archivePost.archivePostMediaList += archivePostMedia
              }
            }
          } else {
            skipValue()
          }
        }
        else -> skipValue()
      }
    }

    return archivePost
  }

  private fun JsonReader.readPostMedia(): ArchivePostMedia {
    val archivePostMedia = ArchivePostMedia()

    var mediaLink: String? = null
    var remoteMediaLink: String? = null

    while (hasNext()) {
      when (nextName()) {
        "spoiler" -> archivePostMedia.spoiler = nextInt() == 1
        "media_orig" -> {
          val serverFileName = nextStringOrNull()

          if (!serverFileName.isNullOrEmpty()) {
            archivePostMedia.serverFilename = removeExtensionIfPresent(serverFileName)
            archivePostMedia.extension = extractFileNameExtension(serverFileName)
          }
        }
        "media_filename_processed" -> {
          val filename = nextStringOrNull()
          if (filename == null) {
            archivePostMedia.filename = ""
          } else {
            archivePostMedia.filename = removeExtensionIfPresent(filename)
          }
        }
        "media_w" -> archivePostMedia.imageWidth = nextInt()
        "media_h" -> archivePostMedia.imageHeight = nextInt()
        "media_size" -> archivePostMedia.size = nextInt().toLong()
        "media_hash" -> archivePostMedia.fileHashBase64 = nextStringOrNull() ?: ""
        "banned" -> archivePostMedia.deleted = nextInt() == 1
        "media_link" -> mediaLink = nextStringOrNull()
        "remote_media_link" -> remoteMediaLink = nextStringOrNull()
        "thumb_link" -> archivePostMedia.thumbnailUrl = nextStringOrNull()
        else -> skipValue()
      }
    }

    if (mediaLink != null) {
      archivePostMedia.imageUrl = mediaLink
    } else {
      // archived.moe doesn't store original media on it's server but it sends links to original
      // media that is store on other archives' servers.
      archivePostMedia.imageUrl = remoteMediaLink
    }

    archivePostMedia.imageUrl = fixImageUrlIfNecessary(archivePostMedia.imageUrl)

    return archivePostMedia
  }

  override suspend fun loadCatalog(
    request: Request,
    responseBody: ResponseBody,
    chanReaderProcessor: ChanReaderProcessor
  ) {
    throw CommonClientException("Catalog is not supported for site ${site.name()}")
  }

  override suspend fun readThreadBookmarkInfoObject(
    threadDescriptor: ChanDescriptor.ThreadDescriptor,
    expectedCapacity: Int,
    reader: JsonReader
  ): ModularResult<ThreadBookmarkInfoObject> {
    val error = CommonClientException("Bookmarks are not supported for site ${site.name()}")

    return ModularResult.error(error)
  }

  override suspend fun readFilterWatchCatalogInfoObject(
    boardDescriptor: BoardDescriptor,
    request: Request,
    responseBody: ResponseBody
  ): ModularResult<FilterWatchCatalogInfoObject> {
    val error = CommonClientException("Filter watching is not supported for site ${site.name()}")

    return ModularResult.error(error)
  }

  class ArchivesApiException(message: String) : Exception(message)

  companion object {
    private const val TAG = "FoolFuukaApi"
  }

}