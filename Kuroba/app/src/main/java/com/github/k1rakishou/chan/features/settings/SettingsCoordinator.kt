package com.github.k1rakishou.chan.features.settings

import android.content.Context
import com.airbnb.epoxy.EpoxyRecyclerView
import com.github.k1rakishou.PersistableChanState
import com.github.k1rakishou.chan.activity.StartActivity
import com.github.k1rakishou.chan.core.cache.CacheHandler
import com.github.k1rakishou.chan.core.cache.FileCacheV2
import com.github.k1rakishou.chan.core.helper.DialogFactory
import com.github.k1rakishou.chan.core.helper.ProxyStorage
import com.github.k1rakishou.chan.core.manager.*
import com.github.k1rakishou.chan.core.repository.ImportExportRepository
import com.github.k1rakishou.chan.features.drawer.DrawerCallbacks
import com.github.k1rakishou.chan.features.gesture_editor.Android10GesturesExclusionZonesHolder
import com.github.k1rakishou.chan.features.settings.screens.*
import com.github.k1rakishou.chan.ui.controller.navigation.NavigationController
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils
import com.github.k1rakishou.chan.utils.RecyclerUtils
import com.github.k1rakishou.common.AppConstants
import com.github.k1rakishou.common.SuspendableInitializer
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.core_themes.ThemeEngine
import com.github.k1rakishou.fsaf.FileChooser
import com.github.k1rakishou.fsaf.FileManager
import com.github.k1rakishou.model.repository.ChanPostRepository
import com.github.k1rakishou.model.repository.InlinedFileInfoRepository
import com.github.k1rakishou.model.repository.MediaServiceLinkExtraContentRepository
import com.github.k1rakishou.model.repository.SeenPostRepository
import io.reactivex.Flowable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.processors.BehaviorProcessor
import io.reactivex.processors.PublishProcessor
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.reactive.asFlow
import java.util.*
import javax.inject.Inject
import kotlin.coroutines.CoroutineContext

class SettingsCoordinator(
  private val context: Context,
  private val navigationController: NavigationController,
  private val drawerCallbacks: DrawerCallbacks?
) : CoroutineScope, SettingsCoordinatorCallbacks {

  @Inject
  lateinit var appConstants: AppConstants
  @Inject
  lateinit var fileCacheV2: FileCacheV2
  @Inject
  lateinit var cacheHandler: CacheHandler
  @Inject
  lateinit var seenPostRepository: SeenPostRepository
  @Inject
  lateinit var mediaServiceLinkExtraContentRepository: MediaServiceLinkExtraContentRepository
  @Inject
  lateinit var inlinedFileInfoRepository: InlinedFileInfoRepository
  @Inject
  lateinit var reportManager: ReportManager
  @Inject
  lateinit var settingsNotificationManager: SettingsNotificationManager
  @Inject
  lateinit var exclusionZonesHolder: Android10GesturesExclusionZonesHolder
  @Inject
  lateinit var fileChooser: FileChooser
  @Inject
  lateinit var fileManager: FileManager
  @Inject
  lateinit var applicationVisibilityManager: ApplicationVisibilityManager
  @Inject
  lateinit var siteManager: SiteManager
  @Inject
  lateinit var boardManager: BoardManager
  @Inject
  lateinit var postHideManager: PostHideManager
  @Inject
  lateinit var chanFilterManager: ChanFilterManager
  @Inject
  lateinit var chanPostRepository: ChanPostRepository
  @Inject
  lateinit var themeEngine: ThemeEngine
  @Inject
  lateinit var dialogFactory: DialogFactory
  @Inject
  lateinit var proxyStorage: ProxyStorage
  @Inject
  lateinit var importExportRepository: ImportExportRepository

  private val mainSettingsScreen by lazy {
    MainSettingsScreen(
      context,
      chanFilterManager,
      siteManager,
      (context as StartActivity).updateManager,
      reportManager,
      navigationController,
      dialogFactory
    )
  }

  private val threadWatcherSettingsScreen by lazy {
    WatcherSettingsScreen(
      context,
      applicationVisibilityManager,
      themeEngine,
      dialogFactory
    )
  }

  private val appearanceSettingsScreen by lazy {
    AppearanceSettingsScreen(
      context,
      navigationController,
      themeEngine
    )
  }

  private val behaviorSettingsScreen by lazy {
    BehaviourSettingsScreen(
      context,
      navigationController,
      postHideManager
    )
  }

  private val experimentalSettingsScreen by lazy {
    ExperimentalSettingsScreen(
      context,
      navigationController,
      exclusionZonesHolder,
      dialogFactory
    )
  }

  private val developerSettingsScreen by lazy {
    DeveloperSettingsScreen(
      context,
      navigationController,
      cacheHandler,
      fileCacheV2,
      themeEngine
    )
  }

  private val databaseSummaryScreen by lazy {
    DatabaseSettingsSummaryScreen(
      context,
      appConstants,
      inlinedFileInfoRepository,
      mediaServiceLinkExtraContentRepository,
      seenPostRepository,
      chanPostRepository
    )
  }

  private val importExportSettingsScreen by lazy {
    ImportExportSettingsScreen(
      context,
      this,
      navigationController,
      fileChooser,
      fileManager,
      dialogFactory,
      importExportRepository
    )
  }

  private val mediaSettingsScreen by lazy {
    val runtimePermissionsHelper = (context as StartActivity).runtimePermissionsHelper

    MediaSettingsScreen(
      context,
      this,
      navigationController,
      fileManager,
      fileChooser,
      runtimePermissionsHelper,
      dialogFactory
    )
  }

  private val securitySettingsScreen by lazy {
    SecuritySettingsScreen(
      context,
      navigationController,
      proxyStorage,
      drawerCallbacks
    )
  }

  private val onSearchEnteredSubject = BehaviorProcessor.create<String>()
  private val renderSettingsSubject = PublishProcessor.create<RenderAction>()

  private val scrollPositionsPerScreen = mutableMapOf<IScreenIdentifier, PersistableChanState.IndexAndTop>()

  private val settingsGraphDelegate = lazy { buildSettingsGraph() }
  private val settingsGraph by settingsGraphDelegate
  private val screenStack = Stack<IScreenIdentifier>()
  private val job = SupervisorJob()

  private val screensBuiltOnce = SuspendableInitializer<Unit>("screensBuiltOnce")

  override val coroutineContext: CoroutineContext
    get() = job + Dispatchers.Main + CoroutineName("SettingsCoordinator")

  fun onCreate() {
    AppModuleAndroidUtils.extractActivityComponent(context)
      .inject(this)

    launch {
      onSearchEnteredSubject
        .asFlow()
        .catch { error -> Logger.e(TAG, "Unknown error received from onSearchEnteredSubject", error) }
        .debounce(DEBOUNCE_TIME_MS)
        .collect { query ->
          screensBuiltOnce.awaitUntilInitialized()

          if (query.length < MIN_QUERY_LENGTH) {
            rebuildCurrentScreen(BuildOptions.Default)
            return@collect
          }

          rebuildScreenWithSearchQuery(query, BuildOptions.Default)
        }
    }

    launch {
      settingsNotificationManager.listenForNotificationUpdates()
        .asFlow()
        .collect {
          screensBuiltOnce.awaitUntilInitialized()

          rebuildCurrentScreen(BuildOptions.BuildWithNotificationType)
        }
    }

    mainSettingsScreen.onCreate()
    developerSettingsScreen.onCreate()
    databaseSummaryScreen.onCreate()
    threadWatcherSettingsScreen.onCreate()
    appearanceSettingsScreen.onCreate()
    behaviorSettingsScreen.onCreate()
    experimentalSettingsScreen.onCreate()
    importExportSettingsScreen.onCreate()
    mediaSettingsScreen.onCreate()
    securitySettingsScreen.onCreate()
  }

  fun onDestroy() {
    mainSettingsScreen.onDestroy()
    developerSettingsScreen.onDestroy()
    databaseSummaryScreen.onDestroy()
    threadWatcherSettingsScreen.onDestroy()
    appearanceSettingsScreen.onDestroy()
    behaviorSettingsScreen.onDestroy()
    experimentalSettingsScreen.onDestroy()
    importExportSettingsScreen.onDestroy()
    mediaSettingsScreen.onDestroy()
    securitySettingsScreen.onDestroy()

    screenStack.clear()

    if (settingsGraphDelegate.isInitialized()) {
      settingsGraph.clear()
    }

    job.cancelChildren()
  }

  fun listenForRenderScreenActions(): Flowable<RenderAction> {
    return renderSettingsSubject
      .observeOn(AndroidSchedulers.mainThread())
      .hide()
  }

  override fun rebuildSetting(
    screenIdentifier: IScreenIdentifier,
    groupIdentifier: IGroupIdentifier,
    settingIdentifier: SettingsIdentifier
  ) {
    val settingsScreen = settingsGraph[screenIdentifier]
      .apply {
        rebuildSetting(
          groupIdentifier,
          settingIdentifier,
          BuildOptions.Default
        )
      }

    renderSettingsSubject.onNext(RenderAction.RenderScreen(settingsScreen))
  }

  fun getCurrentIndexAndTopOrNull(): PersistableChanState.IndexAndTop? {
    val currentScreen = if (screenStack.isEmpty()) {
      null
    } else {
      screenStack.peek()
    }

    if (currentScreen == null) {
      return null
    }

    return scrollPositionsPerScreen[currentScreen]
  }

  fun storeRecyclerPositionForCurrentScreen(recyclerView: EpoxyRecyclerView) {
    val currentScreen = if (screenStack.isEmpty()) {
      null
    } else {
      screenStack.peek()
    }

    if (currentScreen == null) {
      return
    }

    scrollPositionsPerScreen[currentScreen] = RecyclerUtils.getIndexAndTop(recyclerView)
  }

  fun rebuildScreen(
    screenIdentifier: IScreenIdentifier,
    buildOptions: BuildOptions,
    isFirstRebuild: Boolean = false
  ) {
    launch(Dispatchers.Main.immediate) {
      if (isFirstRebuild) {
        renderSettingsSubject.onNext(RenderAction.Loading)

        siteManager.awaitUntilInitialized()
        boardManager.awaitUntilInitialized()
      }

      pushScreen(screenIdentifier)
      rebuildScreenInternal(screenIdentifier, buildOptions)

      screensBuiltOnce.initWithValue(Unit)
    }
  }

  fun onSearchEntered(query: String) {
    onSearchEnteredSubject.onNext(query)
  }

  fun onBack(): Boolean {
    if (screenStack.size <= 1) {
      screenStack.clear()
      Logger.d(TAG, "onBack() screenStack.size <= 1, exiting")
      return false
    }

    rebuildScreen(popScreen(), BuildOptions.Default)
    return true
  }

  fun rebuildCurrentScreen(buildOptions: BuildOptions) {
    require(screenStack.isNotEmpty()) { "Stack is empty" }

    val screenIdentifier = screenStack.peek()
    rebuildScreen(screenIdentifier, buildOptions)
  }

  fun rebuildScreenWithSearchQuery(query: String, buildOptions: BuildOptions) {
    settingsGraph.rebuildScreens(buildOptions)
    val graph = settingsGraph

    val topScreenIdentifier = if (screenStack.isEmpty()) {
      null
    } else {
      screenStack.peek()
    }

    renderSettingsSubject.onNext(RenderAction.RenderSearchScreen(topScreenIdentifier, graph, query))
  }

  private fun rebuildScreenInternal(screen: IScreenIdentifier, buildOptions: BuildOptions) {
    settingsGraph.rebuildScreen(screen, buildOptions)
    val settingsScreen = settingsGraph[screen]

    renderSettingsSubject.onNext(RenderAction.RenderScreen(settingsScreen))
  }

  private fun popScreen(): IScreenIdentifier {
    val currentScreen = screenStack.peek()
    scrollPositionsPerScreen.remove(currentScreen)

    screenStack.pop()
    return screenStack.peek()
  }

  private fun pushScreen(screenIdentifier: IScreenIdentifier) {
    val stackAlreadyContainsScreen = screenStack.any { screenIdentifierInStack ->
      screenIdentifierInStack == screenIdentifier
    }

    if (!stackAlreadyContainsScreen) {
      screenStack.push(screenIdentifier)
    }
  }

  private fun buildSettingsGraph(): SettingsGraph {
    val graph = SettingsGraph()

    graph += mainSettingsScreen.build()
    graph += developerSettingsScreen.build()
    graph += databaseSummaryScreen.build()
    graph += threadWatcherSettingsScreen.build()
    graph += appearanceSettingsScreen.build()
    graph += behaviorSettingsScreen.build()
    graph += experimentalSettingsScreen.build()
    graph += importExportSettingsScreen.build()
    graph += mediaSettingsScreen.build()
    graph += securitySettingsScreen.build()

    return graph
  }

  sealed class RenderAction {
    object Loading : RenderAction()

    class RenderScreen(val settingsScreen: SettingsScreen) : RenderAction()

    class RenderSearchScreen(
      val topScreenIdentifier: IScreenIdentifier?,
      val graph: SettingsGraph,
      val query: String
    ): RenderAction()
  }

  companion object {
    private const val TAG = "SettingsCoordinator"

    private const val MIN_QUERY_LENGTH = 3
    private const val DEBOUNCE_TIME_MS = 350L
  }
}