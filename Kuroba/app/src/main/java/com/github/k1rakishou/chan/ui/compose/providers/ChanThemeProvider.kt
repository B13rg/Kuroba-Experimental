package com.github.k1rakishou.chan.ui.compose.providers

import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import com.github.k1rakishou.chan.utils.appDependencies
import com.github.k1rakishou.core_themes.ChanTheme
import com.github.k1rakishou.core_themes.ThemeEngine

val LocalChanTheme = staticCompositionLocalOf<ChanTheme> { error("Theme not provided") }

@Composable
fun ProvideChanTheme(
  content: @Composable () -> Unit
) {
  val themeEngine = appDependencies().themeEngine

  var chanTheme by remember { mutableStateOf(themeEngine.chanTheme) }

  DisposableEffect(themeEngine.chanTheme) {
    val themeUpdateObserver = object : ThemeEngine.ThemeChangesListener {
      override fun onThemeChanged() {
        chanTheme = themeEngine.chanTheme.copyTheme()
      }
    }

    themeEngine.addListener(themeUpdateObserver)
    onDispose { themeEngine.removeListener(themeUpdateObserver) }
  }

  CompositionLocalProvider(LocalChanTheme provides chanTheme) {
    val originalColors = MaterialTheme.colors

    val updatedColors = remember(key1 = themeEngine.chanTheme) {
      originalColors.copy(
        primary = chanTheme.primaryColorCompose,
        error = chanTheme.errorColorCompose
      )
    }

    val textSelectionColors = remember(key1 = chanTheme.accentColorCompose) {
      TextSelectionColors(
        handleColor = chanTheme.accentColorCompose,
        backgroundColor = chanTheme.accentColorCompose.copy(alpha = 0.4f)
      )
    }

    MaterialTheme(colors = updatedColors) {
      CompositionLocalProvider(LocalTextSelectionColors provides textSelectionColors) {
        content()
      }
    }
  }
}

@Composable
fun OverrideChanTheme(chanTheme: ChanTheme, content: @Composable () -> Unit) {
  CompositionLocalProvider(LocalChanTheme provides chanTheme) {
    content()
  }
}