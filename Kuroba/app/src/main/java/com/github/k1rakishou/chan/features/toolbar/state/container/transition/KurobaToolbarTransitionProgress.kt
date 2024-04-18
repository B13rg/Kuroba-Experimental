package com.github.k1rakishou.chan.features.toolbar.state.container.transition

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import com.github.k1rakishou.chan.features.toolbar.KurobaToolbarTransition
import com.github.k1rakishou.chan.features.toolbar.state.KurobaToolbarSubState
import com.github.k1rakishou.chan.ui.compose.consumeClicks
import com.github.k1rakishou.chan.ui.controller.base.transition.TransitionMode


@Composable
internal fun KurobaToolbarTransitionProgress(
  toolbarHeight: Dp,
  transitionToolbarState: KurobaToolbarTransition?,
  topToolbarState: KurobaToolbarSubState?,
  topToolbarContent: @Composable (KurobaToolbarSubState?) -> Unit,
  transitionToolbarContent: @Composable (KurobaToolbarSubState?) -> Unit
) {
  transitionToolbarState as KurobaToolbarTransition.Progress?

  if (transitionToolbarState == null || transitionToolbarState.progress < 0f) {
    topToolbarContent(topToolbarState)
  } else {
    ToolbarTransitionContainer(
      toolbarHeight = toolbarHeight,
      transitionMode = transitionToolbarState.transitionMode,
      transitionProgress = transitionToolbarState.progress,
      transitionToolbarState = transitionToolbarState.transitionToolbarState,
      topToolbarState = topToolbarState,
      topToolbarContent = topToolbarContent,
      transitionToolbarContent = transitionToolbarContent
    )
  }
}

@Composable
private fun ToolbarTransitionContainer(
  toolbarHeight: Dp,
  transitionMode: TransitionMode,
  transitionProgress: Float,
  transitionToolbarState: KurobaToolbarSubState?,
  topToolbarState: KurobaToolbarSubState?,
  topToolbarContent: @Composable (KurobaToolbarSubState?) -> Unit,
  transitionToolbarContent: @Composable (KurobaToolbarSubState?) -> Unit
) {
  val toolbarHeightPx = with(LocalDensity.current) { toolbarHeight.toPx() }

  Box(
    modifier = Modifier
      .fillMaxSize()
      .consumeClicks(enabled = true)
  ) {
    Box(
      modifier = Modifier
        .fillMaxSize()
        .graphicsLayer {
          animateTransitionProgress(
            transitionMode = transitionMode,
            progress = transitionProgress,
            toolbarHeightPx = toolbarHeightPx,
            isOldToolbar = true
          )
        }
    ) {
      topToolbarContent(topToolbarState)
    }

    Box(
      modifier = Modifier
        .fillMaxSize()
        .graphicsLayer {
          animateTransitionProgress(
            transitionMode = transitionMode,
            progress = transitionProgress,
            toolbarHeightPx = toolbarHeightPx,
            isOldToolbar = false
          )
        }
    ) {
      transitionToolbarContent(transitionToolbarState)
    }
  }
}