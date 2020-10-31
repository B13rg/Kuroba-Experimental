package com.github.k1rakishou.chan.core.site.sites.foolfuuka.sites

import com.github.k1rakishou.chan.core.site.Site
import com.github.k1rakishou.chan.core.site.SiteIcon
import com.github.k1rakishou.chan.core.site.common.FoolFuukaCommentParser
import com.github.k1rakishou.chan.core.site.sites.foolfuuka.FoolFuukaActions
import com.github.k1rakishou.chan.core.site.sites.foolfuuka.FoolFuukaApi
import com.github.k1rakishou.chan.core.site.sites.foolfuuka.FoolFuukaEndpoints
import com.github.k1rakishou.common.DoNotStrip
import com.github.k1rakishou.model.data.archive.ArchiveType
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

@DoNotStrip
class RebeccaBlackTech : BaseFoolFuukaSite() {

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
    setParser(FoolFuukaCommentParser(themeEngine, mockReplyManager, archivesManager))
  }

  companion object {
    val FAVICON_URL: HttpUrl = "https://archive.rebeccablacktech.com/favicon.ico".toHttpUrl()
    val ROOT: String = "https://archive.rebeccablacktech.com/"
    val ROOT_URL: HttpUrl =  ROOT.toHttpUrl()
    val SITE_NAME: String = ArchiveType.RebeccaBlackTech.domain
    val MEDIA_HOSTS: Array<HttpUrl> = arrayOf(ROOT_URL)
    val NAMES: Array<String> = arrayOf("rebeccablacktech")
    val CLASS: Class<out Site> = RebeccaBlackTech::class.java

    val URL_HANDLER = BaseFoolFuukaUrlHandler(ROOT_URL, MEDIA_HOSTS, NAMES, CLASS)
  }

}