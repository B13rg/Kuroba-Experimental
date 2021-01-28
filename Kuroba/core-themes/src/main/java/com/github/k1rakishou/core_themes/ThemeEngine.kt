package com.github.k1rakishou.core_themes

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.view.View
import android.view.ViewGroup
import androidx.annotation.AttrRes
import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import androidx.annotation.FloatRange
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.drawable.DrawableCompat
import com.github.k1rakishou.ChanSettings
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.fsaf.file.ExternalFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.min
import kotlin.math.roundToInt

open class ThemeEngine(
  private val appScope: CoroutineScope,
  private val themeParser: ThemeParser
) {
  private val listeners = hashSetOf<ThemeChangesListener>()
  private val attributeCache = AttributeCache()

  private var rootView: View? = null

  lateinit var defaultDarkTheme: ChanTheme
  lateinit var defaultLightTheme: ChanTheme

  private var actualDarkTheme: ChanTheme? = null
  private var actualLightTheme: ChanTheme? = null

  private var autoThemeSwitcherJob: Job? = null

  open lateinit var chanTheme: ChanTheme

  fun initialize(context: Context) {
    defaultDarkTheme = DefaultDarkTheme(context)
    defaultLightTheme = DefaultLightTheme(context)

    actualDarkTheme = themeParser.readThemeFromDisk(defaultDarkTheme)
    actualLightTheme = themeParser.readThemeFromDisk(defaultLightTheme)

    val nightModeFlag = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
    if (nightModeFlag == Configuration.UI_MODE_NIGHT_UNDEFINED || ChanSettings.ignoreDarkNightMode.get()) {
      chanTheme = if (ChanSettings.isCurrentThemeDark.get()) {
        darkTheme()
      } else {
        lightTheme()
      }

      return
    }

    chanTheme = when (nightModeFlag) {
      Configuration.UI_MODE_NIGHT_NO -> {
        ChanSettings.isCurrentThemeDark.set(false)
        lightTheme()
      }
      Configuration.UI_MODE_NIGHT_YES -> {
        ChanSettings.isCurrentThemeDark.set(true)
        darkTheme()
      }
      else -> defaultDarkTheme
    }
  }

  fun lightTheme(): ChanTheme = actualLightTheme ?: defaultLightTheme
  fun darkTheme(): ChanTheme = actualDarkTheme ?: defaultDarkTheme

  fun setRootView(view: View) {
    this.rootView = view.rootView
  }

  fun removeRootView() {
    this.rootView = null
  }

  fun addListener(listener: ThemeChangesListener) {
    listeners += listener
  }

  fun removeListener(listener: ThemeChangesListener) {
    listeners -= listener
  }

  fun toggleTheme() {
    val isNextThemeDark = !ChanSettings.isCurrentThemeDark.get()
    ChanSettings.isCurrentThemeDark.setSync(isNextThemeDark)

    chanTheme = getThemeInternal(isNextThemeDark)
    refreshViews()
  }

  fun switchTheme(switchToDarkTheme: Boolean) {
    if (chanTheme.isDarkTheme == switchToDarkTheme) {
      return
    }

    ChanSettings.isCurrentThemeDark.set(switchToDarkTheme)

    chanTheme = getThemeInternal(switchToDarkTheme)
    refreshViews()
  }

  fun refreshViews() {
    if (rootView == null) {
      return
    }

    updateViews(rootView!!)
    listeners.forEach { listener -> listener.onThemeChanged() }
  }

  private fun updateViews(view: View) {
    if (view is IColorizableWidget) {
      (view as IColorizableWidget).applyColors()
    }

    if (view is ViewGroup) {
      for (i in 0 until view.childCount) {
        updateViews(view.getChildAt(i))
      }
    }
  }

  fun preloadAttributeResource(context: Context, @AttrRes attrId: Int) {
    attributeCache.preloadAttribute(context, attrId)
  }

  fun getAttributeResource(@AttrRes attrId: Int): Int {
    return attributeCache.getAttribute(attrId)
  }

  fun getDrawableTinted(context: Context, @DrawableRes drawableId: Int, isCurrentColorDark: Boolean): Drawable {
    val drawable = ContextCompat.getDrawable(context, drawableId)?.mutate()
      ?: throw IllegalStateException("Couldn't find drawable ${drawableId}")

    DrawableCompat.setTint(drawable, resolveTintColor(isCurrentColorDark))
    return drawable
  }

  fun tintDrawable(drawable: Drawable, isCurrentColorDark: Boolean): Drawable {
    val drawableMutable = DrawableCompat.wrap(drawable).mutate()
    DrawableCompat.setTint(drawableMutable, resolveTintColor(isCurrentColorDark))

    return drawableMutable
  }

  fun tintDrawable(drawable: Drawable, @ColorInt color: Int): Drawable {
    val drawableMutable = DrawableCompat.wrap(drawable).mutate()
    DrawableCompat.setTint(drawableMutable, color)

    return drawableMutable
  }

  fun resolveTintColor(isCurrentColorDark: Boolean): Int {
    return if (isCurrentColorDark) {
      LIGHT_DRAWABLE_TINT
    } else {
      DARK_DRAWABLE_TINT
    }
  }

  fun tintDrawable(context: Context, @DrawableRes drawableId: Int): Drawable {
    val drawable = ContextCompat.getDrawable(context, drawableId)
      ?: throw IllegalArgumentException("Couldn't find drawable with drawableId: $drawableId")

    val drawableMutable = DrawableCompat.wrap(drawable).mutate()
    if (chanTheme.isLightTheme) {
      DrawableCompat.setTint(drawableMutable, DARK_DRAWABLE_TINT)
    } else {
      DrawableCompat.setTint(drawableMutable, LIGHT_DRAWABLE_TINT)
    }

    return drawableMutable
  }

  fun tintDrawable(context: Context, @DrawableRes drawableId: Int, @ColorInt color: Int): Drawable {
    val drawable = ContextCompat.getDrawable(context, drawableId)
      ?: throw IllegalArgumentException("Couldn't find drawable with drawableId: $drawableId")

    val drawableMutable = DrawableCompat.wrap(drawable).mutate()
    DrawableCompat.setTint(drawableMutable, color)

    return drawableMutable
  }

  private fun getThemeInternal(isDarkTheme: Boolean): ChanTheme {
    return if (isDarkTheme) {
      darkTheme()
    } else {
      lightTheme()
    }
  }

  suspend fun tryParseAndApplyTheme(file: ExternalFile, isDarkTheme: Boolean): ThemeParser.ThemeParseResult {
    val defaultTheme = if (isDarkTheme) {
      darkTheme()
    } else {
      lightTheme()
    }

    return tryApplyTheme(
      themeParser.parseTheme(file, defaultTheme),
      isDarkTheme
    )
  }

  suspend fun tryParseAndApplyTheme(input: String, isDarkTheme: Boolean): ThemeParser.ThemeParseResult {
    val defaultTheme = if (isDarkTheme) {
      darkTheme()
    } else {
      lightTheme()
    }

    return tryApplyTheme(
      themeParser.parseTheme(input, defaultTheme),
      isDarkTheme
    )
  }

  private fun tryApplyTheme(
    themeParseResult: ThemeParser.ThemeParseResult,
    isDarkTheme: Boolean
  ): ThemeParser.ThemeParseResult {
    if (themeParseResult !is ThemeParser.ThemeParseResult.Success) {
      Logger.e(TAG, "themeParser.parseTheme() error=${themeParseResult}")
      return themeParseResult
    }

    if (isDarkTheme) {
      actualDarkTheme = themeParseResult.chanTheme
    } else {
      actualLightTheme = themeParseResult.chanTheme
    }

    ChanSettings.isCurrentThemeDark.set(isDarkTheme)
    chanTheme = themeParseResult.chanTheme
    refreshViews()

    return themeParseResult
  }

  suspend fun exportTheme(file: ExternalFile, darkTheme: Boolean): ThemeParser.ThemeExportResult {
    val result = themeParser.exportTheme(file, getThemeInternal(darkTheme))

    if (result !is ThemeParser.ThemeExportResult.Success) {
      Logger.e(TAG, "themeParser.parseTheme() error=${result}")
    }

    return result
  }

  fun resetTheme(darkTheme: Boolean): Boolean {
    val result = themeParser.deleteThemeFile(darkTheme)
    if (!result) {
      return false
    }

    if (darkTheme) {
      actualDarkTheme = null
    } else {
      actualLightTheme = null
    }

    chanTheme = getThemeInternal(darkTheme)
    refreshViews()

    return true
  }

  @Synchronized
  fun startAutoThemeSwitcher() {
    stopAutoThemeSwitcher()

    autoThemeSwitcherJob = appScope.launch {
      while (isActive) {
        switchTheme(true)
        delay(5000)

        switchTheme(false)
        delay(5000)
      }
    }
  }

  @Synchronized
  fun isAutoThemeSwitcherRunning(): Boolean = autoThemeSwitcherJob != null

  @Synchronized
  fun stopAutoThemeSwitcher() {
    autoThemeSwitcherJob?.cancel()
    autoThemeSwitcherJob = null
  }

  interface ThemeChangesListener {
    fun onThemeChanged()
  }

  companion object {
    private const val TAG = "ThemeEngine"

    val LIGHT_DRAWABLE_TINT = Color.parseColor("#EEEEEE")
    val DARK_DRAWABLE_TINT = Color.parseColor("#7E7E7E")

    /**
     * Makes color brighter if factor > 1.0f or darker if factor < 1.0f
     */
    @JvmStatic
    fun manipulateColor(color: Int, factor: Float): Int {
      val a = Color.alpha(color)
      val r = (Color.red(color) * factor).roundToInt()
      val g = (Color.green(color) * factor).roundToInt()
      val b = (Color.blue(color) * factor).roundToInt()
      return Color.argb(a, min(r, 255), min(g, 255), min(b, 255))
    }

    @JvmStatic
    fun getComplementaryColor(color: Int): Int {
      return Color.rgb(255 - Color.red(color), 255 - Color.green(color), 255 - Color.blue(color))
    }

    @JvmStatic
    fun updateAlphaForColor(color: Int, @FloatRange(from = 0.0, to = 1.0) newAlpha: Float): Int {
      return ColorUtils.setAlphaComponent(color, (newAlpha * 255f).toInt())
    }

    @JvmStatic
    fun isDarkColor(color: Int): Boolean {
      return ColorUtils.calculateLuminance(color) < 0.5f
    }

    @JvmStatic
    fun isBlackOrAlmostBlackColor(color: Int): Boolean {
      return ColorUtils.calculateLuminance(color) < 0.01f
    }

  }
}