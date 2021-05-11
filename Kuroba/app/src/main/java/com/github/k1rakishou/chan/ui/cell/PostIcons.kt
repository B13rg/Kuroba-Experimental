package com.github.k1rakishou.chan.ui.cell

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.View
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.image.ImageLoaderV2
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.sp
import com.github.k1rakishou.chan.utils.MediaUtils
import com.github.k1rakishou.core_themes.ChanTheme
import com.github.k1rakishou.model.data.post.ChanPostHttpIcon
import java.util.*

class PostIcons @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null,
  defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {
  private var iconsSize = sp(14f)
  private var spacing = 0
  private var icons = 0
  private var previousIcons = 0
  private val drawRect = RectF()
  private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)
  private val textRect = Rect()
  private var httpIconTextColor = 0
  private var httpIconTextSize = 0
  private var rtl = false
  private var httpIcons = mutableListOf<PostIconsHttpIcon>()

  init {
    textPaint.typeface = Typeface.create(null as String?, Typeface.ITALIC)
    visibility = GONE

    if (isInEditMode) {
      edit()
      set(DELETED, true)
      set(STICKY, true)
      set(CLOSED, true)
      set(ARCHIVED, true)
      apply()
    }
  }

  fun setSpacing(spacing: Int) {
    this.spacing = spacing
  }

  fun rtl(isRtl: Boolean) {
    this.rtl = isRtl
    invalidate()
  }

  fun edit() {
    previousIcons = icons
    httpIcons.clear()
  }

  fun apply() {
    if (previousIcons == icons) {
      return
    }

    // Require a layout only if the height changed
    if (previousIcons == 0 || icons == 0) {
      visibility = if (icons == 0) {
        GONE
      } else {
        VISIBLE
      }

      requestLayout()
    }

    invalidate()
  }

  fun setHttpIcons(
    imageLoaderV2: ImageLoaderV2,
    icons: List<ChanPostHttpIcon>,
    theme: ChanTheme,
    size: Int
  ) {
    httpIconTextColor = theme.postDetailsColor
    httpIconTextSize = size
    httpIcons = ArrayList(icons.size)

    for (icon in icons) {
      // this is for country codes
      val codeIndex = icon.iconName.indexOf('/')
      val name = icon.iconName.substring(0, if (codeIndex != -1) codeIndex else icon.iconName.length)

      val postIconsHttpIcon = PostIconsHttpIcon(
        context,
        this,
        imageLoaderV2,
        name,
        icon.iconUrl
      )

      httpIcons.add(postIconsHttpIcon)
      postIconsHttpIcon.request()
    }
  }

  fun cancelRequests() {
    if (httpIcons.isEmpty()) {
      return
    }

    for (httpIcon in httpIcons) {
      httpIcon.cancel()
    }

    httpIcons.clear()
  }

  operator fun set(icon: Int, enable: Boolean) {
    icons = if (enable) {
      icons or icon
    } else {
      icons and icon.inv()
    }
  }

  operator fun get(icon: Int): Boolean {
    return icons and icon == icon
  }

  override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
    val measureHeight = if (icons == 0) {
      0
    } else {
      iconsSize + paddingTop + paddingBottom
    }

    setMeasuredDimension(
      widthMeasureSpec,
      MeasureSpec.makeMeasureSpec(measureHeight, MeasureSpec.EXACTLY)
    )
  }

  override fun onDraw(canvas: Canvas) {
    if (icons == 0 || width == 0) {
      return
    }

    drawIcons(canvas)
  }

  private fun drawIcons(canvas: Canvas) {
    canvas.save()

    if (rtl) {
      canvas.translate(width - paddingLeft.toFloat() - getIconsTotalWidth(), paddingTop.toFloat())
    } else {
      canvas.translate(paddingLeft.toFloat(), paddingTop.toFloat())
    }

    var offset = 0
    if (get(STICKY)) {
      offset += drawBitmapDrawable(canvas, stickyIcon, offset)
    }

    if (get(CLOSED)) {
      offset += drawBitmapDrawable(canvas, closedIcon, offset)
    }

    if (get(DELETED)) {
      offset += drawBitmapDrawable(canvas, trashIcon, offset)
    }

    if (get(ARCHIVED)) {
      offset += drawBitmapDrawable(canvas, archivedIcon, offset)
    }

    if (get(HTTP_ICONS) && httpIcons.isNotEmpty()) {
      for (httpIcon in httpIcons) {
        if (httpIcon.drawable == null) {
          continue
        }

        offset += drawDrawable(canvas, httpIcon.drawable!!, offset)

        textPaint.color = httpIconTextColor
        textPaint.textSize = httpIconTextSize.toFloat()
        textPaint.getTextBounds(httpIcon.name, 0, httpIcon.name.length, textRect)

        val y = iconsSize / 2f - textRect.exactCenterY()
        canvas.drawText(httpIcon.name, offset.toFloat(), y, textPaint)
        offset += textRect.width() + spacing
      }
    }

    canvas.restore()
  }

  private fun getIconsTotalWidth(): Int {
    var totalWidth = 0

    if (get(STICKY)) {
      totalWidth += iconsSize + spacing
    }

    if (get(CLOSED)) {
      totalWidth += iconsSize + spacing
    }

    if (get(DELETED)) {
      totalWidth += iconsSize + spacing
    }

    if (get(ARCHIVED)) {
      totalWidth += iconsSize + spacing
    }

    if (get(HTTP_ICONS) && httpIcons.isNotEmpty()) {
      for (httpIcon in httpIcons) {
        if (httpIcon.drawable == null) {
          continue
        }

        totalWidth = iconsSize + spacing
      }
    }

    return totalWidth
  }

  private fun drawBitmapDrawable(canvas: Canvas, bitmapDrawable: BitmapDrawable, offset: Int): Int {
    val bitmap = bitmapDrawable.bitmap

    drawRect[offset.toFloat(), 0f, offset + iconsSize.toFloat()] = iconsSize.toFloat()
    canvas.drawBitmap(bitmap, null, drawRect, null)
    return iconsSize + spacing
  }

  private fun drawDrawable(canvas: Canvas, drawable: Drawable, offset: Int): Int {
    drawable.setBounds(offset, 0, offset + iconsSize, iconsSize)
    drawable.draw(canvas)
    return iconsSize + spacing
  }

  companion object {
    const val STICKY = 0x1
    const val CLOSED = 0x2
    const val DELETED = 0x4
    const val ARCHIVED = 0x8
    const val HTTP_ICONS = 0x10

    private val stickyIcon = MediaUtils.bitmapToDrawable(
      BitmapFactory.decodeResource(AppModuleAndroidUtils.getRes(), R.drawable.sticky_icon)
    )
    private val closedIcon = MediaUtils.bitmapToDrawable(
      BitmapFactory.decodeResource(AppModuleAndroidUtils.getRes(), R.drawable.closed_icon)
    )
    private val trashIcon = MediaUtils.bitmapToDrawable(
      BitmapFactory.decodeResource(AppModuleAndroidUtils.getRes(), R.drawable.trash_icon)
    )
    private val archivedIcon = MediaUtils.bitmapToDrawable(
      BitmapFactory.decodeResource(AppModuleAndroidUtils.getRes(), R.drawable.archived_icon)
    )
  }

}