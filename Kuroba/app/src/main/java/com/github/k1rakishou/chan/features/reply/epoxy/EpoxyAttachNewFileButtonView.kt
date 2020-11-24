package com.github.k1rakishou.chan.features.reply.epoxy

import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout
import com.airbnb.epoxy.CallbackProp
import com.airbnb.epoxy.ModelView
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils
import com.github.k1rakishou.core_themes.ThemeEngine
import java.util.*
import javax.inject.Inject

@ModelView(autoLayout = ModelView.Size.MATCH_WIDTH_WRAP_HEIGHT, fullSpan = false)
class EpoxyAttachNewFileButtonView @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null,
  defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr), ThemeEngine.ThemeChangesListener {

  @Inject
  lateinit var themeEngine: ThemeEngine

  private val newAttachableButton: FrameLayout

  init {
    inflate(context, R.layout.epoxy_attach_new_file_button_view, this)

    AppModuleAndroidUtils.extractStartActivityComponent(context)
      .inject(this)

    newAttachableButton = findViewById(R.id.reply_new_attachable_button)
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
    // no-op
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