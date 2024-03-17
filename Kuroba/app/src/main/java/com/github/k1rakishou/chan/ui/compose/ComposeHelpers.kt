package com.github.k1rakishou.chan.ui.compose

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.isDevBuild
import com.github.k1rakishou.core_logger.Logger

private const val TAG = "ComposeHelpers"
const val enableDebugCompositionLogs = true

class DebugRef(var value: Int)

@Composable
inline fun LogCompositions(tag: String) {
  if (enableDebugCompositionLogs && isDevBuild()) {
    val ref = remember { DebugRef(0) }
    SideEffect { ref.value++ }

    Logger.d("Compositions", "${tag} Count: ${ref.value}, ref=${ref.hashCode()}")
  }
}

@OptIn(ExperimentalFoundationApi::class)
fun Modifier.consumeClicks(enabled: Boolean = true): Modifier {
  if (!enabled) {
    return this
  }

  return combinedClickable(
    interactionSource = null,
    indication = null,
    enabled = true,
    onClick = {}
  )
}

@OptIn(ExperimentalComposeUiApi::class)
fun Modifier.passClicksThrough(passClicks: Boolean = true): Modifier {
  if (!passClicks) {
    return this
  }

  return pointerInteropFilter(onTouchEvent = { false })
}


fun PaddingValues.update(
  layoutDirection: LayoutDirection,
  start: Dp = calculateStartPadding(layoutDirection),
  top: Dp = calculateTopPadding(),
  end: Dp = calculateEndPadding(layoutDirection),
  bottom: Dp = calculateBottomPadding(),
): PaddingValues {
  return PaddingValues(
    start = start,
    top = top,
    end = end,
    bottom = bottom
  )
}

fun FocusRequester.requestFocusSafe() {
  try {
    // Sometimes crashes
    requestFocus()
  } catch (ignored: Throwable) {
    // no-op
  }
}

fun FocusRequester.freeFocusSafe() {
  try {
    // Sometimes crashes
    freeFocus()
  } catch (ignored: Throwable) {
    // no-op
  }
}

fun FocusManager.clearFocusSafe(force: Boolean = false) {
  try {
    // Sometimes crashes
    clearFocus(force)
  } catch (ignored: Throwable) {
    // no-op
  }
}

fun List<Measurable>.ensureSingleMeasurable(): Measurable {
  if (size != 1) {
    error(
      "Expected subcompose() to have only return a single measurable but got ${size} instead. " +
              "Most likely you are trying to emit multiple composables inside of the content() lambda. " +
              "Wrap those composables into any container (Box/Column/Row/etc.) and this crash should go away."
    )
  }

  return first()
}