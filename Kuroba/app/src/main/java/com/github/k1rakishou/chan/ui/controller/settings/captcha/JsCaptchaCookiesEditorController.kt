package com.github.k1rakishou.chan.ui.controller.settings.captcha

import android.content.Context
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.di.component.activity.ActivityComponent
import com.github.k1rakishou.chan.features.toolbar.BackArrowMenuItem
import com.github.k1rakishou.chan.features.toolbar.ToolbarMiddleContent
import com.github.k1rakishou.chan.features.toolbar.ToolbarText
import com.github.k1rakishou.chan.ui.controller.base.Controller

class JsCaptchaCookiesEditorController(context: Context) :
  Controller(context), JsCaptchaCookiesEditorLayout.JsCaptchaCookiesEditorControllerCallbacks {

  override fun injectDependencies(component: ActivityComponent) {
    component.inject(this)
  }

  override fun onCreate() {
    super.onCreate()

    toolbarState.enterDefaultMode(
      leftItem = BackArrowMenuItem(
        onClick = { requireNavController().popController() }
      ),
      middleContent = ToolbarMiddleContent.Title(
        title = ToolbarText.Id(R.string.js_captcha_cookies_editor_controller_title)
      )
    )

    view = JsCaptchaCookiesEditorLayout(context).apply {
      onReady(this@JsCaptchaCookiesEditorController)
    }
  }

  override fun onDestroy() {
    super.onDestroy()

    (view as? JsCaptchaCookiesEditorLayout)?.destroy()
  }

  override fun onFinished() {
    requireNavController().popController()
  }

}