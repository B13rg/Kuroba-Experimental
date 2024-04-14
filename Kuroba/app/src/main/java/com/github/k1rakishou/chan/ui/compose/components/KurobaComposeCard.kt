package com.github.k1rakishou.chan.ui.compose.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Card
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.github.k1rakishou.chan.ui.compose.providers.LocalChanTheme

@Composable
fun KurobaComposeCard(
  modifier: Modifier = Modifier,
  backgroundColor: Color? = null,
  shape: Shape = remember { RoundedCornerShape(2.dp) },
  elevation: Dp = 1.dp,
  content: @Composable () -> Unit
) {
  val chanTheme = LocalChanTheme.current

  Card(
    modifier = modifier,
    shape = shape,
    backgroundColor = backgroundColor ?: chanTheme.backColorCompose,
    elevation = elevation
  ) {
    Box {
      content()
    }
  }
}

@Composable
fun KurobaComposeDraggableCard(modifier: Modifier, isDragging: Boolean, content: @Composable () -> Unit) {
  val chanTheme = LocalChanTheme.current
  val density = LocalDensity.current

  val backgroundColor = animateColorAsState(
    targetValue = if (isDragging) {
      chanTheme.selectedOnBackColor
    } else {
      chanTheme.backColorSecondaryCompose
    }
  )

  val cornerRadius = with(density) { remember { CornerRadius(4.dp.toPx(), 4.dp.toPx()) } }

  Box(
    modifier = modifier
      .then(
        Modifier.drawBehind {
          drawRoundRect(
            cornerRadius = cornerRadius,
            color = chanTheme.backColorSecondaryCompose
          )

          if (chanTheme.backColorCompose != backgroundColor.value) {
            drawRoundRect(
              cornerRadius = cornerRadius,
              color = backgroundColor.value.copy(alpha = 0.5f)
            )
          }
        }
      )
  ) {
    content()
  }
}