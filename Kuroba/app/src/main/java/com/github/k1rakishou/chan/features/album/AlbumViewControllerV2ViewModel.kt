package com.github.k1rakishou.chan.features.album

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.IntState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.github.k1rakishou.ChanSettings
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.base.BaseViewModel
import com.github.k1rakishou.chan.core.di.component.viewmodel.ViewModelComponent
import com.github.k1rakishou.chan.core.di.module.shared.ViewModelAssistedFactory
import com.github.k1rakishou.chan.core.manager.ChanThreadManager
import com.github.k1rakishou.chan.core.manager.CompositeCatalogManager
import com.github.k1rakishou.chan.core.manager.CurrentOpenedDescriptorStateManager
import com.github.k1rakishou.chan.core.manager.RevealedSpoilerImagesManager
import com.github.k1rakishou.chan.core.usecase.FilterOutHiddenImagesUseCase
import com.github.k1rakishou.chan.features.image_saver.ImageSaverV2
import com.github.k1rakishou.chan.features.image_saver.ImageSaverV2OptionsController
import com.github.k1rakishou.chan.features.image_saver.ImageSaverV2ServiceDelegate
import com.github.k1rakishou.chan.ui.compose.image.PostImageThumbnailKey
import com.github.k1rakishou.chan.ui.compose.snackbar.SnackbarScope
import com.github.k1rakishou.chan.ui.compose.snackbar.manager.SnackbarManagerFactory
import com.github.k1rakishou.chan.ui.helper.AppResources
import com.github.k1rakishou.chan.utils.BackgroundUtils
import com.github.k1rakishou.chan.utils.asKurobaMediaType
import com.github.k1rakishou.chan.utils.paramsOrNull
import com.github.k1rakishou.chan.utils.requireParams
import com.github.k1rakishou.common.isNotNullNorBlank
import com.github.k1rakishou.common.mutableListWithCap
import com.github.k1rakishou.common.resumeValueSafe
import com.github.k1rakishou.common.toHashSetBy
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.descriptor.PostDescriptor
import com.github.k1rakishou.model.data.post.ChanPostImage
import com.github.k1rakishou.model.source.cache.ChanCatalogSnapshotCache
import com.github.k1rakishou.model.source.cache.thread.ChanThreadsCache
import com.github.k1rakishou.model.util.ChanPostUtils
import com.github.k1rakishou.persist_state.ImageSaverV2Options
import com.github.k1rakishou.persist_state.PersistableChanState
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toPersistentSet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject

class AlbumViewControllerV2ViewModel(
  private val savedStateHandle: SavedStateHandle,
  private val appResources: AppResources,
  private val currentOpenedDescriptorStateManager: CurrentOpenedDescriptorStateManager,
  private val chanThreadManager: ChanThreadManager,
  private val chanCatalogSnapshotCache: ChanCatalogSnapshotCache,
  private val chanThreadsCache: ChanThreadsCache,
  private val snackbarManagerFactory: SnackbarManagerFactory,
  private val compositeCatalogManager: CompositeCatalogManager,
  private val filterOutHiddenImagesUseCase: FilterOutHiddenImagesUseCase,
  private val imageSaverV2: ImageSaverV2,
  private val imageSaverV2ServiceDelegate: ImageSaverV2ServiceDelegate,
  private val revealedSpoilerImagesManager: RevealedSpoilerImagesManager
) : BaseViewModel() {
  private val _albumItemIdCounter = AtomicLong(0)
  private var _enqueueAlbumItemsDownloadJob: Job? = null

  private val _currentListenMode: AlbumViewControllerV2.ListenMode
    get() = savedStateHandle.requireParams<AlbumViewControllerV2.Params>().listenMode

  private val _currentDescriptor = MutableStateFlow<ChanDescriptor?>(null)
  val currentDescriptor: StateFlow<ChanDescriptor?>
    get() = _currentDescriptor.asStateFlow()

  private val _albumItems = mutableStateListOf<AlbumItemData>()
  val albumItems: SnapshotStateList<AlbumItemData>
    get() = _albumItems

  private val _albumSelection = MutableStateFlow<AlbumSelection>(AlbumSelection())
  val albumSelection: StateFlow<AlbumSelection>
    get() = _albumSelection

  private val _downloadingAlbumItems = mutableStateMapOf<Long, DownloadingAlbumItem>()
  val downloadingAlbumItems: SnapshotStateMap<Long, DownloadingAlbumItem>
    get() = _downloadingAlbumItems

  private val _scrollToPosition = MutableSharedFlow<Int>(
    extraBufferCapacity = 1,
    onBufferOverflow = BufferOverflow.DROP_OLDEST
  )
  val scrollToPosition: SharedFlow<Int>
    get() = _scrollToPosition.asSharedFlow()

  private val _presentController = MutableSharedFlow<PresentController>(
    extraBufferCapacity = 1,
    onBufferOverflow = BufferOverflow.DROP_LATEST
  )
  val presentController: SharedFlow<PresentController>
    get() = _presentController.asSharedFlow()

  private val _lastScrollPosition = mutableIntStateOf(0)
  val lastScrollPosition: IntState
    get() = _lastScrollPosition

  private val _toolbarData = MutableStateFlow<ToolbarData>(ToolbarData())
  val toolbarData: StateFlow<ToolbarData>
    get() = _toolbarData

  val albumSpanCount = ChanSettings.albumSpanCount.listenForChanges()
    .asFlow()
    .stateIn(viewModelScope, SharingStarted.Lazily, null)

  val albumLayoutGridMode = PersistableChanState.albumLayoutGridMode.listenForChanges()
    .asFlow()
    .stateIn(viewModelScope, SharingStarted.Lazily, null)

  val showAlbumViewsImageDetails = PersistableChanState.showAlbumViewsImageDetails.listenForChanges()
    .asFlow()
    .stateIn(viewModelScope, SharingStarted.Lazily, null)

  private val snackbarManager by lazy {
    val snackbarScope = when (_currentListenMode) {
      AlbumViewControllerV2.ListenMode.Catalog -> SnackbarScope.Album(SnackbarScope.MainLayoutAnchor.Catalog)
      AlbumViewControllerV2.ListenMode.Thread -> SnackbarScope.Album(SnackbarScope.MainLayoutAnchor.Thread)
    }

    snackbarManagerFactory.snackbarManager(snackbarScope)
  }

  override fun injectDependencies(component: ViewModelComponent) {
    component.inject(this)
  }

  override suspend fun onViewModelReady() {
    viewModelScope.launch {
      listenForCurrentChanDescriptor()
    }

    viewModelScope.launch(Dispatchers.IO) {
      loadAndUpdateAlbumItems()
    }

    viewModelScope.launch {
      imageSaverV2ServiceDelegate.downloadingImagesFlow
        .onEach { downloadingImageState -> handleDownloadingImageState(downloadingImageState) }
        .collect()
    }

    viewModelScope.launch {
      revealedSpoilerImagesManager.spoilerImageRevealEventsFlow
        .onEach { revealedSpoilerImage -> handleRevealedSpoilerImageEvent(revealedSpoilerImage) }
        .collect()
    }
  }

  fun updateLastScrollPosition(newLastScrollPosition: Int) {
    _lastScrollPosition.intValue = newLastScrollPosition
  }

  fun mapPostImagesToPostDescriptors(): List<PostDescriptor> {
    val duplicateSet = mutableSetOf<PostDescriptor>()

    return albumItems.mapNotNull { postImage ->
      if (duplicateSet.add(postImage.postDescriptor)) {
        return@mapNotNull postImage.postDescriptor
      }

      return@mapNotNull null
    }
  }

  fun findAlbumItemData(chanPostImage: ChanPostImage): AlbumItemData? {
    return albumItems.firstOrNull { albumItemData -> albumItemData.isEqualToChanPostImage(chanPostImage) }
  }

  fun findChanPostImage(albumItemData: AlbumItemData): ChanPostImage? {
    val chanPost = when (val chanDescriptor = _currentDescriptor.value) {
      is ChanDescriptor.ICatalogDescriptor -> {
        chanThreadManager.getChanCatalog(chanDescriptor)?.getPost(albumItemData.postDescriptor)
      }
      is ChanDescriptor.ThreadDescriptor -> {
        chanThreadManager.getChanThread(chanDescriptor)?.getPost(albumItemData.postDescriptor)
      }
      null -> null
    }

    if (chanPost == null) {
      return null
    }

    return chanPost.firstPostImageOrNull { chanPostImage -> chanPostImage.imageUrl == albumItemData.fullImageUrl }
  }

  fun findChanPostImage(albumItemId: Long): ChanPostImage? {
    val albumItemData = _albumItems.firstOrNull { albumItemData -> albumItemData.id == albumItemId }
    if (albumItemData == null) {
      return null
    }

    return findChanPostImage(albumItemData)
  }

  suspend fun requestScrollToImage(chanPostImage: ChanPostImage) {
    val newPosition = albumItems.indexOfFirst { albumItemData ->
      if (albumItemData.postDescriptor != chanPostImage.ownerPostDescriptor) {
        return@indexOfFirst false
      }

      return@indexOfFirst albumItemData.fullImageUrl == chanPostImage.imageUrl
    }

    if (newPosition < 0) {
      return
    }

    _scrollToPosition.emit(newPosition)
  }

  fun enterSelectionMode(chanPostImage: ChanPostImage) {
    val albumItemData = findAlbumItemData(chanPostImage)
      ?: return

    _albumSelection.value = AlbumSelection(
      isInSelectionMode = true,
      selectedItems = persistentSetOf(albumItemData.id)
    )
  }

  fun toggleSelection(albumItemData: AlbumItemData) {
    _albumSelection.value = if (_albumSelection.value.contains(albumItemData.id)) {
      _albumSelection.value.remove(albumItemData.id)
    } else {
      _albumSelection.value.add(albumItemData.id)
    }
  }

  fun toggleAlbumItemsSelection() {
    if (_albumSelection.value.size != albumItems.size) {
      val albumItemIds = albumItems.map { albumItemData -> albumItemData.id }.toPersistentSet()
      _albumSelection.value = _albumSelection.value.addAll(albumItemIds)
    } else {
      _albumSelection.value = _albumSelection.value.copy(selectedItems = persistentSetOf())
    }
  }

  fun isInSelectionMode(): Boolean {
    return _albumSelection.value.isInSelectionMode
  }

  fun exitSelectionMode() {
    _albumSelection.value = AlbumSelection()
  }

  fun clearDownloadingAlbumItemState(downloadingAlbumItem: DownloadingAlbumItem) {
    clearDownloadingAlbumItemState(downloadingAlbumItem.albumItemDataId)
  }

  fun clearDownloadingAlbumItemState(albumItemDataId: Long) {
    _downloadingAlbumItems.remove(albumItemDataId)

    val albumItemDataIndex = _albumItems
      .indexOfFirst { albumItemData -> albumItemData.id == albumItemDataId }
    if (albumItemDataIndex >= 0) {
      val albumItemData = _albumItems[albumItemDataIndex]
      _albumItems[albumItemDataIndex] = albumItemData.copy(downloadUniqueId = null)
    }
  }

  suspend fun onImageClicked(clickedAlbumItemData: AlbumItemData) {
    if (clickedAlbumItemData.spoilerThumbnailImageUrl == null) {
      // No spoiler or already revealed
      return
    }

    revealedSpoilerImagesManager.onImageClicked(clickedAlbumItemData)

    val indexOfExistingAlbumItemData = _albumItems
      .indexOfFirst { albumItemData -> albumItemData.id == clickedAlbumItemData.id }

    if (indexOfExistingAlbumItemData < 0) {
      return
    }

    _albumItems[indexOfExistingAlbumItemData] = _albumItems[indexOfExistingAlbumItemData]
      .copy(spoilerThumbnailImageUrl = null)
  }

  fun downloadSelectedItems() {
    _enqueueAlbumItemsDownloadJob?.cancel()
    _enqueueAlbumItemsDownloadJob = viewModelScope.launch {
      val albumSelection = _albumSelection.value

      val selectedCount = albumSelection.size
      if (selectedCount <= 0) {
        Logger.debug(TAG) { "downloadSelectedItems() album selection is empty" }
        snackbarManager.errorToast(R.string.album_download_none_checked)
        return@launch
      }

      Logger.debug(TAG) { "downloadSelectedItems() selectedItemsCount: ${albumSelection.size}" }

      val simpleSaveableMediaInfoList = ArrayList<ImageSaverV2.SimpleSaveableMediaInfo>(selectedCount)

      for (albumItemId in albumSelection.selectedItems) {
        val chanPostImage = findChanPostImage(albumItemId)
        if (chanPostImage == null) {
          continue
        }

        if (chanPostImage.isInlined || chanPostImage.hidden) {
          // Do not download inlined files via the Album downloads (because they often
          // fail with SSL exceptions) and we can't really trust those files.
          // Also don't download filter hidden items
          continue
        }

        val simpleSaveableMediaInfo = ImageSaverV2.SimpleSaveableMediaInfo.fromChanPostImage(chanPostImage)
        if (simpleSaveableMediaInfo != null) {
          simpleSaveableMediaInfoList.add(simpleSaveableMediaInfo)
        }
      }

      if (simpleSaveableMediaInfoList.isEmpty()) {
        Logger.debug(TAG) { "downloadSelectedItems() simpleSaveableMediaInfoList is empty" }
        snackbarManager.errorToast(R.string.album_download_no_suitable_images)
        return@launch
      }

      Logger.debug(TAG) { "downloadSelectedItems() simpleSaveableMediaInfoList: ${simpleSaveableMediaInfoList.size}" }

      val updatedImageSaverV2Options = suspendCancellableCoroutine<ImageSaverV2Options?> { continuation ->
        val options = ImageSaverV2OptionsController.Options.MultipleImages(
          onSaveClicked = { updatedImageSaverV2Options ->
            continuation.resumeValueSafe(updatedImageSaverV2Options)
          },
          onCanceled = {
            continuation.resumeValueSafe(null)
          }
        )

        if (!_presentController.tryEmit(PresentController.ImageSaverOptionsController(options))) {
          continuation.cancel()
        }
      }

      if (updatedImageSaverV2Options == null) {
        Logger.debug(TAG) { "downloadSelectedItems() updatedImageSaverV2Options is null" }
        snackbarManager.errorToast(R.string.album_download_canceled_by_user)
        return@launch
      }

      val downloadUniqueId = imageSaverV2.saveManySuspend(
        imageSaverV2Options = updatedImageSaverV2Options,
        simpleSaveableMediaInfoList = simpleSaveableMediaInfoList
      )

      if (downloadUniqueId == null) {
        Logger.debug(TAG) { "downloadSelectedItems() downloadUniqueId is null" }
        snackbarManager.errorToast(R.string.album_download_failed_to_enqueue_download)
        return@launch
      }

      _albumSelection.value.selectedItems.forEach { albumItemDataId ->
        val albumItemDataIndex = _albumItems.indexOfFirst { albumItemData -> albumItemData.id == albumItemDataId }
        if (albumItemDataIndex < 0) {
          return@forEach
        }

        val albumItemData = _albumItems[albumItemDataIndex]

        val fullImageUrl = albumItemData.fullImageUrl
        if (fullImageUrl == null) {
          return@forEach
        }

        _downloadingAlbumItems.put(
          albumItemDataId,
          DownloadingAlbumItem(
            albumItemDataId = albumItemDataId,
            downloadUniqueId = downloadUniqueId,
            fullImageUrl = fullImageUrl,
            state = DownloadingAlbumItem.State.Enqueued
          )
        )

        _albumItems[albumItemDataIndex] = albumItemData.copy(downloadUniqueId = downloadUniqueId)
      }

      Logger.debug(TAG) {
        "downloadSelectedItems() Successfully enqueued a download with id downloadUniqueId: '${downloadUniqueId}'"
      }

      exitSelectionMode()
    }
  }

  private suspend fun updateUi(
    initialLoad: Boolean,
    album: Album
  ) {
    val allAlbumItems = album.albumItemDataList
    val scrollToPosition = album.scrollToPosition
    val initialImageFullUrl = album.initialImageFullUrl

    Logger.debug(TAG) { "Got ${allAlbumItems.size} album items" }

    val (duplicateChecker, capacity) = withContext(Dispatchers.Main) {
      val duplicateChecker = _albumItems
        .toHashSetBy(capacity = _albumItems.size) { albumItemData -> albumItemData.fullImageUrl }

      val capacity = (allAlbumItems.size - _albumItems.size).coerceAtLeast(16)
      return@withContext duplicateChecker to capacity
    }

    val newAlbumItemsToAppendList = mutableListWithCap<AlbumItemData>(capacity)

    allAlbumItems.forEach { newAlbumItemData ->
      if (duplicateChecker.contains(newAlbumItemData.fullImageUrl)) {
        return@forEach
      }

      newAlbumItemsToAppendList += newAlbumItemData
    }

    Logger.debug(TAG) { "Got ${newAlbumItemsToAppendList.size} new album items" }

    withContext(Dispatchers.Main) {
      if (newAlbumItemsToAppendList.isNotEmpty()) {
        applyNewAlbumItems(initialLoad, newAlbumItemsToAppendList)
      }

      updateToolbarTitle(
        newAlbumItems = allAlbumItems,
        chanDescriptor = _currentDescriptor.value
      )

      if (scrollToPosition != null) {
        Logger.debug(TAG) { "updateScrollPosition() '${initialImageFullUrl}' found at ${scrollToPosition}, performing scroll" }
        _scrollToPosition.emit(scrollToPosition)
        _lastScrollPosition.intValue = scrollToPosition
      } else {
        Logger.debug(TAG) { "updateScrollPosition() '${initialImageFullUrl}' failed to find scroll position" }
      }
    }
  }

  private suspend fun updateToolbarTitle(
    newAlbumItems: List<AlbumItemData>,
    chanDescriptor: ChanDescriptor?
  ) {
    BackgroundUtils.ensureMainThread()

    val toolbarTitle = when (chanDescriptor) {
      is ChanDescriptor.CompositeCatalogDescriptor,
      is ChanDescriptor.CatalogDescriptor -> {
        if (chanDescriptor is ChanDescriptor.CompositeCatalogDescriptor) {
          compositeCatalogManager.byCompositeCatalogDescriptor(chanDescriptor)?.name
            ?: ChanPostUtils.getTitle(null, chanDescriptor)
        } else {
          ChanPostUtils.getTitle(null, chanDescriptor)
        }
      }
      is ChanDescriptor.ThreadDescriptor -> {
        ChanPostUtils.getTitle(
          chanThreadManager.getChanThread(chanDescriptor)?.getOriginalPost(),
          chanDescriptor
        )
      }
      null -> ""
    }
    val toolbarSubTitle = appResources.quantityString(
      R.plurals.image,
      newAlbumItems.size,
      newAlbumItems.size
    )

    _toolbarData.value = ToolbarData(
      title = toolbarTitle,
      subtitle = toolbarSubTitle
    )
  }

  private fun applyNewAlbumItems(initialLoad: Boolean, newAlbumItemsToAppendList: List<AlbumItemData>) {
    BackgroundUtils.ensureMainThread()

    if (newAlbumItemsToAppendList.isEmpty()) {
      return
    }

    _albumItems.addAll(newAlbumItemsToAppendList)

    // Do not show the toast the first time we open AlbumViewController. Only show it for album item updates.
    if (!initialLoad) {
      snackbarManager.toast(
        appResources.string(
          R.string.album_screen_new_images,
          newAlbumItemsToAppendList.size
        )
      )
    }
  }

  private fun findInitialScrollPosition(initialImageFullUrl: String?, allAlbumItems: List<ChanPostImage>): Int? {
    if (initialImageFullUrl.isNullOrBlank()) {
      Logger.debug(TAG) { "updateScrollPosition() initialImageFullUrl is null or blank" }
      return null
    }

    val indexToScrollTo = allAlbumItems
      .indexOfFirst { albumItemData -> albumItemData.imageUrl?.toString() == initialImageFullUrl }
    if (indexToScrollTo < 0) {
      Logger.debug(TAG) { "updateScrollPosition() failed to find '${initialImageFullUrl}'" }
      return null
    }

    return indexToScrollTo
  }

  private suspend fun getAlbumItemsForChanDescriptor(
    initialLoad: Boolean,
    loadedChanDescriptor: ChanThreadManager.LoadedChanDescriptor
  ): Album? {
    BackgroundUtils.ensureBackgroundThread()
    Logger.debug(TAG) { "loadedChanDescriptor is '${loadedChanDescriptor}'" }

    val actualChanDescriptor = when (loadedChanDescriptor) {
      is ChanThreadManager.LoadedChanDescriptor.Composite -> {
        loadedChanDescriptor.compositeCatalogDescriptor
      }

      is ChanThreadManager.LoadedChanDescriptor.Regular -> {
        loadedChanDescriptor.loadedDescriptor
      }
    }

    val posts = kotlin.run {
      when (actualChanDescriptor) {
        is ChanDescriptor.ICatalogDescriptor -> {
          val catalogSnapshot = chanCatalogSnapshotCache.get(actualChanDescriptor)
          if (catalogSnapshot == null) {
            return@run emptyList()
          }

          return@run catalogSnapshot.catalogThreadDescriptorList
            .mapNotNull { threadDescriptor ->
              chanThreadsCache.getOriginalPostFromCache(threadDescriptor.toOriginalPostDescriptor())
            }
        }
        is ChanDescriptor.ThreadDescriptor -> {
          return@run chanThreadsCache.getThreadPosts(actualChanDescriptor)
        }
      }
    }

    if (posts.isEmpty()) {
      Logger.debug(TAG) { "Got no posts for '${loadedChanDescriptor}'" }
      return null
    }

    Logger.debug(TAG) { "Got ${posts.size} posts for '${loadedChanDescriptor}'" }

    val allImages = posts
      .flatMap { chanPost -> chanPost.postImages }

    val initialImageFullUrl = savedStateHandle
      .paramsOrNull<AlbumViewControllerV2.Params>()
      ?.initialImageFullUrl

    val scrollToPosition = if (initialLoad) {
      findInitialScrollPosition(initialImageFullUrl, allImages)
    } else {
      null
    }

    val input = FilterOutHiddenImagesUseCase.Input(
      images = allImages,
      index = scrollToPosition,
      isOpeningAlbum = true,
      postDescriptorSelector = { chanPostImage -> chanPostImage.ownerPostDescriptor },
      elementExistsInList = { image, images -> images.any { postImage -> postImage.equalUrl(image) } }
    )

    val output = filterOutHiddenImagesUseCase.filter(input)
    val filteredImages = output.images
    val postsMap = posts.associateBy { chanPost -> chanPost.postDescriptor }

    Logger.debug(TAG) { "Got ${filteredImages.size}/${allImages.size} images for '${loadedChanDescriptor}' after filtering" }

    val albumItemDataList = filteredImages.mapNotNull { chanPostImage ->
      BackgroundUtils.ensureBackgroundThread()

      val thumbnailUrl = chanPostImage.actualThumbnailUrl
      if (thumbnailUrl == null) {
        return@mapNotNull null
      }

      val spoilerThumbnailImageUrl = if (chanPostImage.spoiler) {
        if (revealedSpoilerImagesManager.isImageSpoilerImageRevealed(chanPostImage)) {
          null
        } else {
          chanPostImage.spoilerThumbnailUrl
        }
      } else {
        null
      }

      return@mapNotNull AlbumItemData(
        id = _albumItemIdCounter.getAndIncrement(),
        isCatalogMode = actualChanDescriptor.isCatalogDescriptor(),
        postDescriptor = chanPostImage.ownerPostDescriptor,
        thumbnailImageUrl = thumbnailUrl,
        spoilerThumbnailImageUrl = spoilerThumbnailImageUrl,
        fullImageUrl = chanPostImage.imageUrl,
        albumItemPostData = AlbumItemPostData(
          threadSubject = when (actualChanDescriptor) {
            is ChanDescriptor.ICatalogDescriptor -> {
              ChanPostUtils.getTitle(
                post = postsMap[chanPostImage.ownerPostDescriptor],
                chanDescriptor = actualChanDescriptor
              )
            }
            is ChanDescriptor.ThreadDescriptor -> null
          },
          mediaInfo = formatImageDetails(chanPostImage),
          aspectRatio = calculateAspectRatio(chanPostImage)
        ),
        mediaType = chanPostImage.extension.asKurobaMediaType(),
        downloadUniqueId = null
      )
    }

    return Album(
      initialImageFullUrl = initialImageFullUrl,
      scrollToPosition = output.index,
      albumItemDataList = albumItemDataList
    )
  }


  private suspend fun loadAndUpdateAlbumItems() {
    _currentDescriptor
      .flatMapLatest { currentDescriptor ->
        BackgroundUtils.ensureBackgroundThread()

        if (currentDescriptor == null) {
          return@flatMapLatest flowOf(null)
        }

        Logger.debug(TAG) { "Got new descriptor: '${currentDescriptor}'" }

        val loadedChanDescriptor = when (currentDescriptor) {
          is ChanDescriptor.CompositeCatalogDescriptor -> ChanThreadManager.LoadedChanDescriptor.Composite(
            currentDescriptor
          )

          is ChanDescriptor.CatalogDescriptor -> ChanThreadManager.LoadedChanDescriptor.Regular(currentDescriptor)
          is ChanDescriptor.ThreadDescriptor -> ChanThreadManager.LoadedChanDescriptor.Regular(currentDescriptor)
        }

        val album = getAlbumItemsForChanDescriptor(
          initialLoad = true,
          loadedChanDescriptor = loadedChanDescriptor
        )

        Logger.debug(TAG) { "Got ${album?.albumItemDataList?.size ?: 0} initial album items" }

        if (album != null && album.albumItemDataList.isNotEmpty()) {
          updateUi(initialLoad = true, album = album)
        }

        return@flatMapLatest chanThreadManager.chanDescriptorLoadFinishedEventsFlow
          .filter { loadedChanDescriptor ->
            when (currentDescriptor) {
              is ChanDescriptor.CompositeCatalogDescriptor -> {
                if (loadedChanDescriptor !is ChanThreadManager.LoadedChanDescriptor.Composite) {
                  return@filter false
                }

                if (loadedChanDescriptor.compositeCatalogDescriptor != currentDescriptor) {
                  return@filter false
                }

                return@filter true
              }

              is ChanDescriptor.CatalogDescriptor,
              is ChanDescriptor.ThreadDescriptor -> {
                if (loadedChanDescriptor !is ChanThreadManager.LoadedChanDescriptor.Regular) {
                  return@filter false
                }

                return@filter loadedChanDescriptor.loadedDescriptor == currentDescriptor
              }
            }
          }
      }
      .filterNotNull()
      .mapNotNull { loadedChanDescriptor ->
        return@mapNotNull getAlbumItemsForChanDescriptor(
          initialLoad = false,
          loadedChanDescriptor = loadedChanDescriptor
        )
      }
      .filter { album -> album.albumItemDataList.isNotEmpty() }
      .collectLatest { album ->
        updateUi(initialLoad = false, album = album)
      }
  }

  private suspend fun listenForCurrentChanDescriptor() {
    chanThreadManager.awaitUntilDependenciesInitialized()

    Logger.debug(TAG) { "listenForCurrentChanDescriptor() currentListenMode: ${_currentListenMode}" }

    val currentDescriptorFlow = when (_currentListenMode) {
      AlbumViewControllerV2.ListenMode.Catalog -> {
        currentOpenedDescriptorStateManager.currentCatalogDescriptorFlow
          .map { catalogDescriptor -> catalogDescriptor as ChanDescriptor? }
      }
      AlbumViewControllerV2.ListenMode.Thread -> {
        currentOpenedDescriptorStateManager.currentThreadDescriptorFlow
          .map { threadDescriptor -> threadDescriptor as ChanDescriptor? }
      }
    }

    currentDescriptorFlow
      .distinctUntilChanged()
      .collectLatest { currentDescriptor ->
        Logger.debug(TAG) { "listenForCurrentChanDescriptor() currentDescriptor changed to '${currentDescriptor}'" }

        _albumItems.clear()
        _albumSelection.value = AlbumSelection()
        _downloadingAlbumItems.clear()
        _lastScrollPosition.intValue = 0
        _currentDescriptor.value = currentDescriptor
      }
  }

  private fun handleRevealedSpoilerImageEvent(revealedSpoilerImage: RevealedSpoilerImagesManager.RevealedSpoilerImage) {
    val index = _albumItems.indexOfFirst { albumItemData ->
      return@indexOfFirst albumItemData.postDescriptor == revealedSpoilerImage.postDescriptor &&
        albumItemData.fullImageUrl == revealedSpoilerImage.fullImageUrl
    }

    if (index < 0) {
      return
    }

    _albumItems[index] = _albumItems[index].copy(spoilerThumbnailImageUrl = null)
  }

  private fun handleDownloadingImageState(downloadingImageState: ImageSaverV2ServiceDelegate.DownloadingImageState) {
    val uniqueId = downloadingImageState.uniqueId
    val imageFullUrl = downloadingImageState.imageFullUrl
    val postDescriptor = downloadingImageState.postDescriptor
    val state = downloadingImageState.state

    if (imageFullUrl == null || postDescriptor == null) {
      val downloadingAlbumItemKeys = _downloadingAlbumItems.values
        .filter { downloadingAlbumItem -> downloadingAlbumItem.downloadUniqueId == uniqueId }
        .map { downloadingAlbumItem -> downloadingAlbumItem.albumItemDataId }

      downloadingAlbumItemKeys.forEach { downloadingAlbumItemKey ->
        val newState = when (downloadingImageState.state) {
          ImageSaverV2ServiceDelegate.DownloadingImageState.State.Downloading,
          ImageSaverV2ServiceDelegate.DownloadingImageState.State.Downloaded,
          ImageSaverV2ServiceDelegate.DownloadingImageState.State.FailedToDownload,
          ImageSaverV2ServiceDelegate.DownloadingImageState.State.Canceled -> {
            _downloadingAlbumItems[downloadingAlbumItemKey]?.copy(state = DownloadingAlbumItem.State.from(state))
          }
          ImageSaverV2ServiceDelegate.DownloadingImageState.State.Deleted -> {
            _downloadingAlbumItems.remove(downloadingAlbumItemKey)
            null
          }
        }

        if (newState != null) {
          _downloadingAlbumItems[downloadingAlbumItemKey] = newState
        }

        when (downloadingImageState.state) {
          ImageSaverV2ServiceDelegate.DownloadingImageState.State.Downloading,
          ImageSaverV2ServiceDelegate.DownloadingImageState.State.Downloaded,
          ImageSaverV2ServiceDelegate.DownloadingImageState.State.FailedToDownload -> {
            // no-op
          }
          ImageSaverV2ServiceDelegate.DownloadingImageState.State.Canceled,
          ImageSaverV2ServiceDelegate.DownloadingImageState.State.Deleted -> {
            clearDownloadingAlbumItemState(downloadingAlbumItemKey)
          }
        }
      }
    } else {
      val albumItemData = _albumItems.firstOrNull { albumItemData ->
        return@firstOrNull albumItemData.postDescriptor == postDescriptor &&
          albumItemData.fullImageUrl == imageFullUrl
      }

      if (albumItemData == null) {
        return
      }

      val newState = when (downloadingImageState.state) {
        ImageSaverV2ServiceDelegate.DownloadingImageState.State.Downloading,
        ImageSaverV2ServiceDelegate.DownloadingImageState.State.Downloaded,
        ImageSaverV2ServiceDelegate.DownloadingImageState.State.FailedToDownload,
        ImageSaverV2ServiceDelegate.DownloadingImageState.State.Canceled -> {
          _downloadingAlbumItems[albumItemData.id]?.copy(state = DownloadingAlbumItem.State.from(state))
        }
        ImageSaverV2ServiceDelegate.DownloadingImageState.State.Deleted -> {
          _downloadingAlbumItems.remove(albumItemData.id)
          null
        }
      }

      if (newState != null) {
        _downloadingAlbumItems[albumItemData.id] = newState
      }
    }
  }

  private fun calculateAspectRatio(chanPostImage: ChanPostImage): Float? {
    val imageWidth = chanPostImage.imageWidth
    val imageHeight = chanPostImage.imageHeight
    if (imageWidth <= 0 || imageHeight <= 0) {
      return null
    }

    return (imageWidth.toFloat() / imageHeight.toFloat()).coerceIn(MIN_RATIO, MAX_RATIO)
  }

  private fun formatImageDetails(postImage: ChanPostImage): String {
    if (postImage.isInlined) {
      return postImage.extension?.uppercase(Locale.ENGLISH) ?: ""
    }

    return buildString {
      append(postImage.extension?.uppercase(Locale.ENGLISH) ?: "")
      append(" ")
      append(postImage.imageWidth)
      append("x")
      append(postImage.imageHeight)
      append(" ")
      append(ChanPostUtils.getReadableFileSize(postImage.size))
    }
  }

  sealed interface PresentController {
    class ImageSaverOptionsController(
      val options: ImageSaverV2OptionsController.Options
    ) : PresentController
  }

  @Immutable
  data class ToolbarData(
    val title: String = "",
    val subtitle: String = ""
  )

  data class Album(
    val initialImageFullUrl: String?,
    val scrollToPosition: Int?,
    val albumItemDataList: List<AlbumItemData>
  )

  @Immutable
  data class AlbumItemDataKey(
    val postDescriptor: PostDescriptor,
    val thumbnailImageUrl: HttpUrl,
  ) : PostImageThumbnailKey

  @Immutable
  data class AlbumItemPostData(
    val threadSubject: String?,
    val mediaInfo: String?,
    val aspectRatio: Float?
  ) {
    fun isNotEmpty(): Boolean {
      return threadSubject.isNotNullNorBlank() || mediaInfo.isNotNullNorBlank()
    }
  }

  class ViewModelFactory @Inject constructor(
    private val appResources: AppResources,
    private val currentOpenedDescriptorStateManager: CurrentOpenedDescriptorStateManager,
    private val chanThreadManager: ChanThreadManager,
    private val chanCatalogSnapshotCache: ChanCatalogSnapshotCache,
    private val chanThreadsCache: ChanThreadsCache,
    private val snackbarManagerFactory: SnackbarManagerFactory,
    private val compositeCatalogManager: CompositeCatalogManager,
    private val filterOutHiddenImagesUseCase: FilterOutHiddenImagesUseCase,
    private val imageSaverV2: ImageSaverV2,
    private val imageSaverV2ServiceDelegate: ImageSaverV2ServiceDelegate,
    private val revealedSpoilerImagesManager: RevealedSpoilerImagesManager
  ) : ViewModelAssistedFactory<AlbumViewControllerV2ViewModel> {
    override fun create(handle: SavedStateHandle): AlbumViewControllerV2ViewModel {
      return AlbumViewControllerV2ViewModel(
        savedStateHandle = handle,
        appResources = appResources,
        currentOpenedDescriptorStateManager = currentOpenedDescriptorStateManager,
        chanThreadManager = chanThreadManager,
        chanCatalogSnapshotCache = chanCatalogSnapshotCache,
        chanThreadsCache = chanThreadsCache,
        snackbarManagerFactory = snackbarManagerFactory,
        compositeCatalogManager = compositeCatalogManager,
        filterOutHiddenImagesUseCase = filterOutHiddenImagesUseCase,
        imageSaverV2 = imageSaverV2,
        imageSaverV2ServiceDelegate = imageSaverV2ServiceDelegate,
        revealedSpoilerImagesManager = revealedSpoilerImagesManager
      )
    }
  }

  companion object {
    private const val TAG = "AlbumViewControllerV2ViewModel"

    private const val MAX_RATIO = 2f
    private const val MIN_RATIO = .4f
  }

}