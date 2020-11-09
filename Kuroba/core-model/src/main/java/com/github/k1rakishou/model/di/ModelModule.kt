package com.github.k1rakishou.model.di

import com.github.k1rakishou.json.BooleanJsonSetting
import com.github.k1rakishou.json.IntegerJsonSetting
import com.github.k1rakishou.json.JsonSetting
import com.github.k1rakishou.json.LongJsonSetting
import com.github.k1rakishou.json.RuntimeTypeAdapterFactory
import com.github.k1rakishou.json.StringJsonSetting
import com.github.k1rakishou.model.KurobaDatabase
import com.github.k1rakishou.model.repository.BoardRepository
import com.github.k1rakishou.model.repository.BookmarksRepository
import com.github.k1rakishou.model.repository.ChanFilterRepository
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
import com.github.k1rakishou.model.source.cache.ChanDescriptorCache
import com.github.k1rakishou.model.source.cache.GenericCacheSource
import com.github.k1rakishou.model.source.cache.ThreadBookmarkCache
import com.github.k1rakishou.model.source.cache.thread.ChanThreadsCache
import com.github.k1rakishou.model.source.local.BoardLocalSource
import com.github.k1rakishou.model.source.local.ChanFilterLocalSource
import com.github.k1rakishou.model.source.local.ChanPostHideLocalSource
import com.github.k1rakishou.model.source.local.ChanPostLocalSource
import com.github.k1rakishou.model.source.local.ChanSavedReplyLocalSource
import com.github.k1rakishou.model.source.local.ChanThreadViewableInfoLocalSource
import com.github.k1rakishou.model.source.local.InlinedFileInfoLocalSource
import com.github.k1rakishou.model.source.local.MediaServiceLinkExtraContentLocalSource
import com.github.k1rakishou.model.source.local.NavHistoryLocalSource
import com.github.k1rakishou.model.source.local.SeenPostLocalSource
import com.github.k1rakishou.model.source.local.SiteLocalSource
import com.github.k1rakishou.model.source.local.ThreadBookmarkGroupLocalSource
import com.github.k1rakishou.model.source.local.ThreadBookmarkLocalSource
import com.github.k1rakishou.model.source.remote.InlinedFileInfoRemoteSource
import com.github.k1rakishou.model.source.remote.MediaServiceLinkExtraContentRemoteSource
import com.google.gson.Gson
import dagger.Module
import dagger.Provides
import okhttp3.OkHttpClient
import javax.inject.Singleton

@Module
class ModelModule {

  @Singleton
  @Provides
  fun provideDatabase(
    dependencies: ModelComponent.Dependencies
  ): KurobaDatabase {
    return KurobaDatabase.buildDatabase(dependencies.application)
  }

  @Singleton
  @Provides
  fun provideGson(): Gson {
    val gson = Gson().newBuilder()

    val userSettingAdapter = RuntimeTypeAdapterFactory.of(
      JsonSetting::class.java,
      "type"
    ).registerSubtype(StringJsonSetting::class.java, "string")
      .registerSubtype(IntegerJsonSetting::class.java, "integer")
      .registerSubtype(LongJsonSetting::class.java, "long")
      .registerSubtype(BooleanJsonSetting::class.java, "boolean")

    return gson
      .registerTypeAdapterFactory(userSettingAdapter)
      .create()
  }

  @Singleton
  @Provides
  fun provideChanDescriptorCache(database: KurobaDatabase): ChanDescriptorCache {
    return ChanDescriptorCache(database)
  }

  @Singleton
  @Provides
  fun provideThreadBookmarkCache(): ThreadBookmarkCache {
    return ThreadBookmarkCache()
  }

  @Singleton
  @Provides
  fun provideChanThreadsCache(
    dependencies: ModelComponent.Dependencies
  ): ChanThreadsCache {
    return ChanThreadsCache(
      dependencies.isDevFlavor,
      dependencies.appConstants.maxPostsCountInPostsCache
    )
  }

  /**
   * Local sources
   * */

  @Singleton
  @Provides
  fun provideMediaServiceLinkExtraContentLocalSource(
    database: KurobaDatabase,
  ): MediaServiceLinkExtraContentLocalSource {
    return MediaServiceLinkExtraContentLocalSource(
      database
    )
  }

  @Singleton
  @Provides
  fun provideSeenPostLocalSource(
    database: KurobaDatabase
  ): SeenPostLocalSource {
    return SeenPostLocalSource(
      database
    )
  }

  @Singleton
  @Provides
  fun provideInlinedFileInfoLocalSource(
    database: KurobaDatabase
  ): InlinedFileInfoLocalSource {
    return InlinedFileInfoLocalSource(
      database
    )
  }

  @Singleton
  @Provides
  fun provideChanPostLocalSource(
    database: KurobaDatabase,
    gson: Gson
  ): ChanPostLocalSource {
    return ChanPostLocalSource(
      database,
      gson
    )
  }

  @Singleton
  @Provides
  fun provideNavHistoryLocalSource(
    database: KurobaDatabase,
  ): NavHistoryLocalSource {
    return NavHistoryLocalSource(
      database
    )
  }

  @Singleton
  @Provides
  fun provideThreadBookmarkLocalSource(
    dependencies: ModelComponent.Dependencies,
    database: KurobaDatabase,
    chanDescriptorCache: ChanDescriptorCache,
    threadBookmarkCache: ThreadBookmarkCache
  ): ThreadBookmarkLocalSource {
    return ThreadBookmarkLocalSource(
      database,
      dependencies.isDevFlavor,
      chanDescriptorCache,
      threadBookmarkCache
    )
  }

  @Singleton
  @Provides
  fun provideChanThreadViewableInfoLocalSource(
    database: KurobaDatabase,
    chanDescriptorCache: ChanDescriptorCache
  ): ChanThreadViewableInfoLocalSource {
    return ChanThreadViewableInfoLocalSource(
      database,
      chanDescriptorCache
    )
  }

  @Singleton
  @Provides
  fun provideSiteLocalSource(
    database: KurobaDatabase,
    dependencies: ModelComponent.Dependencies,
    chanDescriptorCache: ChanDescriptorCache
  ): SiteLocalSource {
    return SiteLocalSource(
      database,
      dependencies.isDevFlavor,
      chanDescriptorCache
    )
  }

  @Singleton
  @Provides
  fun provideBoardLocalSource(
    database: KurobaDatabase,
    dependencies: ModelComponent.Dependencies,
    chanDescriptorCache: ChanDescriptorCache
  ): BoardLocalSource {
    return BoardLocalSource(
      database,
      dependencies.isDevFlavor,
      chanDescriptorCache
    )
  }

  @Singleton
  @Provides
  fun provideChanSavedReplyLocalSource(
    database: KurobaDatabase,
    dependencies: ModelComponent.Dependencies,
  ): ChanSavedReplyLocalSource {
    return ChanSavedReplyLocalSource(
      database,
      dependencies.isDevFlavor,
    )
  }

  @Singleton
  @Provides
  fun provideChanPostHideLocalSource(
    database: KurobaDatabase,
    dependencies: ModelComponent.Dependencies,
  ): ChanPostHideLocalSource {
    return ChanPostHideLocalSource(
      database,
      dependencies.isDevFlavor,
    )
  }

  @Singleton
  @Provides
  fun provideChanFilterLocalSource(
    database: KurobaDatabase,
    dependencies: ModelComponent.Dependencies,
  ): ChanFilterLocalSource {
    return ChanFilterLocalSource(
      database,
      dependencies.isDevFlavor,
    )
  }

  @Singleton
  @Provides
  fun provideThreadBookmarkGroupLocalSource(
    database: KurobaDatabase,
    dependencies: ModelComponent.Dependencies,
    chanDescriptorCache: ChanDescriptorCache
  ): ThreadBookmarkGroupLocalSource {
    return ThreadBookmarkGroupLocalSource(
      database,
      dependencies.isDevFlavor,
      chanDescriptorCache
    )
  }

  /**
   * Remote sources
   * */

  @Singleton
  @Provides
  fun provideMediaServiceLinkExtraContentRemoteSource(
    okHttpClient: OkHttpClient,
  ): MediaServiceLinkExtraContentRemoteSource {
    return MediaServiceLinkExtraContentRemoteSource(okHttpClient)
  }

  @Singleton
  @Provides
  fun provideInlinedFileInfoRemoteSource(
    okHttpClient: OkHttpClient,
  ): InlinedFileInfoRemoteSource {
    return InlinedFileInfoRemoteSource(okHttpClient)
  }

  /**
   * Repositories
   * */

  @Singleton
  @Provides
  fun provideYoutubeLinkExtraContentRepository(
    dependencies: ModelComponent.Dependencies,
    database: KurobaDatabase,
    mediaServiceLinkExtraContentLocalSource: MediaServiceLinkExtraContentLocalSource,
    mediaServiceLinkExtraContentRemoteSource: MediaServiceLinkExtraContentRemoteSource,
  ): MediaServiceLinkExtraContentRepository {
    return MediaServiceLinkExtraContentRepository(
      database,
      dependencies.scope,
      GenericCacheSource(),
      mediaServiceLinkExtraContentLocalSource,
      mediaServiceLinkExtraContentRemoteSource
    )
  }

  @Singleton
  @Provides
  fun provideSeenPostRepository(
    dependencies: ModelComponent.Dependencies,
    database: KurobaDatabase,
    seenPostLocalSource: SeenPostLocalSource,
  ): SeenPostRepository {
    return SeenPostRepository(
      database,
      dependencies.scope,
      seenPostLocalSource
    )
  }

  @Singleton
  @Provides
  fun provideInlinedFileInfoRepository(
    dependencies: ModelComponent.Dependencies,
    database: KurobaDatabase,
    inlinedFileInfoLocalSource: InlinedFileInfoLocalSource,
    inlinedFileInfoRemoteSource: InlinedFileInfoRemoteSource,
  ): InlinedFileInfoRepository {
    return InlinedFileInfoRepository(
      database,
      dependencies.scope,
      GenericCacheSource(),
      inlinedFileInfoLocalSource,
      inlinedFileInfoRemoteSource
    )
  }

  @Singleton
  @Provides
  fun provideChanPostRepository(
    dependencies: ModelComponent.Dependencies,
    database: KurobaDatabase,
    chanPostLocalSource: ChanPostLocalSource,
    chanThreadsCache: ChanThreadsCache
  ): ChanPostRepository {
    return ChanPostRepository(
      database,
      dependencies.isDevFlavor,
      dependencies.scope,
      dependencies.appConstants,
      chanPostLocalSource,
      chanThreadsCache
    )
  }

  @Singleton
  @Provides
  fun provideHistoryNavigationRepository(
    dependencies: ModelComponent.Dependencies,
    database: KurobaDatabase,
    navHistoryLocalSource: NavHistoryLocalSource
  ): HistoryNavigationRepository {
    return HistoryNavigationRepository(
      database,
      dependencies.scope,
      navHistoryLocalSource
    )
  }

  @Singleton
  @Provides
  fun provideBookmarksRepository(
    dependencies: ModelComponent.Dependencies,
    database: KurobaDatabase,
    threadBookmarkLocalSource: ThreadBookmarkLocalSource
  ): BookmarksRepository {
    return BookmarksRepository(
      database,
      dependencies.scope,
      threadBookmarkLocalSource
    )
  }

  @Singleton
  @Provides
  fun provideChanThreadViewableInfoRepository(
    dependencies: ModelComponent.Dependencies,
    database: KurobaDatabase,
    chanThreadViewableInfoLocalSource: ChanThreadViewableInfoLocalSource
  ): ChanThreadViewableInfoRepository {
    return ChanThreadViewableInfoRepository(
      database,
      dependencies.scope,
      chanThreadViewableInfoLocalSource
    )
  }

  @Singleton
  @Provides
  fun provideSiteRepository(
    dependencies: ModelComponent.Dependencies,
    database: KurobaDatabase,
    siteLocalSource: SiteLocalSource
  ): SiteRepository {
    return SiteRepository(
      database,
      dependencies.scope,
      siteLocalSource
    )
  }

  @Singleton
  @Provides
  fun provideBoardRepository(
    dependencies: ModelComponent.Dependencies,
    database: KurobaDatabase,
    boardLocalSource: BoardLocalSource
  ): BoardRepository {
    return BoardRepository(
      database,
      dependencies.scope,
      boardLocalSource
    )
  }

  @Singleton
  @Provides
  fun provideChanSavedReplyRepository(
    dependencies: ModelComponent.Dependencies,
    database: KurobaDatabase,
    chanSavedReplyLocalSource: ChanSavedReplyLocalSource
  ): ChanSavedReplyRepository {
    return ChanSavedReplyRepository(
      database,
      dependencies.scope,
      chanSavedReplyLocalSource
    )
  }

  @Singleton
  @Provides
  fun provideChanPostHideRepository(
    dependencies: ModelComponent.Dependencies,
    database: KurobaDatabase,
    chanPostHideLocalSource: ChanPostHideLocalSource
  ): ChanPostHideRepository {
    return ChanPostHideRepository(
      database,
      dependencies.scope,
      chanPostHideLocalSource
    )
  }

  @Singleton
  @Provides
  fun provideChanFilterRepository(
    dependencies: ModelComponent.Dependencies,
    database: KurobaDatabase,
    chanFilterLocalSource: ChanFilterLocalSource
  ): ChanFilterRepository {
    return ChanFilterRepository(
      database,
      dependencies.scope,
      chanFilterLocalSource
    )
  }

  @Singleton
  @Provides
  fun provideThreadBookmarkGroupRepository(
    dependencies: ModelComponent.Dependencies,
    database: KurobaDatabase,
    localSource: ThreadBookmarkGroupLocalSource
  ): ThreadBookmarkGroupRepository {
    return ThreadBookmarkGroupRepository(
      database,
      dependencies.scope,
      localSource
    )
  }

}