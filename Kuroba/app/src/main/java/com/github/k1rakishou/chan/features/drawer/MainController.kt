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
package com.github.k1rakishou.chan.features.drawer

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.recyclerview.widget.LinearLayoutManager
import com.airbnb.epoxy.EpoxyTouchHelper
import com.github.k1rakishou.ChanSettings
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.controller.Controller
import com.github.k1rakishou.chan.core.di.component.activity.ActivityComponent
import com.github.k1rakishou.chan.core.helper.DialogFactory
import com.github.k1rakishou.chan.core.manager.ArchivesManager
import com.github.k1rakishou.chan.core.manager.BoardManager
import com.github.k1rakishou.chan.core.manager.BookmarksManager
import com.github.k1rakishou.chan.core.manager.ChanThreadManager
import com.github.k1rakishou.chan.core.manager.GlobalWindowInsetsManager
import com.github.k1rakishou.chan.core.manager.HistoryNavigationManager
import com.github.k1rakishou.chan.core.manager.PageRequestManager
import com.github.k1rakishou.chan.core.manager.SettingsNotificationManager
import com.github.k1rakishou.chan.core.manager.SiteManager
import com.github.k1rakishou.chan.core.manager.WindowInsetsListener
import com.github.k1rakishou.chan.core.navigation.HasNavigation
import com.github.k1rakishou.chan.features.drawer.data.HistoryControllerState
import com.github.k1rakishou.chan.features.drawer.data.NavigationHistoryEntry
import com.github.k1rakishou.chan.features.drawer.epoxy.EpoxyHistoryEntryView
import com.github.k1rakishou.chan.features.drawer.epoxy.EpoxyHistoryEntryViewModel_
import com.github.k1rakishou.chan.features.drawer.epoxy.epoxyHistoryEntryView
import com.github.k1rakishou.chan.features.drawer.epoxy.epoxyHistoryHeaderView
import com.github.k1rakishou.chan.features.image_saver.ImageSaverV2
import com.github.k1rakishou.chan.features.image_saver.ImageSaverV2OptionsController
import com.github.k1rakishou.chan.features.image_saver.ResolveDuplicateImagesController
import com.github.k1rakishou.chan.features.search.GlobalSearchController
import com.github.k1rakishou.chan.features.settings.MainSettingsControllerV2
import com.github.k1rakishou.chan.ui.controller.FloatingListMenuController
import com.github.k1rakishou.chan.ui.controller.ThreadController
import com.github.k1rakishou.chan.ui.controller.ThreadSlideController
import com.github.k1rakishou.chan.ui.controller.navigation.BottomNavBarAwareNavigationController
import com.github.k1rakishou.chan.ui.controller.navigation.DoubleNavigationController
import com.github.k1rakishou.chan.ui.controller.navigation.NavigationController
import com.github.k1rakishou.chan.ui.controller.navigation.SplitNavigationController
import com.github.k1rakishou.chan.ui.controller.navigation.StyledToolbarNavigationController
import com.github.k1rakishou.chan.ui.controller.navigation.TabHostController
import com.github.k1rakishou.chan.ui.controller.navigation.ToolbarNavigationController
import com.github.k1rakishou.chan.ui.epoxy.epoxyErrorView
import com.github.k1rakishou.chan.ui.epoxy.epoxyLoadingView
import com.github.k1rakishou.chan.ui.epoxy.epoxyTextView
import com.github.k1rakishou.chan.ui.misc.ConstraintLayoutBiasPair
import com.github.k1rakishou.chan.ui.theme.widget.ColorizableDivider
import com.github.k1rakishou.chan.ui.theme.widget.ColorizableEpoxyRecyclerView
import com.github.k1rakishou.chan.ui.theme.widget.TouchBlockingFrameLayout
import com.github.k1rakishou.chan.ui.view.HidingBottomNavigationView
import com.github.k1rakishou.chan.ui.view.bottom_menu_panel.BottomMenuPanel
import com.github.k1rakishou.chan.ui.view.bottom_menu_panel.BottomMenuPanelItem
import com.github.k1rakishou.chan.ui.view.floating_menu.CheckableFloatingListMenuItem
import com.github.k1rakishou.chan.ui.view.floating_menu.FloatingListMenuItem
import com.github.k1rakishou.chan.ui.widget.SimpleEpoxySwipeCallbacks
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.dp
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.getDimen
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.getString
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.inflate
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.isDevBuild
import com.github.k1rakishou.chan.utils.BackgroundUtils
import com.github.k1rakishou.chan.utils.addOneshotModelBuildListener
import com.github.k1rakishou.chan.utils.plusAssign
import com.github.k1rakishou.common.updatePaddings
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.core_themes.ThemeEngine
import com.github.k1rakishou.core_themes.ThemeEngine.Companion.isDarkColor
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import kotlinx.coroutines.launch
import java.util.*
import javax.inject.Inject

class MainController(
  context: Context
) : Controller(context),
  DrawerView,
  DrawerCallbacks,
  View.OnClickListener,
  WindowInsetsListener,
  ThemeEngine.ThemeChangesListener {

  @Inject
  lateinit var themeEngine: ThemeEngine
  @Inject
  lateinit var globalWindowInsetsManager: GlobalWindowInsetsManager
  @Inject
  lateinit var settingsNotificationManager: SettingsNotificationManager
  @Inject
  lateinit var historyNavigationManager: HistoryNavigationManager
  @Inject
  lateinit var siteManager: SiteManager
  @Inject
  lateinit var boardManager: BoardManager
  @Inject
  lateinit var bookmarksManager: BookmarksManager
  @Inject
  lateinit var pageRequestManager: PageRequestManager
  @Inject
  lateinit var archivesManager: ArchivesManager
  @Inject
  lateinit var chanThreadManager: ChanThreadManager
  @Inject
  lateinit var dialogFactory: DialogFactory
  @Inject
  lateinit var imageSaverV2: ImageSaverV2

  private lateinit var rootLayout: TouchBlockingFrameLayout
  private lateinit var container: FrameLayout
  private lateinit var drawerLayout: DrawerLayout
  private lateinit var drawer: LinearLayout
  private lateinit var epoxyRecyclerView: ColorizableEpoxyRecyclerView
  private lateinit var bottomNavView: HidingBottomNavigationView
  private lateinit var divider: ColorizableDivider
  private lateinit var bottomMenuPanel: BottomMenuPanel

  private val drawerPresenter by lazy {
    DrawerPresenter(
      isDevFlavor = isDevBuild(),
      historyNavigationManager = historyNavigationManager,
      siteManager = siteManager,
      bookmarksManager = bookmarksManager,
      pageRequestManager = pageRequestManager,
      archivesManager = archivesManager,
      chanThreadManager = chanThreadManager
    )
  }

  private val childControllersStack = Stack<Controller>()

  private val topThreadController: ThreadController?
    get() {
      val nav = mainToolbarNavigationController
        ?: return null

      if (nav.top is ThreadController) {
        return nav.top as ThreadController?
      }

      if (nav.top is ThreadSlideController) {
        val slideNav = nav.top as ThreadSlideController?

        if (slideNav?.leftController is ThreadController) {
          return slideNav.leftController as ThreadController
        }
      }

      return null
    }

  private val mainToolbarNavigationController: ToolbarNavigationController?
    get() {
      var navigationController: ToolbarNavigationController? = null
      val topController = top
        ?: return null

      if (topController is StyledToolbarNavigationController) {
        navigationController = topController
      } else if (topController is SplitNavigationController) {
        if (topController.getLeftController() is StyledToolbarNavigationController) {
          navigationController = topController.getLeftController() as StyledToolbarNavigationController
        }
      } else if (topController is ThreadSlideController) {
        navigationController = topController.getLeftController() as StyledToolbarNavigationController
      }

      if (navigationController == null) {
        Logger.e(TAG, "topController is an unexpected controller " +
          "type: ${topController::class.java.simpleName}")
      }

      return navigationController
    }

  override fun injectDependencies(component: ActivityComponent) {
    component.inject(this)
  }

  override fun onCreate() {
    super.onCreate()

    view = inflate(context, R.layout.controller_main)
    rootLayout = view.findViewById(R.id.main_root_layout)
    container = view.findViewById(R.id.container)
    drawerLayout = view.findViewById(R.id.drawer_layout)
    drawerLayout.setDrawerShadow(R.drawable.panel_shadow, GravityCompat.START)
    drawer = view.findViewById(R.id.drawer_part)
    epoxyRecyclerView = view.findViewById(R.id.drawer_recycler_view)
    divider = view.findViewById(R.id.divider)
    bottomNavView = view.findViewById(R.id.bottom_navigation_view)
    bottomMenuPanel = view.findViewById(R.id.bottom_menu_panel)

    bottomNavView.selectedItemId = R.id.action_browse
    bottomNavView.elevation = dp(4f).toFloat()

    // Must be above bottomNavView
    bottomMenuPanel.elevation = dp(6f).toFloat()

    bottomNavView.setOnNavigationItemSelectedListener { menuItem ->
      if (bottomNavView.selectedItemId == menuItem.itemId) {
        return@setOnNavigationItemSelectedListener true
      }

      onNavigationItemSelectedListener(menuItem)
      return@setOnNavigationItemSelectedListener true
    }

    EpoxyTouchHelper
      .initSwiping(epoxyRecyclerView)
      .right()
      .withTarget(EpoxyHistoryEntryViewModel_::class.java)
      .andCallbacks(object : SimpleEpoxySwipeCallbacks<EpoxyHistoryEntryViewModel_>() {
        override fun onSwipeCompleted(
          model: EpoxyHistoryEntryViewModel_,
          itemView: View?,
          position: Int,
          direction: Int
        ) {
          super.onSwipeCompleted(model, itemView, position, direction)

          drawerPresenter.onNavElementSwipedAway(model.descriptor())
        }
      })

    compositeDisposable += drawerPresenter.listenForStateChanges()
      .subscribe(
        { state -> onDrawerStateChanged(state) },
        { error ->
          Logger.e(TAG, "Unknown error subscribed to drawerPresenter.listenForStateChanges()", error)
        }
      )

    compositeDisposable += drawerPresenter.listenForBookmarksBadgeStateChanges()
      .subscribe(
        { state -> onBookmarksBadgeStateChanged(state) },
        { error ->
          Logger.e(TAG, "Unknown error subscribed to drawerPresenter.listenForBookmarksBadgeStateChanges()", error)
        }
      )

    compositeDisposable += settingsNotificationManager.listenForNotificationUpdates()
      .subscribe { onSettingsNotificationChanged() }

    // Must be called after drawerPresenter.listenForStateChanges() so it receives the "Loading"
    // state as well as other states
    drawerPresenter.onCreate(this)
    globalWindowInsetsManager.addInsetsUpdatesListener(this)

    themeEngine.addListener(this)
    onThemeChanged()
  }

  override fun onShow() {
    super.onShow()

    drawerPresenter.updateBadge()
  }

  override fun onThemeChanged() {
    drawerPresenter.onThemeChanged()
    settingsNotificationManager.onThemeChanged()

    divider.setBackgroundColor(themeEngine.chanTheme.dividerColor)
    bottomNavView.setBackgroundColor(themeEngine.chanTheme.primaryColor)

    val uncheckedColor = if (ThemeEngine.isBlackOrAlmostBlackColor(themeEngine.chanTheme.primaryColor)) {
      Color.DKGRAY
    } else {
      ThemeEngine.manipulateColor(themeEngine.chanTheme.primaryColor, .7f)
    }

    bottomNavView.itemIconTintList = ColorStateList(
      arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf(-android.R.attr.state_checked)),
      intArrayOf(Color.WHITE, uncheckedColor)
    )

    bottomNavView.itemTextColor = ColorStateList(
      arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf(-android.R.attr.state_checked)),
      intArrayOf(Color.WHITE, uncheckedColor)
    )
  }

  override fun onDestroy() {
    super.onDestroy()

    epoxyRecyclerView.swapAdapter(null, true)
    themeEngine.removeListener(this)
    globalWindowInsetsManager.removeInsetsUpdatesListener(this)
    drawerPresenter.onDestroy()
    compositeDisposable.clear()
  }

  override fun onInsetsChanged() {
    val bottomNavBarHeight = getDimen(R.dimen.bottom_nav_view_height)

    epoxyRecyclerView.updatePaddings(
      top = globalWindowInsetsManager.top(),
      bottom = globalWindowInsetsManager.bottom()
    )

    bottomNavView.layoutParams.height = bottomNavBarHeight + globalWindowInsetsManager.bottom()
    bottomNavView.updateMaxViewHeight(bottomNavBarHeight + globalWindowInsetsManager.bottom())
    bottomNavView.updateBottomPadding(globalWindowInsetsManager.bottom())
  }

  fun pushChildController(childController: Controller) {
    if (childControllers.isNotEmpty()) {
      childControllersStack.push(childControllers.last())
    }

    setCurrentChildController(childController)
  }

  private fun setCurrentChildController(childController: Controller) {
    addChildController(childController)
    childController.attachToParentView(container)
    childController.onShow()
  }

  private fun popChildController(isFromOnBack: Boolean): Boolean {
    if (childControllers.isEmpty() || childControllersStack.isEmpty()) {
      return false
    }

    val prevController = childControllers.last()

    if (isFromOnBack) {
      if (prevController is NavigationController && prevController.onBack()) {
        return true
      }
    }

    prevController.onHide()
    removeChildController(prevController)

    if (childControllersStack.isNotEmpty()) {
      val newController = childControllersStack.pop()

      newController.attachToParentView(container)
      newController.onShow()
    }

    if (childControllersStack.isEmpty()) {
      resetBottomNavViewCheckState()
    }

    return true
  }

  fun attachBottomNavViewToToolbar() {
    val topController = top
      ?: return

    if (topController is ToolbarNavigationController) {
      val toolbar = topController.toolbar
      if (toolbar != null) {
        bottomNavView.setToolbar(toolbar)
      }
    }
  }

  fun openGlobalSearchController() {
    closeAllNonMainControllers()
    openControllerWrappedIntoBottomNavAwareController(GlobalSearchController(context))
    setGlobalSearchMenuItemSelected()

    setDrawerEnabled(false)
  }

  fun openBookmarksController(threadDescriptors: List<ChanDescriptor.ThreadDescriptor>) {
    closeAllNonMainControllers()
    openControllerWrappedIntoBottomNavAwareController(TabHostController(context, threadDescriptors, this))
    setBookmarksMenuItemSelected()

    setDrawerEnabled(false)
  }

  fun openSettingsController() {
    closeAllNonMainControllers()
    openControllerWrappedIntoBottomNavAwareController(MainSettingsControllerV2(context, this))
    setSettingsMenuItemSelected()

    setDrawerEnabled(false)
  }

  fun openControllerWrappedIntoBottomNavAwareController(controller: Controller) {
    val bottomNavBarAwareNavigationController = BottomNavBarAwareNavigationController(
      context,
      object : BottomNavBarAwareNavigationController.CloseBottomNavBarAwareNavigationControllerListener {
        override fun onCloseController() {
          closeBottomNavBarAwareNavigationControllerListener()
        }
      }
    )

    pushChildController(bottomNavBarAwareNavigationController)
    bottomNavBarAwareNavigationController.pushController(controller)
  }

  fun setSettingsMenuItemSelected() {
    bottomNavView.menu.findItem(R.id.action_settings)?.isChecked = true
  }

  fun setBookmarksMenuItemSelected() {
    bottomNavView.menu.findItem(R.id.action_bookmarks)?.isChecked = true
  }

  fun setGlobalSearchMenuItemSelected() {
    bottomNavView.menu.findItem(R.id.action_search)?.isChecked = true
  }

  suspend fun loadThread(
    descriptor: ChanDescriptor.ThreadDescriptor,
    closeAllNonMainControllers: Boolean = false,
    animated: Boolean
  ) {
    mainScope.launch {
      if (closeAllNonMainControllers) {
        closeAllNonMainControllers()
      }

      topThreadController?.showThread(descriptor, animated)
    }
  }

  private fun onNavigationItemSelectedListener(menuItem: MenuItem) {
    when (menuItem.itemId) {
      R.id.action_search -> openGlobalSearchController()
      R.id.action_browse -> {
        closeAllNonMainControllers()
        setDrawerEnabled(true)
      }
      R.id.action_bookmarks -> openBookmarksController(emptyList())
      R.id.action_settings -> openSettingsController()
    }
  }

  private fun closeBottomNavBarAwareNavigationControllerListener() {
    val currentNavController = top
      ?: return

    if (currentNavController !is BottomNavBarAwareNavigationController) {
      return
    }

    popChildController(false)
  }

  fun closeAllNonMainControllers() {
    var currentNavController = top
      ?: return

    while (true) {
      if (currentNavController is BottomNavBarAwareNavigationController) {
        popChildController(false)

        currentNavController = top
          ?: return

        continue
      }

      val topController = currentNavController.top
        ?: return

      // Closing any "floating" controllers like ImageViewController
      closeAllFloatingControllers(topController.childControllers)

      if (topController is HasNavigation) {
        return
      }

      if (currentNavController is NavigationController) {
        currentNavController.popController(false)
      } else if (currentNavController is DoubleNavigationController) {
        currentNavController.popController(false)
      }
    }
  }

  private fun closeAllFloatingControllers(childControllers: List<Controller>) {
    for (childController in childControllers) {
      childController.presentingThisController?.stopPresenting(false)

      if (childController.childControllers.isNotEmpty()) {
        closeAllFloatingControllers(childController.childControllers)
      }
    }
  }

  override fun onClick(v: View) {
    // no-op
  }

  fun onMenuClicked() {
    val topController = mainToolbarNavigationController?.top
      ?: return

    if (topController.navigation.hasDrawer) {
      drawerLayout.openDrawer(drawer)
    }
  }

  override fun onBack(): Boolean {
    if (popChildController(true)) {
      return true
    }

    return if (drawerLayout.isDrawerOpen(drawer)) {
      drawerLayout.closeDrawer(drawer)
      true
    } else {
      super.onBack()
    }
  }

  override fun hideBottomNavBar(lockTranslation: Boolean, lockCollapse: Boolean) {
    bottomNavView.hide(lockTranslation, lockCollapse)
  }

  override fun showBottomNavBar(unlockTranslation: Boolean, unlockCollapse: Boolean) {
    bottomNavView.show(unlockTranslation, unlockCollapse)
  }

  override fun resetBottomNavViewState(unlockTranslation: Boolean, unlockCollapse: Boolean) {
    bottomNavView.resetState(unlockTranslation, unlockCollapse)
  }

  override fun passMotionEventIntoDrawer(event: MotionEvent): Boolean {
    return drawerLayout.onTouchEvent(event)
  }

  override fun resetBottomNavViewCheckState() {
    BackgroundUtils.ensureMainThread()

    // Hack! To reset the bottomNavView's checked item to "browse" when pressing back one either
    // of the bottomNavView's child controllers (Bookmarks or Settings)
    bottomNavView.menu.findItem(R.id.action_browse)?.isChecked = true
    setDrawerEnabled(true)
  }

  override fun showBottomPanel(items: List<BottomMenuPanelItem>) {
    bottomNavView.isEnabled = false
    bottomMenuPanel.show(items)
  }

  override fun hideBottomPanel() {
    bottomNavView.isEnabled = true
    bottomMenuPanel.hide()
  }

  override fun passOnBackToBottomPanel(): Boolean {
    return bottomMenuPanel.onBack()
  }

  fun setDrawerEnabled(enabled: Boolean) {
    val lockMode = if (enabled) {
      DrawerLayout.LOCK_MODE_UNLOCKED
    } else {
      DrawerLayout.LOCK_MODE_LOCKED_CLOSED
    }

    drawerLayout.setDrawerLockMode(lockMode, GravityCompat.START)

    if (!enabled) {
      drawerLayout.closeDrawer(drawer)
    }
  }

  private fun onBookmarksBadgeStateChanged(state: DrawerPresenter.BookmarksBadgeState) {
    if (state.totalUnseenPostsCount <= 0) {
      if (bottomNavView.getBadge(R.id.action_bookmarks) != null) {
        bottomNavView.removeBadge(R.id.action_bookmarks)
      }

      return
    }

    val badgeDrawable = bottomNavView.getOrCreateBadge(R.id.action_bookmarks)

    badgeDrawable.verticalOffset = BADGE_DRAWABLE_VERTICAL_OFFSET
    badgeDrawable.maxCharacterCount = BOOKMARKS_BADGE_COUNTER_MAX_NUMBERS
    badgeDrawable.number = state.totalUnseenPostsCount

    val backgroundColor = if (state.hasUnreadReplies) {
      themeEngine.chanTheme.accentColor
    } else {
      if (isDarkColor(themeEngine.chanTheme.primaryColor)) {
        Color.LTGRAY
      } else {
        Color.DKGRAY
      }
    }

    badgeDrawable.backgroundColor = backgroundColor
    badgeDrawable.badgeTextColor = if (isDarkColor(backgroundColor)) {
      Color.WHITE
    } else {
      Color.BLACK
    }
  }

  private fun onSettingsNotificationChanged() {
    val notificationsCount = settingsNotificationManager.count()

    if (notificationsCount <= 0) {
      if (bottomNavView.getBadge(R.id.action_settings) != null) {
        bottomNavView.removeBadge(R.id.action_settings)
      }

      return
    }

    val badgeDrawable = bottomNavView.getOrCreateBadge(R.id.action_settings)

    badgeDrawable.verticalOffset = BADGE_DRAWABLE_VERTICAL_OFFSET
    badgeDrawable.maxCharacterCount = SETTINGS_BADGE_COUNTER_MAX_NUMBERS
    badgeDrawable.number = notificationsCount

    badgeDrawable.backgroundColor = themeEngine.chanTheme.accentColor
    badgeDrawable.badgeTextColor = if (isDarkColor(themeEngine.chanTheme.accentColor)) {
      Color.WHITE
    } else {
      Color.BLACK
    }
  }

  private fun onDrawerStateChanged(state: HistoryControllerState) {
    epoxyRecyclerView.withModels {
      addOneshotModelBuildListener {
        val llm = (epoxyRecyclerView.layoutManager as LinearLayoutManager)

        if (llm.findFirstCompletelyVisibleItemPosition() <= 1) {
          // Scroll to the top of the nav history list if the previous fully visible item's position
          // was either 0 or 1
          llm.scrollToPosition(0)
        }
      }

      if (state is HistoryControllerState.Loading) {
        epoxyLoadingView {
          id("history_loading_view")
        }

        return@withModels
      }

      epoxyHistoryHeaderView {
        id("navigation_history_header")
        onThemeSwitcherClicked { epoxyRecyclerView.postDelayed({ themeEngine.toggleTheme() }, 125L) }
        onDrawerSettingsClicked { showDrawerOptions() }
      }

      when (state) {
        HistoryControllerState.Empty -> {
          epoxyTextView {
            id("history_is_empty_text_view")
            message(context.getString(R.string.drawer_controller_navigation_history_is_empty))
          }
        }
        is HistoryControllerState.Error -> {
          epoxyErrorView {
            id("history_error_view")
            errorMessage(state.errorText)
          }
        }
        is HistoryControllerState.Data -> {
          state.navHistoryEntryList.forEach { navHistoryEntry ->
            val requestData = EpoxyHistoryEntryView.ImagesLoaderRequestData(
              navHistoryEntry.threadThumbnailUrl,
              navHistoryEntry.siteThumbnailUrl
            )

            epoxyHistoryEntryView {
              id("navigation_history_entry_${navHistoryEntry.hashCode()}")
              descriptor(navHistoryEntry.descriptor)
              imageLoaderRequestData(requestData)
              title(navHistoryEntry.title)
              bindNavHistoryBookmarkAdditionalInfo(navHistoryEntry.additionalInfo)
              clickListener { onHistoryEntryViewClicked(navHistoryEntry) }
            }
          }
        }
        HistoryControllerState.Loading -> throw IllegalStateException("Must be handled separately")
      }
    }
  }

  private fun showDrawerOptions() {
    val drawerOptions = mutableListOf<FloatingListMenuItem>()

    drawerOptions += CheckableFloatingListMenuItem(
      key = ACTION_MOVE_LAST_ACCESSED_THREAD_TO_TOP,
      name = getString(R.string.drawer_controller_move_last_accessed_thread_to_top),
      isCurrentlySelected = ChanSettings.drawerMoveLastAccessedThreadToTop.get()
    )

    drawerOptions += CheckableFloatingListMenuItem(
      key = ACTION_SHOW_BOOKMARKS,
      name = getString(R.string.drawer_controller_show_bookmarks),
      isCurrentlySelected = ChanSettings.drawerShowBookmarkedThreads.get()
    )

    drawerOptions += CheckableFloatingListMenuItem(
      key = ACTION_SHOW_NAV_HISTORY,
      name = getString(R.string.drawer_controller_show_navigation_history),
      isCurrentlySelected = ChanSettings.drawerShowNavigationHistory.get()
    )

    drawerOptions += FloatingListMenuItem(
      key = ACTION_CLEAR_NAV_HISTORY,
      name = getString(R.string.drawer_controller_clear_nav_history)
    )

    val floatingListMenuController = FloatingListMenuController(
      context = context,
      constraintLayoutBiasPair = ConstraintLayoutBiasPair.TopLeft,
      items = drawerOptions,
      itemClickListener = { item -> onDrawerOptionClicked(item) }
    )

    presentController(floatingListMenuController)
  }

  private fun onDrawerOptionClicked(item: FloatingListMenuItem) {
    when (item.key) {
      ACTION_MOVE_LAST_ACCESSED_THREAD_TO_TOP -> {
        ChanSettings.drawerMoveLastAccessedThreadToTop.toggle()
      }
      ACTION_SHOW_BOOKMARKS -> {
        ChanSettings.drawerShowBookmarkedThreads.toggle()

        if (ChanSettings.drawerShowBookmarkedThreads.get()) {
          historyNavigationManager.createNewNavElements(
            drawerPresenter.mapBookmarksIntoNewNavigationElements()
          )
        } else {
          val bookmarkDescriptors = bookmarksManager
            .mapAllBookmarks { threadBookmarkView -> threadBookmarkView.threadDescriptor }

          historyNavigationManager.removeNavElements(bookmarkDescriptors)
        }

        drawerPresenter.reloadNavigationHistory()
      }
      ACTION_SHOW_NAV_HISTORY -> {
        ChanSettings.drawerShowNavigationHistory.toggle()
        drawerPresenter.reloadNavigationHistory()
      }
      ACTION_CLEAR_NAV_HISTORY -> {
        dialogFactory.createSimpleConfirmationDialog(
          context = context,
          titleTextId = R.string.drawer_controller_clear_nav_history_dialog_title,
          negativeButtonText = getString(R.string.do_not),
          positiveButtonText = getString(R.string.clear),
          onPositiveButtonClickListener = { historyNavigationManager.clear() }
        )
      }
    }
  }

  private fun onHistoryEntryViewClicked(navHistoryEntry: NavigationHistoryEntry) {
    mainScope.launch {
      val currentTopThreadController = topThreadController
        ?: return@launch

      when (val descriptor = navHistoryEntry.descriptor) {
        is ChanDescriptor.ThreadDescriptor -> {
          currentTopThreadController.showThread(descriptor, true)
        }
        is ChanDescriptor.CatalogDescriptor -> {
          currentTopThreadController.showBoard(descriptor.boardDescriptor, true)
        }
      }

      if (drawerLayout.isDrawerOpen(drawer)) {
        drawerLayout.closeDrawer(drawer)
      }
    }
  }

  fun showResolveDuplicateImagesController(uniqueId: String, imageSaverOptionsJson: String) {
    val alreadyPresenting = isAlreadyPresenting { controller -> controller is ResolveDuplicateImagesController }
    if (alreadyPresenting) {
      return
    }

    val resolveDuplicateImagesController = ResolveDuplicateImagesController(
      context,
      uniqueId,
      imageSaverOptionsJson
    )

    presentController(resolveDuplicateImagesController)
  }

  fun showImageSaverV2OptionsController(uniqueId: String) {
    val alreadyPresenting = isAlreadyPresenting { controller -> controller is ImageSaverV2OptionsController }
    if (alreadyPresenting) {
      return
    }

    val options = ImageSaverV2OptionsController.Options.ResultDirAccessProblems(
      uniqueId,
      onRetryClicked = { imageSaverV2Options -> imageSaverV2.restartUncompleted(uniqueId, imageSaverV2Options) },
      onCancelClicked = { imageSaverV2.deleteDownload(uniqueId) }
    )

    val imageSaverV2OptionsController = ImageSaverV2OptionsController(context, options)
    presentController(imageSaverV2OptionsController)
  }

  companion object {
    private const val TAG = "DrawerController"
    private const val BOOKMARKS_BADGE_COUNTER_MAX_NUMBERS = 5
    private const val SETTINGS_BADGE_COUNTER_MAX_NUMBERS = 2

    private const val ACTION_MOVE_LAST_ACCESSED_THREAD_TO_TOP = 0
    private const val ACTION_SHOW_BOOKMARKS = 1
    private const val ACTION_SHOW_NAV_HISTORY = 2
    private const val ACTION_CLEAR_NAV_HISTORY = 3

    private val BADGE_DRAWABLE_VERTICAL_OFFSET = dp(4f)
  }
}