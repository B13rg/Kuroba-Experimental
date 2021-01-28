package com.github.k1rakishou.chan.core.site.sites.fuuka

import com.github.k1rakishou.ChanSettings
import com.github.k1rakishou.chan.core.site.common.CommonClientException
import com.github.k1rakishou.chan.core.site.common.CommonSite
import com.github.k1rakishou.chan.core.site.parser.ChanReaderProcessor
import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.core_parser.html.KurobaHtmlParserCollector
import com.github.k1rakishou.core_parser.html.KurobaHtmlParserCommandExecutor
import com.github.k1rakishou.model.data.archive.ArchivePost
import com.github.k1rakishou.model.data.archive.ArchivePostMedia
import com.github.k1rakishou.model.data.bookmark.ThreadBookmarkInfoObject
import com.github.k1rakishou.model.data.descriptor.BoardDescriptor
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.filter.FilterWatchCatalogInfoObject
import com.github.k1rakishou.model.mapper.ArchiveThreadMapper
import com.google.gson.stream.JsonReader
import okhttp3.Request
import okhttp3.ResponseBody

class FuukaApi(
  site: CommonSite
) : CommonSite.CommonApi(site) {
  private val verboseLogs by lazy { ChanSettings.verboseLogs.get() }

  private val threadParseCommandBuffer = FuukaApiThreadPostParseCommandBufferBuilder(verboseLogs)
    .getBuilder()
    .build()

  override suspend fun loadThread(
    request: Request,
    responseBody: ResponseBody,
    chanReaderProcessor: ChanReaderProcessor
  ) {
    readBodyHtml(request, responseBody) { document ->
      require(chanReaderProcessor.chanDescriptor is ChanDescriptor.ThreadDescriptor) {
        "Cannot load catalogs here!"
      }

      val threadDescriptor = chanReaderProcessor.chanDescriptor
      val collector = ArchiveThreadPostCollector(threadDescriptor)
      val parserCommandExecutor = KurobaHtmlParserCommandExecutor<ArchiveThreadPostCollector>()

      try {
        parserCommandExecutor.executeCommands(
          document,
          threadParseCommandBuffer,
          collector
        )
      } catch (error: Throwable) {
        Logger.e(TAG, "parserCommandExecutor.executeCommands() error", error)
        return@readBodyHtml
      }

      val postBuilders = collector.archivePosts.map { archivePost ->
        return@map ArchiveThreadMapper.fromPost(threadDescriptor.boardDescriptor, archivePost)
      }

      val originalPost = postBuilders.firstOrNull()
      if (originalPost == null || !originalPost.op) {
        Logger.e(TAG, "Failed to parse original post or first post is not original post for some reason")
        return@readBodyHtml
      }

      chanReaderProcessor.setOp(originalPost)
      postBuilders.forEach { chanPostBuilder -> chanReaderProcessor.addPost(chanPostBuilder) }
    }
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

  data class ArchiveThreadPostCollector(
    val threadDescriptor: ChanDescriptor.ThreadDescriptor,
    val archivePosts: MutableList<ArchivePost> = mutableListOf()
  ) : KurobaHtmlParserCollector {

    fun lastPostOrNull(): ArchivePost? {
      return archivePosts.lastOrNull()
    }

    fun lastMediaOrNull(): ArchivePostMedia? {
      return archivePosts.lastOrNull()?.archivePostMediaList?.lastOrNull()
    }

  }

  companion object {
    private const val TAG = "FuukaApi"
  }
}
