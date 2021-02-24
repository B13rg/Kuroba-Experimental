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
package com.github.k1rakishou.chan

import android.app.Activity
import android.app.Application
import android.app.Application.ActivityLifecycleCallbacks
import android.content.Context
import android.os.Build
import android.os.Bundle
import com.github.k1rakishou.BookmarkGridViewInfo
import com.github.k1rakishou.ChanSettings
import com.github.k1rakishou.ChanSettingsInfo
import com.github.k1rakishou.PersistableChanStateInfo
import com.github.k1rakishou.chan.core.cache.downloader.FileCacheException
import com.github.k1rakishou.chan.core.cache.downloader.FileCacheException.FileNotFoundOnTheServerException
import com.github.k1rakishou.chan.core.di.component.application.ApplicationComponent
import com.github.k1rakishou.chan.core.di.component.application.DaggerApplicationComponent
import com.github.k1rakishou.chan.core.di.module.application.AppModule
import com.github.k1rakishou.chan.core.di.module.application.ExecutorsModule
import com.github.k1rakishou.chan.core.di.module.application.GsonModule
import com.github.k1rakishou.chan.core.di.module.application.LoaderModule
import com.github.k1rakishou.chan.core.di.module.application.ManagerModule
import com.github.k1rakishou.chan.core.di.module.application.NetModule
import com.github.k1rakishou.chan.core.di.module.application.ParserModule
import com.github.k1rakishou.chan.core.di.module.application.RepositoryModule
import com.github.k1rakishou.chan.core.di.module.application.RoomDatabaseModule
import com.github.k1rakishou.chan.core.di.module.application.SiteModule
import com.github.k1rakishou.chan.core.di.module.application.UseCaseModule
import com.github.k1rakishou.chan.core.manager.ApplicationVisibilityManager
import com.github.k1rakishou.chan.core.manager.ArchivesManager
import com.github.k1rakishou.chan.core.manager.BoardManager
import com.github.k1rakishou.chan.core.manager.BookmarksManager
import com.github.k1rakishou.chan.core.manager.ChanFilterManager
import com.github.k1rakishou.chan.core.manager.HistoryNavigationManager
import com.github.k1rakishou.chan.core.manager.ReportManager
import com.github.k1rakishou.chan.core.manager.SettingsNotificationManager
import com.github.k1rakishou.chan.core.manager.SiteManager
import com.github.k1rakishou.chan.core.manager.ThreadBookmarkGroupManager
import com.github.k1rakishou.chan.core.manager.watcher.BookmarkWatcherCoordinator
import com.github.k1rakishou.chan.core.manager.watcher.FilterWatcherCoordinator
import com.github.k1rakishou.chan.core.net.DnsSelector
import com.github.k1rakishou.chan.ui.adapter.PostsFilter
import com.github.k1rakishou.chan.ui.settings.SettingNotificationType
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.getDimen
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.isTablet
import com.github.k1rakishou.common.AndroidUtils
import com.github.k1rakishou.common.AndroidUtils.getApplicationLabel
import com.github.k1rakishou.common.AppConstants
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.core_spannable.SpannableModuleInjector
import com.github.k1rakishou.core_themes.ThemesModuleInjector
import com.github.k1rakishou.fsaf.BadPathSymbolResolutionStrategy
import com.github.k1rakishou.fsaf.FileManager
import com.github.k1rakishou.fsaf.manager.base_directory.DirectoryManager
import com.github.k1rakishou.model.ModelModuleInjector
import com.github.k1rakishou.model.di.NetworkModule
import com.github.k1rakishou.persist_state.PersistableChanState
import io.reactivex.exceptions.UndeliverableException
import io.reactivex.plugins.RxJavaPlugins
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import okhttp3.Dns
import okhttp3.Protocol
import org.greenrobot.eventbus.EventBus
import java.io.IOException
import java.io.PrintWriter
import java.io.StringWriter
import javax.inject.Inject
import kotlin.system.exitProcess

class Chan : Application(), ActivityLifecycleCallbacks {
  private var activityForegroundCounter = 0

  private val job = SupervisorJob(null)
  private var applicationScope: CoroutineScope? = null

  private val tagPrefix by lazy { AndroidUtils.getApplicationLabel().toString() + " | " }

  @Inject
  lateinit var siteManager: SiteManager
  @Inject
  lateinit var boardManager: BoardManager
  @Inject
  lateinit var reportManager: ReportManager
  @Inject
  lateinit var bookmarkWatcherCoordinator: BookmarkWatcherCoordinator
  @Inject
  lateinit var filterWatcherCoordinator: FilterWatcherCoordinator
  @Inject
  lateinit var archivesManager: ArchivesManager
  @Inject
  lateinit var chanFilterManager: ChanFilterManager
  @Inject
  lateinit var threadBookmarkGroupManager: ThreadBookmarkGroupManager
  @Inject
  lateinit var settingsNotificationManager: SettingsNotificationManager
  @Inject
  lateinit var applicationVisibilityManager: ApplicationVisibilityManager
  @Inject
  lateinit var historyNavigationManager: HistoryNavigationManager
  @Inject
  lateinit var bookmarksManager: BookmarksManager

  private val okHttpDns: Dns
    get() {
      if (ChanSettings.okHttpAllowIpv6.get()) {
        Logger.d(AppModule.DI_TAG, "Using DnsSelector.Mode.SYSTEM")
        return DnsSelector(DnsSelector.Mode.SYSTEM)
      }

      Logger.d(AppModule.DI_TAG, "Using DnsSelector.Mode.IPV4_ONLY")
      return DnsSelector(DnsSelector.Mode.IPV4_ONLY)
    }

  private val okHttpProtocols: OkHttpProtocols
    get() {
      if (ChanSettings.okHttpAllowHttp2.get()) {
        Logger.d(AppModule.DI_TAG, "Using HTTP_2 and HTTP_1_1")
        return OkHttpProtocols(listOf(Protocol.HTTP_2, Protocol.HTTP_1_1))
      }

      Logger.d(AppModule.DI_TAG, "Using HTTP_1_1")
      return OkHttpProtocols(listOf(Protocol.HTTP_1_1))
    }

  private val isEmulator: Boolean
    get() = (Build.MODEL.contains("google_sdk")
      || Build.MODEL.contains("Emulator")
      || Build.MODEL.contains("Android SDK"))


  val applicationInForeground: Boolean
    get() = activityForegroundCounter > 0

  override fun attachBaseContext(base: Context) {
    super.attachBaseContext(base)

    AndroidUtils.init(this)
    AppModuleAndroidUtils.init(this)
    Logger.init(tagPrefix)
    ChanSettings.init(createChanSettingsInfo())
    PersistableChanState.init(createPersistableChanStateInfo())

    AppModuleAndroidUtils.printApplicationSignatureHash()

    // remove this if you need to debug some sort of event bus issue
    EventBus.builder()
      .logNoSubscriberMessages(false)
      .installDefaultEventBus()
  }

  override fun onCreate() {
    super.onCreate()

    val start = System.currentTimeMillis()
    onCreateInternal()
    val diff = System.currentTimeMillis() - start

    Logger.d(TAG, "Application initialization took " + diff + "ms")
  }

  private fun onCreateInternal() {
    registerActivityLifecycleCallbacks(this)

    job.cancelChildren()
    applicationScope = CoroutineScope(job + Dispatchers.Main + CoroutineName("Chan"))

    val isDev = AppModuleAndroidUtils.isDevBuild()
    val flavorType = AppModuleAndroidUtils.getFlavorType()

    System.setProperty("kotlinx.coroutines.debug", if (isDev) "on" else "off")

    val kurobaExUserAgent = buildString {
      append(getApplicationLabel())
      append(" ")
      append(BuildConfig.VERSION_NAME)
      append(".")
      append(BuildConfig.BUILD_NUMBER)
    }

    val appConstants = AppConstants(applicationContext, flavorType, kurobaExUserAgent)
    logAppConstants(appConstants)

    val okHttpDns = okHttpDns
    val okHttpProtocols = okHttpProtocols
    val fileManager = provideFileManager()

    val themeEngine = ThemesModuleInjector.build(
      this,
      applicationScope!!,
      fileManager
    ).getThemeEngine()

    themeEngine.initialize(this)
    SpannableModuleInjector.initialize(themeEngine)

    val modelComponent = ModelModuleInjector.build(
      this,
      applicationScope!!,
      okHttpDns,
      NetworkModule.OkHttpProtocolList(okHttpProtocols.protocols),
      ChanSettings.verboseLogs.get(),
      isDev,
      appConstants
    )

    // We need to start initializing ChanPostRepository first because it deletes old posts during
    // the initialization.
    modelComponent.getChanPostRepository().initialize()

    applicationComponent = DaggerApplicationComponent.builder()
      .application(this)
      .appContext(this)
      .themeEngine(themeEngine)
      .fileManager(fileManager)
      .applicationCoroutineScope(applicationScope)
      .okHttpDns(okHttpDns)
      .okHttpProtocols(okHttpProtocols)
      .appConstants(appConstants)
      .modelMainComponent(modelComponent)
      .appModule(AppModule())
      .executorsModule(ExecutorsModule())
      .roomDatabaseModule(RoomDatabaseModule())
      .gsonModule(GsonModule())
      .loaderModule(LoaderModule())
      .managerModule(ManagerModule())
      .netModule(NetModule())
      .repositoryModule(RepositoryModule())
      .siteModule(SiteModule())
      .parserModule(ParserModule())
      .useCaseModule(UseCaseModule())
      .build()
      .also { component -> component.inject(this) }

    siteManager.initialize()
    boardManager.initialize()
    bookmarksManager.initialize()
    historyNavigationManager.initialize()
    bookmarkWatcherCoordinator.initialize()
    filterWatcherCoordinator.initialize()
    archivesManager.initialize()
    chanFilterManager.initialize()
    threadBookmarkGroupManager.initialize()

    setupErrorHandlers()

    // TODO(KurobaEx): move to background thread!
    if (ChanSettings.collectCrashLogs.get()) {
      if (reportManager.hasCrashLogs()) {
        settingsNotificationManager.notify(SettingNotificationType.CrashLog)
      }
    }
  }

  private fun setupErrorHandlers() {
    RxJavaPlugins.setErrorHandler { e: Throwable? ->
      var error = e

      if (error is UndeliverableException) {
        error = error.cause
      }

      if (error == null) {
        return@setErrorHandler
      }

      if (error is IOException) {
        // fine, irrelevant network problem or API that throws on cancellation
        return@setErrorHandler
      }

      if (error is InterruptedException) {
        // fine, some blocking code was interrupted by a dispose call
        return@setErrorHandler
      }

      if (error is RuntimeException && error.cause is InterruptedException) {
        // fine, DB synchronous call (via runTask) was interrupted when a reactive stream
        // was disposed of.
        return@setErrorHandler
      }

      if (error is FileCacheException.CancellationException
        || error is FileNotFoundOnTheServerException
      ) {
        // fine, sometimes they get through all the checks but it doesn't really matter
        return@setErrorHandler
      }

      if (error is NullPointerException || error is IllegalArgumentException) {
        // that's likely a bug in the application
        Thread.currentThread().uncaughtExceptionHandler!!.uncaughtException(
          Thread.currentThread(),
          error
        )
        return@setErrorHandler
      }

      if (error is IllegalStateException) {
        // that's a bug in RxJava or in a custom operator
        Thread.currentThread().uncaughtExceptionHandler!!.uncaughtException(
          Thread.currentThread(),
          error
        )
        return@setErrorHandler
      }

      Logger.e(TAG, "RxJava undeliverable exception", error)
      onUnhandledException(error, exceptionToString(true, error))
    }

    Thread.setDefaultUncaughtExceptionHandler { _, e ->
      // if there's any uncaught crash stuff, just dump them to the log and exit immediately
      Logger.e(TAG, "Unhandled exception", e)
      onUnhandledException(e, exceptionToString(false, e))
      exitProcess(999)
    }
  }

  private fun logAppConstants(appConstants: AppConstants) {
    Logger.d(TAG, "maxPostsCountInPostsCache = " + appConstants.maxPostsCountInPostsCache)
    Logger.d(TAG, "maxAmountOfPostsInDatabase = " + appConstants.maxAmountOfPostsInDatabase)
    Logger.d(TAG, "maxAmountOfThreadsInDatabase = " + appConstants.maxAmountOfThreadsInDatabase)
    Logger.d(TAG, "userAgent = " + appConstants.userAgent)
    Logger.d(TAG, "kurobaExUserAgent = " + appConstants.kurobaExUserAgent)
  }

  private fun exceptionToString(isCalledFromRxJavaHandler: Boolean, e: Throwable): String {
    try {
      StringWriter().use { sw ->
        PrintWriter(sw).use { pw ->
          e.printStackTrace(pw)
          val stackTrace = sw.toString()

          return if (isCalledFromRxJavaHandler) {
            "Called from RxJava onError handler.\n$stackTrace"
          } else {
            "Called from unhandled exception handler.\n$stackTrace"
          }
        }
      }
    } catch (ex: IOException) {
      throw RuntimeException("Error while trying to convert exception to string!", ex)
    }
  }

  private fun onUnhandledException(exception: Throwable, errorText: String) {
    Logger.e("UNCAUGHT", errorText)
    Logger.e("UNCAUGHT", "------------------------------")
    Logger.e("UNCAUGHT", "END OF CURRENT RUNTIME MESSAGES")
    Logger.e("UNCAUGHT", "------------------------------")
    Logger.e("UNCAUGHT", "Android API Level: " + Build.VERSION.SDK_INT)
    Logger.e(
      "UNCAUGHT",
      "App Version: " + BuildConfig.VERSION_NAME + "." + BuildConfig.BUILD_NUMBER
    )
    Logger.e("UNCAUGHT", "Development Build: " + AppModuleAndroidUtils.getVerifiedBuildType().name)
    Logger.e("UNCAUGHT", "Phone Model: " + Build.MANUFACTURER + " " + Build.MODEL)

    // don't upload debug crashes
    if ("Debug crash" == exception.message) {
      return
    }

    if (isEmulator) {
      return
    }

    if (ChanSettings.collectCrashLogs.get()) {
      reportManager.storeCrashLog(exception.message, errorText)
    }
  }

  private fun activityEnteredForeground() {
    val lastForeground = applicationInForeground
    activityForegroundCounter++

    if (applicationInForeground != lastForeground) {
      Logger.d(TAG, "^^^ App went foreground ^^^")

      applicationVisibilityManager.onEnteredForeground()
    }
  }

  private fun activityEnteredBackground() {
    val lastForeground = applicationInForeground
    activityForegroundCounter--

    if (activityForegroundCounter < 0) {
      activityForegroundCounter = 0
    }

    if (applicationInForeground != lastForeground) {
      Logger.d(TAG, "vvv App went background vvv")

      applicationVisibilityManager.onEnteredBackground()
    }
  }

  private fun createPersistableChanStateInfo(): PersistableChanStateInfo {
    return PersistableChanStateInfo(
      versionCode = BuildConfig.VERSION_CODE,
      commitHash = BuildConfig.COMMIT_HASH
    )
  }

  private fun createChanSettingsInfo(): ChanSettingsInfo {
    return ChanSettingsInfo(
      applicationId = BuildConfig.APPLICATION_ID,
      isTablet = isTablet(),
      defaultFilterOrderName = PostsFilter.Order.BUMP.orderName,
      isDevBuild = AppModuleAndroidUtils.isDevBuild(),
      isBetaBuild = AppModuleAndroidUtils.isBetaBuild(),
      bookmarkGridViewInfo = BookmarkGridViewInfo(
        getDimen(R.dimen.thread_grid_bookmark_view_default_width),
        getDimen(R.dimen.thread_grid_bookmark_view_min_width),
        getDimen(R.dimen.thread_grid_bookmark_view_max_width)
      )
    )
  }

  private fun provideFileManager(): FileManager {
    val directoryManager = DirectoryManager(this)

    // Add new base directories here
    var resolutionStrategy = BadPathSymbolResolutionStrategy.ReplaceBadSymbols

    if (AppModuleAndroidUtils.getFlavorType() != AndroidUtils.FlavorType.Stable) {
      resolutionStrategy = BadPathSymbolResolutionStrategy.ThrowAnException
    }

    return FileManager(
      this,
      resolutionStrategy,
      directoryManager
    )
  }

  override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
  override fun onActivityStarted(activity: Activity) {
    activityEnteredForeground()
  }

  override fun onActivityResumed(activity: Activity) {}
  override fun onActivityPaused(activity: Activity) {}
  override fun onActivityStopped(activity: Activity) {
    activityEnteredBackground()
  }

  override fun onActivityDestroyed(activity: Activity) {}
  override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}

  class OkHttpProtocols(val protocols: List<Protocol>)

  companion object {
    private const val TAG = "Chan"
    private lateinit var applicationComponent: ApplicationComponent

    @JvmStatic
    fun getComponent(): ApplicationComponent {
      return applicationComponent
    }
  }
}