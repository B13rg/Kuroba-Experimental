package com.github.k1rakishou.chan.core.di.component.application

import com.github.k1rakishou.chan.Chan
import com.github.k1rakishou.chan.core.cache.CacheHandler
import com.github.k1rakishou.chan.core.image.loader.KurobaImageLoader
import com.github.k1rakishou.chan.core.manager.DownloadedImagesManager
import com.github.k1rakishou.chan.core.manager.OnDemandContentLoaderManager
import com.github.k1rakishou.chan.core.manager.PrefetchStateManager
import com.github.k1rakishou.chan.core.manager.RevealedSpoilerImagesManager
import com.github.k1rakishou.chan.core.manager.SiteManager
import com.github.k1rakishou.chan.core.manager.ThirdEyeManager
import com.github.k1rakishou.chan.ui.compose.snackbar.manager.SnackbarManagerFactory
import com.github.k1rakishou.chan.ui.globalstate.GlobalUiStateHolder
import com.github.k1rakishou.chan.ui.helper.AppResources
import com.github.k1rakishou.core_themes.ThemeEngine
import com.github.k1rakishou.model.source.cache.thread.ChanThreadsCache

interface ApplicationDependencies {
  val application: Chan
  val themeEngine: ThemeEngine
  val siteManager: SiteManager
  val globalUiStateHolder: GlobalUiStateHolder
  val appResources: AppResources
  val snackbarManagerFactory: SnackbarManagerFactory
  val onDemandContentLoaderManager: OnDemandContentLoaderManager
  val chanThreadsCache: ChanThreadsCache
  val kurobaImageLoader: KurobaImageLoader
  val thirdEyeManager: ThirdEyeManager
  val prefetchStateManager: PrefetchStateManager
  val downloadedImagesManager: DownloadedImagesManager
  val cacheHandler: CacheHandler
  val revealedSpoilerImagesManager: RevealedSpoilerImagesManager
}