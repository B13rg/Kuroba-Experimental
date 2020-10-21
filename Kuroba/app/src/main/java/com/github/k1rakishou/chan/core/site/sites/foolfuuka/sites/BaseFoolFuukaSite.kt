package com.github.k1rakishou.chan.core.site.sites.foolfuuka.sites

import com.github.k1rakishou.chan.core.site.ChunkDownloaderSiteProperties
import com.github.k1rakishou.chan.core.site.Site
import com.github.k1rakishou.chan.core.site.common.CommonSite
import com.github.k1rakishou.chan.core.site.parser.CommentParserType
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import okhttp3.HttpUrl

abstract class BaseFoolFuukaSite : CommonSite() {
  private val chunkDownloaderSiteProperties = ChunkDownloaderSiteProperties(
    enabled = false,
    siteSendsCorrectFileSizeInBytes = false,
    canFileHashBeTrusted = false
  )

  abstract fun rootUrl(): HttpUrl

  final override fun commentParserType(): CommentParserType = CommentParserType.FoolFuukaParser

  final override fun getChunkDownloaderSiteProperties(): ChunkDownloaderSiteProperties = chunkDownloaderSiteProperties

  open class BaseFoolFuukaUrlHandler(
    override val url: HttpUrl,
    override val mediaHosts: Array<HttpUrl>,
    override val names: Array<String>,
    private val siteClass: Class<out Site>
  ) : CommonSiteUrlHandler() {

    override fun desktopUrl(chanDescriptor: ChanDescriptor, postNo: Long?): String {
      // https://archived.moe/
      val baseUrl = url.toString()

      return when (chanDescriptor) {
          is ChanDescriptor.CatalogDescriptor -> "${baseUrl}${chanDescriptor.boardCode()}"
          is ChanDescriptor.ThreadDescriptor -> {
            if (postNo == null) {
              // https://archived.moe/a/thread/208364509/
              "${baseUrl}${chanDescriptor.boardCode()}/thread/${chanDescriptor.threadNo}"
            } else {
              // https://archived.moe/a/thread/208364509#208364685
              "${baseUrl}${chanDescriptor.boardCode()}/thread/${chanDescriptor.threadNo}#${postNo}"
            }
          }
      }
    }

    override fun getSiteClass(): Class<out Site> = siteClass
  }
}