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
package com.github.k1rakishou.chan.ui.view

import android.animation.AnimatorSet
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.RippleDrawable
import android.util.AttributeSet
import android.util.TypedValue
import android.view.MotionEvent
import android.view.animation.Interpolator
import androidx.appcompat.widget.AppCompatImageView
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import com.github.k1rakishou.ChanSettings
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.base.Debouncer
import com.github.k1rakishou.chan.core.base.KurobaCoroutineScope
import com.github.k1rakishou.chan.core.cache.CacheFileType
import com.github.k1rakishou.chan.core.cache.CacheHandler
import com.github.k1rakishou.chan.core.image.ImageLoaderDeprecated
import com.github.k1rakishou.chan.core.manager.GlobalWindowInsetsManager
import com.github.k1rakishou.chan.ui.globalstate.GlobalUiStateHolder
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils
import com.github.k1rakishou.common.errorMessageOrClassName
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.core_themes.ThemeEngine
import com.github.k1rakishou.model.data.descriptor.PostDescriptor
import dagger.Lazy
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject

open class ThumbnailView : AppCompatImageView, ThemeEngine.ThemeChangesListener {
  private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)
  private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG)
  private val tmpTextRect = Rect()
  private val alphaAnimator = AnimatorSet()
  private val debouncer = Debouncer(false)
  private val kurobaScope: KurobaCoroutineScope = KurobaCoroutineScope()
  private val ioErrorRetryAttempt = AtomicInteger(0)

  private var _error = false
  val error: Boolean
    get() = _error
  private var _thumbnailViewOptions: ThumbnailViewOptions? = null
  val thumbnailViewOptions: ThumbnailViewOptions?
    get() = _thumbnailViewOptions

  private var _imageUrl: String? = null
  val imageUrl: String?
    get() = _imageUrl

  private var postDescriptor: PostDescriptor? = null
  private var imageSize: ImageLoaderDeprecated.ImageSize? = null
  private var cacheFileType: CacheFileType = CacheFileType.PostMediaThumbnail
  private var requestDisposable: ImageLoaderDeprecated.ImageLoaderRequestDisposable? = null
  private var errorText: String? = null
  private var foregroundCalculate = false
  private var imageForeground: Drawable? = null

  @Inject
  lateinit var imageLoaderDeprecated: Lazy<ImageLoaderDeprecated>
  @Inject
  lateinit var themeEngine: ThemeEngine
  @Inject
  lateinit var globalUiStateHolder: GlobalUiStateHolder
  @Inject
  lateinit var cacheHandler: Lazy<CacheHandler>
  @Inject
  lateinit var globalWindowInsetsManager: GlobalWindowInsetsManager

  constructor(context: Context) : super(context) {
    init()
  }

  constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
    init()
  }

  constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int)
    : super(context, attrs, defStyleAttr) {
    init()
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
    backgroundPaint.color = themeEngine.chanTheme.backColorSecondary
    invalidate()
  }

  @SuppressLint("ClickableViewAccessibility")
  private fun init() {
    AppModuleAndroidUtils.extractActivityComponent(context)
      .inject(this)

    textPaint.color = themeEngine.chanTheme.textColorPrimary
    textPaint.textSize = AppModuleAndroidUtils.sp(14f).toFloat()
    imageForeground = initRippleDrawable()

    setOnTouchListener { _, event ->
      if (event.actionMasked == MotionEvent.ACTION_DOWN) {
        drawableHotspotChanged(event.x, event.y)
      }

      return@setOnTouchListener false
    }
  }

  private fun initRippleDrawable(): RippleDrawable {
    val rippleAttrForThemeValue = TypedValue()

    context.theme.resolveAttribute(
      androidx.appcompat.R.attr.colorControlHighlight,
      rippleAttrForThemeValue,
      true
    )

    val newImageForeground = RippleDrawable(
      ColorStateList.valueOf(rippleAttrForThemeValue.data),
      null,
      ColorDrawable(Color.WHITE)
    )

    newImageForeground.callback = this
    if (newImageForeground.isStateful) {
      newImageForeground.state = drawableState
    }

    return newImageForeground
  }

  fun bindImageUrl(
    url: String,
    cacheFileType: CacheFileType,
    postDescriptor: PostDescriptor,
    imageSize: ImageLoaderDeprecated.ImageSize,
    thumbnailViewOptions: ThumbnailViewOptions
  ) {
    scaleType = when (thumbnailViewOptions.postThumbnailScaling) {
      ChanSettings.PostThumbnailScaling.FitCenter -> ScaleType.FIT_CENTER
      ChanSettings.PostThumbnailScaling.CenterCrop -> ScaleType.CENTER_CROP
    }

    onThemeChanged()

    if (url != this._imageUrl) {
      if (this._imageUrl != null) {
        unbindImageUrl()
      }

      ioErrorRetryAttempt.set(0)
      kurobaScope.cancelChildren()
    }

    this._imageUrl = url
    this.postDescriptor = postDescriptor
    this.imageSize = imageSize
    this.cacheFileType = cacheFileType
    this._thumbnailViewOptions = thumbnailViewOptions

    kurobaScope.launch {
      setUrlInternal(
        url = url,
        postDescriptor = postDescriptor,
        imageSize = imageSize,
        thumbnailViewOptions = thumbnailViewOptions
      )
    }
  }

  fun unbindImageUrl() {
    debouncer.clear()

    requestDisposable?.dispose()
    requestDisposable = null
    kurobaScope.cancelChildren()

    _thumbnailViewOptions = null

    cleanupImage()
  }

  private fun cleanupImage() {
    _error = false
    alphaAnimator.end()

    _imageUrl = null
    postDescriptor = null
    imageSize = null

    setImageBitmap(null)
    invalidate()
  }

  override fun onSetAlpha(alpha: Int): Boolean {
    if (_error) {
      textPaint.alpha = alpha
    } else {
      drawable?.alpha = alpha
    }

    invalidate()
    return true
  }

  override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
    super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    foregroundCalculate = true
  }

  fun onThumbnailViewClicked(listener: OnClickListener) {
    listener.onClick(this)
  }

  fun onThumbnailViewLongClicked(listener: OnLongClickListener): Boolean {
    return listener.onLongClick(this)
  }

  override fun onDraw(canvas: Canvas) {
    if (_thumbnailViewOptions?.drawThumbnailBackground == true) {
      canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), backgroundPaint)
    }

    if (alpha == 0f) {
      return
    }

    val width = width - paddingLeft - paddingRight
    val height = height - paddingTop - paddingBottom

    if (_error) {
      canvas.save()
      textPaint.getTextBounds(errorText, 0, errorText!!.length, tmpTextRect)
      val x = width / 2f - tmpTextRect.exactCenterX()
      val y = height / 2f - tmpTextRect.exactCenterY()
      canvas.drawText(errorText!!, x + paddingLeft, y + paddingTop, textPaint)
      canvas.restore()
    } else {
      super.onDraw(canvas)
    }

    if (_thumbnailViewOptions?.drawRipple == true && imageForeground != null) {
      if (foregroundCalculate) {
        foregroundCalculate = false
        imageForeground!!.setBounds(0, 0, right, bottom)
      }

      imageForeground!!.draw(canvas)
    }
  }

  override fun verifyDrawable(who: Drawable): Boolean {
    return super.verifyDrawable(who) || who === imageForeground
  }

  override fun jumpDrawablesToCurrentState() {
    super.jumpDrawablesToCurrentState()

    imageForeground?.jumpToCurrentState()
  }

  override fun drawableStateChanged() {
    super.drawableStateChanged()

    if (imageForeground?.isStateful == true) {
      imageForeground?.state = drawableState
    }
  }

  override fun drawableHotspotChanged(x: Float, y: Float) {
    super.drawableHotspotChanged(x, y)
    imageForeground?.setHotspot(x, y)
  }

  private suspend fun setUrlInternal(
    url: String,
    postDescriptor: PostDescriptor,
    imageSize: ImageLoaderDeprecated.ImageSize,
    thumbnailViewOptions: ThumbnailViewOptions
  ) {
    val currentAttempt = ioErrorRetryAttempt.get()
    Logger.verbose(TAG) { "setUrlInternal(${url}) attempt: ${currentAttempt}" }

    if (currentAttempt > 0) {
      delay(currentAttempt * 2000L)
    }

    val listener = object : ImageLoaderDeprecated.FailureAwareImageListener {
      override fun onResponse(drawable: BitmapDrawable, isImmediate: Boolean) {
        if (url != this@ThumbnailView._imageUrl) {
          // Request was canceled (probably because the parent view was unbound) so we don't
          // want to do anything here
          return
        }

        this@ThumbnailView._error = false
        this@ThumbnailView.errorText = null

        setImageBitmap(drawable.bitmap)
        onImageSet(isImmediate)
        invalidate()

        Logger.verbose(TAG) { "setUrlInternal(${url}) attempt: ${currentAttempt}, success" }
      }

      override fun onNotFound() {
        if (url != this@ThumbnailView._imageUrl) {
          // Request was canceled (probably because the parent view was unbound) so we don't
          // want to do anything here
          return
        }

        this@ThumbnailView._error = true
        this@ThumbnailView.errorText = AppModuleAndroidUtils.getString(R.string.thumbnail_load_failed_404)

        onImageSet(false)
        invalidate()

        Logger.verbose(TAG) { "setUrlInternal(${url}) attempt: ${currentAttempt}, error (not found)" }
      }

      override fun onResponseError(error: Throwable) {
        if (url != this@ThumbnailView._imageUrl) {
          // Request was canceled (probably because the parent view was unbound) so we don't
          // want to do anything here
          return
        }

        val isIoError = error is IOException
        val isScopeActive = kurobaScope.isActive

        Logger.verbose(TAG) {
          "setUrlInternal(${url}) attempt: ${currentAttempt}, isIoError: $isIoError, isScopeActive: $isScopeActive"
        }

        if (isIoError && ioErrorRetryAttempt.getAndIncrement() < MAX_RELOAD_ATTEMPTS && isScopeActive) {
          kurobaScope.launch {
            setUrlInternal(
              url = url,
              postDescriptor = postDescriptor,
              imageSize = imageSize,
              thumbnailViewOptions = thumbnailViewOptions
            )
          }

          return
        }

        Logger.e(TAG, "setUrlInternal(${url}) attempt: ${currentAttempt}, error (${error.errorMessageOrClassName()})")

        this@ThumbnailView._error = true
        this@ThumbnailView.errorText = AppModuleAndroidUtils.getString(R.string.thumbnail_load_failed_network)

        onImageSet(false)
        invalidate()
      }
    }

    fun loadImage() {
      if (url != this._imageUrl) {
        requestDisposable?.dispose()
        requestDisposable = null
      }

      requestDisposable = imageLoaderDeprecated.get().loadFromNetwork(
        context = context,
        cacheFileType = cacheFileType,
        requestUrl = url,
        imageSize = imageSize,
        transformations = emptyList(),
        listener = listener,
        postDescriptor = postDescriptor
      )
    }

    val isCached = imageLoaderDeprecated.get().isImageCachedLocally(
      cacheFileType = cacheFileType,
      url = url
    )

    val fastScroller = globalUiStateHolder.fastScroller

    if (!fastScroller.isDraggingFastScroller() && isCached) {
      loadImage()
    } else {
      debouncer.post(IMAGE_REQUEST_DEBOUNCER_TIMEOUT_MS) { loadImage() }
    }
  }

  private fun onImageSet(isImmediate: Boolean) {
    if (!isImmediate) {
      alpha = 0f

      val alphaAnimation = ValueAnimator.ofFloat(0f, 1f)
      alphaAnimation.duration = 300
      alphaAnimation.interpolator = INTERPOLATOR
      alphaAnimation.addUpdateListener { animation: ValueAnimator ->
        val alpha = animation.animatedValue as Float
        setAlpha(alpha)
      }

      alphaAnimator.play(alphaAnimation)
      alphaAnimator.start()
    } else {
      alphaAnimator.end()
      alpha = 1f
    }
  }

  data class ThumbnailViewOptions(
    val postThumbnailScaling: ChanSettings.PostThumbnailScaling = ChanSettings.postThumbnailScaling.get(),
    val drawThumbnailBackground: Boolean = ChanSettings.drawPostThumbnailBackground.get(),
    val drawRipple: Boolean = true,
    val revealSpoilerImage: Boolean = false
  )

  companion object {
    private const val TAG = "ThumbnailView"
    private const val IMAGE_REQUEST_DEBOUNCER_TIMEOUT_MS = 250L
    private const val MAX_RELOAD_ATTEMPTS = 5

    private val INTERPOLATOR: Interpolator = FastOutSlowInInterpolator()
  }
}