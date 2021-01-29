package com.github.k1rakishou.chan.features.settings.screens

import android.content.Context
import com.github.k1rakishou.ChanSettings
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.features.settings.AppearanceScreen
import com.github.k1rakishou.chan.features.settings.SettingsGroup
import com.github.k1rakishou.chan.features.settings.setting.BooleanSettingV2
import com.github.k1rakishou.chan.features.settings.setting.LinkSettingV2
import com.github.k1rakishou.chan.features.settings.setting.ListSettingV2
import com.github.k1rakishou.chan.ui.controller.navigation.NavigationController
import com.github.k1rakishou.chan.ui.controller.settings.ThemeSettingsController
import com.github.k1rakishou.core_themes.ThemeEngine

class AppearanceSettingsScreen(
  context: Context,
  private val navigationController: NavigationController,
  private val themeEngine: ThemeEngine
) : BaseSettingsScreen(
  context,
  AppearanceScreen,
  R.string.settings_screen_appearance
) {

  override fun buildGroups(): List<SettingsGroup.SettingsGroupBuilder> {
    return listOf(
      buildAppearanceSettingsGroup(),
      buildLayoutSettingsGroup(),
      buildPostSettingsGroup(),
      buildPostLinksSettingsGroup(),
      buildImageSettingsGroup()
    )
  }

  private fun buildImageSettingsGroup(): SettingsGroup.SettingsGroupBuilder {
    val identifier = AppearanceScreen.ImagesGroup

    return SettingsGroup.SettingsGroupBuilder(
      groupIdentifier = identifier,
      buildFunction = fun(): SettingsGroup {
        val group = SettingsGroup(
          groupTitle = context.getString(R.string.settings_group_images),
          groupIdentifier = identifier
        )

        group += BooleanSettingV2.createBuilder(
          context = context,
          identifier = AppearanceScreen.ImagesGroup.HideImages,
          topDescriptionIdFunc = { R.string.setting_hide_images },
          bottomDescriptionIdFunc = { R.string.setting_hide_images_description },
          setting = ChanSettings.hideImages,
          requiresUiRefresh = true
        )

        group += BooleanSettingV2.createBuilder(
          context = context,
          identifier = AppearanceScreen.ImagesGroup.RemoveImageSpoilers,
          topDescriptionIdFunc = { R.string.settings_remove_image_spoilers },
          bottomDescriptionIdFunc = { R.string.settings_remove_image_spoilers_description },
          setting = ChanSettings.removeImageSpoilers
        )

        group += BooleanSettingV2.createBuilder(
          context = context,
          identifier = AppearanceScreen.ImagesGroup.RevealImageSpoilers,
          topDescriptionIdFunc = { R.string.settings_reveal_image_spoilers },
          bottomDescriptionIdFunc = { R.string.settings_reveal_image_spoilers_description },
          setting = ChanSettings.revealImageSpoilers
        )

        group += BooleanSettingV2.createBuilder(
          context = context,
          identifier = AppearanceScreen.ImagesGroup.HighResCells,
          topDescriptionIdFunc = { R.string.setting_images_high_res },
          bottomDescriptionIdFunc = { R.string.setting_images_high_res_description },
          setting = ChanSettings.highResCells,
          requiresUiRefresh = true
        )

        group += BooleanSettingV2.createBuilder(
          context = context,
          identifier = AppearanceScreen.ImagesGroup.ParsePostImageLinks,
          topDescriptionIdFunc = { R.string.setting_image_link_loading_title },
          bottomDescriptionIdFunc = { R.string.setting_image_link_loading_description },
          setting = ChanSettings.parsePostImageLinks,
          requiresUiRefresh = true
        )

        group += BooleanSettingV2.createBuilder(
          context = context,
          identifier = AppearanceScreen.ImagesGroup.FetchInlinedFileSizes,
          topDescriptionIdFunc = { R.string.setting_fetch_inlined_file_sizes_title },
          bottomDescriptionIdFunc = { R.string.setting_fetch_inlined_file_sizes_description },
          setting = ChanSettings.fetchInlinedFileSizes,
          dependsOnSetting = ChanSettings.parsePostImageLinks,
          requiresUiRefresh = true
        )

        group += BooleanSettingV2.createBuilder(
          context = context,
          identifier = AppearanceScreen.ImagesGroup.TransparencyOn,
          topDescriptionIdFunc = { R.string.setting_fetch_image_opacity },
          bottomDescriptionIdFunc = { R.string.setting_fetch_image_opacity_description },
          setting = ChanSettings.transparencyOn
        )

        return group
      }
    )

  }

  private fun buildPostSettingsGroup(): SettingsGroup.SettingsGroupBuilder {
    val identifier = AppearanceScreen.PostGroup

    return SettingsGroup.SettingsGroupBuilder(
      groupIdentifier = identifier,
      buildFunction = fun(): SettingsGroup {
        val group = SettingsGroup(
          groupTitle = context.getString(R.string.settings_group_post),
          groupIdentifier = identifier
        )

        group += ListSettingV2.createBuilder<String>(
          context = context,
          identifier = AppearanceScreen.PostGroup.FontSize,
          topDescriptionIdFunc = { R.string.setting_font_size },
          bottomDescriptionStringFunc = { itemName -> itemName },
          items = SUPPORTED_FONT_SIZES.map { fontSize -> fontSize.toString() }.toList(),
          itemNameMapper = { fontSize ->
            when (fontSize.toIntOrNull()) {
              in SUPPORTED_FONT_SIZES -> fontSize
              else -> throw IllegalArgumentException("Bad font size: $fontSize")
            }
          },
          setting = ChanSettings.fontSize,
          requiresUiRefresh = true
        )

        group += BooleanSettingV2.createBuilder(
          context = context,
          identifier = AppearanceScreen.PostGroup.PostFullDate,
          topDescriptionIdFunc = { R.string.setting_post_full_date },
          setting = ChanSettings.postFullDate,
          requiresUiRefresh = true
        )

        group += BooleanSettingV2.createBuilder(
          context = context,
          identifier = AppearanceScreen.PostGroup.PostFileInfo,
          topDescriptionIdFunc = { R.string.setting_post_file_info },
          setting = ChanSettings.postFileInfo,
          requiresUiRefresh = true
        )

        group += BooleanSettingV2.createBuilder(
          context = context,
          identifier = AppearanceScreen.PostGroup.PostFileName,
          topDescriptionIdFunc = { R.string.setting_post_filename },
          setting = ChanSettings.postFilename,
          requiresUiRefresh = true
        )

        group += BooleanSettingV2.createBuilder(
          context = context,
          identifier = AppearanceScreen.PostGroup.TextOnly,
          topDescriptionIdFunc = { R.string.setting_text_only },
          bottomDescriptionIdFunc = { R.string.setting_text_only_description },
          setting = ChanSettings.textOnly,
          requiresUiRefresh = true
        )

        group += BooleanSettingV2.createBuilder(
          context = context,
          identifier = AppearanceScreen.PostGroup.RevealTextSpoilers,
          topDescriptionIdFunc = { R.string.settings_reveal_text_spoilers },
          bottomDescriptionIdFunc = { R.string.settings_reveal_text_spoilers_description },
          setting = ChanSettings.revealTextSpoilers,
          requiresUiRefresh = true
        )

        group += BooleanSettingV2.createBuilder(
          context = context,
          identifier = AppearanceScreen.PostGroup.Anonymize,
          topDescriptionIdFunc = { R.string.setting_anonymize },
          bottomDescriptionIdFunc = { R.string.setting_anonymize_description },
          setting = ChanSettings.anonymize,
          requiresUiRefresh = true
        )

        group += BooleanSettingV2.createBuilder(
          context = context,
          identifier = AppearanceScreen.PostGroup.ShowAnonymousName,
          topDescriptionIdFunc = { R.string.setting_show_anonymous_name },
          bottomDescriptionIdFunc = { R.string.setting_show_anonymous_name_description },
          setting = ChanSettings.showAnonymousName,
          requiresUiRefresh = true
        )

        group += BooleanSettingV2.createBuilder(
          context = context,
          identifier = AppearanceScreen.PostGroup.AnonymizeIds,
          topDescriptionIdFunc = { R.string.setting_anonymize_ids },
          setting = ChanSettings.anonymizeIds,
          requiresUiRefresh = true
        )

        return group
      }
    )
  }

  private fun buildPostLinksSettingsGroup(): SettingsGroup.SettingsGroupBuilder {
    val identifier = AppearanceScreen.PostLinksGroup

    return SettingsGroup.SettingsGroupBuilder(
      groupIdentifier = identifier,
      buildFunction = fun(): SettingsGroup {
        val group = SettingsGroup(
          groupTitle = context.getString(R.string.setting_group_post_links),
          groupIdentifier = identifier
        )

        group += ListSettingV2.createBuilder<ChanSettings.NetworkContentAutoLoadMode>(
          context = context,
          identifier = AppearanceScreen.PostLinksGroup.ParseYoutubeTitlesAndDuration,
          topDescriptionIdFunc = { R.string.setting_youtube_title_and_durations },
          bottomDescriptionStringFunc = {
            context.getString(
              R.string.setting_youtube_title_and_durations_description,
              networkContentAutoLoadNameMapper(ChanSettings.parseYoutubeTitlesAndDuration.get())
            )
          },
          setting = ChanSettings.parseYoutubeTitlesAndDuration,
          items = ChanSettings.NetworkContentAutoLoadMode.values().toList(),
          itemNameMapper = { item -> networkContentAutoLoadNameMapper(item) },
          requiresUiRefresh = true
        )

        group += ListSettingV2.createBuilder<ChanSettings.NetworkContentAutoLoadMode>(
          context = context,
          identifier = AppearanceScreen.PostLinksGroup.ParseSoundCloudTitlesAndDuration,
          topDescriptionIdFunc = { R.string.setting_soundcloud_title_and_durations },
          bottomDescriptionStringFunc = {
            context.getString(
              R.string.setting_soundcloud_title_and_durations_description,
              networkContentAutoLoadNameMapper(ChanSettings.parseSoundCloudTitlesAndDuration.get())
            )
          },
          setting = ChanSettings.parseSoundCloudTitlesAndDuration,
          items = ChanSettings.NetworkContentAutoLoadMode.values().toList(),
          itemNameMapper = { item -> networkContentAutoLoadNameMapper(item) },
          requiresUiRefresh = true
        )

        group += ListSettingV2.createBuilder<ChanSettings.NetworkContentAutoLoadMode>(
          context = context,
          identifier = AppearanceScreen.PostLinksGroup.ParseStreamableTitlesAndDuration,
          topDescriptionIdFunc = { R.string.setting_streamable_title_and_durations },
          bottomDescriptionStringFunc = {
            context.getString(
              R.string.setting_streamable_title_and_durations_description,
              networkContentAutoLoadNameMapper(ChanSettings.parseStreamableTitlesAndDuration.get())
            )
          },
          setting = ChanSettings.parseStreamableTitlesAndDuration,
          items = ChanSettings.NetworkContentAutoLoadMode.values().toList(),
          itemNameMapper = { item -> networkContentAutoLoadNameMapper(item) },
          requiresUiRefresh = true
        )

        group += BooleanSettingV2.createBuilder(
          context = context,
          identifier = AppearanceScreen.PostLinksGroup.ShowLinkAlongWithTitleAndDuration,
          topDescriptionIdFunc = { R.string.setting_show_link_along_with_title_and_duration_title },
          bottomDescriptionIdFunc = { R.string.setting_show_link_along_with_title_and_duration_description },
          setting = ChanSettings.showLinkAlongWithTitleAndDuration,
          requiresUiRefresh = true
        )

        return group
      })
  }

  private fun buildLayoutSettingsGroup(): SettingsGroup.SettingsGroupBuilder {
    val identifier = AppearanceScreen.LayoutGroup

    return SettingsGroup.SettingsGroupBuilder(
      groupIdentifier = identifier,
      buildFunction = fun(): SettingsGroup {
        val group = SettingsGroup(
          groupTitle = context.getString(R.string.settings_group_layout),
          groupIdentifier = identifier
        )

        group += ListSettingV2.createBuilder<ChanSettings.LayoutMode>(
          context = context,
          identifier = AppearanceScreen.LayoutGroup.LayoutMode,
          topDescriptionIdFunc = { R.string.setting_layout_mode },
          bottomDescriptionStringFunc = { itemName -> itemName },
          items = ChanSettings.LayoutMode.values().toList(),
          itemNameMapper = { layoutMode ->
            when (layoutMode) {
              ChanSettings.LayoutMode.AUTO -> context.getString(R.string.setting_layout_mode_auto)
              ChanSettings.LayoutMode.SLIDE -> context.getString(R.string.setting_layout_mode_slide)
              ChanSettings.LayoutMode.PHONE -> context.getString(R.string.setting_layout_mode_phone)
              ChanSettings.LayoutMode.SPLIT -> context.getString(R.string.setting_layout_mode_split)
            }
          },
          requiresRestart = true,
          setting = ChanSettings.layoutMode
        )

        group += ListSettingV2.createBuilder<Int>(
          context = context,
          identifier = AppearanceScreen.LayoutGroup.CatalogColumnsCount,
          topDescriptionIdFunc = { R.string.setting_board_grid_span_count },
          bottomDescriptionStringFunc = { itemName -> itemName },
          items = ALL_COLUMNS,
          itemNameMapper = { columnsCount ->
            when (columnsCount) {
              AUTO_COLUMN -> context.getString(R.string.setting_board_grid_span_count_default)
              in ALL_COLUMNS_EXCLUDING_AUTO -> {
                context.getString(R.string.setting_board_grid_span_count_item, columnsCount)
              }
              else -> throw IllegalArgumentException("Bad columns count: $columnsCount")
            }
          },
          requiresUiRefresh = true,
          setting = ChanSettings.boardGridSpanCount
        )

        group += BooleanSettingV2.createBuilder(
          context = context,
          identifier = AppearanceScreen.LayoutGroup.NeverHideToolbar,
          topDescriptionIdFunc = { R.string.setting_never_hide_toolbar },
          setting = ChanSettings.neverHideToolbar
        )

        group += BooleanSettingV2.createBuilder(
          context = context,
          identifier = AppearanceScreen.LayoutGroup.EnableReplyFAB,
          topDescriptionIdFunc = { R.string.setting_enable_reply_fab },
          bottomDescriptionIdFunc = { R.string.setting_enable_reply_fab_description },
          setting = ChanSettings.enableReplyFab
        )

        group += BooleanSettingV2.createBuilder(
          context = context,
          identifier = AppearanceScreen.LayoutGroup.BottomJsCaptcha,
          topDescriptionIdFunc = { R.string.setting_bottom_js_captcha },
          bottomDescriptionIdFunc = { R.string.setting_bottom_js_captcha_description },
          setting = ChanSettings.captchaOnBottom,
          requiresUiRefresh = true
        )

        group += BooleanSettingV2.createBuilder(
          context = context,
          identifier = AppearanceScreen.LayoutGroup.NeverShowPages,
          topDescriptionIdFunc = { R.string.setting_never_show_pages },
          bottomDescriptionIdFunc = { R.string.setting_never_show_pages_bottom },
          setting = ChanSettings.neverShowPages
        )

        group += BooleanSettingV2.createBuilder(
          context = context,
          identifier = AppearanceScreen.LayoutGroup.EnableDraggableScrollbars,
          topDescriptionIdFunc = { R.string.setting_enable_draggable_scrollbars },
          bottomDescriptionIdFunc = { R.string.setting_enable_draggable_scrollbars_bottom },
          setting = ChanSettings.enableDraggableScrollbars,
          requiresUiRefresh = true
        )

        return group
      }
    )
  }

  private fun buildAppearanceSettingsGroup(): SettingsGroup.SettingsGroupBuilder {
    val identifier = AppearanceScreen.MainGroup

    return SettingsGroup.SettingsGroupBuilder(
      groupIdentifier = identifier,
      buildFunction = fun(): SettingsGroup {
        val group = SettingsGroup(
          groupTitle = context.getString(R.string.settings_group_appearance),
          groupIdentifier = identifier
        )

        group += LinkSettingV2.createBuilder(
          context = context,
          identifier = AppearanceScreen.MainGroup.ThemeCustomization,
          topDescriptionIdFunc = { R.string.setting_theme },
          bottomDescriptionStringFunc = { themeEngine.chanTheme.name },
          callback = { navigationController.pushController(ThemeSettingsController(context)) }
        )

        group += BooleanSettingV2.createBuilder(
          context = context,
          identifier = AppearanceScreen.MainGroup.ImageViewerFullscreenMode,
          topDescriptionIdFunc = { R.string.setting_full_screen_mode },
          bottomDescriptionIdFunc = { R.string.setting_full_screen_mode_description },
          setting = ChanSettings.imageViewerFullscreenMode
        )

        return group
      }
    )
  }

  private fun networkContentAutoLoadNameMapper(item: ChanSettings.NetworkContentAutoLoadMode): String {
    return when (item) {
      ChanSettings.NetworkContentAutoLoadMode.ALL -> {
        context.getString(R.string.setting_image_auto_load_all)
      }
      ChanSettings.NetworkContentAutoLoadMode.WIFI -> {
        context.getString(R.string.setting_image_auto_load_wifi)
      }
      ChanSettings.NetworkContentAutoLoadMode.NONE -> {
        context.getString(R.string.setting_image_auto_load_none)
      }
    }
  }

  companion object {
    private val SUPPORTED_FONT_SIZES = (10..19)
    private const val AUTO_COLUMN = 0
    private val ALL_COLUMNS = listOf(AUTO_COLUMN, 2, 3, 4, 5)
    private val ALL_COLUMNS_EXCLUDING_AUTO = setOf(2, 3, 4, 5)
  }
}