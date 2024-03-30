package com.github.k1rakishou.chan.ui.controller

import android.content.Context
import android.view.KeyEvent
import android.view.MotionEvent
import android.widget.Toast
import androidx.compose.ui.unit.Dp
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout.OnRefreshListener
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.controller.Controller
import com.github.k1rakishou.chan.core.base.SerializedCoroutineExecutor
import com.github.k1rakishou.chan.core.helper.DialogFactory
import com.github.k1rakishou.chan.core.manager.ApplicationVisibility
import com.github.k1rakishou.chan.core.manager.ApplicationVisibilityListener
import com.github.k1rakishou.chan.core.manager.ApplicationVisibilityManager
import com.github.k1rakishou.chan.core.manager.ArchivesManager
import com.github.k1rakishou.chan.core.manager.ChanThreadManager
import com.github.k1rakishou.chan.core.manager.ChanThreadViewableInfoManager
import com.github.k1rakishou.chan.core.manager.CurrentOpenedDescriptorStateManager
import com.github.k1rakishou.chan.core.manager.GlobalWindowInsetsManager
import com.github.k1rakishou.chan.core.manager.SiteManager
import com.github.k1rakishou.chan.core.manager.ThreadFollowHistoryManager
import com.github.k1rakishou.chan.core.site.Site
import com.github.k1rakishou.chan.features.drawer.MainControllerCallbacks
import com.github.k1rakishou.chan.features.filters.FiltersController
import com.github.k1rakishou.chan.features.media_viewer.MediaLocation
import com.github.k1rakishou.chan.features.media_viewer.MediaViewerActivity
import com.github.k1rakishou.chan.features.media_viewer.MediaViewerOptions
import com.github.k1rakishou.chan.features.media_viewer.helper.MediaViewerOpenAlbumHelper
import com.github.k1rakishou.chan.features.media_viewer.helper.MediaViewerScrollerHelper
import com.github.k1rakishou.chan.features.report.Chan4ReportPostController
import com.github.k1rakishou.chan.features.toolbar_v2.KurobaToolbarState
import com.github.k1rakishou.chan.ui.controller.ThreadSlideController.SlideChangeListener
import com.github.k1rakishou.chan.ui.helper.AppSettingsUpdateAppRefreshHelper
import com.github.k1rakishou.chan.ui.helper.OpenExternalThreadHelper
import com.github.k1rakishou.chan.ui.helper.ShowPostsInExternalThreadHelper
import com.github.k1rakishou.chan.ui.layout.ThreadLayout
import com.github.k1rakishou.chan.ui.layout.ThreadLayout.ThreadLayoutCallback
import com.github.k1rakishou.chan.ui.view.floating_menu.FloatingListMenuItem
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.dp
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.getString
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.inflate
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.isDevBuild
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.core_themes.ThemeEngine
import com.github.k1rakishou.model.data.descriptor.ArchiveDescriptor
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.descriptor.PostDescriptor
import com.github.k1rakishou.model.data.filter.ChanFilterMutable
import com.github.k1rakishou.model.data.filter.FilterType
import com.github.k1rakishou.model.data.post.ChanPost
import com.github.k1rakishou.model.data.post.ChanPostImage
import com.github.k1rakishou.persist_state.ReplyMode
import dagger.Lazy
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import okhttp3.HttpUrl
import javax.inject.Inject

abstract class ThreadController(
  context: Context,
  val mainControllerCallbacks: MainControllerCallbacks
) : Controller(context),
  ThreadLayoutCallback,
  OnRefreshListener,
  SlideChangeListener,
  ApplicationVisibilityListener,
  ThemeEngine.ThemeChangesListener,
  AlbumViewController.ThreadControllerCallbacks {

  @Inject
  lateinit var siteManagerLazy: Lazy<SiteManager>
  @Inject
  lateinit var themeEngineLazy: Lazy<ThemeEngine>
  @Inject
  lateinit var applicationVisibilityManagerLazy: Lazy<ApplicationVisibilityManager>
  @Inject
  lateinit var chanThreadManagerLazy: Lazy<ChanThreadManager>
  @Inject
  lateinit var threadFollowHistoryManagerLazy: Lazy<ThreadFollowHistoryManager>
  @Inject
  lateinit var archivesManagerLazy: Lazy<ArchivesManager>
  @Inject
  lateinit var globalWindowInsetsManagerLazy: Lazy<GlobalWindowInsetsManager>
  @Inject
  lateinit var chanThreadViewableInfoManagerLazy: Lazy<ChanThreadViewableInfoManager>
  @Inject
  lateinit var mediaViewerScrollerHelperLazy: Lazy<MediaViewerScrollerHelper>
  @Inject
  lateinit var mediaViewerOpenAlbumHelperLazy: Lazy<MediaViewerOpenAlbumHelper>
  @Inject
  lateinit var appSettingsUpdateAppRefreshHelperLazy: Lazy<AppSettingsUpdateAppRefreshHelper>
  @Inject
  lateinit var dialogFactoryLazy: Lazy<DialogFactory>
  @Inject
  lateinit var currentOpenedDescriptorStateManagerLazy: Lazy<CurrentOpenedDescriptorStateManager>

  protected val siteManager: SiteManager
    get() = siteManagerLazy.get()
  protected val themeEngine: ThemeEngine
    get() = themeEngineLazy.get()
  protected val chanThreadViewableInfoManager: ChanThreadViewableInfoManager
    get() = chanThreadViewableInfoManagerLazy.get()
  protected val archivesManager: ArchivesManager
    get() = archivesManagerLazy.get()
  protected val dialogFactory: DialogFactory
    get() = dialogFactoryLazy.get()
  protected val chanThreadManager: ChanThreadManager
    get() = chanThreadManagerLazy.get()
  protected val threadFollowHistoryManager: ThreadFollowHistoryManager
    get() = threadFollowHistoryManagerLazy.get()
  protected val currentOpenedDescriptorStateManager: CurrentOpenedDescriptorStateManager
    get() = currentOpenedDescriptorStateManagerLazy.get()

  private val applicationVisibilityManager: ApplicationVisibilityManager
    get() = applicationVisibilityManagerLazy.get()
  protected val globalWindowInsetsManager: GlobalWindowInsetsManager
    get() = globalWindowInsetsManagerLazy.get()
  private val mediaViewerScrollerHelper: MediaViewerScrollerHelper
    get() = mediaViewerScrollerHelperLazy.get()
  private val mediaViewerOpenAlbumHelper: MediaViewerOpenAlbumHelper
    get() = mediaViewerOpenAlbumHelperLazy.get()
  private val appSettingsUpdateAppRefreshHelper: AppSettingsUpdateAppRefreshHelper
    get() = appSettingsUpdateAppRefreshHelperLazy.get()

  protected lateinit var threadLayout: ThreadLayout
  protected lateinit var showPostsInExternalThreadHelper: ShowPostsInExternalThreadHelper
  protected lateinit var openExternalThreadHelper: OpenExternalThreadHelper

  private lateinit var swipeRefreshLayout: SwipeRefreshLayout
  private lateinit var serializedCoroutineExecutor: SerializedCoroutineExecutor

  val chanDescriptor: ChanDescriptor?
    get() = threadLayout.presenter.currentChanDescriptor

  override val kurobaToolbarState: KurobaToolbarState
    get() = navigationController!!.requireToolbarNavController().containerToolbarState

  abstract override val threadControllerType: ThreadControllerType

  override fun onCreate() {
    super.onCreate()

    threadLayout = inflate(context, R.layout.layout_thread, null) as ThreadLayout
    threadLayout.create(this, threadControllerType, mainControllerCallbacks.navigationViewContractType)
    threadLayout.setDrawerCallbacks(mainControllerCallbacks)

    swipeRefreshLayout = SwipeRefreshLayout(context)

    swipeRefreshLayout.setOnChildScrollUpCallback { parent, child ->
      return@setOnChildScrollUpCallback threadLayout.canChildScrollUp()
    }

    swipeRefreshLayout.id = R.id.swipe_refresh_layout
    swipeRefreshLayout.addView(threadLayout)
    swipeRefreshLayout.setOnRefreshListener(this)

    view = swipeRefreshLayout

    serializedCoroutineExecutor = SerializedCoroutineExecutor(controllerScope)
    applicationVisibilityManager.addListener(this)

    showPostsInExternalThreadHelper = ShowPostsInExternalThreadHelper(
      context = context,
      scope = controllerScope,
      postPopupHelper = threadLayout.popupHelper,
      chanThreadManagerLazy = chanThreadManagerLazy,
      presentControllerFunc = { controller -> presentController(controller) },
      showAvailableArchivesListFunc = { postDescriptor, canAutoSelectArchive ->
        showAvailableArchivesList(
          postDescriptor = postDescriptor,
          preview = true,
          canAutoSelectArchive = canAutoSelectArchive
        )
      },
      showToastFunc = { message -> showToast(message) }
    )

    openExternalThreadHelper = OpenExternalThreadHelper(
      postPopupHelper = threadLayout.popupHelper,
      chanThreadViewableInfoManagerLazy = chanThreadViewableInfoManagerLazy,
      threadFollowHistoryManagerLazy = threadFollowHistoryManagerLazy
    )

    controllerScope.launch {
      mediaViewerScrollerHelper.mediaViewerScrollEventsFlow
        .collect { scrollToImageEvent ->
          val descriptor = scrollToImageEvent.chanDescriptor
          if (descriptor != chanDescriptor) {
            return@collect
          }

          threadLayout.presenter.scrollToImage(scrollToImageEvent.chanPostImage, true)
        }
    }

    controllerScope.launch {
      mediaViewerOpenAlbumHelper.mediaViewerOpenAlbumEventsFlow
        .collect { openAlbumEvent ->
          val descriptor = openAlbumEvent.chanDescriptor
          if (descriptor != chanDescriptor) {
            return@collect
          }

          showAlbum(openAlbumEvent.chanPostImage.imageUrl, threadLayout.displayingPostDescriptors)
        }
    }

    controllerScope.launch {
      appSettingsUpdateAppRefreshHelper.settingsUpdatedEvent.collect {
        Logger.d(TAG, "Reloading thread because app settings were updated")
        threadLayout.presenter.quickReloadFromMemoryCache()
      }
    }

    controllerScope.launch {
      globalUiStateHolder.replyLayout.replyLayoutVisibilityEventsFlow
        .onEach { replyLayoutVisibilityEvents ->
          val isInReplyLayoutMode = kurobaToolbarState.isInReplyMode()
          val anyReplyLayoutOpenedOrExpanded = replyLayoutVisibilityEvents.anyOpened() || replyLayoutVisibilityEvents.anyExpanded()

          if (isInReplyLayoutMode == anyReplyLayoutOpenedOrExpanded) {
            return@onEach
          }

          if (anyReplyLayoutOpenedOrExpanded) {
            kurobaToolbarState.enterReplyMode()
          } else {
            if (toolbarState.isInReplyMode()) {
              toolbarState.pop()
            }
          }
        }
        .collect()
    }

    controllerScope.launch {
      toolbarState.toolbarHeightState
        .onEach { toolbarHeight -> onToolbarHeightChanged(toolbarHeight) }
        .collect()
    }

    onThemeChanged()
    themeEngine.addListener(this)
  }

  override fun onShow() {
    super.onShow()

    threadLayout.onShown(threadControllerType)
  }

  override fun onHide() {
    super.onHide()

    threadLayout.onHidden(threadControllerType)
  }

  override fun onDestroy() {
    super.onDestroy()

    threadLayout.destroy()
    applicationVisibilityManager.removeListener(this)
    themeEngine.removeListener(this)
  }

  override fun onThemeChanged() {
  }

  fun passMotionEventIntoDrawer(event: MotionEvent): Boolean {
    return mainControllerCallbacks.passMotionEventIntoDrawer(event)
  }

  fun passMotionEventIntoSlidingPaneLayout(event: MotionEvent): Boolean {
    val threadSlideController = (this.parentController as? ThreadSlideController)
      ?: return false

    return threadSlideController.passMotionEventIntoSlidingPaneLayout(event)
  }

  fun showLoading(animateTransition: Boolean = false) {
    threadLayout.showLoading(animateTransition = animateTransition)
  }

  open suspend fun showSitesNotSetup() {
    threadLayout.presenter.showNoContent()
  }

  fun highlightPost(postDescriptor: PostDescriptor?, blink: Boolean) {
    threadLayout.presenter.highlightPost(postDescriptor, blink)
  }

  override fun onBack(): Boolean {
    return threadLayout.onBack()
  }

  override fun dispatchKeyEvent(event: KeyEvent): Boolean {
    return threadLayout.sendKeyEvent(event) || super.dispatchKeyEvent(event)
  }

  override fun onApplicationVisibilityChanged(applicationVisibility: ApplicationVisibility) {
    threadLayout.presenter.onForegroundChanged(applicationVisibility.isInForeground())
  }

  override fun onRefresh() {
    threadLayout.refreshFromSwipe()
  }

  override fun openReportController(post: ChanPost) {
    val site = siteManager.bySiteDescriptor(post.boardDescriptor.siteDescriptor)
    if (site == null || navigationController == null) {
      return
    }

    if (site.siteDescriptor().is4chan()) {
      val chan4ReportPostController = Chan4ReportPostController(
        context = context,
        postDescriptor = post.postDescriptor,
        onCaptchaRequired = {
          val threadDescriptor = ChanDescriptor.ThreadDescriptor.create(post.boardDescriptor, 1L)

          threadLayout.showCaptchaController(
            chanDescriptor = threadDescriptor,
            replyMode = ReplyMode.ReplyModeSendWithoutCaptcha,
            autoReply = false,
            afterPostingAttempt = true
          )
        },
        onOpenInWebView = {
          openWebViewReportController(post, site)
        }
      )

      requireNavController().presentController(chan4ReportPostController)
      return
    } else if (site.siteDescriptor().isDvach()) {
      dialogFactory.createSimpleDialogWithInput(
        context = context,
        titleText = getString(R.string.dvach_report_post_title, post.postDescriptor.userReadableString()),
        descriptionText = getString(R.string.dvach_report_post_description),
        inputType = DialogFactory.DialogInputType.String,
        onValueEntered = { reason -> threadLayout.presenter.processDvachPostReport(reason, post, site) }
      )
      return
    }

    openWebViewReportController(post, site)
  }

  fun selectPostImage(postImage: ChanPostImage) {
    threadLayout.presenter.selectPostImage(postImage)
  }

  override fun openMediaLinkInMediaViewer(link: String) {
    Logger.d(TAG, "openMediaLinkInMediaViewer($link)")

    MediaViewerActivity.mixedMedia(
      context = context,
      mixedMedia = listOf(MediaLocation.Remote(link))
    )
  }

  override fun showImages(
    chanDescriptor: ChanDescriptor,
    initialImageUrl: String?,
    transitionThumbnailUrl: String
  ) {
    when (chanDescriptor) {
      is ChanDescriptor.ICatalogDescriptor -> {
        MediaViewerActivity.catalogMedia(
          context = context,
          catalogDescriptor = chanDescriptor,
          initialImageUrl = initialImageUrl,
          transitionThumbnailUrl = transitionThumbnailUrl,
          lastTouchCoordinates = globalWindowInsetsManager.lastTouchCoordinates(),
          mediaViewerOptions = MediaViewerOptions(
            mediaViewerOpenedFromAlbum = false
          )
        )
      }
      is ChanDescriptor.ThreadDescriptor -> {
        MediaViewerActivity.threadMedia(
          context = context,
          threadDescriptor = chanDescriptor,
          postDescriptorList = threadLayout.displayingPostDescriptors,
          initialImageUrl = initialImageUrl,
          transitionThumbnailUrl = transitionThumbnailUrl,
          lastTouchCoordinates = globalWindowInsetsManager.lastTouchCoordinates(),
          mediaViewerOptions = MediaViewerOptions(
            mediaViewerOpenedFromAlbum = false
          )
        )
      }
    }
  }

  override fun showAlbum(initialImageUrl: HttpUrl?, displayingPostDescriptors: List<PostDescriptor>) {
    val descriptor = chanDescriptor
      ?: return

    val albumViewController = AlbumViewController(context, descriptor, displayingPostDescriptors)
    if (!albumViewController.tryCollectingImages(initialImageUrl)) {
      return
    }

    pushController(albumViewController)
  }

  override fun pushController(controller: Controller) {
    if (doubleNavigationController != null) {
      doubleNavigationController!!.pushController(controller)
    } else {
      navigationController!!.pushController(controller)
    }
  }

  override fun onShowPosts() {
    // no-op
  }

  override fun onShowError() {
    // no-op
  }

  override fun unpresentController(predicate: (Controller) -> Boolean) {
    getControllerOrNull { controller ->
      if (predicate(controller)) {
        controller.stopPresenting()
        return@getControllerOrNull true
      }

      return@getControllerOrNull false
    }
  }

  override fun isAlreadyPresentingController(predicate: (Controller) -> Boolean): Boolean {
    return super.isAlreadyPresenting(predicate)
  }

  override fun hideSwipeRefreshLayout() {
    if (!::swipeRefreshLayout.isInitialized) {
      return
    }

    swipeRefreshLayout.isRefreshing = false
  }

  override fun openFilterForType(type: FilterType, filterText: String, caseSensitive: Boolean) {
    val caseInsensitiveFlag = if (caseSensitive) {
      ""
    } else {
      "i"
    }

    val filter = ChanFilterMutable()
    filter.type = type.flag
    filter.pattern = "/${filterText.trim()}/$caseInsensitiveFlag"
    openFiltersController(filter)
  }

  override fun openFiltersController(chanFilterMutable: ChanFilterMutable) {
    if (chanDescriptor != null) {
      chanFilterMutable.boards.add(chanDescriptor!!.boardDescriptor())
    }

    val filtersController = FiltersController(
      context = context,
      chanFilterMutable = chanFilterMutable,
      mainControllerCallbacks = mainControllerCallbacks
    )

    if (doubleNavigationController != null) {
      doubleNavigationController!!.openControllerWrappedIntoBottomNavAwareController(filtersController)
    } else {
      requireStartActivity().openControllerWrappedIntoBottomNavAwareController(filtersController)
    }

    requireStartActivity().setSettingsMenuItemSelected()
  }

  override fun onLostFocus(wasFocused: ThreadControllerType) {
    if (isDevBuild()) {
      check(wasFocused == threadControllerType) {
        "ThreadControllerTypes do not match! wasFocused=$wasFocused, current=$threadControllerType"
      }
    }

    threadLayout.lostFocus(wasFocused)
    controllerNavigationManager.onControllerSwipedFrom(this)
  }

  override fun onGainedFocus(nowFocused: ThreadControllerType) {
    if (isDevBuild()) {
      check(nowFocused == threadControllerType) {
        "ThreadControllerTypes do not match! nowFocused=$nowFocused, current=$threadControllerType"
      }
    }

    threadLayout.gainedFocus(nowFocused)
    controllerNavigationManager.onControllerSwipedTo(this)
  }

  override fun threadBackPressed(): Boolean {
    return false
  }

  override fun threadBackLongPressed() {
    // no-op
  }

  override fun showAvailableArchivesList(
    postDescriptor: PostDescriptor,
    preview: Boolean,
    canAutoSelectArchive: Boolean
  ) {
    Logger.d(TAG, "showAvailableArchivesList($postDescriptor, $preview, $canAutoSelectArchive)")

    val descriptor = postDescriptor.descriptor as? ChanDescriptor.ThreadDescriptor
      ?: return

    val supportedArchiveDescriptors = archivesManager.getSupportedArchiveDescriptors(descriptor)
      .filter { archiveDescriptor ->
        return@filter siteManager.bySiteDescriptor(archiveDescriptor.siteDescriptor)?.enabled()
          ?: false
      }

    if (supportedArchiveDescriptors.isEmpty()) {
      Logger.d(TAG, "showAvailableArchives($descriptor) supportedThreadDescriptors is empty")

      val message = AppModuleAndroidUtils.getString(
        R.string.thread_presenter_no_archives_found_to_open_thread,
        descriptor.toString()
      )
      showToast(message, Toast.LENGTH_LONG)
      return
    }

    if (canAutoSelectArchive && supportedArchiveDescriptors.size == 1) {
      controllerScope.launch { onArchiveSelected(supportedArchiveDescriptors.first(), postDescriptor, preview) }
      return
    }

    val items = mutableListOf<FloatingListMenuItem>()

    supportedArchiveDescriptors.forEach { archiveDescriptor ->
      items += FloatingListMenuItem(
        archiveDescriptor,
        archiveDescriptor.name
      )
    }

    if (items.isEmpty()) {
      Logger.d(TAG, "showAvailableArchives($descriptor) items is empty")
      return
    }

    val floatingListMenuController = FloatingListMenuController(
      context,
      globalWindowInsetsManager.lastTouchCoordinatesAsConstraintLayoutBias(),
      items,
      itemClickListener = { clickedItem ->
        controllerScope.launch {
          val archiveDescriptor = (clickedItem.key as? ArchiveDescriptor)
            ?: return@launch

          onArchiveSelected(archiveDescriptor, postDescriptor, preview)
        }
      }
    )

    presentController(floatingListMenuController)
  }

  private fun onToolbarHeightChanged(toolbarHeightDp: Dp?) {
    var toolbarHeight = with(appResources.composeDensity) { toolbarHeightDp?.toPx()?.toInt() }
    if (toolbarHeight == null) {
      toolbarHeight = appResources.dimension(com.github.k1rakishou.chan.R.dimen.toolbar_height).toInt()
    }

    swipeRefreshLayout.setProgressViewOffset(
      false,
      toolbarHeight - dp(40f),
      toolbarHeight + dp(64 - 40.toFloat())
    )
  }

  private fun openWebViewReportController(post: ChanPost, site: Site) {
    if (!site.siteFeature(Site.SiteFeature.POST_REPORT)) {
      return
    }

    if (site.endpoints().report(post) == null) {
      return
    }

    val reportController = WebViewReportController(context, post, site)
    requireNavController().pushController(reportController)
  }

  private suspend fun onArchiveSelected(
    archiveDescriptor: ArchiveDescriptor,
    postDescriptor: PostDescriptor,
    preview: Boolean
  ) {
    val externalArchivePostDescriptor = PostDescriptor.create(
      archiveDescriptor.domain,
      postDescriptor.descriptor.boardCode(),
      postDescriptor.getThreadNo(),
      postDescriptor.postNo
    )

    if (preview) {
      showPostsInExternalThread(
        postDescriptor = externalArchivePostDescriptor,
        isPreviewingCatalogThread = false
      )
    } else {
      openExternalThread(
        postDescriptor = externalArchivePostDescriptor,
        scrollToPost = true
      )
    }
  }

  data class ShowThreadOptions(
    val switchToThreadController: Boolean,
    val pushControllerWithAnimation: Boolean
  )

  data class ShowCatalogOptions(
    val switchToCatalogController: Boolean,
    val withAnimation: Boolean
  )

  companion object {
    private const val TAG = "ThreadController"
  }
}