/*
 * KurobaEx - *chan browser https://github.com/K1rakishou/Kuroba-Experimental/
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.github.k1rakishou.chan.core.site.loader

import com.github.k1rakishou.chan.core.base.okhttp.CloudFlareHandlerInterceptor
import com.github.k1rakishou.chan.core.base.okhttp.ProxiedOkHttpClient
import com.github.k1rakishou.chan.core.helper.FilterEngine
import com.github.k1rakishou.chan.core.manager.BoardManager
import com.github.k1rakishou.chan.core.manager.PostFilterManager
import com.github.k1rakishou.chan.core.manager.SavedReplyManager
import com.github.k1rakishou.chan.core.site.Site
import com.github.k1rakishou.chan.core.site.loader.internal.ChanPostPersister
import com.github.k1rakishou.chan.core.site.loader.internal.DatabasePostLoader
import com.github.k1rakishou.chan.core.site.loader.internal.usecase.ParsePostsUseCase
import com.github.k1rakishou.chan.core.site.loader.internal.usecase.ReloadPostsFromDatabaseUseCase
import com.github.k1rakishou.chan.core.site.loader.internal.usecase.StorePostsInRepositoryUseCase
import com.github.k1rakishou.chan.core.site.parser.ChanReader
import com.github.k1rakishou.chan.core.site.parser.ChanReaderProcessor
import com.github.k1rakishou.chan.utils.BackgroundUtils
import com.github.k1rakishou.common.AppConstants
import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.common.ModularResult.Companion.Try
import com.github.k1rakishou.common.errorMessageOrClassName
import com.github.k1rakishou.common.options.ChanCacheOptions
import com.github.k1rakishou.common.options.ChanReadOptions
import com.github.k1rakishou.common.suspendCall
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.repository.ChanCatalogSnapshotRepository
import com.github.k1rakishou.model.repository.ChanPostRepository
import com.github.k1rakishou.model.source.cache.thread.ChanThreadsCache
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.*
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration
import kotlin.time.ExperimentalTime
import kotlin.time.measureTimedValue

/**
 * This class is kinda over complicated right now. It does way too much stuff. It tries to load the
 * catalog/thread json from the network as well as thread json from third-party archives (only
 * for 4chan). It automatically redirects you to an archived thread in case of original thread getting
 * 404ed. It automatically loads cached posts from the database when it was impossible to load posts
 * from the network. All of that stuff should be separated into their own classes some time in the
 * future. For now it will stay the way it is.
 * */
class ChanThreadLoaderCoordinator(
  private val proxiedOkHttpClient: ProxiedOkHttpClient,
  private val savedReplyManager: SavedReplyManager,
  private val filterEngine: FilterEngine,
  private val chanPostRepository: ChanPostRepository,
  private val chanCatalogSnapshotRepository: ChanCatalogSnapshotRepository,
  private val appConstants: AppConstants,
  private val postFilterManager: PostFilterManager,
  private val verboseLogsEnabled: Boolean,
  private val boardManager: BoardManager
) : CoroutineScope {
  private val job = SupervisorJob()

  override val coroutineContext: CoroutineContext
    get() = Dispatchers.IO + job + CoroutineName("ChanThreadLoaderCoordinator")

  private val reloadPostsFromDatabaseUseCase by lazy {
    ReloadPostsFromDatabaseUseCase(
      chanPostRepository,
      boardManager
    )
  }

  private val parsePostsUseCase by lazy {
    ParsePostsUseCase(
      verboseLogsEnabled,
      dispatcher,
      chanPostRepository,
      filterEngine,
      postFilterManager,
      savedReplyManager,
      boardManager
    )
  }

  private val storePostsInRepositoryUseCase by lazy {
    StorePostsInRepositoryUseCase(
      chanPostRepository
    )
  }

  private val chanPostPersister by lazy {
    ChanPostPersister(
      parsePostsUseCase,
      storePostsInRepositoryUseCase,
      chanPostRepository,
      chanCatalogSnapshotRepository
    )
  }

  private val databasePostLoader by lazy {
    DatabasePostLoader(
      reloadPostsFromDatabaseUseCase,
      chanCatalogSnapshotRepository
    )
  }

  @OptIn(ExperimentalTime::class)
  suspend fun loadThreadOrCatalog(
    url: String,
    chanDescriptor: ChanDescriptor,
    chanCacheOptions: ChanCacheOptions,
    chanReadOptions: ChanReadOptions,
    chanReader: ChanReader
  ): ModularResult<ThreadLoadResult> {
    Logger.d(TAG, "loadThreadOrCatalog(url=$url, chanDescriptor=$chanDescriptor, " +
      "chanCacheOptions=$chanCacheOptions, chanReadOptions=$chanReadOptions, " +
      "chanReader=${chanReader.javaClass.simpleName})")

    return withContext(Dispatchers.IO) {
      BackgroundUtils.ensureBackgroundThread()
      chanPostRepository.awaitUntilInitialized()

      return@withContext Try {
        val request = Request.Builder()
          .url(url)
          .get()
          .header("User-Agent", appConstants.userAgent)
          .build()

        val (response, requestDuration) = try {
          measureTimedValue { proxiedOkHttpClient.okHttpClient().suspendCall(request) }
        } catch (error: IOException) {
          if (error is CloudFlareHandlerInterceptor.CloudFlareDetectedException) {
            throw error
          }

          return@Try fallbackPostLoadOnNetworkError(chanDescriptor, error)
        }

        if (!response.isSuccessful) {
          return@Try fallbackPostLoadOnNetworkError(chanDescriptor, ServerException(response.code))
        }

        val (chanReaderProcessor, readPostsDuration) = measureTimedValue {
          return@measureTimedValue readPostsFromResponse(
            request,
            response,
            chanDescriptor,
            chanReadOptions,
            chanReader
          ).unwrap()
        }

        val (threadLoadResult, loadTimeInfo) = chanPostPersister.persistPosts(
          url,
          chanReaderProcessor,
          chanCacheOptions,
          chanDescriptor,
          chanReader
        )

        loadRequestStatistics(chanDescriptor, loadTimeInfo, requestDuration, readPostsDuration)
        return@Try threadLoadResult
      }.mapError { error -> ChanLoaderException(error) }
    }
  }

  @OptIn(ExperimentalTime::class)
  private suspend fun loadRequestStatistics(
    chanDescriptor: ChanDescriptor,
    loadTimeInfo: ChanPostPersister.LoadTimeInfo?,
    requestDuration: Duration,
    readPostsDuration: Duration
  ) {
    if (loadTimeInfo == null) {
      return
    }

    val url = loadTimeInfo.url
    val storeDuration = loadTimeInfo.storeDuration
    val storedPostsCount = loadTimeInfo.storedPostsCount
    val parsingDuration = loadTimeInfo.parsingDuration
    val parsedPostsCount = loadTimeInfo.parsedPostsCount
    val postsInChanReaderProcessor = loadTimeInfo.postsInChanReaderProcessor

    val cachedPostsCount = chanPostRepository.getTotalCachedPostsCount()
    val cachedThreadsCount = chanPostRepository.getTotalCachedThreadCount()
    val threadsWithMoreThanOnePostCount = chanPostRepository.getThreadsWithMoreThanOnePostCount()

    val currentThreadCachedPostsCount = if (chanDescriptor is ChanDescriptor.ThreadDescriptor) {
      chanPostRepository.getThreadCachedPostsCount(chanDescriptor)
    } else {
      null
    }

    val logString = buildString {
      appendLine("ChanReaderRequest.readJson() stats:")
      appendLine("url = $url.")
      appendLine("Network request execution took $requestDuration.")
      appendLine("Json reading took $readPostsDuration.")
      appendLine("Store new posts took $storeDuration (stored ${storedPostsCount} posts).")
      appendLine("Parse posts took = $parsingDuration, (parsed ${parsedPostsCount} out of $postsInChanReaderProcessor posts).")
      appendLine("Total in-memory cached posts count = $cachedPostsCount/${appConstants.maxPostsCountInPostsCache}.")

      if (currentThreadCachedPostsCount != null) {
        appendLine("Current thread cached posts count = ${currentThreadCachedPostsCount}")
      }

      appendLine("Threads with more than one post " +
        "count = ($threadsWithMoreThanOnePostCount/${ChanThreadsCache.IMMUNE_THREADS_COUNT}), " +
        "total cached threads count = ${cachedThreadsCount}.")
    }

    Logger.d(TAG, logString)
  }

  suspend fun reloadThreadFromDatabase(
    threadDescriptor: ChanDescriptor.ThreadDescriptor
  ): ModularResult<ThreadLoadResult> {
    Logger.d(TAG, "reloadThreadFromDatabase($threadDescriptor)")

    return withContext(Dispatchers.IO) {
      return@withContext Try {
        databasePostLoader.loadPosts(threadDescriptor)
        return@Try ThreadLoadResult.Loaded(threadDescriptor)
      }
    }
  }

  suspend fun reloadCatalogFromDatabase(
    catalogDescriptor: ChanDescriptor.CatalogDescriptor
  ): ModularResult<ThreadLoadResult> {
    Logger.d(TAG, "reloadCatalogFromDatabase($catalogDescriptor)")

    return withContext(Dispatchers.IO) {
      return@withContext Try {
        databasePostLoader.loadCatalog(catalogDescriptor)
        return@Try ThreadLoadResult.Loaded(catalogDescriptor)
      }
    }
  }

  private suspend fun fallbackPostLoadOnNetworkError(
    chanDescriptor: ChanDescriptor,
    error: Exception
  ): ThreadLoadResult {
    BackgroundUtils.ensureBackgroundThread()

    databasePostLoader.loadPosts(chanDescriptor)
      ?: throw error

    val isThreadDeleted = error is ServerException && error.statusCode == 404
    if (isThreadDeleted && chanDescriptor is ChanDescriptor.ThreadDescriptor) {
      chanPostRepository.markThreadAsDeleted(chanDescriptor, true)
    }

    // TODO(KurobaEx): update in the database that the thread is deleted

    Logger.e(TAG, "Successfully recovered from network error (${error.errorMessageOrClassName()})")
    return ThreadLoadResult.Loaded(chanDescriptor)
  }

  private suspend fun readPostsFromResponse(
    request: Request,
    response: Response,
    chanDescriptor: ChanDescriptor,
    chanReadOptions: ChanReadOptions,
    chanReader: ChanReader
  ): ModularResult<ChanReaderProcessor> {
    BackgroundUtils.ensureBackgroundThread()

    return Try {
      val body = response.body
        ?: throw IOException("Response has no body")

      val chanReaderProcessor = ChanReaderProcessor(
        chanPostRepository,
        chanReadOptions,
        chanDescriptor
      )

      when (chanDescriptor) {
        is ChanDescriptor.ThreadDescriptor -> {
          chanReader.loadThread(request, body, chanReaderProcessor)
        }
        is ChanDescriptor.CatalogDescriptor -> {
          chanReader.loadCatalog(request, body, chanReaderProcessor)
        }
        else -> throw IllegalArgumentException("Unknown mode")
      }

      return@Try chanReaderProcessor
    }
  }

  companion object {
    private const val TAG = "ChanThreadLoaderCoordinator"
    private const val threadFactoryName = "post_parser_thread_%d"

    private val THREAD_COUNT = Runtime.getRuntime().availableProcessors()
    private val threadIndex = AtomicInteger(0)
    private val dispatcher: CoroutineDispatcher

    init {
      Logger.d(TAG, "Thread count: $THREAD_COUNT")

      val executor = Executors.newFixedThreadPool(THREAD_COUNT) { runnable ->
        val threadName = String.format(
          Locale.ENGLISH,
          threadFactoryName,
          threadIndex.getAndIncrement()
        )

        return@newFixedThreadPool Thread(runnable, threadName)
      }

      dispatcher = executor.asCoroutineDispatcher()
    }

    @JvmStatic
    fun getChanUrl(site: Site, chanDescriptor: ChanDescriptor): HttpUrl {
      return when (chanDescriptor) {
        is ChanDescriptor.ThreadDescriptor -> site.endpoints().thread(chanDescriptor)
        is ChanDescriptor.CatalogDescriptor -> site.endpoints().catalog(chanDescriptor.boardDescriptor)
        else -> throw IllegalArgumentException("Unknown mode")
      }
    }
  }

}