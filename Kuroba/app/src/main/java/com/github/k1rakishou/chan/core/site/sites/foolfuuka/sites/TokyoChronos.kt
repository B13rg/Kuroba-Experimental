package com.github.k1rakishou.chan.core.site.sites.foolfuuka.sites

import com.github.k1rakishou.chan.core.site.Site
import com.github.k1rakishou.chan.core.site.SiteIcon
import com.github.k1rakishou.chan.core.site.sites.foolfuuka.FoolFuukaActions
import com.github.k1rakishou.chan.core.site.sites.foolfuuka.FoolFuukaApi
import com.github.k1rakishou.chan.core.site.sites.foolfuuka.FoolFuukaCommentParser
import com.github.k1rakishou.chan.core.site.sites.foolfuuka.FoolFuukaEndpoints
import com.github.k1rakishou.common.DoNotStrip
import com.github.k1rakishou.common.data.ArchiveType
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

@DoNotStrip
class TokyoChronos : BaseFoolFuukaSite() {

  override fun rootUrl(): HttpUrl = ROOT_URL

  override fun setup() {
    setEnabled(true)
    setName(SITE_NAME)
    setIcon(SiteIcon.fromFavicon(imageLoaderV2, FAVICON_URL))
    setBoardsType(Site.BoardsType.INFINITE)
    setResolvable(URL_HANDLER)
    setConfig(object : CommonConfig() {})
    setEndpoints(FoolFuukaEndpoints(this, rootUrl()))
    setActions(FoolFuukaActions(this))
    setApi(FoolFuukaApi(this))
    setParser(FoolFuukaCommentParser(mockReplyManager, archivesManager))
  }

  companion object {
    val FAVICON_URL: HttpUrl = "https://tokyochronos.net/upload/htvr0u.png".toHttpUrl()
    val ROOT: String = "https://tokyochronos.net/"
    val ROOT_URL: HttpUrl =  ROOT.toHttpUrl()
    val SITE_NAME: String = ArchiveType.TokyoChronos.domain
    val MEDIA_HOSTS: Array<HttpUrl> = arrayOf(ROOT_URL)
    val NAMES: Array<String> = arrayOf("tokyochronos")
    val CLASS: Class<out Site> = TokyoChronos::class.java

    val URL_HANDLER = BaseFoolFuukaUrlHandler(ROOT_URL, MEDIA_HOSTS, NAMES, CLASS)
  }

}