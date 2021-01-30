package com.github.k1rakishou.model.di

import android.app.Application
import com.github.k1rakishou.common.AppConstants
import com.github.k1rakishou.model.repository.BoardRepository
import com.github.k1rakishou.model.repository.BookmarksRepository
import com.github.k1rakishou.model.repository.ChanCatalogSnapshotRepository
import com.github.k1rakishou.model.repository.ChanFilterRepository
import com.github.k1rakishou.model.repository.ChanFilterWatchRepository
import com.github.k1rakishou.model.repository.ChanPostHideRepository
import com.github.k1rakishou.model.repository.ChanPostRepository
import com.github.k1rakishou.model.repository.ChanSavedReplyRepository
import com.github.k1rakishou.model.repository.ChanThreadViewableInfoRepository
import com.github.k1rakishou.model.repository.HistoryNavigationRepository
import com.github.k1rakishou.model.repository.InlinedFileInfoRepository
import com.github.k1rakishou.model.repository.MediaServiceLinkExtraContentRepository
import com.github.k1rakishou.model.repository.SeenPostRepository
import com.github.k1rakishou.model.repository.SiteRepository
import com.github.k1rakishou.model.repository.ThreadBookmarkGroupRepository
import com.github.k1rakishou.model.source.cache.thread.ChanThreadsCache
import com.google.gson.Gson
import dagger.BindsInstance
import dagger.Component
import kotlinx.coroutines.CoroutineScope
import okhttp3.Dns
import javax.inject.Singleton

@Singleton
@Component(
  modules = [
    NetworkModule::class,
    ModelModule::class
  ]
)
interface ModelComponent {
  fun getGson(): Gson
  fun getMediaServiceLinkExtraContentRepository(): MediaServiceLinkExtraContentRepository
  fun getSeenPostRepository(): SeenPostRepository
  fun getInlinedFileInfoRepository(): InlinedFileInfoRepository
  fun getChanPostRepository(): ChanPostRepository
  fun getHistoryNavigationRepository(): HistoryNavigationRepository
  fun getBookmarksRepository(): BookmarksRepository
  fun getChanThreadViewableInfoRepository(): ChanThreadViewableInfoRepository
  fun getSiteRepository(): SiteRepository
  fun getBoardRepository(): BoardRepository
  fun getChanSavedReplyRepository(): ChanSavedReplyRepository
  fun getChanPostHideRepository(): ChanPostHideRepository
  fun getChanFilterRepository(): ChanFilterRepository
  fun getThreadBookmarkGroupRepository(): ThreadBookmarkGroupRepository
  fun getChanCatalogSnapshotRepository(): ChanCatalogSnapshotRepository
  fun getChanFilterWatchRepository(): ChanFilterWatchRepository
  fun getChanThreadsCache(): ChanThreadsCache

  @Component.Builder
  interface Builder {
    @BindsInstance
    fun dependencies(deps: Dependencies): Builder
    fun build(): ModelComponent
  }

  class Dependencies(
    val application: Application,
    val coroutineScope: CoroutineScope,
    val verboseLogs: Boolean,
    val isDevFlavor: Boolean,
    val dns: Dns,
    val okHttpProtocols: NetworkModule.OkHttpProtocolList,
    val appConstants: AppConstants
  )

}