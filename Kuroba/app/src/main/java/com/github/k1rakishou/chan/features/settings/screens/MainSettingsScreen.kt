package com.github.k1rakishou.chan.features.settings.screens

import android.content.Context
import com.github.k1rakishou.ChanSettings
import com.github.k1rakishou.chan.BuildConfig
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.helper.DialogFactory
import com.github.k1rakishou.chan.core.manager.ChanFilterManager
import com.github.k1rakishou.chan.core.manager.ReportManager
import com.github.k1rakishou.chan.core.manager.SiteManager
import com.github.k1rakishou.chan.core.manager.UpdateManager
import com.github.k1rakishou.chan.features.settings.AppearanceScreen
import com.github.k1rakishou.chan.features.settings.BehaviorScreen
import com.github.k1rakishou.chan.features.settings.DeveloperScreen
import com.github.k1rakishou.chan.features.settings.ExperimentalScreen
import com.github.k1rakishou.chan.features.settings.ImportExportScreen
import com.github.k1rakishou.chan.features.settings.MainScreen
import com.github.k1rakishou.chan.features.settings.MediaScreen
import com.github.k1rakishou.chan.features.settings.SecurityScreen
import com.github.k1rakishou.chan.features.settings.SettingClickAction
import com.github.k1rakishou.chan.features.settings.SettingsGroup
import com.github.k1rakishou.chan.features.settings.ThreadWatcherScreen
import com.github.k1rakishou.chan.features.settings.setting.BooleanSettingV2
import com.github.k1rakishou.chan.features.settings.setting.LinkSettingV2
import com.github.k1rakishou.chan.features.setup.SitesSetupController
import com.github.k1rakishou.chan.ui.controller.FiltersController
import com.github.k1rakishou.chan.ui.controller.LicensesController
import com.github.k1rakishou.chan.ui.controller.ReportProblemController
import com.github.k1rakishou.chan.ui.controller.crashlogs.ReviewCrashLogsController
import com.github.k1rakishou.chan.ui.controller.navigation.NavigationController
import com.github.k1rakishou.chan.ui.settings.SettingNotificationType
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.getVerifiedBuildType
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.isDevBuild
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.isFdroidBuild
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.openLink
import com.github.k1rakishou.common.AndroidUtils.VerifiedBuildType
import com.github.k1rakishou.common.AndroidUtils.getApplicationLabel
import com.github.k1rakishou.common.AndroidUtils.getQuantityString
import com.github.k1rakishou.common.AndroidUtils.getString

class MainSettingsScreen(
  context: Context,
  private val chanFilterManager: ChanFilterManager,
  private val siteManager: SiteManager,
  private val updateManager: UpdateManager,
  private val reportManager: ReportManager,
  private val navigationController: NavigationController,
  private val dialogFactory: DialogFactory
) : BaseSettingsScreen(
  context,
  MainScreen,
  R.string.settings_screen
) {

  override fun buildGroups(): List<SettingsGroup.SettingsGroupBuilder> {
    return listOf(
      buildMainSettingsGroup(),
      buildAboutAppGroup()
    )
  }

  private fun buildAboutAppGroup(): SettingsGroup.SettingsGroupBuilder {
    val identifier = MainScreen.AboutAppGroup

    return SettingsGroup.SettingsGroupBuilder(
      groupIdentifier = identifier,
      buildFunction = fun(): SettingsGroup {
        val group = SettingsGroup(
          groupTitle = context.getString(R.string.settings_group_about),
          groupIdentifier = identifier
        )

        group += LinkSettingV2.createBuilder(
          context = context,
          identifier = MainScreen.AboutAppGroup.AppVersion,
          topDescriptionStringFunc = { createAppVersionString() },
          bottomDescriptionStringFunc = {
            if (isDevBuild() || isFdroidBuild()) {
              context.getString(R.string.settings_updates_are_disabled)
            } else {
              context.getString(R.string.settings_update_check)
            }
          },
          callbackWithClickAction = {
            when {
              isDevBuild() -> SettingClickAction.ShowToast(R.string.updater_is_disabled_for_dev_builds)
              isFdroidBuild() -> SettingClickAction.ShowToast(R.string.updater_is_disabled_for_fdroid_builds)
              else -> {
                updateManager.manualUpdateCheck()
                SettingClickAction.NoAction
              }
            }
          },
          notificationType = SettingNotificationType.ApkUpdate
        )

        group += LinkSettingV2.createBuilder(
          context = context,
          identifier = MainScreen.AboutAppGroup.Reports,
          topDescriptionIdFunc = { R.string.settings_report },
          bottomDescriptionIdFunc = { R.string.settings_report_description },
          callback = { onReportSettingClick() },
          notificationType = SettingNotificationType.CrashLog
        )

        group += BooleanSettingV2.createBuilder(
          context = context,
          identifier = MainScreen.AboutAppGroup.CollectCrashReport,
          topDescriptionIdFunc = { R.string.settings_collect_crash_logs },
          bottomDescriptionIdFunc = { R.string.settings_collect_crash_logs_description },
          setting = ChanSettings.collectCrashLogs,
          checkChangedCallback = { isChecked ->
            if (!isChecked) {
              reportManager.deleteAllCrashLogs()
            }
          }
        )

        group += LinkSettingV2.createBuilder(
          context = context,
          identifier = MainScreen.AboutAppGroup.FindAppOnGithub,
          topDescriptionStringFunc = { getString(R.string.settings_find_app_on_github, getApplicationLabel()) },
          bottomDescriptionIdFunc = { R.string.settings_find_app_on_github_bottom },
          callback = { openLink(BuildConfig.GITHUB_ENDPOINT) }
        )

        group += LinkSettingV2.createBuilder(
          context = context,
          identifier = MainScreen.AboutAppGroup.AppLicense,
          topDescriptionIdFunc = { R.string.settings_about_license },
          bottomDescriptionIdFunc = { R.string.settings_about_license_description },
          callback = {
            navigationController.pushController(
              LicensesController(context,
                getString(R.string.settings_about_license),
                "file:///android_asset/html/license.html"
              )
            )
          }
        )

        group += LinkSettingV2.createBuilder(
          context = context,
          identifier = MainScreen.AboutAppGroup.AppLicenses,
          topDescriptionIdFunc = { R.string.settings_about_licenses },
          bottomDescriptionIdFunc = { R.string.settings_about_licenses_description },
          callback = {
            navigationController.pushController(
              LicensesController(context,
                getString(R.string.settings_about_licenses),
                "file:///android_asset/html/licenses.html"
              )
            )
          }
        )

        group += LinkSettingV2.createBuilder(
          context = context,
          identifier = MainScreen.AboutAppGroup.DeveloperSettings,
          topDescriptionIdFunc = { R.string.settings_developer },
          callbackWithClickAction = { SettingClickAction.OpenScreen(DeveloperScreen) }
        )

        return group
      }
    )
  }

  private fun buildMainSettingsGroup(): SettingsGroup.SettingsGroupBuilder {
    val identifier = MainScreen.MainGroup

    return SettingsGroup.SettingsGroupBuilder(
      groupIdentifier = identifier,
      buildFunction = fun(): SettingsGroup {
        val group = SettingsGroup(
          groupTitle = context.getString(R.string.settings_screen),
          groupIdentifier = identifier
        )

        group += LinkSettingV2.createBuilder(
          context = context,
          identifier = MainScreen.MainGroup.ThreadWatcher,
          topDescriptionIdFunc = { R.string.settings_watch },
          bottomDescriptionIdFunc = { R.string.setting_watch_summary_enabled },
          callbackWithClickAction = { SettingClickAction.OpenScreen(ThreadWatcherScreen) }
        )

        group += LinkSettingV2.createBuilder(
          context = context,
          identifier = MainScreen.MainGroup.SitesSetup,
          topDescriptionIdFunc = { R.string.settings_sites },
          bottomDescriptionStringFunc = {
            val sitesCount = siteManager.activeSiteCount()
            getQuantityString(R.plurals.site, sitesCount, sitesCount)
          },
          callback = { navigationController.pushController(SitesSetupController(context)) }
        )

        group += LinkSettingV2.createBuilder(
          context = context,
          identifier = MainScreen.MainGroup.Appearance,
          topDescriptionIdFunc = { R.string.settings_appearance },
          bottomDescriptionIdFunc = { R.string.settings_appearance_description },
          callbackWithClickAction = { SettingClickAction.OpenScreen(AppearanceScreen) }
        )

        group += LinkSettingV2.createBuilder(
          context = context,
          identifier = MainScreen.MainGroup.Behavior,
          topDescriptionIdFunc = { R.string.settings_behavior },
          bottomDescriptionIdFunc = { R.string.settings_behavior_description },
          callbackWithClickAction = { SettingClickAction.OpenScreen(BehaviorScreen) }
        )

        group += LinkSettingV2.createBuilder(
          context = context,
          identifier = MainScreen.MainGroup.Media,
          topDescriptionIdFunc = { R.string.settings_media },
          bottomDescriptionIdFunc = { R.string.settings_media_description },
          callbackWithClickAction = { SettingClickAction.OpenScreen(MediaScreen) }
        )

        group += LinkSettingV2.createBuilder(
          context = context,
          identifier = MainScreen.MainGroup.ImportExport,
          topDescriptionIdFunc = { R.string.settings_import_export },
          bottomDescriptionIdFunc = { R.string.settings_import_export_description },
          callbackWithClickAction = {
            SettingClickAction.OpenScreen(ImportExportScreen)
          }
        )

        group += LinkSettingV2.createBuilder(
          context = context,
          identifier = MainScreen.MainGroup.Filters,
          topDescriptionIdFunc = { R.string.settings_filters },
          bottomDescriptionStringFunc = {
            val filtersCount = chanFilterManager.filtersCount()
            getQuantityString(R.plurals.filter, filtersCount, filtersCount)
          },
          callback = { navigationController.pushController(FiltersController(context)) }
        )

        group += LinkSettingV2.createBuilder(
          context = context,
          identifier = MainScreen.MainGroup.Security,
          topDescriptionIdFunc = { R.string.settings_security },
          bottomDescriptionIdFunc = { R.string.settings_security_description },
          callbackWithClickAction = { SettingClickAction.OpenScreen(SecurityScreen) }
        )

        group += LinkSettingV2.createBuilder(
          context = context,
          identifier = MainScreen.MainGroup.Experimental,
          topDescriptionIdFunc = { R.string.settings_experimental_settings },
          bottomDescriptionIdFunc = { R.string.settings_experimental_settings_description },
          callbackWithClickAction = { SettingClickAction.OpenScreen(ExperimentalScreen) }
        )

        return group
      }
    )
  }

  private fun createAppVersionString(): String {
    val verifiedBuildType = getVerifiedBuildType()

    val isVerified = verifiedBuildType == VerifiedBuildType.Release
      || verifiedBuildType == VerifiedBuildType.Debug

    val verificationBadge = if (isVerified) {
      "✓"
    } else {
      "✗"
    }

    return String.format(
      "%s %s.%s %s (commit %s)",
      getApplicationLabel().toString(),
      BuildConfig.VERSION_NAME,
      BuildConfig.BUILD_NUMBER,
      verificationBadge,
      BuildConfig.COMMIT_HASH.take(12)
    )
  }

  private fun onReportSettingClick() {
    fun openReportProblemController() {
      navigationController.pushController(ReportProblemController(context))
    }

    val crashLogsCount: Int = reportManager.countCrashLogs()
    if (crashLogsCount > 0) {
      dialogFactory.createSimpleConfirmationDialog(
        context = context,
        titleText = getString(R.string.settings_report_suggest_sending_logs_title, crashLogsCount),
        descriptionTextId = R.string.settings_report_suggest_sending_logs_message,
        positiveButtonText = getString(R.string.settings_report_review_button_text),
        onPositiveButtonClickListener = {
          navigationController.pushController(ReviewCrashLogsController(context))
        },
        neutralButtonText = getString(R.string.settings_report_review_later_button_text),
        onNeutralButtonClickListener = {
          openReportProblemController()
        },
        negativeButtonText = getString(R.string.settings_report_delete_all_crash_logs),
        onNegativeButtonClickListener = {
          reportManager.deleteAllCrashLogs()
          openReportProblemController()
        }
      )

      return
    }

    openReportProblemController()
  }

}