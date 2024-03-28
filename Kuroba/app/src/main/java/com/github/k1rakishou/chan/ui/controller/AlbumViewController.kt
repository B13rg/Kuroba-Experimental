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
package com.github.k1rakishou.chan.ui.controller

import android.content.Context
import android.view.View
import android.view.View.OnLongClickListener
import android.view.ViewGroup
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.github.k1rakishou.ChanSettings
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.controller.Controller
import com.github.k1rakishou.chan.core.di.component.activity.ActivityComponent
import com.github.k1rakishou.chan.core.helper.ThumbnailLongtapOptionsHelper
import com.github.k1rakishou.chan.core.manager.ChanThreadManager
import com.github.k1rakishou.chan.core.manager.CompositeCatalogManager
import com.github.k1rakishou.chan.core.manager.GlobalWindowInsetsManager
import com.github.k1rakishou.chan.core.manager.WindowInsetsListener
import com.github.k1rakishou.chan.core.navigation.RequiresNoBottomNavBar
import com.github.k1rakishou.chan.core.usecase.FilterOutHiddenImagesUseCase
import com.github.k1rakishou.chan.features.media_viewer.MediaViewerActivity
import com.github.k1rakishou.chan.features.media_viewer.MediaViewerOptions
import com.github.k1rakishou.chan.features.media_viewer.helper.MediaViewerGoToImagePostHelper
import com.github.k1rakishou.chan.features.media_viewer.helper.MediaViewerGoToPostHelper
import com.github.k1rakishou.chan.features.media_viewer.helper.MediaViewerOpenThreadHelper
import com.github.k1rakishou.chan.features.media_viewer.helper.MediaViewerScrollerHelper
import com.github.k1rakishou.chan.features.settings.screens.AppearanceSettingsScreen.Companion.clampColumnsCount
import com.github.k1rakishou.chan.features.toolbar_v2.BackArrowMenuItem
import com.github.k1rakishou.chan.features.toolbar_v2.ToolbarMenuItem
import com.github.k1rakishou.chan.features.toolbar_v2.ToolbarMiddleContent
import com.github.k1rakishou.chan.features.toolbar_v2.ToolbarText
import com.github.k1rakishou.chan.ui.cell.AlbumViewCell
import com.github.k1rakishou.chan.ui.globalstate.fastsroller.FastScrollerControllerType
import com.github.k1rakishou.chan.ui.theme.widget.ColorizableGridRecyclerView
import com.github.k1rakishou.chan.ui.view.FastScroller
import com.github.k1rakishou.chan.ui.view.FastScrollerHelper
import com.github.k1rakishou.chan.ui.view.FixedLinearLayoutManager
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.dp
import com.github.k1rakishou.common.AndroidUtils
import com.github.k1rakishou.common.isNotNullNorBlank
import com.github.k1rakishou.common.mutableListWithCap
import com.github.k1rakishou.common.updatePaddings
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.descriptor.PostDescriptor
import com.github.k1rakishou.model.data.filter.ChanFilterMutable
import com.github.k1rakishou.model.data.post.ChanPostImage
import com.github.k1rakishou.model.util.ChanPostUtils
import com.github.k1rakishou.persist_state.PersistableChanState.albumLayoutGridMode
import com.github.k1rakishou.persist_state.PersistableChanState.showAlbumViewsImageDetails
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import okhttp3.HttpUrl
import javax.inject.Inject

class AlbumViewController(
  context: Context,
  private val chanDescriptor: ChanDescriptor,
  private val displayingPostDescriptors: List<PostDescriptor>
) : Controller(context), RequiresNoBottomNavBar, WindowInsetsListener {
  private lateinit var recyclerView: ColorizableGridRecyclerView

  private val postImages = mutableListOf<ChanPostImage>()
  private var targetIndex = -1

  private var fastScroller: FastScroller? = null
  private var albumAdapter: AlbumAdapter? = null

  private val spanCountAndSpanWidth: SpanInfo
    get() {
      var albumSpanCount = ChanSettings.albumSpanCount.get()
      var albumSpanWith = DEFAULT_SPAN_WIDTH
      val displayWidth = AndroidUtils.getDisplaySize(context).x

      if (albumSpanCount == 0) {
        albumSpanCount = displayWidth / DEFAULT_SPAN_WIDTH
      } else {
        albumSpanWith = displayWidth / albumSpanCount
      }

      albumSpanCount = clampColumnsCount(albumSpanCount)
      return SpanInfo(albumSpanCount, albumSpanWith)
    }

  @Inject
  lateinit var chanThreadManager: ChanThreadManager
  @Inject
  lateinit var globalWindowInsetsManager: GlobalWindowInsetsManager
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
  lateinit var compositeCatalogManager: CompositeCatalogManager

  override fun injectDependencies(component: ActivityComponent) {
    component.inject(this)
  }

  override fun onCreate() {
    super.onCreate()

    val toolbarTitle = when (chanDescriptor) {
      is ChanDescriptor.CompositeCatalogDescriptor,
      is ChanDescriptor.CatalogDescriptor -> {
        ChanPostUtils.getTitle(null, chanDescriptor)
      }
      is ChanDescriptor.ThreadDescriptor -> {
        ChanPostUtils.getTitle(
          chanThreadManager.getChanThread(chanDescriptor)?.getOriginalPost(),
          chanDescriptor
        )
      }
    }
    val toolbarSubTitle = AppModuleAndroidUtils.getQuantityString(R.plurals.image, postImages.size, postImages.size)

    val downloadDrawableId = if (albumLayoutGridMode.get()) {
      R.drawable.ic_baseline_view_quilt_24
    } else {
      R.drawable.ic_baseline_view_comfy_24
    }

    toolbarState.enterDefaultMode(
      leftItem = BackArrowMenuItem(
        onClick = { requireNavController().popController() }
      ),
      middleContent = ToolbarMiddleContent.Title(
        title = ToolbarText.String(toolbarTitle),
        subtitle = ToolbarText.String(toolbarSubTitle)
      ),
      menuBuilder = {
        withMenuItem(
          id = ACTION_TOGGLE_LAYOUT_MODE,
          drawableId = downloadDrawableId,
          onClick = { item -> toggleLayoutModeClicked(item) }
        )
        withMenuItem(
          id = ACTION_DOWNLOAD,
          drawableId = R.drawable.ic_file_download_white_24dp,
          onClick = { item -> downloadAlbumClicked(item) }
        )

        withOverflowMenu {
          withCheckableOverflowMenuItem(
            id = ACTION_TOGGLE_IMAGE_DETAILS,
            stringId = R.string.action_album_show_image_details,
            visible = true,
            checked = showAlbumViewsImageDetails.get(),
            onClick = { onToggleAlbumViewsImageInfoToggled() }
          )
        }
      }
    )

    // View setup
    view = AppModuleAndroidUtils.inflate(context, R.layout.controller_album_view)
    recyclerView = view.findViewById(R.id.recycler_view)
    recyclerView.setHasFixedSize(true)
    albumAdapter = AlbumAdapter()
    recyclerView.adapter = albumAdapter
    updateRecyclerView(false)

    fastScroller = FastScrollerHelper.create(
      FastScrollerControllerType.Album,
      recyclerView,
      null
    )

    controllerScope.launch {
      mediaViewerScrollerHelper.mediaViewerScrollEventsFlow
        .collect { scrollToImageEvent ->
          val descriptor = scrollToImageEvent.chanDescriptor
          if (descriptor != chanDescriptor) {
            return@collect
          }

          val index = postImages.indexOf(scrollToImageEvent.chanPostImage)
          if (index < 0) {
            return@collect
          }

          scrollToInternal(index)
        }
    }

    controllerScope.launch {
      mediaViewerGoToImagePostHelper.mediaViewerGoToPostEventsFlow
        .collect { goToPostEvent ->
          val postImage = goToPostEvent.chanPostImage
          val chanDescriptor = goToPostEvent.chanDescriptor

          popFromNavControllerWithAction(chanDescriptor) { threadController ->
            threadController.selectPostImage(postImage)
          }
        }
    }

    controllerScope.launch {
      mediaViewerGoToPostHelper.mediaViewerGoToPostEventsFlow
        .collect { postDescriptor ->
          if (postDescriptor.descriptor != chanDescriptor) {
            return@collect
          }

          popFromNavController(postDescriptor.descriptor)
        }
    }

    controllerScope.launch {
      toolbarState.toolbarHeightState
        .onEach { onInsetsChanged() }
        .collect()
    }

    if (chanDescriptor is ChanDescriptor.CompositeCatalogDescriptor) {
      controllerScope.launch {
        val compositeCatalogName = compositeCatalogManager.byCompositeCatalogDescriptor(chanDescriptor)
          ?.name

        if (compositeCatalogName.isNotNullNorBlank()) {
          toolbarState.default.updateTitle(
            newTitle = ToolbarText.String(compositeCatalogName)
          )
        }
      }
    }

    globalWindowInsetsManager.addInsetsUpdatesListener(this)
    onInsetsChanged()
  }

  private fun scrollToInternal(scrollPosition: Int) {
    val layoutManager = recyclerView.layoutManager

    if (layoutManager is GridLayoutManager) {
      layoutManager.scrollToPositionWithOffset(scrollPosition, 0)
      return
    }

    if (layoutManager is StaggeredGridLayoutManager) {
      layoutManager.scrollToPositionWithOffset(scrollPosition, 0)
      return
    }

    if (layoutManager is FixedLinearLayoutManager) {
      layoutManager.scrollToPositionWithOffset(scrollPosition, 0)
      return
    }

    recyclerView.scrollToPosition(scrollPosition)
  }

  private fun updateRecyclerView(reloading: Boolean) {
    val spanInfo = spanCountAndSpanWidth
    val staggeredGridLayoutManager = StaggeredGridLayoutManager(
      spanInfo.spanCount,
      StaggeredGridLayoutManager.VERTICAL
    )

    recyclerView.layoutManager = staggeredGridLayoutManager
    recyclerView.setSpanWidth(spanInfo.spanWidth)
    recyclerView.itemAnimator = null
    recyclerView.scrollToPosition(targetIndex)

    if (reloading) {
      albumAdapter?.refresh()
    }
  }

  override fun onDestroy() {
    super.onDestroy()
    globalWindowInsetsManager.removeInsetsUpdatesListener(this)

    fastScroller?.onCleanup()
    fastScroller = null

    recyclerView.swapAdapter(null, true)
  }

  override fun onInsetsChanged() {
    var toolbarHeight = with(appResources.composeDensity) { toolbarState.toolbarHeight?.toPx()?.toInt() }
    if (toolbarHeight == null) {
      toolbarHeight = appResources.dimension(com.github.k1rakishou.chan.R.dimen.toolbar_height).toInt()
    }

    val bottomPaddingDp = calculateBottomPaddingForRecyclerInDp(
      globalWindowInsetsManager = globalWindowInsetsManager,
      mainControllerCallbacks = null
    )

    recyclerView.updatePaddings(
      left = null,
      right = FastScrollerHelper.FAST_SCROLLER_WIDTH,
      top = toolbarHeight,
      bottom = dp(bottomPaddingDp.toFloat())
    )
  }

  fun tryCollectingImages(initialImageUrl: HttpUrl?): Boolean {
    val (images, index) = collectImages(initialImageUrl)

    if (images.isEmpty()) {
      return false
    }

    val input = FilterOutHiddenImagesUseCase.Input(
      images = images,
      index = index,
      isOpeningAlbum = true,
      postDescriptorSelector = { chanPostImage -> chanPostImage.ownerPostDescriptor }
    )

    val output = filterOutHiddenImagesUseCase.filter(input)
    val filteredImages = output.images
    val newIndex = output.index

    if (filteredImages.isEmpty()) {
      return false
    }

    targetIndex = newIndex

    postImages.clear()
    postImages.addAll(filteredImages)

    return true
  }

  private fun collectImages(initialImageUrl: HttpUrl?): Pair<List<ChanPostImage>, Int> {
    var imageIndexToScroll = 0
    var index = 0

    when (chanDescriptor) {
      is ChanDescriptor.CompositeCatalogDescriptor,
      is ChanDescriptor.CatalogDescriptor -> {
        val postImages = mutableListOf<ChanPostImage>()

        displayingPostDescriptors.forEach { displayingPostDescriptor ->
          val chanPost = chanThreadManager.getPost(displayingPostDescriptor)
          if (chanPost == null) {
            return@forEach
          }

          chanPost.iteratePostImages { chanPostImage ->
            postImages += chanPostImage

            if (initialImageUrl != null && chanPostImage.imageUrl == initialImageUrl) {
              imageIndexToScroll = index
            }

            ++index
          }
        }

        return postImages to imageIndexToScroll
      }
      is ChanDescriptor.ThreadDescriptor -> {
        val chanThread = chanThreadManager.getChanThread(chanDescriptor)
        if (chanThread == null) {
          return emptyList<ChanPostImage>() to imageIndexToScroll
        }

        val postImages = mutableListWithCap<ChanPostImage>(chanThread.postsCount)

        chanThread.iteratePostsOrdered { chanPost ->
          chanPost.iteratePostImages { chanPostImage ->
            postImages += chanPostImage

            if (initialImageUrl != null && chanPostImage.imageUrl == initialImageUrl) {
              imageIndexToScroll = index
            }

            ++index
          }
        }

        return postImages to imageIndexToScroll
      }
    }
  }

  private fun onToggleAlbumViewsImageInfoToggled() {
    toolbarState.findCheckableOverflowItem(ACTION_TOGGLE_IMAGE_DETAILS)
      ?.updateChecked(showAlbumViewsImageDetails.toggle())

    albumAdapter?.refresh()
  }

  private fun downloadAlbumClicked(item: ToolbarMenuItem) {
    val albumDownloadController = AlbumDownloadController(context)
    albumDownloadController.setPostImages(postImages)
    requireNavController().pushController(albumDownloadController)
  }

  private fun toggleLayoutModeClicked(item: ToolbarMenuItem) {
    albumLayoutGridMode.toggle()
    updateRecyclerView(true)

    toolbarState.findItem(ACTION_TOGGLE_LAYOUT_MODE)?.let { toolbarMenuItem ->
      val drawableId = if (albumLayoutGridMode.get()) {
        R.drawable.ic_baseline_view_quilt_24
      } else {
        R.drawable.ic_baseline_view_comfy_24
      }

      toolbarMenuItem.updateDrawableId(drawableId)
    }
  }

  private fun openImage(postImage: ChanPostImage) {
    val index = postImages.indexOf(postImage)
    if (index < 0) {
      return
    }

    when (chanDescriptor) {
      is ChanDescriptor.ICatalogDescriptor -> {
        MediaViewerActivity.catalogMedia(
          context = context,
          catalogDescriptor = chanDescriptor,
          initialImageUrl = postImages[index].imageUrl?.toString(),
          transitionThumbnailUrl = postImages[index].getThumbnailUrl()!!.toString(),
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
          postDescriptorList = mapPostImagesToPostDescriptors(),
          initialImageUrl = postImages[index].imageUrl?.toString(),
          transitionThumbnailUrl = postImages[index].getThumbnailUrl()!!.toString(),
          lastTouchCoordinates = globalWindowInsetsManager.lastTouchCoordinates(),
          mediaViewerOptions = MediaViewerOptions(
            mediaViewerOpenedFromAlbum = true
          )
        )
      }
    }
  }

  private fun mapPostImagesToPostDescriptors(): List<PostDescriptor> {
    val duplicateSet = mutableSetOf<PostDescriptor>()

    return postImages.mapNotNull { postImage ->
      if (duplicateSet.add(postImage.ownerPostDescriptor)) {
        return@mapNotNull postImage.ownerPostDescriptor
      }

      return@mapNotNull null
    }
  }

  private fun showImageLongClickOptions(postImage: ChanPostImage) {
    thumbnailLongtapOptionsHelper.onThumbnailLongTapped(
      context = context,
      chanDescriptor = chanDescriptor,
      isCurrentlyInAlbum = true,
      postImage = postImage,
      presentControllerFunc = { controller -> presentController(controller) },
      showFiltersControllerFunc = { },
      openThreadFunc = { postDescriptor ->
        popFromNavController(chanDescriptor)
        mediaViewerOpenThreadHelper.tryToOpenThread(postDescriptor)
      },
      goToPostFunc = {
        popFromNavControllerWithAction(chanDescriptor) { threadController ->
          threadController.selectPostImage(postImage)
        }
      }
    )
  }

  private inner class AlbumAdapter : RecyclerView.Adapter<AlbumItemCellHolder>() {
    private val albumCellType = 1

    init {
      setHasStableIds(true)
    }

    override fun getItemViewType(position: Int): Int {
      return albumCellType
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AlbumItemCellHolder {
      val view = AppModuleAndroidUtils.inflate(parent.context, R.layout.cell_album_view, parent, false)
      return AlbumItemCellHolder(view)
    }

    override fun onBindViewHolder(holder: AlbumItemCellHolder, position: Int) {
      val postImage = postImages.get(position)
      val canUseHighResCells = ColorizableGridRecyclerView.canUseHighResCells(recyclerView.currentSpanCount)
      val isStaggeredGridMode = !albumLayoutGridMode.get()

      holder.cell.bindPostImage(
        chanDescriptor = chanDescriptor,
        postImage = postImage,
        canUseHighResCells = canUseHighResCells,
        isStaggeredGridMode = isStaggeredGridMode,
        showDetails = showAlbumViewsImageDetails.get()
      )
    }

    override fun onViewRecycled(holder: AlbumItemCellHolder) {
      holder.cell.unbindPostImage()
    }

    override fun getItemCount(): Int {
      return postImages.size
    }

    override fun getItemId(position: Int): Long {
      return position.toLong()
    }

    fun refresh() {
      notifyDataSetChanged()
    }
  }

  private inner class AlbumItemCellHolder(itemView: View) : RecyclerView.ViewHolder(itemView), View.OnClickListener, OnLongClickListener {
    private val ALBUM_VIEW_CELL_THUMBNAIL_CLICK_TOKEN = "ALBUM_VIEW_CELL_THUMBNAIL_CLICK"
    private val ALBUM_VIEW_CELL_THUMBNAIL_LONG_CLICK_TOKEN = "ALBUM_VIEW_CELL_THUMBNAIL_LONG_CLICK"

    val cell = itemView as AlbumViewCell
    val thumbnailView = cell.postImageThumbnailView

    init {
      thumbnailView.setImageClickListener(ALBUM_VIEW_CELL_THUMBNAIL_CLICK_TOKEN, this)
      thumbnailView.setImageLongClickListener(ALBUM_VIEW_CELL_THUMBNAIL_LONG_CLICK_TOKEN, this)
    }

    override fun onClick(v: View) {
      val postImage = postImages.getOrNull(adapterPosition)
        ?: return

      openImage(postImage)
    }

    override fun onLongClick(v: View): Boolean {
      val postImage = postImages.getOrNull(adapterPosition)
        ?: return false

      showImageLongClickOptions(postImage)
      return true
    }

  }

  private class SpanInfo(val spanCount: Int, val spanWidth: Int)

  interface ThreadControllerCallbacks {
    fun openFiltersController(chanFilterMutable: ChanFilterMutable)
  }

  companion object {
    private val DEFAULT_SPAN_WIDTH = dp(120f)
    private const val ACTION_DOWNLOAD = 0
    private const val ACTION_TOGGLE_LAYOUT_MODE = 1
    private const val ACTION_TOGGLE_IMAGE_DETAILS = 2
  }
}