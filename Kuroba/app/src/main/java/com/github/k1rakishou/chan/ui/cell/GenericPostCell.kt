package com.github.k1rakishou.chan.ui.cell

import android.content.Context
import android.os.SystemClock
import android.view.View
import android.widget.FrameLayout
import com.github.k1rakishou.ChanSettings.PostViewMode
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.ui.view.ThumbnailView
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.model.data.post.ChanPost
import com.github.k1rakishou.model.data.post.ChanPostImage

class GenericPostCell(context: Context) : FrameLayout(context), PostCellInterface {
  private var layoutId: Int? = null

  private val gridModeMargins = context.resources.getDimension(R.dimen.grid_card_margin).toInt()

  init {
    layoutParams = FrameLayout.LayoutParams(
      FrameLayout.LayoutParams.MATCH_PARENT,
      FrameLayout.LayoutParams.WRAP_CONTENT
    )
  }

  fun getMargins(): Int {
    val childPostCell = getChildPostCell()
      ?: return 0

    return when (childPostCell) {
      is PostCell,
      is PostStubCell -> 0
      is CardPostCell -> gridModeMargins
      else -> throw IllegalStateException("Unknown childPostCell: ${childPostCell.javaClass.simpleName}")
    }
  }

  override fun postDataDiffers(postCellData: PostCellData): Boolean {
    throw IllegalStateException("Shouldn't be called")
  }

  override fun setPost(postCellData: PostCellData) {
    val startTime = SystemClock.elapsedRealtime()
    setPostCellInternal(postCellData)
    val deltaTime = SystemClock.elapsedRealtime() - startTime

    if (AppModuleAndroidUtils.isDevBuild()) {
      Logger.d(TAG, "postDescriptor=${postCellData.postDescriptor} bind took ${deltaTime}ms")
    }
  }

  private fun setPostCellInternal(postCellData: PostCellData) {
    val childPostCell = getChildPostCell()

    val postDataDiffers = childPostCell?.postDataDiffers(postCellData)
      ?: true

    if (!postDataDiffers) {
      return
    }

    val newLayoutId = getLayoutId(
      postCellData.stub,
      postCellData.postViewMode,
      postCellData.post
    )

    if (childCount != 1 || newLayoutId != layoutId) {
      removeAllViews()

      val postCellView = when (newLayoutId) {
        R.layout.cell_post_stub -> PostStubCell(context)
        R.layout.cell_post,
        R.layout.cell_post_single_image -> PostCell(context)
        R.layout.cell_post_card -> CardPostCell(context)
        else -> throw IllegalStateException("Unknown layoutId: $newLayoutId")
      }

      addView(
        postCellView,
        LayoutParams(
          LayoutParams.MATCH_PARENT,
          LayoutParams.WRAP_CONTENT
        )
      )

      AppModuleAndroidUtils.inflate(context, newLayoutId, postCellView, true)
      this.layoutId = newLayoutId
    }

    getChildPostCell()!!.setPost(postCellData)
  }

  private fun getLayoutId(
    stub: Boolean,
    postViewMode: PostViewMode,
    post: ChanPost
  ): Int {
    if (stub) {
      return R.layout.cell_post_stub
    }

    when (postViewMode) {
      PostViewMode.LIST -> {
        if (post.postImages.size == 1) {
          return R.layout.cell_post_single_image
        } else {
          return R.layout.cell_post
        }
      }
      PostViewMode.GRID,
      PostViewMode.STAGGER -> {
        return R.layout.cell_post_card
      }
    }
  }

  override fun onPostRecycled(isActuallyRecycling: Boolean) {
    getChildPostCell()?.onPostRecycled(isActuallyRecycling)
  }

  override fun getPost(): ChanPost? {
    return getChildPostCell()?.getPost()
  }

  override fun getThumbnailView(postImage: ChanPostImage): ThumbnailView? {
    return getChildPostCell()?.getThumbnailView(postImage)
  }

  private fun getChildPostCell(): PostCellInterface? {
    if (childCount != 0) {
      return getChildAt(0) as PostCellInterface
    }

    return null
  }

  fun getChildPostCellView(): View? {
    if (childCount != 0) {
      return getChildAt(0)
    }

    return null
  }

  companion object {
    private const val TAG = "GenericPostCell"
  }
}