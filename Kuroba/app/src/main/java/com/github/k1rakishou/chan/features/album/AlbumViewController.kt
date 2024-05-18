package com.github.k1rakishou.chan.features.album

import android.content.Context
import android.os.Parcelable
import android.view.MotionEvent
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.github.k1rakishou.ChanSettings
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.di.component.controller.ControllerComponent
import com.github.k1rakishou.chan.core.helper.ThumbnailLongtapOptionsHelper
import com.github.k1rakishou.chan.core.usecase.FilterOutHiddenImagesUseCase
import com.github.k1rakishou.chan.features.image_saver.ImageSaverV2OptionsController
import com.github.k1rakishou.chan.features.media_viewer.MediaViewerActivity
import com.github.k1rakishou.chan.features.media_viewer.MediaViewerOptions
import com.github.k1rakishou.chan.features.media_viewer.helper.AlbumThreadControllerHelpers
import com.github.k1rakishou.chan.features.media_viewer.helper.MediaViewerGoToImagePostHelper
import com.github.k1rakishou.chan.features.media_viewer.helper.MediaViewerGoToPostHelper
import com.github.k1rakishou.chan.features.media_viewer.helper.MediaViewerOpenThreadHelper
import com.github.k1rakishou.chan.features.media_viewer.helper.MediaViewerScrollerHelper
import com.github.k1rakishou.chan.features.settings.screens.AppearanceSettingsScreen
import com.github.k1rakishou.chan.features.settings.screens.AppearanceSettingsScreen.Companion.clampColumnsCount
import com.github.k1rakishou.chan.features.toolbar.BackArrowMenuItem
import com.github.k1rakishou.chan.features.toolbar.CloseMenuItem
import com.github.k1rakishou.chan.features.toolbar.ToolbarMenuItem
import com.github.k1rakishou.chan.features.toolbar.ToolbarMiddleContent
import com.github.k1rakishou.chan.features.toolbar.ToolbarText
import com.github.k1rakishou.chan.ui.compose.compose_task.rememberSingleInstanceCoroutineTask
import com.github.k1rakishou.chan.ui.compose.snackbar.SnackbarContainer
import com.github.k1rakishou.chan.ui.compose.snackbar.SnackbarScope
import com.github.k1rakishou.chan.ui.controller.FloatingListMenuController
import com.github.k1rakishou.chan.ui.controller.base.BaseComposeController
import com.github.k1rakishou.chan.ui.controller.base.DeprecatedNavigationFlags
import com.github.k1rakishou.chan.ui.view.floating_menu.CheckableFloatingListMenuItem
import com.github.k1rakishou.chan.utils.ViewModelScope
import com.github.k1rakishou.common.resumeValueSafe
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.persist_state.PersistableChanState
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.parcelize.Parcelize
import javax.inject.Inject

class AlbumViewController(
  context: Context,
  private val listenMode: ListenMode,
  private val initialImageFullUrl: String?
) : BaseComposeController<AlbumViewControllerViewModel, AlbumViewController.Params>(
  context = context,
  viewModelClass = AlbumViewControllerViewModel::class.java
) {

  @Inject
  lateinit var thumbnailLongtapOptionsHelper: ThumbnailLongtapOptionsHelper
  @Inject
  lateinit var mediaViewerScrollerHelper: MediaViewerScrollerHelper
  @Inject
  lateinit var mediaViewerGoToImagePostHelper: MediaViewerGoToImagePostHelper
  @Inject
  lateinit var mediaViewerGoToPostHelper: MediaViewerGoToPostHelper
  @Inject
  lateinit var mediaViewerOpenThreadHelper: MediaViewerOpenThreadHelper
  @Inject
  lateinit var filterOutHiddenImagesUseCase: FilterOutHiddenImagesUseCase
  @Inject
  lateinit var albumThreadControllerHelpers: AlbumThreadControllerHelpers

  override val viewModelScope: ViewModelScope
    get() = ViewModelScope.ControllerScope(this)

  override fun injectControllerDependencies(component: ControllerComponent) {
    component.inject(this)
  }

  override fun viewModelParams(): Params = Params(listenMode, initialImageFullUrl)

  override val snackbarScope: SnackbarScope
    get() {
      return when (listenMode) {
        ListenMode.Catalog -> SnackbarScope.Album(mainLayoutAnchor = SnackbarScope.MainLayoutAnchor.Catalog)
        ListenMode.Thread -> SnackbarScope.Album(mainLayoutAnchor = SnackbarScope.MainLayoutAnchor.Thread)
      }
    }

  override fun setupNavigation() {
    updateNavigationFlags(
      newNavigationFlags = DeprecatedNavigationFlags()
    )

    toolbarState.enterDefaultMode(
      leftItem = BackArrowMenuItem(
        onClick = { requireNavController().popController() }
      ),
      middleContent = ToolbarMiddleContent.Title(
        title = ToolbarText.String(""),
        subtitle = null
      ),
      menuBuilder = {
        withMenuItem(
          id = ACTION_TOGGLE_LAYOUT_MODE,
          drawableId = if (PersistableChanState.albumLayoutGridMode.get()) {
            R.drawable.ic_baseline_view_quilt_24
          } else {
            R.drawable.ic_baseline_view_comfy_24
          },
          onClick = { item -> toggleLayoutModeClicked(item) }
        )

        withMenuItem(
          id = ACTION_ENTER_DOWNLOAD_MODE,
          drawableId = com.github.k1rakishou.chan.R.drawable.ic_baseline_file_download_24,
          onClick = { controllerViewModel.enterSelectionMode() }
        )

        withOverflowMenu {
          withCheckableOverflowMenuItem(
            id = ACTION_TOGGLE_IMAGE_DETAILS,
            stringId = R.string.action_album_show_image_details,
            visible = true,
            checked = PersistableChanState.showAlbumViewsImageDetails.get(),
            onClick = { onToggleAlbumViewsImageInfoToggled() }
          )

          withOverflowMenuItem(
            id = ACTION_ALBUM_COLUMNS_COUNT,
            stringId = R.string.setting_album_span_count,
            onClick = { controllerScope.launch { onChangeAlbumColumnsCountClicked() } }
          )
        }
      }
    )
  }

  override fun onPrepare() {
    globalUiStateHolder.updateMainUiState {
      startTrackingScrollSpeed(controllerKey)
    }

    controllerScope.launch {
      controllerViewModel.toolbarData
        .onEach { toobarData ->
          toolbarState.default.updateTitle(
            newTitle = ToolbarText.Companion.from(toobarData.title),
            newSubTitle = ToolbarText.Companion.from(toobarData.subtitle)
          )
        }
        .collect()
    }

    controllerScope.launch {
      controllerViewModel.albumLayoutGridMode
        .onEach { onAlbumLayoutGridModeToggled() }
        .collect()
    }

    controllerScope.launch {
      mediaViewerScrollerHelper.mediaViewerScrollEventsFlow
        .collect { scrollToImageEvent ->
          val chanDescriptor = controllerViewModel.currentDescriptor.value
          if (chanDescriptor == null) {
            return@collect
          }

          val descriptor = scrollToImageEvent.chanDescriptor
          if (descriptor != chanDescriptor) {
            return@collect
          }

          controllerViewModel.requestScrollToImage(scrollToImageEvent.chanPostImage)
        }
    }

    controllerScope.launch {
      mediaViewerGoToImagePostHelper.mediaViewerGoToPostEventsFlow
        .collect { goToPostEvent ->
          val postImage = goToPostEvent.chanPostImage
          val chanDescriptor = goToPostEvent.chanDescriptor

          requireNavController().popController {
            albumThreadControllerHelpers.highlightPostWithImage(chanDescriptor, postImage)
          }
        }
    }

    controllerScope.launch {
      mediaViewerGoToPostHelper.mediaViewerGoToPostEventsFlow
        .collect { postDescriptor ->
          val chanDescriptor = controllerViewModel.currentDescriptor.value
          if (chanDescriptor == null) {
            return@collect
          }

          if (postDescriptor.descriptor != chanDescriptor) {
            return@collect
          }

          requireNavController().popController()
        }
    }

    controllerScope.launch {
      controllerViewModel.albumSelection
        .collectLatest { albumSelection -> updateToolbarSelectionMode(albumSelection) }
    }

    controllerScope.launch {
      controllerViewModel.presentController
        .collectLatest { presentController ->
          when (presentController) {
            is AlbumViewControllerViewModel.PresentController.ImageSaverOptionsController -> {
              val controller = ImageSaverV2OptionsController(context, presentController.options)
              presentController(controller)
            }
          }
        }
    }
  }

  override fun dispatchTouchEvent(event: MotionEvent): Boolean {
    if (!isTouchInsideView(event)) {
      return false
    }

    globalUiStateHolder.updateMainUiState {
      updateScrollSpeed(controllerKey, event)
    }

    return true
  }

  override fun onDestroy() {
    super.onDestroy()

    globalUiStateHolder.updateMainUiState {
      stopTrackingScrollSpeed(controllerKey)
    }
  }

  @Composable
  override fun ScreenContent() {
    val density = LocalDensity.current
    val coroutineTask = rememberSingleInstanceCoroutineTask()

    val albumSpanCountMut by controllerViewModel.albumSpanCount.collectAsState()
    val albumSpanCount = albumSpanCountMut
    if (albumSpanCount == null) {
      return
    }

    val albumLayoutGridModeMut by controllerViewModel.albumLayoutGridMode.collectAsState()
    val albumLayoutGridMode = albumLayoutGridModeMut
    if (albumLayoutGridMode == null) {
      return
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
      val actualSpanCount = remember(albumSpanCount, maxWidth) {
        with(density) { calculateActualSpanCount(albumSpanCount, maxWidth.roundToPx()) }
      }

      if (albumLayoutGridMode) {
        AlbumItemsGrid(
          controllerKey = controllerKey,
          controllerViewModel = controllerViewModel,
          albumSpanCount = actualSpanCount,
          onClick = { albumItemData ->
            coroutineTask.launch { onImageClick(albumItemData) }
          },
          onLongClick = { albumItemData ->
            coroutineTask.launch { onImageLongClick(albumItemData) }
          },
          clearDownloadingAlbumItemState = { downloadingAlbumItem ->
            controllerViewModel.clearDownloadingAlbumItemState(downloadingAlbumItem)
          }
        )
      } else {
        AlbumItemsStaggeredGrid(
          controllerKey = controllerKey,
          controllerViewModel = controllerViewModel,
          albumSpanCount = actualSpanCount,
          onClick = { albumItemData ->
            coroutineTask.launch { onImageClick(albumItemData) }
          },
          onLongClick = { albumItemData ->
            coroutineTask.launch { onImageLongClick(albumItemData) }
          },
          clearDownloadingAlbumItemState = { downloadingAlbumItem ->
            controllerViewModel.clearDownloadingAlbumItemState(downloadingAlbumItem)
          }
        )
      }

      SnackbarContainer(modifier = Modifier.fillMaxSize())
    }
  }

  private suspend fun onImageLongClick(albumItemData: AlbumItemData) {
    if (controllerViewModel.isInSelectionMode()) {
      // TODO: start "Move to select/unselect" mode.
      //  For now we will just toggle selection because that mode is not implemented yet.
      controllerViewModel.toggleSelection(albumItemData)
      return
    }

    val chanDescriptor = controllerViewModel.currentDescriptor.value
    if (chanDescriptor == null) {
      return
    }

    val postImage = controllerViewModel.findChanPostImage(albumItemData)
    if (postImage == null) {
      return
    }

    thumbnailLongtapOptionsHelper.onThumbnailLongTapped(
      context = context,
      chanDescriptor = chanDescriptor,
      isCurrentlyInAlbum = true,
      postImage = postImage,
      presentControllerFunc = { controller -> presentController(controller) },
      showFiltersControllerFunc = { },
      openThreadFunc = { postDescriptor ->
        withLayoutMode(
          phone = { requireNavController().popController(false) }
        )

        mediaViewerOpenThreadHelper.tryToOpenThread(postDescriptor)
      },
      goToPostFunc = {
        requireNavController().popController {
          albumThreadControllerHelpers.highlightPostWithImage(chanDescriptor, postImage)
        }
      },
      selectFunc = { chanPostImage ->
        controllerViewModel.enterSelectionMode(chanPostImage)
      },
      downloadMediaFileFunc = { chanPostImage, showOptions ->
        controllerViewModel.downloadImage(chanPostImage, showOptions)
      }
    )
  }

  private fun updateToolbarSelectionMode(albumSelection: AlbumSelection) {
    val isInSelectionMode = albumSelection.isInSelectionMode
    if (!isInSelectionMode) {
      if (toolbarState.isInSelectionMode()) {
        toolbarState.pop()
        controllerViewModel.exitSelectionMode()
      }

      return
    }

    val selectedItemsCount = albumSelection.selectedItems.size
    val totalItemsCount = controllerViewModel.albumItems.size

    if (!toolbarState.isInSelectionMode()) {
      toolbarState.enterSelectionMode(
        leftItem = CloseMenuItem(
          onClick = {
            if (toolbarState.isInSelectionMode()) {
              toolbarState.pop()
              controllerViewModel.exitSelectionMode()
            }
          }
        ),
        selectedItemsCount = selectedItemsCount,
        totalItemsCount = totalItemsCount,
        menuBuilder = {
          withMenuItem(
            ACTION_TOGGLE_SELECTION,
            com.github.k1rakishou.chan.R.drawable.ic_select_all_white_24dp
          ) { controllerViewModel.toggleAlbumItemsSelection() }

          withMenuItem(
            ACTION_DOWNLOAD_SELECTED_IMAGES,
            com.github.k1rakishou.chan.R.drawable.ic_baseline_file_download_24
          ) { controllerViewModel.downloadSelectedItems() }
        }
      )
    }

    toolbarState.selection.updateCounters(
      selectedItemsCount = selectedItemsCount,
      totalItemsCount = totalItemsCount
    )
  }

  private suspend fun onImageClick(albumItemData: AlbumItemData) {
    if (controllerViewModel.isInSelectionMode()) {
      controllerViewModel.toggleSelection(albumItemData)
      return
    }

    val chanDescriptor = controllerViewModel.currentDescriptor.value
    if (chanDescriptor == null) {
      return
    }

    val transitionThumbnailUrl = albumItemData.spoilerThumbnailImageUrl
      ?: albumItemData.thumbnailImageUrl

    when (chanDescriptor) {
      is ChanDescriptor.ICatalogDescriptor -> {
        MediaViewerActivity.catalogMedia(
          context = context,
          catalogDescriptor = chanDescriptor,
          initialImageUrl = albumItemData.fullImageUrl?.toString(),
          transitionThumbnailUrl = transitionThumbnailUrl.toString(),
          lastTouchCoordinates = globalWindowInsetsManager.lastTouchCoordinates(),
          mediaViewerOptions = MediaViewerOptions(
            mediaViewerOpenedFromAlbum = true
          )
        )
      }
      is ChanDescriptor.ThreadDescriptor -> {
        MediaViewerActivity.threadMedia(
          context = context,
          threadDescriptor = chanDescriptor,
          postDescriptorList = controllerViewModel.mapPostImagesToPostDescriptors(),
          initialImageUrl = albumItemData.fullImageUrl?.toString(),
          transitionThumbnailUrl = transitionThumbnailUrl.toString(),
          lastTouchCoordinates = globalWindowInsetsManager.lastTouchCoordinates(),
          mediaViewerOptions = MediaViewerOptions(
            mediaViewerOpenedFromAlbum = true
          )
        )
      }
    }
  }

  private fun toggleLayoutModeClicked(item: ToolbarMenuItem) {
    PersistableChanState.albumLayoutGridMode.toggle()

    toolbarState.findItem(ACTION_TOGGLE_LAYOUT_MODE)?.let { toolbarMenuItem ->
      val drawableId = if (PersistableChanState.albumLayoutGridMode.get()) {
        R.drawable.ic_baseline_view_quilt_24
      } else {
        R.drawable.ic_baseline_view_comfy_24
      }

      toolbarMenuItem.updateDrawableId(drawableId)
    }
  }

  private fun onAlbumLayoutGridModeToggled() {
    toolbarState.findItem(ACTION_TOGGLE_LAYOUT_MODE)?.let { toolbarMenuItem ->
      val downloadDrawableId = if (PersistableChanState.albumLayoutGridMode.get()) {
        R.drawable.ic_baseline_view_quilt_24
      } else {
        R.drawable.ic_baseline_view_comfy_24
      }

      toolbarMenuItem.updateDrawableId(downloadDrawableId)
    }
  }

  private fun onToggleAlbumViewsImageInfoToggled() {
    toolbarState.findCheckableOverflowItem(ACTION_TOGGLE_IMAGE_DETAILS)
      ?.updateChecked(PersistableChanState.showAlbumViewsImageDetails.toggle())
  }

  private suspend fun onChangeAlbumColumnsCountClicked() {
    val currentColumnsCount = ChanSettings.albumSpanCount.get()

    val items = AppearanceSettingsScreen.ALL_COLUMNS.mapIndexed { index, columnsCount ->
      val name = if (columnsCount == AppearanceSettingsScreen.AUTO_COLUMN) {
        appResources.string(R.string.setting_span_count_default)
      } else {
        appResources.string(R.string.setting_span_count_item, columnsCount)
      }

      return@mapIndexed CheckableFloatingListMenuItem(
        key = index,
        name = name,
        value = columnsCount,
        groupId = "album_column_count",
        checked = columnsCount == currentColumnsCount
      )
    }

    val clickedColumnCount = suspendCancellableCoroutine<Int?> { continuation ->
      val controller = FloatingListMenuController(
        context = context,
        items = items,
        constraintLayoutBias = globalWindowInsetsManager.lastTouchCoordinatesAsConstraintLayoutBias(),
        itemClickListener = { clickedItem -> continuation.resumeValueSafe(clickedItem.value as Int?) },
        menuDismissListener = { continuation.resumeValueSafe(null) }
      )

      requireNavController().presentController(controller)
    }

    if (clickedColumnCount == null) {
      return
    }

    if (clickedColumnCount !in AppearanceSettingsScreen.ALL_COLUMNS) {
      return
    }

    ChanSettings.albumSpanCount.set(clickedColumnCount)
  }

  private fun Density.calculateActualSpanCount(albumSpanCount: Int, maxWidth: Int): Int {
    val defaultSpanWidth = 120.dp.roundToPx()

    val actualAlbumSpanCount = if (albumSpanCount <= 0) {
      maxWidth / defaultSpanWidth
    } else {
      albumSpanCount
    }

    return clampColumnsCount(actualAlbumSpanCount)
  }

  @Parcelize
  data class Params(
    val listenMode: ListenMode,
    val initialImageFullUrl: String?
  ) : Parcelable

  @Parcelize
  enum class ListenMode : Parcelable {
    Catalog,
    Thread
  }

  companion object {
    private const val ACTION_TOGGLE_LAYOUT_MODE = 1
    private const val ACTION_ENTER_DOWNLOAD_MODE = 2
    private const val ACTION_TOGGLE_IMAGE_DETAILS = 3
    private const val ACTION_ALBUM_COLUMNS_COUNT = 4

    private const val ACTION_TOGGLE_SELECTION = 1
    private const val ACTION_DOWNLOAD_SELECTED_IMAGES = 2
  }

}