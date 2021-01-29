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
package com.github.k1rakishou.chan.ui.layout

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Rect
import android.util.AttributeSet
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.TextView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.ItemDecoration
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.github.k1rakishou.ChanSettings
import com.github.k1rakishou.ChanSettings.PostViewMode
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.controller.Controller
import com.github.k1rakishou.chan.core.base.Debouncer
import com.github.k1rakishou.chan.core.base.RendezvousCoroutineExecutor
import com.github.k1rakishou.chan.core.base.SerializedCoroutineExecutor
import com.github.k1rakishou.chan.core.helper.LastViewedPostNoInfoHolder
import com.github.k1rakishou.chan.core.manager.BottomNavBarVisibilityStateManager
import com.github.k1rakishou.chan.core.manager.ChanThreadManager
import com.github.k1rakishou.chan.core.manager.ChanThreadViewableInfoManager
import com.github.k1rakishou.chan.core.manager.GlobalWindowInsetsManager
import com.github.k1rakishou.chan.core.manager.PostFilterManager
import com.github.k1rakishou.chan.core.presenter.ThreadPresenter
import com.github.k1rakishou.chan.core.usecase.ExtractPostMapInfoHolderUseCase
import com.github.k1rakishou.chan.features.reply.ReplyLayout
import com.github.k1rakishou.chan.features.reply.ReplyLayout.ThreadListLayoutCallbacks
import com.github.k1rakishou.chan.features.reply.ReplyLayoutFilesArea
import com.github.k1rakishou.chan.features.reply.ReplyPresenter
import com.github.k1rakishou.chan.ui.adapter.PostAdapter
import com.github.k1rakishou.chan.ui.adapter.PostAdapter.PostAdapterCallback
import com.github.k1rakishou.chan.ui.adapter.PostsFilter
import com.github.k1rakishou.chan.ui.cell.PostCellInterface
import com.github.k1rakishou.chan.ui.cell.PostCellInterface.PostCellCallback
import com.github.k1rakishou.chan.ui.cell.PostStubCell
import com.github.k1rakishou.chan.ui.cell.ThreadStatusCell
import com.github.k1rakishou.chan.ui.controller.FloatingListMenuController
import com.github.k1rakishou.chan.ui.controller.LoadingViewController
import com.github.k1rakishou.chan.ui.controller.ThreadSlideController
import com.github.k1rakishou.chan.ui.theme.widget.ColorizableRecyclerView
import com.github.k1rakishou.chan.ui.toolbar.Toolbar
import com.github.k1rakishou.chan.ui.view.FastScroller
import com.github.k1rakishou.chan.ui.view.FastScrollerHelper
import com.github.k1rakishou.chan.ui.view.PostInfoMapItemDecoration
import com.github.k1rakishou.chan.ui.view.ThumbnailView
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.dp
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.getDimen
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.getQuantityString
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.getString
import com.github.k1rakishou.chan.utils.BackgroundUtils
import com.github.k1rakishou.common.AndroidUtils
import com.github.k1rakishou.common.updatePaddings
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.core_themes.ThemeEngine
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor.ThreadDescriptor
import com.github.k1rakishou.model.data.descriptor.PostDescriptor
import com.github.k1rakishou.model.data.post.ChanPost
import com.github.k1rakishou.model.data.post.ChanPostImage
import io.reactivex.disposables.CompositeDisposable
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import java.util.*
import javax.inject.Inject
import kotlin.coroutines.CoroutineContext
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * A layout that wraps around a [RecyclerView] and a [ReplyLayout] to manage showing and replying to posts.
 */
class ThreadListLayout(context: Context, attrs: AttributeSet?)
  : FrameLayout(context, attrs),
  ThreadListLayoutCallbacks,
  Toolbar.ToolbarHeightUpdatesCallback,
  CoroutineScope,
  ThemeEngine.ThemeChangesListener,
  FastScroller.ThumbDragListener,
  ReplyLayoutFilesArea.ThreadListLayoutCallbacks {

  @Inject
  lateinit var themeEngine: ThemeEngine
  @Inject
  lateinit var postFilterManager: PostFilterManager
  @Inject
  lateinit var bottomNavBarVisibilityStateManager: BottomNavBarVisibilityStateManager
  @Inject
  lateinit var extractPostMapInfoHolderUseCase: ExtractPostMapInfoHolderUseCase
  @Inject
  lateinit var lastViewedPostNoInfoHolder: LastViewedPostNoInfoHolder
  @Inject
  lateinit var chanThreadViewableInfoManager: ChanThreadViewableInfoManager
  @Inject
  lateinit var globalWindowInsetsManager: GlobalWindowInsetsManager
  @Inject
  lateinit var chanThreadManager: ChanThreadManager

  private val PARTY: ItemDecoration = object : ItemDecoration() {
    override fun onDrawOver(c: Canvas, parent: RecyclerView, state: RecyclerView.State) {
      if (hat == null) {
        hat = BitmapFactory.decodeResource(resources, R.drawable.partyhat)
      }

      var i = 0
      val j = parent.childCount

      while (i < j) {
        val child = parent.getChildAt(i)
        if (child is PostCellInterface) {
          val postView = child as PostCellInterface
          val post = postView.getPost()

          if (post == null || !post.isOP() || post.postImages.isEmpty()) {
            i++
            continue
          }

          val params = child.layoutParams as RecyclerView.LayoutParams
          val top = child.top + params.topMargin
          val left = child.left + params.leftMargin

          c.drawBitmap(
            hat!!,
            left - parent.paddingLeft - dp(25f).toFloat(),
            top - dp(80f) - parent.paddingTop + toolbarHeight().toFloat(),
            null
          )
        }

        i++
      }
    }
  }

  private val scrollListener: RecyclerView.OnScrollListener = object : RecyclerView.OnScrollListener() {
    override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
      if (newState == RecyclerView.SCROLL_STATE_IDLE) {
        onRecyclerViewScrolled()
      }
    }
  }

  val replyPresenter: ReplyPresenter
    get() = replyLayout.presenter

  val displayingPostDescriptors: List<PostDescriptor>
    get() = postAdapter.displayList

  val indexAndTop: IntArray?
    get() {
      var index = 0
      var top = 0

      val layoutManager = recyclerView.layoutManager
        ?: return null

      if (layoutManager.childCount > 0) {
        val topChild = layoutManager.getChildAt(0)
          ?: return null

        index = (topChild.layoutParams as RecyclerView.LayoutParams).viewLayoutPosition
        val params = topChild.layoutParams as RecyclerView.LayoutParams
        top = layoutManager.getDecoratedTop(topChild) - params.topMargin - recyclerView.paddingTop
      }

      return intArrayOf(index, top)
    }

  private val topAdapterPosition: Int
    get() {
      if (layoutManager == null) {
        return -1
      }

      when (postViewMode) {
        PostViewMode.LIST -> return (layoutManager as LinearLayoutManager).findFirstVisibleItemPosition()
        PostViewMode.CARD -> return (layoutManager as GridLayoutManager).findFirstVisibleItemPosition()
      }

      return -1
    }

  private val completeBottomAdapterPosition: Int
    get() {
      if (layoutManager == null) {
        return -1
      }

      when (postViewMode) {
        PostViewMode.LIST -> return (layoutManager as LinearLayoutManager).findLastCompletelyVisibleItemPosition()
        PostViewMode.CARD -> return (layoutManager as GridLayoutManager).findLastCompletelyVisibleItemPosition()
      }
      return -1
    }

  private lateinit var replyLayout: ReplyLayout
  private lateinit var searchStatus: TextView
  private lateinit var recyclerView: ColorizableRecyclerView
  private lateinit var postAdapter: PostAdapter

  private val compositeDisposable = CompositeDisposable()
  private val job = SupervisorJob()
  private val updateRecyclerPaddingsDebouncer = Debouncer(false)

  private lateinit var listScrollToBottomExecutor: RendezvousCoroutineExecutor
  private lateinit var serializedCoroutineExecutor: SerializedCoroutineExecutor

  override val coroutineContext: CoroutineContext
    get() = job + Dispatchers.Main + CoroutineName("ThreadListLayout")

  private var threadPresenter: ThreadPresenter? = null

  private var layoutManager: RecyclerView.LayoutManager? = null
  private var fastScroller: FastScroller? = null
  private var postInfoMapItemDecoration: PostInfoMapItemDecoration? = null
  private var callback: ThreadListLayoutPresenterCallback? = null
  private var threadListLayoutCallback: ThreadListLayoutCallback? = null
  private var postViewMode: PostViewMode? = null
  private var spanCount = 2
  private var searchOpen = false
  private var prevLastPostNo = 0L
  private var hat: Bitmap? = null

  var replyOpen = false
    private set

  override fun getCurrentChanDescriptor(): ChanDescriptor? {
    return threadPresenter?.currentChanDescriptor
  }

  private fun currentThreadDescriptorOrNull(): ThreadDescriptor? {
    return getCurrentChanDescriptor()?.threadDescriptorOrNull()
  }

  private fun currentChanDescriptorOrNull(): ChanDescriptor? {
    return getCurrentChanDescriptor()
  }

  private fun forceRecycleAllPostViews() {
    val adapter = recyclerView.adapter
    if (adapter is PostAdapter) {
      recyclerView.recycledViewPool.clear()
      adapter.cleanup()
    }
  }

  override fun onAttachedToWindow() {
    super.onAttachedToWindow()

    themeEngine.addListener(this)
  }

  override fun onDetachedFromWindow() {
    super.onDetachedFromWindow()

    themeEngine.removeListener(this)
  }

  override fun onThemeChanged() {
    setBackgroundColor(themeEngine.chanTheme.backColor)
    replyLayout.setBackgroundColor(themeEngine.chanTheme.backColor)
    searchStatus.setBackgroundColor(themeEngine.chanTheme.backColor)

    searchStatus.setTextColor(themeEngine.chanTheme.textColorSecondary)
    searchStatus.typeface = themeEngine.chanTheme.mainFont
  }

  override fun onFinishInflate() {
    super.onFinishInflate()

    AppModuleAndroidUtils.extractActivityComponent(context)
      .inject(this)

    // View binding
    replyLayout = findViewById(R.id.reply)
    searchStatus = findViewById(R.id.search_status)
    recyclerView = findViewById(R.id.recycler_view)

    val params = replyLayout.layoutParams as LayoutParams
    params.gravity = Gravity.BOTTOM
    replyLayout.layoutParams = params

    onThemeChanged()
  }

  fun onCreate(
    threadPresenter: ThreadPresenter,
    threadListLayoutCallback: ThreadListLayoutCallback
  ) {
    this.callback = threadPresenter
    this.threadPresenter = threadPresenter
    this.threadListLayoutCallback = threadListLayoutCallback

    listScrollToBottomExecutor = RendezvousCoroutineExecutor(this)
    serializedCoroutineExecutor = SerializedCoroutineExecutor(this)

    postAdapter = PostAdapter(
      postFilterManager,
      recyclerView,
      threadPresenter as PostAdapterCallback,
      threadPresenter as PostCellCallback,
      threadPresenter as ThreadStatusCell.Callback
    )

    replyLayout.onCreate(this, this)

    recyclerView.adapter = postAdapter
    // Man, fuck the RecycledViewPool. Sometimes when scrolling away from a view and the swiftly
    // back to it onViewRecycled() will be called TWICE for that view. Setting setMaxRecycledViews
    // for TYPE_POST to 0 solves this problem. What a buggy piece of shit.
    recyclerView.recycledViewPool.setMaxRecycledViews(PostAdapter.TYPE_POST, 0)
    recyclerView.addOnScrollListener(scrollListener)

    setFastScroll(false)
    attachToolbarScroll(true)

    threadListLayoutCallback.toolbar?.addToolbarHeightUpdatesCallback(this)

    // Wait a little bit so that the toolbar has it's updated height (which depends on the window
    // insets)
    post {
      searchStatus.updatePaddings(top = searchStatus.paddingTop + toolbarHeight())
    }
  }

  fun onDestroy() {
    compositeDisposable.clear()
    job.cancelChildren()

    threadListLayoutCallback?.toolbar?.removeToolbarHeightUpdatesCallback(this)
    replyLayout.onDestroy()

    forceRecycleAllPostViews()
    recyclerView.adapter = null
    threadPresenter = null
  }

  override fun onToolbarHeightKnown(heightChanged: Boolean) {
    setRecyclerViewPadding()
  }

  private fun onRecyclerViewScrolled() {
    recyclerView.post {
      // onScrolled can be called after cleanup()
      if (getCurrentChanDescriptor() == null) {
        return@post
      }

      val chanThreadLoadingState = threadPresenter?.chanThreadLoadingState
        ?: ThreadPresenter.ChanThreadLoadingState.Uninitialized

      if (chanThreadLoadingState != ThreadPresenter.ChanThreadLoadingState.Loaded) {
        // When reloading a thread, this callback will be called immediately which will result in
        //  "indexAndTop" being zeroes which will overwrite the old scroll position with incorrect
        //  values.
        return@post
      }

      val chanDescriptor = currentChanDescriptorOrNull()
        ?: return@post
      val indexTop = indexAndTop
        ?: return@post

      chanThreadViewableInfoManager.update(chanDescriptor) { chanThreadViewableInfo ->
        chanThreadViewableInfo.listViewIndex = indexTop[0]
        chanThreadViewableInfo.listViewTop = indexTop[1]
      }

      val currentLastPostNo = postAdapter.lastPostNo

      val lastVisibleItemPosition = completeBottomAdapterPosition
      if (lastVisibleItemPosition >= 0) {
        updateLastViewedPostNo(lastVisibleItemPosition)
      }

      if (lastVisibleItemPosition == postAdapter.itemCount - 1 && currentLastPostNo > prevLastPostNo) {
        prevLastPostNo = currentLastPostNo

        // As requested by the RecyclerView, make sure that the adapter isn't changed
        // while in a layout pass. Postpone to the next frame.
        listScrollToBottomExecutor.post { callback?.onListScrolledToBottom() }
      }

      if (lastVisibleItemPosition == postAdapter.itemCount - 1) {
        val isDragging = fastScroller?.isDragging ?: false
        if (!isDragging) {
          threadListLayoutCallback?.showToolbar()
        }
      }
    }
  }

  private fun updateLastViewedPostNo(last: Int) {
    if (last < 0) {
      return
    }

    val threadDescriptor = currentThreadDescriptorOrNull()
    if (threadDescriptor != null) {
      val postNo = postAdapter.getPostNo(last)
      if (postNo >= 0L) {
        lastViewedPostNoInfoHolder.setLastViewedPostNo(threadDescriptor, postNo)
      }
    }
  }

  override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
    super.onMeasure(widthMeasureSpec, heightMeasureSpec)

    val cardWidth = getDimen(R.dimen.grid_card_width)
    val gridCountSetting = ChanSettings.boardGridSpanCount.get()
    val compactMode: Boolean

    if (gridCountSetting > 0) {
      spanCount = gridCountSetting
      compactMode = measuredWidth / spanCount < dp(120f)
    } else {
      spanCount = max(1, (measuredWidth.toFloat() / cardWidth).roundToInt())
      compactMode = false
    }

    if (postViewMode == PostViewMode.CARD) {
      postAdapter.setCompact(compactMode)
      (layoutManager as GridLayoutManager).spanCount = spanCount
    }
  }

  fun setPostViewMode(postViewMode: PostViewMode) {
    if (this.postViewMode == postViewMode) {
      return
    }

    this.postViewMode = postViewMode
    layoutManager = null

    when (postViewMode) {
      PostViewMode.LIST -> {
        val linearLayoutManager: LinearLayoutManager = object : LinearLayoutManager(context) {
          override fun requestChildRectangleOnScreen(
            parent: RecyclerView,
            child: View,
            rect: Rect,
            immediate: Boolean,
            focusedChildVisible: Boolean
          ): Boolean {
            return false
          }
        }

        setRecyclerViewPadding()

        recyclerView.layoutManager = linearLayoutManager
        layoutManager = linearLayoutManager

        setBackgroundColor(themeEngine.chanTheme.backColor)
      }
      PostViewMode.CARD -> {
        val gridLayoutManager: GridLayoutManager = object : GridLayoutManager(
          null,
          spanCount,
          VERTICAL,
          false
        ) {
          override fun requestChildRectangleOnScreen(
            parent: RecyclerView,
            child: View,
            rect: Rect,
            immediate: Boolean,
            focusedChildVisible: Boolean
          ): Boolean {
            return false
          }
        }

        setRecyclerViewPadding()

        recyclerView.layoutManager = gridLayoutManager
        layoutManager = gridLayoutManager

        setBackgroundColor(themeEngine.chanTheme.backColorSecondary())
      }
    }

    recyclerView.recycledViewPool.clear()
    postAdapter.setPostViewMode(postViewMode)
  }

  suspend fun showPosts(
    descriptor: ChanDescriptor,
    filter: PostsFilter,
    initial: Boolean
  ): Boolean {
    val presenter = threadPresenter
    if (presenter == null) {
      Logger.d(TAG, "showPosts() threadPresenter==null")
      return false
    }

    if (initial) {
      replyLayout.bindLoadable(descriptor)

      recyclerView.layoutManager = null
      recyclerView.layoutManager = layoutManager
      recyclerView.recycledViewPool.clear()
      party()
    }

    setFastScroll(true)
    val posts = chanThreadManager.getMutableListOfPosts(descriptor)

    postAdapter.setThread(
      descriptor,
      filter.applyFilter(descriptor, posts),
      themeEngine.chanTheme
    )

    val chanDescriptor = currentChanDescriptorOrNull()
    if (chanDescriptor != null) {
      restorePrevScrollPosition(chanDescriptor, initial)
    }

    return true
  }

  private fun restorePrevScrollPosition(
    chanDescriptor: ChanDescriptor,
    initial: Boolean
  ) {
    val markedPostNo = chanThreadViewableInfoManager.getMarkedPostNo(chanDescriptor)
    val markedPost = if (markedPostNo != null) {
      chanThreadManager.findPostByPostNo(chanDescriptor, markedPostNo)
    } else {
      null
    }

    if (markedPost == null && initial) {
      chanThreadViewableInfoManager.view(chanDescriptor) { (_, index, top) ->
        when (postViewMode) {
          PostViewMode.LIST -> (layoutManager as LinearLayoutManager).scrollToPositionWithOffset(
            index,
            top
          )
          PostViewMode.CARD -> (layoutManager as GridLayoutManager).scrollToPositionWithOffset(
            index,
            top
          )
        }
      }

      return
    }

    if (markedPost != null) {
      chanThreadViewableInfoManager.getAndConsumeMarkedPostNo(chanDescriptor) { postNo ->
        val position = getPostPositionInAdapter(postNo)
        if (position < 0) {
          return@getAndConsumeMarkedPostNo
        }

        // Delay because for some reason recycler doesn't scroll to the post sometimes
        recyclerView.post {
          highlightPost(markedPost.postDescriptor)

          when (postViewMode) {
            PostViewMode.LIST -> (layoutManager as LinearLayoutManager).scrollToPositionWithOffset(
              position,
              0
            )
            PostViewMode.CARD -> (layoutManager as GridLayoutManager).scrollToPositionWithOffset(
              position,
              0
            )
          }
        }
      }

      return
    }
  }

  private fun getPostPositionInAdapter(postNo: Long): Int {
    var position = -1
    val postDescriptors = postAdapter.displayList

    for (i in postDescriptors.indices) {
      val postDescriptor = postDescriptors[i]
      if (postDescriptor.postNo == postNo) {
        position = i
        break
      }
    }

    return position
  }

  fun onBack(): Boolean {
    return when {
      replyLayout.onBack() -> true
      replyOpen -> {
        openReply(false)
        true
      }
      else -> threadListLayoutCallback!!.threadBackPressed()
    }
  }

  fun sendKeyEvent(event: KeyEvent): Boolean {
    when (event.keyCode) {
      KeyEvent.KEYCODE_VOLUME_UP,
      KeyEvent.KEYCODE_VOLUME_DOWN -> {
        if (!ChanSettings.volumeKeysScrolling.get()) {
          return false
        }

        if (event.action == KeyEvent.ACTION_DOWN) {
          val down = event.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN
          val scroll = (height * 0.75).toInt()
          recyclerView.smoothScrollBy(0, if (down) scroll else -scroll)
        }

        return true
      }
      KeyEvent.KEYCODE_BACK -> {
        if (event.isLongPress) {
          threadListLayoutCallback?.threadBackLongPressed()
          return true
        }
      }
    }

    return false
  }

  fun gainedFocus(
    threadControllerType: ThreadSlideController.ThreadControllerType,
    isThreadVisible: Boolean
  ) {
    threadPresenter?.gainedFocus(threadControllerType)

    if (isThreadVisible) {
      val chanDescriptor = currentChanDescriptorOrNull()
      if (chanDescriptor != null) {
        restorePrevScrollPosition(chanDescriptor, false)
      }

      showToolbarIfNeeded()
    }
  }

  override fun openReply(open: Boolean) {
    if (currentChanDescriptorOrNull() == null || replyOpen == open) {
      return
    }

    val chanDescriptor = currentChanDescriptorOrNull()
    replyOpen = open

    measureReplyLayout()

    fun notifyBottomNavBarVisibilityStateManager() {
      if (chanDescriptor != null) {
        bottomNavBarVisibilityStateManager.replyViewStateChanged(
          chanDescriptor.isCatalogDescriptor(),
          open
        )
      }
    }

    val height = replyLayout.measuredHeight
    val viewPropertyAnimator = replyLayout.animate()

    viewPropertyAnimator.setListener(null)
    viewPropertyAnimator.interpolator = DecelerateInterpolator(2f)
    viewPropertyAnimator.duration = 350

    if (open) {
      replyLayout.visibility = VISIBLE
      replyLayout.translationY = height.toFloat()

      viewPropertyAnimator.translationY(0f)
      viewPropertyAnimator.setListener(object : AnimatorListenerAdapter() {
        override fun onAnimationStart(animation: Animator?) {
          notifyBottomNavBarVisibilityStateManager()
        }

        override fun onAnimationEnd(animation: Animator) {
          viewPropertyAnimator.setListener(null)
        }
      })
    } else {
      replyLayout.translationY = 0f

      viewPropertyAnimator.translationY(height.toFloat())
      viewPropertyAnimator.setListener(object : AnimatorListenerAdapter() {
        override fun onAnimationStart(animation: Animator?) {
          notifyBottomNavBarVisibilityStateManager()
        }

        override fun onAnimationEnd(animation: Animator) {
          viewPropertyAnimator.setListener(null)
          replyLayout.visibility = GONE
        }
      })
    }

    replyLayout.onOpen(open)
    setRecyclerViewPadding()

    if (!open) {
      AndroidUtils.hideKeyboard(replyLayout)
    }

    threadListLayoutCallback?.replyLayoutOpen(open)
    attachToolbarScroll(!open && !searchOpen)
  }

  fun showError(error: String?) {
    postAdapter.showError(error)
  }

  fun openSearch(open: Boolean) {
    if (currentChanDescriptorOrNull() == null || searchOpen == open) {
      return
    }

    searchOpen = open
    searchStatus.measure(
      MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
      MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
    )

    val height = searchStatus.measuredHeight
    val viewPropertyAnimator = searchStatus.animate()

    viewPropertyAnimator.setListener(null)
    viewPropertyAnimator.interpolator = DecelerateInterpolator(2f)
    viewPropertyAnimator.duration = 600

    val topPosition = topAdapterPosition

    if (open) {
      searchStatus.visibility = VISIBLE
      searchStatus.translationY = -height.toFloat()

      viewPropertyAnimator.translationY(0f)
      viewPropertyAnimator.setListener(object : AnimatorListenerAdapter() {
        override fun onAnimationEnd(animation: Animator) {
          setRecyclerViewPadding()

          searchStatus.setText(R.string.search_empty)
          attachToolbarScroll(!open && !replyOpen)

          if (topPosition <= 0) {
            recyclerView.post {
              recyclerView.scrollToPosition(0)
            }
          }
        }
      })
    } else {
      searchStatus.translationY = 0f

      viewPropertyAnimator.translationY(-height.toFloat())
      viewPropertyAnimator.setListener(object : AnimatorListenerAdapter() {
        override fun onAnimationEnd(animation: Animator) {
          viewPropertyAnimator.setListener(null)
          searchStatus.visibility = GONE

          setRecyclerViewPadding()
          threadListLayoutCallback?.toolbar?.closeSearch()

          attachToolbarScroll(!open && !replyOpen)
        }
      })
    }
  }

  @SuppressLint("StringFormatMatches")
  //android studio doesn't like the nested getQuantityString and messes up, but nothing is wrong
  fun setSearchStatus(query: String?, setEmptyText: Boolean, hideKeyboard: Boolean) {
    if (hideKeyboard) {
      AndroidUtils.hideKeyboard(this)
    }

    if (setEmptyText) {
      searchStatus.setText(R.string.search_empty)
    }

    if (query != null) {
      val size = displayingPostDescriptors.size
      searchStatus.text = getString(
        R.string.search_results,
        getQuantityString(R.plurals.posts, size, size),
        query
      )
    }
  }

  fun canChildScrollUp(): Boolean {
    if (replyOpen) {
      return true
    }

    if (topAdapterPosition != 0) {
      return true
    }

    val isDragging = fastScroller?.isDragging ?: false
    if (isDragging) {
      // Disable SwipeRefresh layout when dragging the fast scroller
      return true
    }

    val topView = layoutManager?.findViewByPosition(0)
      ?: return true

    if (searchOpen) {
      val searchExtraHeight = findViewById<View>(R.id.search_status).height

      return if (postViewMode == PostViewMode.LIST) {
        topView.top != searchExtraHeight
      } else {
        if (topView is PostStubCell) {
          // PostStubCell does not have grid_card_margin
          topView.top != searchExtraHeight + dp(1f)
        } else {
          topView.top != getDimen(R.dimen.grid_card_margin) + dp(1f) + searchExtraHeight
        }
      }
    }

    when (postViewMode) {
      PostViewMode.LIST -> return topView.top != toolbarHeight()
      PostViewMode.CARD -> return if (topView is PostStubCell) {
        // PostStubCell does not have grid_card_margin
        topView.top != toolbarHeight() + dp(1f)
      } else {
        topView.top != getDimen(R.dimen.grid_card_margin) + dp(1f) + toolbarHeight()
      }
    }
    
    return true
  }

  fun scrolledToBottom(): Boolean {
    return completeBottomAdapterPosition == postAdapter.itemCount - 1
  }

  fun smoothScrollNewPosts(displayPosition: Int) {
    if (layoutManager !is LinearLayoutManager) {
      Logger.wtf(TAG, "Layout manager is grid inside thread??")
      return
    }

    (layoutManager as LinearLayoutManager).scrollToPositionWithOffset(
      // position + 1 for last seen view
      displayPosition + 1,
      SCROLL_OFFSET
    )
  }

  fun cleanup() {
    postAdapter.cleanup()
    replyLayout.cleanup()

    openReply(false)

    if (currentChanDescriptorOrNull() is ThreadDescriptor) {
      openSearch(false)
    }

    prevLastPostNo = 0
    noParty()
  }

  fun getThumbnail(postImage: ChanPostImage?): ThumbnailView? {
    val layoutManager = recyclerView.layoutManager
      ?: return null

    for (i in 0 until layoutManager.childCount) {
      val view = layoutManager.getChildAt(i)

      if (view is PostCellInterface) {
        val postView = view as PostCellInterface
        val post = postView.getPost()

        if (post != null) {
          for (image in post.postImages) {
            if (image.equalUrl(postImage)) {
              return postView.getThumbnailView(postImage!!)
            }
          }
        }
      }
    }

    return null
  }

  fun scrollTo(displayPosition: Int) {
    val scrollPosition = if (displayPosition < 0) {
      postAdapter.itemCount - 1
    } else {
      postAdapter.getScrollPosition(displayPosition)
    }

    recyclerView.post {
      scrollToInternal(scrollPosition)
      onRecyclerViewScrolled()
    }
  }

  private fun scrollToInternal(scrollPosition: Int) {
    if (layoutManager is GridLayoutManager) {
      (layoutManager as GridLayoutManager).scrollToPositionWithOffset(
        scrollPosition,
        SCROLL_OFFSET
      )

      return
    }

    if (layoutManager is StaggeredGridLayoutManager) {
      (layoutManager as StaggeredGridLayoutManager).scrollToPositionWithOffset(
        scrollPosition,
        SCROLL_OFFSET
      )

      return
    }

    if (layoutManager is LinearLayoutManager) {
      (layoutManager as LinearLayoutManager).scrollToPositionWithOffset(
        scrollPosition,
        SCROLL_OFFSET
      )

      return
    }

    recyclerView.scrollToPosition(scrollPosition)
  }

  fun highlightPost(postDescriptor: PostDescriptor?) {
    postAdapter.highlightPost(postDescriptor)
  }

  fun highlightPostId(id: String?) {
    postAdapter.highlightPostId(id)
  }

  fun highlightPostTripcode(tripcode: CharSequence?) {
    postAdapter.highlightPostTripcode(tripcode)
  }

  fun selectPost(post: Long) {
    postAdapter.selectPost(post)
  }

  override fun highlightPostNos(postNos: Set<Long>) {
    postAdapter.highlightPostNos(postNos)
  }

  override fun showThread(threadDescriptor: ThreadDescriptor) {
    serializedCoroutineExecutor.post {
      callback?.showThread(threadDescriptor)
    }
  }

  override fun requestNewPostLoad() {
    callback?.requestNewPostLoad()
  }

  override fun showImageReencodingWindow(fileUuid: UUID, supportsReencode: Boolean) {
    threadListLayoutCallback?.showImageReencodingWindow(fileUuid, supportsReencode)
  }

  private fun canToolbarCollapse(): Boolean {
    return (ChanSettings.getCurrentLayoutMode() != ChanSettings.LayoutMode.SPLIT
      && !ChanSettings.neverHideToolbar.get())
  }

  private fun attachToolbarScroll(attach: Boolean) {
    if (!canToolbarCollapse()) {
      return
    }

    val toolbar = threadListLayoutCallback?.toolbar
      ?: return

    if (attach && !searchOpen && !replyOpen) {
      toolbar.attachRecyclerViewScrollStateListener(recyclerView)
    } else {
      toolbar.detachRecyclerViewScrollStateListener(recyclerView)
      toolbar.collapseShow(true)
    }
  }

  private fun showToolbarIfNeeded() {
    if (canToolbarCollapse()) {
      // Of coming back to focus from a dual controller, like the threadlistcontroller,
      // check if we should show the toolbar again (after the other controller made it hide).
      // It should show if the search or reply is open, or if the thread was scrolled at the
      // top showing an empty space.
      val toolbar = threadListLayoutCallback?.toolbar
        ?: return

      if (searchOpen || replyOpen) {
        // force toolbar to show
        toolbar.collapseShow(true)
      } else {
        // check if it should show if it was scrolled at the top
        toolbar.checkToolbarCollapseState(recyclerView)
      }
    }
  }

  private fun setFastScroll(enabled: Boolean) {
    if (!enabled) {
      if (fastScroller != null) {
        recyclerView.removeItemDecoration(fastScroller!!)
        fastScroller?.onCleanup()
        fastScroller = null
      }

      postInfoMapItemDecoration = null
      recyclerView.isVerticalScrollBarEnabled = true

      return
    }

    val chanDescriptor = currentChanDescriptorOrNull()
    if (chanDescriptor != null) {
      if (chanDescriptor is ThreadDescriptor) {
        val chanThread = chanThreadManager.getChanThread(chanDescriptor)
        if (chanThread != null) {
          if (postInfoMapItemDecoration == null) {
            postInfoMapItemDecoration = PostInfoMapItemDecoration(
              context,
              ChanSettings.getCurrentLayoutMode() == ChanSettings.LayoutMode.SPLIT
            )
          }

          postInfoMapItemDecoration!!.setItems(
            extractPostMapInfoHolderUseCase.execute(chanThread.getPostDescriptors()),
            chanThread.postsCount
          )
        }
      }

      if (fastScroller == null && ChanSettings.enableDraggableScrollbars.get()) {
        val scroller = FastScrollerHelper.create(
          recyclerView,
          postInfoMapItemDecoration,
          themeEngine.chanTheme,
          toolbarPaddingTop()
        )
        scroller.setThumbDragListener(this)

        fastScroller = scroller

        recyclerView.isVerticalScrollBarEnabled = false
      }else{
        fastScroller?.destroyCallbacks()

        fastScroller = null

        recyclerView.isVerticalScrollBarEnabled = true
      }
    }
  }

  override fun onDragStarted() {
    if (!canToolbarCollapse() || replyOpen || searchOpen) {
      return
    }

    val toolbar = threadListLayoutCallback?.toolbar
      ?: return

    toolbar.detachRecyclerViewScrollStateListener(recyclerView)
    toolbar.collapseHide(true)
  }

  override fun onDragEnded() {
    // Fast scroller does not trigger RecyclerView's onScrollStateChanged() so we need to call it
    //  manually after we are down scrolling via Fast scroller.
    onRecyclerViewScrolled()

    if (!canToolbarCollapse() || replyOpen || searchOpen) {
      return
    }

    val toolbar = threadListLayoutCallback?.toolbar
      ?: return

    toolbar.attachRecyclerViewScrollStateListener(recyclerView)
    toolbar.collapseShow(true)
  }

  override fun updateRecyclerViewPaddings() {
    updateRecyclerPaddingsDebouncer.post({ setRecyclerViewPadding() }, 250L)
  }

  override fun measureReplyLayout() {
    replyLayout.measure(
      MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
      MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
    )
  }

  override fun presentController(controller: FloatingListMenuController) {
    BackgroundUtils.ensureMainThread()
    threadListLayoutCallback?.presentController(controller)
  }

  override fun showLoadingView(cancellationFunc: () -> Unit, titleTextId: Int) {
    BackgroundUtils.ensureMainThread()

    val loadingViewController = LoadingViewController(
      context,
      true,
      context.getString(titleTextId)
    ).apply { enableBack(cancellationFunc) }

    threadListLayoutCallback?.presentController(loadingViewController)
  }

  override fun hideLoadingView() {
    BackgroundUtils.ensureMainThread()

    threadListLayoutCallback?.unpresentController { controller -> controller is LoadingViewController }
  }

  private fun setRecyclerViewPadding() {
    val defaultPadding = if (postViewMode == PostViewMode.CARD) {
      dp(1f)
    } else {
      0
    }

    var recyclerTop = defaultPadding + toolbarHeight()
    var recyclerBottom = defaultPadding
    val keyboardOpened = globalWindowInsetsManager.isKeyboardOpened

    // measurements
    if (replyOpen) {
      measureReplyLayout()

      val bottomPadding = if (keyboardOpened) {
        replyLayout.paddingBottom
      } else {
        0
      }

      recyclerBottom += (replyLayout.measuredHeight - replyLayout.paddingTop - bottomPadding)
    } else {
      recyclerBottom += if (ChanSettings.getCurrentLayoutMode() == ChanSettings.LayoutMode.SPLIT) {
        globalWindowInsetsManager.bottom()
      } else {
        globalWindowInsetsManager.bottom() + getDimen(R.dimen.bottom_nav_view_height)
      }
    }

    if (searchOpen) {
      searchStatus.measure(
        MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
        MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
      )

      // search status has built-in padding for the toolbar height
      recyclerTop += searchStatus.measuredHeight
      recyclerTop -= toolbarHeight()
    }

    recyclerView.setPadding(
      defaultPadding,
      recyclerTop,
      defaultPadding,
      recyclerBottom
    )
  }

  fun toolbarHeight(): Int {
    return threadListLayoutCallback!!.toolbar!!.toolbarHeight
  }

  fun toolbarPaddingTop(): Int {
    return threadListLayoutCallback!!.toolbar!!.paddingTop
  }

  private fun party() {
    val chanDescriptor = getCurrentChanDescriptor()
      ?: return

    if (chanDescriptor.siteDescriptor().is4chan()) {
      val calendar = Calendar.getInstance()
      if (calendar[Calendar.MONTH] == Calendar.OCTOBER && calendar[Calendar.DAY_OF_MONTH] == 1) {
        recyclerView.addItemDecoration(PARTY)
      }
    }
  }

  private fun noParty() {
    recyclerView.removeItemDecoration(PARTY)
  }

  fun onPostUpdated(post: ChanPost) {
    BackgroundUtils.ensureMainThread()
    postAdapter.updatePost(post)
  }

  fun isErrorShown(): Boolean {
    BackgroundUtils.ensureMainThread()
    return postAdapter.isErrorShown
  }

  fun onImageOptionsComplete() {
    replyLayout.onImageOptionsComplete()
  }

  interface ThreadListLayoutPresenterCallback {
    suspend fun showThread(threadDescriptor: ThreadDescriptor)
    fun requestNewPostLoad()
    suspend fun onListScrolledToBottom()
  }

  interface ThreadListLayoutCallback {
    val toolbar: Toolbar?
    val chanDescriptor: ChanDescriptor?

    fun hideBottomNavBar(lockTranslation: Boolean, lockCollapse: Boolean)
    fun showBottomNavBar(unlockTranslation: Boolean, unlockCollapse: Boolean)
    fun showToolbar()
    fun replyLayoutOpen(open: Boolean)
    fun showImageReencodingWindow(fileUuid: UUID, supportsReencode: Boolean)
    fun threadBackPressed(): Boolean
    fun threadBackLongPressed()
    fun presentController(controller: Controller)
    fun unpresentController(predicate: (Controller) -> Boolean)
  }

  companion object {
    private const val TAG = "ThreadListLayout"
    private val SCROLL_OFFSET = dp(128f)
  }
}