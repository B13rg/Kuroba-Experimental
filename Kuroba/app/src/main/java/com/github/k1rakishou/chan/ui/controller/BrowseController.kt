package com.github.k1rakishou.chan.ui.controller

import android.Manifest
import android.content.Context
import android.os.Build
import android.widget.Toast
import com.github.k1rakishou.ChanSettings
import com.github.k1rakishou.ChanSettings.BoardPostViewMode
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.base.SerializedCoroutineExecutor
import com.github.k1rakishou.chan.core.di.component.activity.ActivityComponent
import com.github.k1rakishou.chan.core.helper.DialogFactory
import com.github.k1rakishou.chan.core.helper.SitesSetupControllerOpenNotifier
import com.github.k1rakishou.chan.core.manager.FirewallBypassManager
import com.github.k1rakishou.chan.core.manager.HistoryNavigationManager
import com.github.k1rakishou.chan.core.presenter.BrowsePresenter
import com.github.k1rakishou.chan.core.presenter.ThreadPresenter
import com.github.k1rakishou.chan.core.site.SiteResolver
import com.github.k1rakishou.chan.features.bypass.CookieResult
import com.github.k1rakishou.chan.features.bypass.SiteFirewallBypassController
import com.github.k1rakishou.chan.features.drawer.MainControllerCallbacks
import com.github.k1rakishou.chan.features.media_viewer.MediaLocation
import com.github.k1rakishou.chan.features.media_viewer.MediaViewerActivity
import com.github.k1rakishou.chan.features.setup.BoardSelectionController
import com.github.k1rakishou.chan.features.setup.SiteSettingsController
import com.github.k1rakishou.chan.features.setup.SitesSetupController
import com.github.k1rakishou.chan.features.site_archive.BoardArchiveController
import com.github.k1rakishou.chan.features.toolbar_v2.HamburgMenuItem
import com.github.k1rakishou.chan.features.toolbar_v2.KurobaToolbarState
import com.github.k1rakishou.chan.features.toolbar_v2.ToolbarMenuCheckableOverflowItem
import com.github.k1rakishou.chan.features.toolbar_v2.ToolbarMenuItem
import com.github.k1rakishou.chan.features.toolbar_v2.ToolbarMenuOverflowItem
import com.github.k1rakishou.chan.features.toolbar_v2.ToolbarOverflowMenuBuilder
import com.github.k1rakishou.chan.features.toolbar_v2.ToolbarText
import com.github.k1rakishou.chan.ui.adapter.PostsFilter
import com.github.k1rakishou.chan.ui.controller.ThreadSlideController.ReplyAutoCloseListener
import com.github.k1rakishou.chan.ui.controller.ThreadSlideController.SlideChangeListener
import com.github.k1rakishou.chan.ui.controller.base.DeprecatedNavigationFlags
import com.github.k1rakishou.chan.ui.controller.base.ui.NavigationControllerContainerLayout
import com.github.k1rakishou.chan.ui.controller.navigation.SplitNavigationController
import com.github.k1rakishou.chan.ui.controller.navigation.StyledToolbarNavigationController
import com.github.k1rakishou.chan.ui.helper.RuntimePermissionsHelper
import com.github.k1rakishou.chan.ui.layout.ThreadLayout.ThreadLayoutCallback
import com.github.k1rakishou.chan.ui.view.KurobaBottomNavigationView
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.getString
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.inflate
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.isDevBuild
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.sp
import com.github.k1rakishou.chan.utils.SpannableHelper
import com.github.k1rakishou.common.FirewallType
import com.github.k1rakishou.common.errorMessageOrClassName
import com.github.k1rakishou.common.resumeValueSafe
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.model.data.descriptor.BoardDescriptor
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor.CatalogDescriptor
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor.ThreadDescriptor
import com.github.k1rakishou.model.data.descriptor.PostDescriptor
import com.github.k1rakishou.model.data.descriptor.SiteDescriptor
import com.github.k1rakishou.model.data.options.ChanCacheUpdateOptions
import dagger.Lazy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import javax.inject.Inject

class BrowseController(
  context: Context,
  mainControllerCallbacks: MainControllerCallbacks
) : ThreadController(context, mainControllerCallbacks),
  ThreadLayoutCallback,
  BrowsePresenter.Callback,
  SlideChangeListener,
  ReplyAutoCloseListener {

  @Inject
  lateinit var presenter: BrowsePresenter
  @Inject
  lateinit var historyNavigationManagerLazy: Lazy<HistoryNavigationManager>
  @Inject
  lateinit var siteResolverLazy: Lazy<SiteResolver>
  @Inject
  lateinit var firewallBypassManagerLazy: Lazy<FirewallBypassManager>
  @Inject
  lateinit var runtimePermissionsHelper: RuntimePermissionsHelper
  @Inject
  lateinit var sitesSetupControllerOpenNotifier: SitesSetupControllerOpenNotifier

  private val historyNavigationManager: HistoryNavigationManager
    get() = historyNavigationManagerLazy.get()
  private val siteResolver: SiteResolver
    get() = siteResolverLazy.get()
  private val firewallBypassManager: FirewallBypassManager
    get() = firewallBypassManagerLazy.get()

  private lateinit var serializedCoroutineExecutor: SerializedCoroutineExecutor

  private var updateCompositeCatalogNavigationSubtitleJob: Job? = null

  override val threadControllerType: ThreadControllerType
    get() = ThreadControllerType.Catalog

  override val kurobaToolbarState: KurobaToolbarState
    get() = toolbarState

  override fun injectDependencies(component: ActivityComponent) {
    component.inject(this)
  }

  override fun onCreate() {
    super.onCreate()

    val navControllerContainerLayout = inflate(context, R.layout.controller_browse)
    val container = navControllerContainerLayout.findViewById<NavigationControllerContainerLayout>(R.id.browse_controller_container)
    container.initBrowseControllerTracker(this, requireNavController())
    container.addView(view)
    view = container

    // Navigation
    updateNavigationFlags(
      newNavigationFlags = DeprecatedNavigationFlags(
        hasBack = false,
        hasDrawer = true
      )
    )

    buildMenu()

    // Presenter
    presenter.create(controllerScope, this)

    // Initialization
    serializedCoroutineExecutor = SerializedCoroutineExecutor(controllerScope)

    threadLayout.setBoardPostViewMode(ChanSettings.boardPostViewMode.get())

    serializedCoroutineExecutor.post {
      val order = PostsFilter.Order.find(ChanSettings.boardOrder.get())

      threadLayout.presenter.setOrder(
        order = order,
        isManuallyChangedOrder = false
      )
    }

    controllerScope.launch {
      firewallBypassManager.showFirewallControllerEvents.collect { showFirewallControllerInfo ->
        val alreadyPresenting = isAlreadyPresenting { controller ->
          controller is SiteFirewallBypassController && controller.alive
        }

        if (alreadyPresenting) {
          return@collect
        }

        val firewallType = showFirewallControllerInfo.firewallType
        val urlToOpen = showFirewallControllerInfo.urlToOpen
        val siteDescriptor = showFirewallControllerInfo.siteDescriptor
        val onFinished = showFirewallControllerInfo.onFinished

        showSiteFirewallBypassController(
          firewallType = firewallType,
          urlToOpen = urlToOpen,
          siteDescriptor = siteDescriptor,
          onBypassControllerClosed = { success -> onFinished.complete(success) }
        )
      }
    }

    controllerScope.launch {
      requestApi33NotificationsPermissionOnce()
    }
  }

  override fun onShow() {
    super.onShow()

    mainControllerCallbacks.resetBottomNavViewCheckState()

    if (KurobaBottomNavigationView.isBottomNavViewEnabled()) {
      mainControllerCallbacks.showBottomNavBar(unlockTranslation = false, unlockCollapse = false)
    }
  }

  override fun onDestroy() {
    super.onDestroy()

    updateCompositeCatalogNavigationSubtitleJob?.cancel()
    updateCompositeCatalogNavigationSubtitleJob = null

    presenter.destroy()
  }

  override suspend fun showSitesNotSetup() {
    super.showSitesNotSetup()

    toolbarState.catalog.updateTitle(
      newTitle = ToolbarText.Id(R.string.browse_controller_title_app_setup),
      newSubTitle = ToolbarText.Id(R.string.browse_controller_subtitle)
    )
  }

  suspend fun setCatalog(catalogDescriptor: ChanDescriptor.ICatalogDescriptor) {
    Logger.d(TAG, "setCatalog($catalogDescriptor)")

    presenter.loadCatalog(catalogDescriptor)
  }

  suspend fun loadWithDefaultBoard() {
    Logger.d(TAG, "loadWithDefaultBoard()")

    presenter.loadWithDefaultBoard()
  }

  override suspend fun loadCatalog(catalogDescriptor: ChanDescriptor.ICatalogDescriptor) {
    controllerScope.launch(Dispatchers.Main.immediate) {
      Logger.d(TAG, "loadCatalog($catalogDescriptor)")

      threadLayout.presenter.bindChanDescriptor(catalogDescriptor as ChanDescriptor)

      updateToolbarTitle(catalogDescriptor)
      updateMenuItems()
    }
  }

  override suspend fun updateToolbarTitle(catalogDescriptor: ChanDescriptor.ICatalogDescriptor) {
    boardManager.awaitUntilInitialized()

    updateCompositeCatalogNavigationSubtitleJob?.cancel()
    updateCompositeCatalogNavigationSubtitleJob = null

    when (catalogDescriptor) {
      is CatalogDescriptor -> {
        val boardDescriptor = catalogDescriptor.boardDescriptor

        val board = boardManager.byBoardDescriptor(boardDescriptor)
          ?: return

        toolbarState.catalog.updateTitle(
          newTitle = ToolbarText.String("/" + boardDescriptor.boardCode + "/"),
          newSubTitle = ToolbarText.String(board.name ?: "")
        )
      }
      is ChanDescriptor.CompositeCatalogDescriptor -> {
        toolbarState.catalog.updateTitle(
          newTitle = ToolbarText.Id(R.string.composite_catalog),
          newSubTitle = ToolbarText.Id(R.string.browse_controller_composite_catalog_subtitle_loading)
        )

        updateCompositeCatalogNavigationSubtitleJob = controllerScope.launch {
          val newTitle = presenter.getCompositeCatalogNavigationTitle(catalogDescriptor)

          val compositeCatalogSubTitle = SpannableHelper.getCompositeCatalogNavigationSubtitle(
            siteManager = siteManager,
            coroutineScope = this,
            context = context,
            fontSizePx = sp(12f),
            compositeCatalogDescriptor = catalogDescriptor
          )

          toolbarState.catalog.updateTitle(
            newTitle = ToolbarText.String(newTitle ?: ""),
            newSubTitle = ToolbarText.Spanned(compositeCatalogSubTitle)
          )
        }
      }
    }
  }

  override suspend fun showCatalogWithoutFocusing(catalogDescriptor: ChanDescriptor.ICatalogDescriptor, animated: Boolean) {
    controllerScope.launch(Dispatchers.Main.immediate) {
      Logger.d(TAG, "showCatalogWithoutFocusing($catalogDescriptor, $animated)")

      showCatalogInternal(
        catalogDescriptor = catalogDescriptor,
        showCatalogOptions = ShowCatalogOptions(
          switchToCatalogController = false,
          withAnimation = animated
        )
      )
    }
  }

  override suspend fun showCatalog(catalogDescriptor: ChanDescriptor.ICatalogDescriptor, animated: Boolean) {
    controllerScope.launch(Dispatchers.Main.immediate) {
      Logger.d(TAG, "showBoard($catalogDescriptor, $animated)")

      showCatalogInternal(
        catalogDescriptor = catalogDescriptor,
        showCatalogOptions = ShowCatalogOptions(
          switchToCatalogController = true,
          withAnimation = animated
        )
      )
    }
  }

  override suspend fun setCatalog(catalogDescriptor: ChanDescriptor.ICatalogDescriptor, animated: Boolean) {
    controllerScope.launch(Dispatchers.Main.immediate) {
      Logger.d(TAG, "setCatalog($catalogDescriptor, $animated)")

      setCatalog(catalogDescriptor)
    }
  }

  override suspend fun showPostsInExternalThread(
    postDescriptor: PostDescriptor,
    isPreviewingCatalogThread: Boolean
  ) {
    showPostsInExternalThreadHelper.showPostsInExternalThread(postDescriptor, isPreviewingCatalogThread)
  }

  override suspend fun openExternalThread(postDescriptor: PostDescriptor, scrollToPost: Boolean) {
    val descriptor = chanDescriptor
      ?: return

    openExternalThreadHelper.openExternalThread(
      currentChanDescriptor = descriptor,
      postDescriptor = postDescriptor,
      scrollToPost = scrollToPost
    ) { threadDescriptor ->
      controllerScope.launch { showThread(descriptor = threadDescriptor, animated = true) }
    }
  }

  fun getViewThreadController(): ViewThreadController? {
    var splitNav: SplitNavigationController? = null
    var slideNav: ThreadSlideController? = null

    if (doubleNavigationController is SplitNavigationController) {
      splitNav = doubleNavigationController as SplitNavigationController?
    }

    if (doubleNavigationController is ThreadSlideController) {
      slideNav = doubleNavigationController as ThreadSlideController?
    }

    return when {
      splitNav != null -> {
        val navigationController = splitNav.rightController() as StyledToolbarNavigationController?
        navigationController?.topController as? ViewThreadController
      }
      slideNav != null -> {
        slideNav.rightController()
      }
      else -> null
    }
  }

  // Creates or updates the target ThreadViewController
  // This controller can be in various places depending on the layout
  // We dynamically search for it
  override suspend fun showThread(descriptor: ThreadDescriptor, animated: Boolean) {
    showThreadInternal(
      descriptor = descriptor,
      showThreadOptions = ShowThreadOptions(
        switchToThreadController = true,
        pushControllerWithAnimation = animated
      )
    )
  }

  override suspend fun showThreadWithoutFocusing(descriptor: ThreadDescriptor, animated: Boolean) {
    showThreadInternal(
      descriptor = descriptor,
      showThreadOptions = ShowThreadOptions(
        switchToThreadController = false,
        pushControllerWithAnimation = animated
      )
    )
  }

  override fun onLostFocus(wasFocused: ThreadControllerType) {
    super.onLostFocus(wasFocused)
    check(wasFocused == threadControllerType) { "Unexpected controllerType: $wasFocused" }
  }

  override fun onGainedFocus(nowFocused: ThreadControllerType) {
    super.onGainedFocus(nowFocused)
    check(nowFocused == threadControllerType) { "Unexpected controllerType: $nowFocused" }

    if (chanDescriptor != null && historyNavigationManager.isInitialized) {
      if (threadLayout.presenter.chanThreadLoadingState == ThreadPresenter.ChanThreadLoadingState.Loaded) {
        controllerScope.launch { historyNavigationManager.moveNavElementToTop(chanDescriptor!!) }
      }
    }

    currentOpenedDescriptorStateManager.updateCurrentFocusedController(
      ThreadPresenter.CurrentFocusedController.Catalog
    )
  }

  private suspend fun showThreadInternal(descriptor: ThreadDescriptor, showThreadOptions: ShowThreadOptions) {
    Logger.d(TAG, "showThread($descriptor, $showThreadOptions)")

    // The target ThreadViewController is in a split nav
    // (BrowseController -> ToolbarNavigationController -> SplitNavigationController)
    var splitNav: SplitNavigationController? = null

    // The target ThreadViewController is in a slide nav
    // (BrowseController -> SlideController -> ToolbarNavigationController)
    var slideNav: ThreadSlideController? = null
    if (doubleNavigationController is SplitNavigationController) {
      splitNav = doubleNavigationController as SplitNavigationController?
    }

    if (doubleNavigationController is ThreadSlideController) {
      slideNav = doubleNavigationController as ThreadSlideController?
    }

    when {
      splitNav != null -> {
        // Create a threadview inside a toolbarnav in the right part of the split layout
        if (splitNav.rightController() is StyledToolbarNavigationController) {
          val navigationController = splitNav.rightController() as StyledToolbarNavigationController
          if (navigationController.topController is ViewThreadController) {
            val viewThreadController = navigationController.topController as ViewThreadController
            viewThreadController.loadThread(descriptor)
            viewThreadController.onShow()
            viewThreadController.onGainedFocus(ThreadControllerType.Thread)
          }
        } else {
          val navigationController = StyledToolbarNavigationController(context)
          splitNav.updateRightController(navigationController, showThreadOptions.pushControllerWithAnimation)
          val viewThreadController = ViewThreadController(context, mainControllerCallbacks, descriptor)
          navigationController.pushController(viewThreadController, false)
          viewThreadController.onGainedFocus(ThreadControllerType.Thread)
        }

        splitNav.switchToController(
          false,
          showThreadOptions.pushControllerWithAnimation
        )
      }
      slideNav != null -> {
        // Create a threadview in the right part of the slide nav *without* a toolbar
        if (slideNav.rightController() is ViewThreadController) {
          (slideNav.rightController() as ViewThreadController).loadThread(descriptor)
          (slideNav.rightController() as ViewThreadController).onShow()
        } else {
          val viewThreadController = ViewThreadController(
            context,
            mainControllerCallbacks,
            descriptor
          )

          slideNav.updateRightController(
            viewThreadController,
            showThreadOptions.pushControllerWithAnimation
          )
        }

        if (showThreadOptions.switchToThreadController) {
          slideNav.switchToController(
            false,
            showThreadOptions.pushControllerWithAnimation
          )
        }
      }
      else -> {
        // the target ThreadNav must be pushed to the parent nav controller
        // (BrowseController -> ToolbarNavigationController)
        val viewThreadController = ViewThreadController(
          context,
          mainControllerCallbacks,
          descriptor
        )

        navigationController!!.pushController(
          viewThreadController,
          showThreadOptions.pushControllerWithAnimation
        )
      }
    }
  }

  private suspend fun showCatalogInternal(
    catalogDescriptor: ChanDescriptor.ICatalogDescriptor,
    showCatalogOptions: ShowCatalogOptions
  ) {
    Logger.d(TAG, "showCatalogInternal($catalogDescriptor, $showCatalogOptions)")

    // The target ThreadViewController is in a split nav
    // (BrowseController -> ToolbarNavigationController -> SplitNavigationController)
    val splitNav = if (doubleNavigationController is SplitNavigationController) {
      doubleNavigationController as SplitNavigationController?
    } else {
      null
    }

    // The target ThreadViewController is in a slide nav
    // (BrowseController -> SlideController -> ToolbarNavigationController)
    val slideNav = if (doubleNavigationController is ThreadSlideController) {
      doubleNavigationController as ThreadSlideController?
    } else {
      null
    }

    // Do nothing when split navigation is enabled because both controllers are always visible
    // so we don't need to switch between left and right controllers
    if (splitNav == null) {
      if (slideNav != null) {
        if (showCatalogOptions.switchToCatalogController) {
          slideNav.switchToController(true, showCatalogOptions.withAnimation)
        }
      } else {
        if (navigationController != null) {
          // We wouldn't want to pop BrowseController when opening a board
          if (requireNavController().topController !is BrowseController) {
            requireNavController().popController(showCatalogOptions.withAnimation)
          }
        }
      }
    }

    setCatalog(catalogDescriptor)
  }

  private fun updateMenuItems() {
    toolbarState.findOverflowItem(ACTION_BOARD_ARCHIVE)?.let { menuItem ->
      val supportsArchive = siteSupportsBuiltInBoardArchive()
      menuItem.updateVisibility(supportsArchive)
    }

    toolbarState.findOverflowItem(ACTION_LOAD_WHOLE_COMPOSITE_CATALOG)?.let { menuItem ->
      val isCompositeCatalog =
        threadLayout.presenter.currentChanDescriptor is ChanDescriptor.CompositeCatalogDescriptor

      menuItem.updateVisibility(isCompositeCatalog)
    }

    toolbarState.findOverflowItem(ACTION_OPEN_BROWSER)?.let { menuItem ->
      val isNotCompositeCatalog =
        threadLayout.presenter.currentChanDescriptor !is ChanDescriptor.CompositeCatalogDescriptor

      menuItem.updateVisibility(isNotCompositeCatalog)
    }

    toolbarState.findOverflowItem(ACTION_OPEN_UNLIMITED_CATALOG_PAGE)?.let { menuItem ->
      val isUnlimitedCatalog = threadLayout.presenter.isUnlimitedOrCompositeCatalog
        && threadLayout.presenter.currentChanDescriptor !is ChanDescriptor.CompositeCatalogDescriptor

      menuItem.updateVisibility(isUnlimitedCatalog)
    }

    toolbarState.findOverflowItem(ACTION_SHARE)?.let { menuItem ->
      val isNotCompositeCatalog =
        threadLayout.presenter.currentChanDescriptor !is ChanDescriptor.CompositeCatalogDescriptor

      menuItem.updateVisibility(isNotCompositeCatalog)
    }
  }

  private fun siteSupportsBuiltInBoardArchive(): Boolean {
    val chanDescriptor = threadLayout.presenter.currentChanDescriptor
    if (chanDescriptor == null) {
      return false
    }

    if (chanDescriptor !is CatalogDescriptor) {
      return false
    }

    return chanDescriptor.siteDescriptor().is4chan() || chanDescriptor.siteDescriptor().isDvach()
  }

  private suspend fun showSiteFirewallBypassController(
    firewallType: FirewallType,
    urlToOpen: HttpUrl,
    siteDescriptor: SiteDescriptor,
    onBypassControllerClosed: (Boolean) -> Unit
  ) {
    val cookieResult = suspendCancellableCoroutine<CookieResult> { continuation ->
      val controller = SiteFirewallBypassController(
        context = context,
        firewallType = firewallType,
        headerTitleText = getString(R.string.firewall_check_header_title, firewallType.name),
        urlToOpen = urlToOpen.toString(),
        onResult = { cookieResult ->
          continuation.resumeValueSafe(cookieResult)
          onBypassControllerClosed(cookieResult is CookieResult.CookieValue)
        }
      )

      Logger.d(TAG, "presentController SiteFirewallBypassController (firewallType: ${firewallType}, urlToOpen: ${urlToOpen})")
      presentController(controller)

      continuation.invokeOnCancellation {
        Logger.d(TAG, "stopPresenting SiteFirewallBypassController (firewallType: ${firewallType}, urlToOpen: ${urlToOpen})")

        if (controller.alive) {
          controller.stopPresenting()
        }
      }
    }

    when (firewallType) {
      FirewallType.Cloudflare -> {
        when (cookieResult) {
          CookieResult.Canceled -> {
            AppModuleAndroidUtils.showToast(
              context,
              getString(R.string.firewall_check_canceled, firewallType),
              Toast.LENGTH_LONG
            )
          }
          CookieResult.NotSupported -> {
            AppModuleAndroidUtils.showToast(
              context,
              getString(R.string.firewall_check_not_supported, firewallType, siteDescriptor.siteName),
              Toast.LENGTH_LONG
            )
          }
          is CookieResult.Error -> {
            val errorMsg = cookieResult.exception.errorMessageOrClassName()
            AppModuleAndroidUtils.showToast(
              context,
              getString(R.string.firewall_check_failure, firewallType, errorMsg),
              Toast.LENGTH_LONG
            )
          }
          is CookieResult.CookieValue -> {
            AppModuleAndroidUtils.showToast(
              context,
              getString(R.string.firewall_check_success, firewallType),
              Toast.LENGTH_LONG
            )
          }
        }
      }
      FirewallType.DvachAntiSpam -> {
        when (cookieResult) {
          CookieResult.Canceled -> {
            AppModuleAndroidUtils.showToast(
              context,
              R.string.dvach_antispam_result_canceled,
              Toast.LENGTH_LONG
            )
          }
          CookieResult.NotSupported -> {
            AppModuleAndroidUtils.showToast(
              context,
              getString(R.string.firewall_check_not_supported, firewallType, siteDescriptor.siteName),
              Toast.LENGTH_LONG
            )
          }
          is CookieResult.Error -> {
            val errorMsg = cookieResult.exception.errorMessageOrClassName()
            AppModuleAndroidUtils.showToast(
              context,
              getString(R.string.dvach_antispam_result_error, errorMsg),
              Toast.LENGTH_LONG
            )
          }
          is CookieResult.CookieValue -> {
            AppModuleAndroidUtils.showToast(
              context,
              getString(R.string.dvach_antispam_result_success),
              Toast.LENGTH_LONG
            )
          }
        }
      }
      FirewallType.YandexSmartCaptcha -> {
        // No-op. We only handle Yandex's captcha in one place (ImageSearchController)
      }
    }
  }

  private fun requestApi33NotificationsPermissionOnce() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
      return
    }

    if (ChanSettings.api33NotificationPermissionRequested.get()) {
      return
    }

    if (runtimePermissionsHelper.hasPermission(Manifest.permission.POST_NOTIFICATIONS)) {
      return
    }

    runtimePermissionsHelper.requestPermission(
      Manifest.permission.POST_NOTIFICATIONS
    ) { granted ->
      ChanSettings.api33NotificationPermissionRequested.set(true)

      if (granted) {
        return@requestPermission
      }

      dialogFactory.createSimpleInformationDialog(
        context = context,
        titleText = context.getString(R.string.api_33_android_13_notifications_permission_title),
        descriptionText = context.getString(R.string.api_33_android_13_notifications_permission_descriptor),
      )
    }
  }


  private fun openBoardSelectionController() {
    val siteDescriptor = if (chanDescriptor is ChanDescriptor.CompositeCatalogDescriptor) {
      null
    } else {
      chanDescriptor?.siteDescriptor()
    }

    val boardSelectionController = BoardSelectionController(
      context = context,
      currentSiteDescriptor = siteDescriptor,
      callback = object : BoardSelectionController.UserSelectionListener {
        override fun onOpenSitesSettingsClicked() {
          sitesSetupControllerOpenNotifier.onSitesSetupControllerOpened()
          openSitesSetupController()
        }

        override fun onSiteSelected(siteDescriptor: SiteDescriptor) {
          openSiteSettingsController(siteDescriptor)
        }

        override fun onCatalogSelected(catalogDescriptor: ChanDescriptor.ICatalogDescriptor) {
          if (currentOpenedDescriptorStateManager.currentCatalogDescriptor == catalogDescriptor) {
            return
          }

          controllerScope.launch(Dispatchers.Main.immediate) { loadCatalog(catalogDescriptor) }
        }
      })

    requireNavController().presentController(boardSelectionController)
  }

  private fun openSitesSetupController() {
    val sitesSetupController = SitesSetupController(context)
    if (doubleNavigationController != null) {
      doubleNavigationController!!.openControllerWrappedIntoBottomNavAwareController(sitesSetupController)
    } else {
      requireStartActivity().openControllerWrappedIntoBottomNavAwareController(sitesSetupController)
    }

    requireStartActivity().setSettingsMenuItemSelected()
  }

  private fun openSiteSettingsController(siteDescriptor: SiteDescriptor) {
    val siteSettingsController = SiteSettingsController(context, siteDescriptor)
    if (doubleNavigationController != null) {
      doubleNavigationController!!.openControllerWrappedIntoBottomNavAwareController(siteSettingsController)
    } else {
      requireStartActivity().openControllerWrappedIntoBottomNavAwareController(siteSettingsController)
    }

    requireStartActivity().setSettingsMenuItemSelected()
  }

  @Suppress("MoveLambdaOutsideParentheses")
  private fun buildMenu() {
    val modeStringId = when (ChanSettings.boardPostViewMode.get()) {
      BoardPostViewMode.LIST -> R.string.action_switch_catalog_grid
      BoardPostViewMode.GRID -> R.string.action_switch_catalog_stagger
      BoardPostViewMode.STAGGER -> R.string.action_switch_board
    }

    val supportsArchive = siteSupportsBuiltInBoardArchive()
    val isCompositeCatalog = threadLayout.presenter.currentChanDescriptor is ChanDescriptor.CompositeCatalogDescriptor
    val isUnlimitedCatalog = threadLayout.presenter.isUnlimitedCatalog && !isCompositeCatalog

    toolbarState.enterCatalogMode(
      leftItem = HamburgMenuItem(
        onClick = {
          globalUiStateHolder.updateDrawerState {
            openDrawer()
          }
        }
      ),
      onMainContentClick = {
        if (!siteManager.areSitesSetup()) {
          openSitesSetupController()
        } else {
          openBoardSelectionController()
        }
      },
      menuBuilder = {
        withMenuItem(drawableId = R.drawable.ic_search_white_24dp, onClick = { item -> searchClicked(item) })
        withMenuItem(drawableId = R.drawable.ic_refresh_white_24dp, onClick = { item -> reloadClicked(item) })

        withOverflowMenu {
          withOverflowMenuItem(id = ACTION_REPLY, stringId = R.string.action_reply, onClick = { item -> replyClicked(item) })
          withOverflowMenuItem(id = ACTION_CHANGE_VIEW_MODE, stringId = modeStringId, onClick = { item -> viewModeClicked(item) })

          addSortMenu()
          addDevMenu()

          withOverflowMenuItem(
            id = ACTION_LOAD_WHOLE_COMPOSITE_CATALOG,
            stringId = R.string.action_rest_composite_catalog,
            visible = isCompositeCatalog,
            onClick = { controllerScope.launch { threadLayout.presenter.loadWholeCompositeCatalog() } }
          )
          withOverflowMenuItem(
            id = ACTION_CATALOG_ALBUM,
            stringId = R.string.action_catalog_album,
            onClick = { threadLayout.presenter.showAlbum() }
          )
          withOverflowMenuItem(
            id = ACTION_OPEN_BROWSER,
            stringId = R.string.action_open_browser,
            visible = !isCompositeCatalog,
            onClick = { item -> openBrowserClicked(item) }
          )
          withOverflowMenuItem(
            id = ACTION_OPEN_CATALOG_OR_THREAD_BY_IDENTIFIER,
            stringId = R.string.action_open_catalog_or_thread_by_identifier,
            onClick = { item -> openCatalogOrThreadByIdentifier(item) }
          )
          withOverflowMenuItem(
            id = ACTION_OPEN_MEDIA_BY_URL,
            stringId = R.string.action_open_media_by_url,
            onClick = { item -> openMediaByUrl(item) }
          )
          withOverflowMenuItem(
            id = ACTION_OPEN_UNLIMITED_CATALOG_PAGE,
            stringId = R.string.action_open_catalog_page,
            visible = isUnlimitedCatalog,
            onClick = { openCatalogPageClicked() }
          )
          withOverflowMenuItem(
            id = ACTION_SHARE,
            stringId = R.string.action_share,
            onClick = { item -> shareClicked(item) }
          )
          withOverflowMenuItem(
            id = ACTION_BOARD_ARCHIVE,
            stringId = R.string.action_board_archive,
            visible = supportsArchive,
            onClick = { viewBoardArchiveClicked() }
          )
          withOverflowMenuItem(
            id = ACTION_VIEW_REMOVED_THREADS,
            stringId = R.string.action_view_removed_threads,
            onClick = { threadLayout.presenter.showRemovedPostsDialog() }
          )
          withOverflowMenuItem(
            id = ACTION_SCROLL_TO_TOP,
            stringId = R.string.action_scroll_to_top,
            onClick = { item -> upClicked(item) }
          )
          withOverflowMenuItem(
            id = ACTION_SCROLL_TO_BOTTOM,
            stringId = R.string.action_scroll_to_bottom,
            onClick = { item -> downClicked(item) }
          )
        }
      }
    )
  }

  @Suppress("MoveLambdaOutsideParentheses")
  private fun ToolbarOverflowMenuBuilder.addDevMenu() {
    withOverflowMenuItem(
      id = ACTION_DEV_MENU,
      stringId = R.string.action_browse_dev_menu,
      visible = isDevBuild(),
      builder = {
        withOverflowMenuItem(
          id = DEV_BOOKMARK_EVERY_THREAD,
          stringId = R.string.dev_bookmark_every_thread,
          onClick = {
            controllerScope.launch {
              presenter.bookmarkEveryThread(threadLayout.presenter.currentChanDescriptor)
            }
          }
        )
        withOverflowMenuItem(
          id = DEV_CACHE_EVERY_THREAD,
          stringId = R.string.dev_cache_every_thread,
          onClick = { presenter.cacheEveryThreadClicked(threadLayout.presenter.currentChanDescriptor) }
        )
      }
    )
  }

  @Suppress("MoveLambdaOutsideParentheses")
  private fun ToolbarOverflowMenuBuilder.addSortMenu() {
    val currentOrder = PostsFilter.Order.find(ChanSettings.boardOrder.get())

    withOverflowMenuItem(
      id = ACTION_SORT,
      stringId = R.string.action_sort,
      groupId = "catalog_sort",
      builder = {
        withCheckableOverflowMenuItem(
          id = SORT_MODE_BUMP,
          stringId = R.string.order_bump,
          visible = true,
          checked = currentOrder == PostsFilter.Order.BUMP,
          value = PostsFilter.Order.BUMP,
          onClick = { subItem -> onSortItemClicked(subItem) }
        )
        withCheckableOverflowMenuItem(
          id = SORT_MODE_REPLY,
          stringId = R.string.order_reply,
          visible = true,
          checked = currentOrder == PostsFilter.Order.REPLY,
          value = PostsFilter.Order.REPLY,
          onClick = { subItem -> onSortItemClicked(subItem) }
        )
        withCheckableOverflowMenuItem(
          id = SORT_MODE_IMAGE,
          stringId = R.string.order_image,
          visible = true,
          checked = currentOrder == PostsFilter.Order.IMAGE,
          value = PostsFilter.Order.IMAGE,
          onClick = { subItem -> onSortItemClicked(subItem) }
        )
        withCheckableOverflowMenuItem(
          id = SORT_MODE_NEWEST,
          stringId = R.string.order_newest,
          visible = true,
          checked = currentOrder == PostsFilter.Order.NEWEST,
          value = PostsFilter.Order.NEWEST,
          onClick = { subItem -> onSortItemClicked(subItem) }
        )
        withCheckableOverflowMenuItem(
          id = SORT_MODE_OLDEST,
          stringId = R.string.order_oldest,
          visible = true,
          checked = currentOrder == PostsFilter.Order.OLDEST,
          value = PostsFilter.Order.OLDEST,
          onClick = { subItem -> onSortItemClicked(subItem) }
        )
        withCheckableOverflowMenuItem(
          id = SORT_MODE_MODIFIED,
          stringId = R.string.order_modified,
          visible = true,
          checked = currentOrder == PostsFilter.Order.MODIFIED,
          value = PostsFilter.Order.MODIFIED,
          onClick = { subItem -> onSortItemClicked(subItem) }
        )
        withCheckableOverflowMenuItem(
          id = SORT_MODE_ACTIVITY,
          stringId = R.string.order_activity,
          visible = true,
          checked = currentOrder == PostsFilter.Order.ACTIVITY,
          value = PostsFilter.Order.ACTIVITY,
          onClick = { subItem -> onSortItemClicked(subItem) }
        )
      }
    )
  }

  private fun onSortItemClicked(subItem: ToolbarMenuCheckableOverflowItem) {
    serializedCoroutineExecutor.post {
      val order = subItem.value as? PostsFilter.Order
        ?: return@post

      ChanSettings.boardOrder.set(order.orderName)
      toolbarState.checkOrUncheckItem(subItem, true)

      val presenter = threadLayout.presenter
      presenter.setOrder(order, isManuallyChangedOrder = true)
    }
  }

  private fun searchClicked(item: ToolbarMenuItem) {
    val presenter = threadLayout.presenter
    if (!presenter.isBoundAndCached || chanDescriptor == null) {
      return
    }

    threadLayout.popupHelper.showSearchPopup(chanDescriptor!!)
  }

  private fun reloadClicked(item: ToolbarMenuItem) {
    val presenter = threadLayout.presenter
    if (!presenter.isBound) {
      return
    }

    presenter.normalLoad(
      showLoading = true,
      chanCacheUpdateOptions = ChanCacheUpdateOptions.UpdateCache
    )

    item.spinItemOnce()
  }

  private fun replyClicked(item: ToolbarMenuOverflowItem) {
    threadLayout.openOrCloseReply(true)
  }

  private fun viewModeClicked(item: ToolbarMenuOverflowItem) {
    var postViewMode = ChanSettings.boardPostViewMode.get()

    postViewMode = when (postViewMode) {
      BoardPostViewMode.LIST -> BoardPostViewMode.GRID
      BoardPostViewMode.GRID -> BoardPostViewMode.STAGGER
      BoardPostViewMode.STAGGER -> BoardPostViewMode.LIST
    }

    ChanSettings.boardPostViewMode.set(postViewMode)

    val viewModeText = when (postViewMode) {
      BoardPostViewMode.LIST -> R.string.action_switch_catalog_grid
      BoardPostViewMode.GRID -> R.string.action_switch_catalog_stagger
      BoardPostViewMode.STAGGER -> R.string.action_switch_board
    }

    item.updateMenuText(getString(viewModeText))
    threadLayout.setBoardPostViewMode(postViewMode)
  }

  private fun openBrowserClicked(item: ToolbarMenuOverflowItem) {
    handleShareOrOpenInBrowser(false)
  }

  private fun openCatalogOrThreadByIdentifier(item: ToolbarMenuOverflowItem) {
    if (chanDescriptor == null) {
      return
    }

    dialogFactory.createSimpleDialogWithInput(
      context = context,
      titleTextId = R.string.browse_controller_enter_identifier,
      descriptionTextId = R.string.browse_controller_enter_identifier_description,
      onValueEntered = { input: String -> openCatalogOrThreadByIdentifierInternal(input) },
      inputType = DialogFactory.DialogInputType.String
    )
  }

  private fun openMediaByUrl(item: ToolbarMenuOverflowItem) {
    if (chanDescriptor == null) {
      return
    }

    dialogFactory.createSimpleDialogWithInput(
      context = context,
      titleTextId = R.string.browse_controller_enter_media_url,
      onValueEntered = { input: String ->
        val mediaUrl = input.toHttpUrlOrNull()
        if (mediaUrl == null) {
          showToast(getString(R.string.browse_controller_enter_media_url_error, input))
          return@createSimpleDialogWithInput
        }

        MediaViewerActivity.mixedMedia(context, listOf(MediaLocation.Remote(input)))
      },
      inputType = DialogFactory.DialogInputType.String
    )
  }

  private fun openCatalogOrThreadByIdentifierInternal(input: String) {
    controllerScope.launch {
      val chanDescriptorResult = siteResolver.resolveChanDescriptorForUrl(input)
      if (chanDescriptorResult == null) {
        if (chanDescriptor is ChanDescriptor.CompositeCatalogDescriptor) {
          showToast(
            getString(R.string.open_by_board_code_or_thread_no_composite_catalog_error, input),
            Toast.LENGTH_LONG
          )

          return@launch
        }

        val currentCatalogDescriptor = chanDescriptor as? CatalogDescriptor
        if (currentCatalogDescriptor == null) {
          return@launch
        }

        val inputTrimmed = input.trim()

        val asThreadNo = inputTrimmed.toLongOrNull()
        if (asThreadNo != null && asThreadNo > 0) {
          val threadDescriptor = currentCatalogDescriptor.toThreadDescriptor(threadNo = asThreadNo)
          showThread(threadDescriptor, false)
          return@launch
        }

        val asBoardCode = inputTrimmed.replace("/", "")
        if (asBoardCode.all { ch -> ch.isLetter() }) {
          val boardDescriptor = BoardDescriptor.create(
            siteDescriptor = currentCatalogDescriptor.siteDescriptor(),
            boardCode = asBoardCode
          )

          showCatalog(CatalogDescriptor.create(boardDescriptor), false)
          return@launch
        }

        // fallthrough
      } else {
        val resolvedChanDescriptor = chanDescriptorResult.chanDescriptor
        if (resolvedChanDescriptor is ChanDescriptor.ICatalogDescriptor) {
          showCatalog(resolvedChanDescriptor.catalogDescriptor(), false)
          return@launch
        }

        if (resolvedChanDescriptor is ThreadDescriptor) {
          if (chanDescriptorResult.markedPostNo > 0L) {
            chanThreadViewableInfoManager.update(
              chanDescriptor = resolvedChanDescriptor,
              createEmptyWhenNull = true
            ) { ctvi -> ctvi.markedPostNo = chanDescriptorResult.markedPostNo }
          }

          showThread(resolvedChanDescriptor, false)
          return@launch
        }

        // fallthrough
      }

      showToast(
        getString(R.string.open_link_not_matched, input),
        Toast.LENGTH_LONG
      )
    }
  }

  private fun openThreadByIdInternal(input: String) {
    controllerScope.launch(Dispatchers.Main.immediate) {
      val threadNo = input.toLong()
      if (threadNo <= 0) {
        showToast(getString(R.string.browse_controller_error_thread_id_negative_or_zero))
        return@launch
      }

      try {
        val threadDescriptor = ThreadDescriptor.create(
          siteName = chanDescriptor!!.siteName(),
          boardCode = chanDescriptor!!.boardCode(),
          threadNo = input.toLong()
        )

        showThread(threadDescriptor, true)
      } catch (e: NumberFormatException) {
        showToast(context.getString(R.string.browse_controller_error_parsing_thread_id))
      }
    }
  }

  private fun shareClicked(item: ToolbarMenuOverflowItem) {
    handleShareOrOpenInBrowser(true)
  }

  private fun viewBoardArchiveClicked() {
    val boardArchiveController = BoardArchiveController(
      context = context,
      catalogDescriptor = chanDescriptor!! as CatalogDescriptor,
      onThreadClicked = { threadDescriptor ->
        controllerScope.launch { showThread(threadDescriptor, animated = true) }
      }
    )

    threadLayout.pushController(boardArchiveController)
  }

  private fun openCatalogPageClicked() {
    dialogFactory.createSimpleDialogWithInput(
      context = context,
      inputType = DialogFactory.DialogInputType.Integer,
      titleText = getString(R.string.browse_controller_enter_page_number),
      onValueEntered = { pageString ->
        val page = pageString.toIntOrNull()
        if (page == null) {
          showToast(getString(R.string.browse_controller_failed_to_parse_page_number, pageString))
          return@createSimpleDialogWithInput
        }

        threadLayout.presenter.loadCatalogPage(overridePage = page)
      }
    )
  }

  private fun upClicked(item: ToolbarMenuOverflowItem) {
    threadLayout.presenter.scrollTo(0, false)
  }

  private fun downClicked(item: ToolbarMenuOverflowItem) {
    threadLayout.presenter.scrollTo(-1, false)
  }

  override fun onReplyViewShouldClose() {
    if (toolbarState.isInReplyMode()) {
      toolbarState.pop()
    }

    threadLayout.openOrCloseReply(false)
  }

  private fun handleShareOrOpenInBrowser(share: Boolean) {
    val presenter = threadLayout.presenter
    if (!presenter.isBound) {
      return
    }

    if (presenter.currentChanDescriptor == null) {
      Logger.e(TAG, "handleShareOrOpenInBrowser() chanThread == null")
      showToast(R.string.cannot_open_in_browser_already_deleted)
      return
    }

    val chanDescriptor = presenter.currentChanDescriptor
    if (chanDescriptor == null) {
      Logger.e(TAG, "handleShareOrOpenInBrowser() chanDescriptor == null")
      showToast(R.string.cannot_open_in_browser_already_deleted)
      return
    }

    val site = siteManager.bySiteDescriptor(chanDescriptor.siteDescriptor())
    if (site == null) {
      Logger.e(TAG, "handleShareOrOpenInBrowser() site == null " +
        "(siteDescriptor = ${chanDescriptor.siteDescriptor()})")
      showToast(R.string.cannot_open_in_browser_already_deleted)
      return
    }

    val link = site.resolvable().desktopUrl(chanDescriptor, null)
    if (share) {
      AppModuleAndroidUtils.shareLink(link)
    } else {
      AppModuleAndroidUtils.openLink(link)
    }
  }

  companion object {
    private const val TAG = "BrowseController"

    private const val ACTION_CHANGE_VIEW_MODE = 901
    private const val ACTION_SORT = 902
    private const val ACTION_DEV_MENU = 903
    private const val ACTION_REPLY = 904
    private const val ACTION_OPEN_BROWSER = 905
    private const val ACTION_SHARE = 906
    private const val ACTION_SCROLL_TO_TOP = 907
    private const val ACTION_SCROLL_TO_BOTTOM = 908
    private const val ACTION_OPEN_CATALOG_OR_THREAD_BY_IDENTIFIER = 910
    private const val ACTION_OPEN_MEDIA_BY_URL = 911
    private const val ACTION_CATALOG_ALBUM = 912
    private const val ACTION_BOARD_ARCHIVE = 913
    private const val ACTION_OPEN_UNLIMITED_CATALOG_PAGE = 914
    private const val ACTION_LOAD_WHOLE_COMPOSITE_CATALOG = 915
    private const val ACTION_VIEW_REMOVED_THREADS = 916

    private const val SORT_MODE_BUMP = 1000
    private const val SORT_MODE_REPLY = 1001
    private const val SORT_MODE_IMAGE = 1002
    private const val SORT_MODE_NEWEST = 1003
    private const val SORT_MODE_OLDEST = 1004
    private const val SORT_MODE_MODIFIED = 1005
    private const val SORT_MODE_ACTIVITY = 1006

    private const val DEV_BOOKMARK_EVERY_THREAD = 2000
    private const val DEV_CACHE_EVERY_THREAD = 2001
  }
}
