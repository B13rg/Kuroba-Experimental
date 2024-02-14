package com.github.k1rakishou.chan.features.reply.epoxy

import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout
import androidx.appcompat.widget.AppCompatImageView
import com.airbnb.epoxy.AfterPropsSet
import com.airbnb.epoxy.CallbackProp
import com.airbnb.epoxy.ModelProp
import com.airbnb.epoxy.ModelView
import com.airbnb.epoxy.OnViewRecycled
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils
import com.github.k1rakishou.chan.utils.setVisibilityFast
import com.github.k1rakishou.common.updateMargins
import com.github.k1rakishou.core_themes.ThemeEngine
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import javax.inject.Inject

@ModelView(autoLayout = ModelView.Size.MATCH_WIDTH_WRAP_HEIGHT, fullSpan = true)
class EpoxyAttachNewFileButtonWideView @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null,
  defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr), ThemeEngine.ThemeChangesListener {

  @Inject
  lateinit var themeEngine: ThemeEngine

  private val newAttachableButton: FrameLayout
  private val attachSoundToMedia: AppCompatImageView
  private val attachImageByUrl: AppCompatImageView
  private val imageRemoteSearch: AppCompatImageView

  private var chanDescriptor: ChanDescriptor? = null

  init {
    inflate(context, R.layout.epoxy_attach_new_file_button_wide_view, this)

    AppModuleAndroidUtils.extractActivityComponent(context)
      .inject(this)

    newAttachableButton = findViewById(R.id.reply_new_attachable_button_wide)
    attachSoundToMedia = findViewById(R.id.reply_attach_sound_to_media)
    attachImageByUrl = findViewById(R.id.reply_attach_file_by_url_button)
    imageRemoteSearch = findViewById(R.id.reply_image_remote_search)

    onThemeChanged()
  }

  override fun onAttachedToWindow() {
    super.onAttachedToWindow()

    val margin = context.resources.getDimension(R.dimen.attach_new_file_button_vertical_margin).toInt()
    updateMargins(
      left = margin,
      right = margin,
      start = margin,
      end = margin,
      top = margin,
      bottom = margin
    )

    themeEngine.addListener(this)
  }

  override fun onDetachedFromWindow() {
    super.onDetachedFromWindow()

    themeEngine.removeListener(this)
  }

  override fun onThemeChanged() {
    val tintColor = ThemeEngine.resolveDrawableTintColor(themeEngine.chanTheme.isBackColorDark)

    attachSoundToMedia.setImageDrawable(themeEngine.tintDrawable(attachSoundToMedia.drawable, tintColor))
    attachImageByUrl.setImageDrawable(themeEngine.tintDrawable(attachImageByUrl.drawable, tintColor))
    imageRemoteSearch.setImageDrawable(themeEngine.tintDrawable(imageRemoteSearch.drawable, tintColor))
  }

  @OnViewRecycled
  fun onViewRecycled() {
    this.chanDescriptor = null
  }

  @AfterPropsSet
  fun afterPropsSet() {
    if (chanDescriptor?.siteDescriptor()?.is4chan() == true) {
      attachSoundToMedia.setVisibilityFast(VISIBLE)
    } else {
      attachSoundToMedia.setVisibilityFast(GONE)
    }
  }

  @ModelProp
  fun chanDescriptor(chanDescriptor: ChanDescriptor) {
    this.chanDescriptor = chanDescriptor
  }

  @CallbackProp
  fun setOnClickListener(listener: (() -> Unit)?) {
    if (listener == null) {
      newAttachableButton.setOnClickListener(null)
      return
    }

    newAttachableButton.setOnClickListener {
      listener.invoke()
    }
  }

  @CallbackProp
  fun setOnAttachSoundToMediaClickListener(listener: (() -> Unit)?) {
    if (listener == null) {
      attachSoundToMedia.setOnClickListener(null)
      return
    }

    attachSoundToMedia.setOnClickListener {
      listener.invoke()
    }
  }

  @CallbackProp
  fun setOnAttachImageByUrlClickListener(listener: (() -> Unit)?) {
    if (listener == null) {
      attachImageByUrl.setOnClickListener(null)
      return
    }

    attachImageByUrl.setOnClickListener {
      listener.invoke()
    }
  }

  @CallbackProp
  fun setOnImageRemoteSearchClickListener(listener: (() -> Unit)?) {
    if (listener == null) {
      imageRemoteSearch.setOnClickListener(null)
      return
    }

    imageRemoteSearch.setOnClickListener {
      listener.invoke()
    }
  }

  @CallbackProp
  fun setOnLongClickListener(listener: (() -> Unit)?) {
    if (listener == null) {
      newAttachableButton.setOnLongClickListener(null)
      return
    }

    newAttachableButton.setOnLongClickListener {
      listener.invoke()
      return@setOnLongClickListener true
    }
  }

}