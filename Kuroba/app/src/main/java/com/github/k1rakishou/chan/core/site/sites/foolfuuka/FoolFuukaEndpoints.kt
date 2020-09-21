package com.github.k1rakishou.chan.core.site.sites.foolfuuka

import com.github.k1rakishou.chan.core.model.Post
import com.github.k1rakishou.chan.core.site.common.CommonSite
import com.github.k1rakishou.model.data.board.ChanBoard
import com.github.k1rakishou.model.data.descriptor.BoardDescriptor
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import okhttp3.HttpUrl
import java.util.regex.Pattern

class FoolFuukaEndpoints(
  site: CommonSite,
  private val rootUrl: HttpUrl
) : CommonSite.CommonEndpoints(site) {

  override fun catalog(boardDescriptor: BoardDescriptor): HttpUrl {
    throw IllegalStateException("Catalog is not supported by ${site.name()}")
  }

  // https://archived.moe/_/api/chan/thread/?board=a&num=208364509
  override fun thread(threadDescriptor: ChanDescriptor.ThreadDescriptor): HttpUrl {
    return rootUrl.newBuilder()
      .addPathSegments("_/api/chan/thread")
      .addQueryParameter("board", threadDescriptor.boardCode())
      .addQueryParameter("num", threadDescriptor.threadNo.toString())
      .build()
  }

  override fun imageUrl(post: Post.Builder, arg: Map<String, String>): HttpUrl {
    throw NotImplementedError("imageUrl")
  }

  // https://archived.moe/files/a/thumb/1599/43/1599433446565s.jpg
  override fun thumbnailUrl(post: Post.Builder, spoiler: Boolean, customSpoilers: Int, arg: Map<String, String>): HttpUrl {
    val param1 = requireNotNull(arg[THUMBNAIL_PARAM_1]) { "THUMBNAIL_PARAM_1_NAME not provided" }
    val param2 = requireNotNull(arg[THUMBNAIL_PARAM_2]) { "THUMBNAIL_PARAM_2_NAME not provided" }
    val fileId = requireNotNull(arg[THUMBNAIL_FILE_ID]) { "THUMBNAIL_FILE_ID not provided" }
    val extension = requireNotNull(arg[THUMBNAIL_EXTENSION]) { "THUMBNAIL_EXTENSION not provided" }

    return rootUrl.newBuilder()
      .addPathSegments("files/a/thumb/$param1/$param2/${fileId}s.${extension}")
      .build()
  }

  override fun icon(icon: String, arg: Map<String, String>?): HttpUrl {
    throw NotImplementedError("icon")
  }

  override fun boards(): HttpUrl {
    throw NotImplementedError("boards")
  }

  override fun pages(board: ChanBoard?): HttpUrl {
    throw NotImplementedError("pages")
  }

  override fun archive(board: ChanBoard): HttpUrl {
    throw NotImplementedError("archive")
  }

  override fun reply(chanDescriptor: ChanDescriptor): HttpUrl {
    throw NotImplementedError("reply")
  }

  override fun delete(post: Post): HttpUrl {
    throw NotImplementedError("delete")
  }

  override fun report(post: Post): HttpUrl {
    throw NotImplementedError("report")
  }

  override fun login(): HttpUrl {
    throw NotImplementedError("login")
  }

  companion object {
    val THUMBNAIL_PARAMS_PATTERN = Pattern.compile("thumb/(\\d+)/(\\d+)/\\d+s.")

    const val THUMBNAIL_PARAM_1 = "param1"
    const val THUMBNAIL_PARAM_2 = "param2"
    const val THUMBNAIL_FILE_ID = "file_id"
    const val THUMBNAIL_EXTENSION = "extension"
  }
}