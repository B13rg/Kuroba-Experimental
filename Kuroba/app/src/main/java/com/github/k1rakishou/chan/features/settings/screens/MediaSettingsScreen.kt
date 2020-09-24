package com.github.k1rakishou.chan.features.settings.screens

import android.content.Context
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.settings.ChanSettings
import com.github.k1rakishou.chan.features.settings.MediaScreen
import com.github.k1rakishou.chan.features.settings.SettingsCoordinatorCallbacks
import com.github.k1rakishou.chan.features.settings.SettingsGroup
import com.github.k1rakishou.chan.features.settings.screens.delegate.MediaSettingsDelegate
import com.github.k1rakishou.chan.features.settings.setting.BooleanSettingV2
import com.github.k1rakishou.chan.features.settings.setting.LinkSettingV2
import com.github.k1rakishou.chan.features.settings.setting.ListSettingV2
import com.github.k1rakishou.chan.ui.controller.navigation.NavigationController
import com.github.k1rakishou.chan.ui.helper.RuntimePermissionsHelper
import com.github.k1rakishou.fsaf.FileChooser
import com.github.k1rakishou.fsaf.FileManager

class MediaSettingsScreen(
  context: Context,
  private val callback: SettingsCoordinatorCallbacks,
  private val navigationController: NavigationController,
  private val fileManager: FileManager,
  private val fileChooser: FileChooser,
  private val runtimePermissionsHelper: RuntimePermissionsHelper
) : BaseSettingsScreen(
  context,
  MediaScreen,
  R.string.settings_screen_media
) {
  private val mediaSettingsDelegate by lazy {
    MediaSettingsDelegate(
      context,
      callback,
      navigationController,
      fileManager,
      fileChooser,
      runtimePermissionsHelper
    )
  }

  override fun onCreate() {
    super.onCreate()

    mediaSettingsDelegate.onCreate()
  }

  override fun onDestroy() {
    super.onDestroy()

    mediaSettingsDelegate.onDestroy()
  }

  override fun buildGroups(): List<SettingsGroup.SettingsGroupBuilder> {
    return listOf(
      buildMediaSavingSettingsGroup(),
      buildVideoSettingsGroup(),
      buildLoadingSettingsGroup()
    )
  }

  private fun buildLoadingSettingsGroup(): SettingsGroup.SettingsGroupBuilder {
    val identifier = MediaScreen.LoadingGroup

    return SettingsGroup.SettingsGroupBuilder(
      groupIdentifier = identifier,
      buildFunction = fun(): SettingsGroup {
        val group = SettingsGroup(
          groupTitle = context.getString(R.string.settings_group_media_loading),
          groupIdentifier = identifier
        )

        group += ListSettingV2.createBuilder<ChanSettings.MediaAutoLoadMode>(
          context = context,
          identifier = MediaScreen.LoadingGroup.ImageAutoLoadNetwork,
          setting = ChanSettings.imageAutoLoadNetwork,
          topDescriptionIdFunc = { R.string.setting_image_auto_load },
          bottomDescriptionStringFunc = { itemName -> itemName },
          items = ChanSettings.MediaAutoLoadMode.values().toList(),
          itemNameMapper = { item ->
            when (item) {
              ChanSettings.MediaAutoLoadMode.ALL -> {
                context.getString(R.string.setting_image_auto_load_all)
              }
              ChanSettings.MediaAutoLoadMode.WIFI -> {
                context.getString(R.string.setting_image_auto_load_wifi)
              }
              ChanSettings.MediaAutoLoadMode.NONE -> {
                context.getString(R.string.setting_image_auto_load_none)
              }
            }
          }
        )

        group += ListSettingV2.createBuilder<ChanSettings.MediaAutoLoadMode>(
          context = context,
          identifier = MediaScreen.LoadingGroup.VideoAutoLoadNetwork,
          setting = ChanSettings.videoAutoLoadNetwork,
          topDescriptionIdFunc = { R.string.setting_video_auto_load },
          bottomDescriptionStringFunc = { itemName -> itemName },
          items = ChanSettings.MediaAutoLoadMode.values().toList(),
          itemNameMapper = { item ->
            when (item) {
              ChanSettings.MediaAutoLoadMode.ALL -> {
                context.getString(R.string.setting_image_auto_load_all)
              }
              ChanSettings.MediaAutoLoadMode.WIFI -> {
                context.getString(R.string.setting_image_auto_load_wifi)
              }
              ChanSettings.MediaAutoLoadMode.NONE -> {
                context.getString(R.string.setting_image_auto_load_none)
              }
            }
          }
        )

        group += ListSettingV2.createBuilder<ChanSettings.ImageClickPreloadStrategy>(
          context = context,
          identifier = MediaScreen.LoadingGroup.ImageClickPreloadStrategy,
          setting = ChanSettings.imageClickPreloadStrategy,
          topDescriptionIdFunc = { R.string.media_settings_image_click_preload_strategy },
          bottomDescriptionStringFunc = { itemName ->
            context.getString(R.string.media_settings_image_click_preload_strategy_description) +
              "\n\n" + itemName
          },
          items = ChanSettings.ImageClickPreloadStrategy.values().toList(),
          itemNameMapper = { item -> item.name }
        )

        group += BooleanSettingV2.createBuilder(
          context = context,
          identifier = MediaScreen.LoadingGroup.AutoLoadThreadImages,
          topDescriptionIdFunc = { R.string.setting_auto_load_thread_images },
          bottomDescriptionIdFunc = { R.string.setting_auto_load_thread_images_description },
          setting = ChanSettings.autoLoadThreadImages,
          requiresRestart = true
        )

        group += BooleanSettingV2.createBuilder(
          context = context,
          identifier = MediaScreen.LoadingGroup.ShowPrefetchLoadingIndicator,
          topDescriptionIdFunc = { R.string.setting_show_prefetch_loading_indicator_title },
          setting = ChanSettings.showPrefetchLoadingIndicator,
          dependsOnSetting = ChanSettings.autoLoadThreadImages
        )

        return group
      }
    )
  }

  private fun buildVideoSettingsGroup(): SettingsGroup.SettingsGroupBuilder {
    val identifier = MediaScreen.VideoGroup

    return SettingsGroup.SettingsGroupBuilder(
      groupIdentifier = identifier,
      buildFunction = fun(): SettingsGroup {
        val group = SettingsGroup(
          groupTitle = context.getString(R.string.setting_video_options),
          groupIdentifier = identifier
        )

        group += BooleanSettingV2.createBuilder(
          context = context,
          identifier = MediaScreen.VideoGroup.VideoAutoLoop,
          topDescriptionIdFunc = { R.string.setting_video_auto_loop },
          bottomDescriptionIdFunc = { R.string.setting_video_auto_loop_description },
          setting = ChanSettings.videoAutoLoop
        )

        group += BooleanSettingV2.createBuilder(
          context = context,
          identifier = MediaScreen.VideoGroup.VideoDefaultMuted,
          topDescriptionIdFunc = { R.string.setting_video_default_muted },
          bottomDescriptionIdFunc = { R.string.setting_video_default_muted_description },
          setting = ChanSettings.videoDefaultMuted
        )

        group += BooleanSettingV2.createBuilder(
          context = context,
          identifier = MediaScreen.VideoGroup.HeadsetDefaultMuted,
          topDescriptionIdFunc = { R.string.setting_headset_default_muted },
          bottomDescriptionIdFunc = { R.string.setting_headset_default_muted_description },
          setting = ChanSettings.headsetDefaultMuted,
          dependsOnSetting = ChanSettings.videoDefaultMuted
        )

        group += BooleanSettingV2.createBuilder(
          context = context,
          identifier = MediaScreen.VideoGroup.VideoOpenExternal,
          topDescriptionIdFunc = { R.string.setting_video_open_external },
          bottomDescriptionIdFunc = { R.string.setting_video_open_external_description },
          setting = ChanSettings.videoOpenExternal
        )

        group += BooleanSettingV2.createBuilder(
          context = context,
          identifier = MediaScreen.VideoGroup.VideoStream,
          topDescriptionIdFunc = { R.string.setting_video_stream },
          bottomDescriptionIdFunc = { R.string.setting_video_stream_description },
          setting = ChanSettings.videoStream
        )

        return group
      }
    )
  }

  private fun buildMediaSavingSettingsGroup(): SettingsGroup.SettingsGroupBuilder {
    val identifier = MediaScreen.MediaSavingGroup

    return SettingsGroup.SettingsGroupBuilder(
      groupIdentifier = identifier,
      buildFunction = fun(): SettingsGroup {
        val group = SettingsGroup(
          groupTitle = context.getString(R.string.settings_group_saving),
          groupIdentifier = identifier
        )

        group += LinkSettingV2.createBuilder(
          context = context,
          identifier =  MediaScreen.MediaSavingGroup.MediaSaveLocation,
          topDescriptionIdFunc = { R.string.save_location_screen },
          bottomDescriptionStringFunc = { mediaSettingsDelegate.getSaveLocation() },
          callback = { mediaSettingsDelegate.showUseSAFOrOldAPIForSaveLocationDialog() }
        )

        // Individual images
        group += BooleanSettingV2.createBuilder(
          context = context,
          identifier = MediaScreen.MediaSavingGroup.SaveBoardFolder,
          topDescriptionIdFunc = { R.string.setting_save_board_folder },
          bottomDescriptionIdFunc = { R.string.setting_save_board_folder_description },
          setting = ChanSettings.saveBoardFolder
        )

        group += BooleanSettingV2.createBuilder(
          context = context,
          identifier = MediaScreen.MediaSavingGroup.SaveThreadFolder,
          topDescriptionIdFunc = { R.string.setting_save_thread_folder },
          bottomDescriptionIdFunc = { R.string.setting_save_thread_folder_description },
          setting = ChanSettings.saveThreadFolder,
          dependsOnSetting = ChanSettings.saveBoardFolder
        )
        // =============

        // Albums
        group += BooleanSettingV2.createBuilder(
          context = context,
          identifier = MediaScreen.MediaSavingGroup.SaveAlbumBoardFolder,
          topDescriptionIdFunc = { R.string.setting_save_album_board_folder },
          bottomDescriptionIdFunc = { R.string.setting_save_album_board_folder_description },
          setting = ChanSettings.saveAlbumBoardFolder
        )

        group += BooleanSettingV2.createBuilder(
          context = context,
          identifier = MediaScreen.MediaSavingGroup.SaveAlbumThreadFolder,
          topDescriptionIdFunc = { R.string.setting_save_album_thread_folder },
          bottomDescriptionIdFunc = { R.string.setting_save_album_thread_folder_description },
          setting = ChanSettings.saveAlbumThreadFolder,
          dependsOnSetting = ChanSettings.saveAlbumBoardFolder
        )
        // =============

        group += BooleanSettingV2.createBuilder(
          context = context,
          identifier = MediaScreen.MediaSavingGroup.SaveServerFilename,
          topDescriptionIdFunc = { R.string.setting_save_server_filename },
          bottomDescriptionIdFunc = { R.string.setting_save_server_filename_description },
          setting = ChanSettings.saveServerFilename
        )

        return group
      }
    )
  }
}