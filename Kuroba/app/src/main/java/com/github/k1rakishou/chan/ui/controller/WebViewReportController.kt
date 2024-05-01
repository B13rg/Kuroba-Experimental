package com.github.k1rakishou.chan.ui.controller

import android.annotation.SuppressLint
import android.content.Context
import android.util.AndroidRuntimeException
import android.webkit.WebView
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.di.component.activity.ActivityComponent
import com.github.k1rakishou.chan.core.manager.GlobalWindowInsetsManager
import com.github.k1rakishou.chan.core.manager.WindowInsetsListener
import com.github.k1rakishou.chan.core.site.Site
import com.github.k1rakishou.chan.features.toolbar.BackArrowMenuItem
import com.github.k1rakishou.chan.features.toolbar.ToolbarMiddleContent
import com.github.k1rakishou.chan.features.toolbar.ToolbarText
import com.github.k1rakishou.chan.ui.controller.base.Controller
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils
import com.github.k1rakishou.common.updatePaddings
import com.github.k1rakishou.model.data.post.ChanPost
import com.github.k1rakishou.model.util.ChanPostUtils.getTitle
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

class WebViewReportController(
  context: Context,
  private val post: ChanPost,
  private val site: Site
) : Controller(context), WindowInsetsListener {

  @Inject
  lateinit var globalWindowInsetsManager: GlobalWindowInsetsManager

  private lateinit var frameLayout: FrameLayout

  override fun injectActivityDependencies(component: ActivityComponent) {
    component.inject(this)
  }

  @SuppressLint("SetJavaScriptEnabled")
  override fun onCreate() {
    super.onCreate()

    toolbarState.enterDefaultMode(
      leftItem = BackArrowMenuItem(
        onClick = { requireNavController().popController() }
      ),
      middleContent = ToolbarMiddleContent.Title(
        title = ToolbarText.String(appResources.string(R.string.report_screen, getTitle(post, null)))
      )
    )

    initUi()

    controllerScope.launch {
      combine(
        globalUiStateHolder.toolbar.toolbarHeight,
        globalUiStateHolder.bottomPanel.bottomPanelHeight
      ) { t1, t2 -> t1 to t2 }
        .onEach { onInsetsChanged() }
        .collect()
    }

    onInsetsChanged()
    globalWindowInsetsManager.addInsetsUpdatesListener(this)
  }

  override fun onDestroy() {
    super.onDestroy()

    globalWindowInsetsManager.removeInsetsUpdatesListener(this)
  }

  override fun onInsetsChanged() {
    val bottomPadding = with(appResources.composeDensity) {
      maxOf(
        globalWindowInsetsManager.bottom(),
        globalUiStateHolder.bottomPanel.bottomPanelHeight.value.roundToPx()
      )
    }

    val topPadding = with(appResources.composeDensity) {
      maxOf(
        globalWindowInsetsManager.top(),
        globalUiStateHolder.toolbar.toolbarHeight.value.roundToPx()
      )
    }

    frameLayout.updatePaddings(
      top = topPadding,
      bottom = bottomPadding
    )
  }

  private fun initUi() {
    val url = site.endpoints().report(post)

    frameLayout = FrameLayout(context)
    frameLayout.setLayoutParams(
      LinearLayout.LayoutParams(
        FrameLayout.LayoutParams.MATCH_PARENT,
        FrameLayout.LayoutParams.MATCH_PARENT
      )
    )

    try {
      val webView = WebView(context)
      val siteRequestModifier = site.requestModifier()

      if (siteRequestModifier != null) {
        siteRequestModifier.modifyWebView(webView)
      }

      val settings = webView.getSettings()
      settings.javaScriptEnabled = true
      settings.domStorageEnabled = true
      webView.loadUrl(url.toString())

      frameLayout.addView(
        webView,
        FrameLayout.LayoutParams(
          FrameLayout.LayoutParams.MATCH_PARENT,
          FrameLayout.LayoutParams.MATCH_PARENT
        )
      )

      view = frameLayout
      onInsetsChanged()
    } catch (error: Throwable) {
      var errmsg = ""

      if (
        error is AndroidRuntimeException &&
        error.message != null &&
        error.message?.contains("MissingWebViewPackageException") == true
      ) {
        errmsg = appResources.string(R.string.fail_reason_webview_is_not_installed)
      } else {
        errmsg = appResources.string(
          R.string.fail_reason_some_part_of_webview_not_initialized,
          error.message ?: "Unknown error"
        )
      }

      view = AppModuleAndroidUtils.inflate(context, R.layout.layout_webview_error)
      view.findViewById<TextView>(R.id.text).text = errmsg
    }
  }

}
