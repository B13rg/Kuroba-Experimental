package com.github.k1rakishou.chan.ui.globalstate.global

import android.view.MotionEvent
import androidx.compose.ui.geometry.Offset
import com.github.k1rakishou.chan.ui.compose.window.WindowSizeClass
import com.github.k1rakishou.core_logger.Logger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

interface IMainUiState {
  interface Readable {
    val touchPositionFlow: StateFlow<Offset>
    val windowSizeClass: StateFlow<WindowSizeClass?>
  }

  interface Writeable {
    fun updateTouchPosition(touchPosition: Offset, eventAction: Int?)
    fun updateWindowSizeClass(windowSizeClass: WindowSizeClass)
  }
}

internal class MainUiState : IMainUiState.Readable, IMainUiState.Writeable {
  private val _touchPosition = MutableStateFlow<Offset>(Offset.Zero)
  override val touchPositionFlow: StateFlow<Offset>
    get() = _touchPosition.asStateFlow()

  private val _windowSizeClass = MutableStateFlow<WindowSizeClass?>(null)
  override val windowSizeClass: StateFlow<WindowSizeClass?>
    get() = _windowSizeClass.asStateFlow()

  override fun updateTouchPosition(touchPosition: Offset, eventAction: Int?) {
    when (eventAction) {
      MotionEvent.ACTION_DOWN -> Logger.verbose(TAG) { "updateTouchPosition() ACTION_DOWN at ${touchPosition}" }
      MotionEvent.ACTION_UP -> Logger.verbose(TAG) { "updateTouchPosition() ACTION_UP at ${touchPosition}" }
      MotionEvent.ACTION_CANCEL -> Logger.verbose(TAG) { "updateTouchPosition() ACTION_CANCEL at ${touchPosition}" }
      else -> {
        // no-op
      }
    }

    _touchPosition.value = touchPosition
  }

  override fun updateWindowSizeClass(windowSizeClass: WindowSizeClass) {
    _windowSizeClass.value = windowSizeClass
  }

  companion object {
    private const val TAG = "MainUiState"
  }

}