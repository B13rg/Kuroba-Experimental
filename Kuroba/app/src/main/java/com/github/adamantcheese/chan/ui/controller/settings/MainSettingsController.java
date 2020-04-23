/*
 * KurobaEx - *chan browser https://github.com/K1rakishou/Kuroba-Experimental/
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.github.adamantcheese.chan.ui.controller.settings;

import android.content.Context;
import android.view.ViewGroup;

import androidx.appcompat.app.AlertDialog;

import com.github.adamantcheese.chan.BuildConfig;
import com.github.adamantcheese.chan.R;
import com.github.adamantcheese.chan.StartActivity;
import com.github.adamantcheese.chan.core.manager.ReportManager;
import com.github.adamantcheese.chan.core.presenter.SettingsPresenter;
import com.github.adamantcheese.chan.core.settings.ChanSettings;
import com.github.adamantcheese.chan.ui.controller.FiltersController;
import com.github.adamantcheese.chan.ui.controller.LicensesController;
import com.github.adamantcheese.chan.ui.controller.ReportProblemController;
import com.github.adamantcheese.chan.ui.controller.SitesSetupController;
import com.github.adamantcheese.chan.ui.controller.crashlogs.ReviewCrashLogsController;
import com.github.adamantcheese.chan.ui.settings.BooleanSettingView;
import com.github.adamantcheese.chan.ui.settings.LinkSettingView;
import com.github.adamantcheese.chan.ui.settings.SettingNotificationType;
import com.github.adamantcheese.chan.ui.settings.SettingView;
import com.github.adamantcheese.chan.ui.settings.SettingsGroup;
import com.github.adamantcheese.chan.utils.Logger;

import javax.inject.Inject;

import io.reactivex.disposables.Disposable;

import static com.github.adamantcheese.chan.Chan.inject;
import static com.github.adamantcheese.chan.utils.AndroidUtils.BuildType.Release;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getApplicationLabel;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getBuildType;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getQuantityString;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getString;
import static com.github.adamantcheese.chan.utils.AndroidUtils.openLink;

public class MainSettingsController
        extends SettingsController
        implements SettingsPresenter.Callback {
    private static final String TAG = "MainSettingsController";

    @Inject
    private SettingsPresenter presenter;
    @Inject
    ReportManager reportManager;

    private LinkSettingView watchLink;
    private LinkSettingView sitesSetting;
    private LinkSettingView filtersSetting;
    private LinkSettingView updateSettingView;
    private LinkSettingView reportSettingView;
    private BooleanSettingView collectCrashLogsSettingView;

    public MainSettingsController(Context context) {
        super(context);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        inject(this);

        navigation.setTitle(R.string.settings_screen);
        setupLayout();
        populatePreferences();
        buildPreferences();

        Disposable disposable = settingsNotificationManager.listenForNotificationUpdates()
                .subscribe((event) -> onNotificationsChanged(), (error) -> {
                    Logger.e(TAG, "Unknown error received from SettingsNotificationManager", error);
                });

        compositeDisposable.add(disposable);

        presenter.create(this);
    }

    @Override
    public void onShow() {
        super.onShow();

        presenter.show();
    }

    @Override
    public void setFiltersCount(int count) {
        String filters = getQuantityString(R.plurals.filter, count, count);
        filtersSetting.setDescription(filters);
    }

    @Override
    public void setSiteCount(int count) {
        String sites = getQuantityString(R.plurals.site, count, count);
        sitesSetting.setDescription(sites);
    }

    @Override
    public void setWatchEnabled(boolean enabled) {
        watchLink.setDescription(enabled
                ? R.string.setting_watch_summary_enabled
                : R.string.setting_watch_summary_disabled);
    }

    private void onNotificationsChanged() {
        Logger.d(TAG, "onNotificationsChanged called");

        updateSettingNotificationIcon(settingsNotificationManager.getOrDefault(SettingNotificationType.ApkUpdate),
                getViewGroupOrThrow(updateSettingView)
        );
        updateSettingNotificationIcon(settingsNotificationManager.getOrDefault(SettingNotificationType.CrashLog),
                getViewGroupOrThrow(reportSettingView)
        );
    }

    @Override
    public void onPreferenceChange(SettingView item) {
        super.onPreferenceChange(item);

        if (item == collectCrashLogsSettingView) {
            if (!ChanSettings.collectCrashLogs.get()) {
                // If disabled delete all already collected crash logs to cancel the notification
                // (if it's shown) and to avoid showing notification afterwards.

                reportManager.deleteAllCrashLogs();
            }
        }
    }

    private ViewGroup getViewGroupOrThrow(SettingView settingView) {
        if (!(settingView.getView() instanceof ViewGroup)) {
            throw new IllegalStateException("updateSettingView must have ViewGroup attached to it");
        }

        return (ViewGroup) settingView.getView();
    }

    private void populatePreferences() {
        // General group
        {
            SettingsGroup general = new SettingsGroup(R.string.settings_group_settings);

            watchLink = (LinkSettingView) general.add(new LinkSettingView(this,
                    R.string.settings_watch,
                    0,
                    v -> navigationController.pushController(new WatchSettingsController(context))
            ));

            sitesSetting = (LinkSettingView) general.add(new LinkSettingView(this,
                    R.string.settings_sites,
                    0,
                    v -> navigationController.pushController(new SitesSetupController(context))
            ));

            general.add(new LinkSettingView(this,
                    R.string.settings_appearance,
                    R.string.settings_appearance_description,
                    v -> navigationController.pushController(new AppearanceSettingsController(context))
            ));

            general.add(new LinkSettingView(this,
                    R.string.settings_behavior,
                    R.string.settings_behavior_description,
                    v -> navigationController.pushController(new BehaviourSettingsController(context))
            ));

            general.add(new LinkSettingView(this,
                    R.string.settings_media,
                    R.string.settings_media_description,
                    v -> navigationController.pushController(new MediaSettingsController(context))
            ));

            general.add(new LinkSettingView(this,
                    R.string.settings_import_export,
                    R.string.settings_import_export_description,
                    v -> navigationController.pushController(new ImportExportSettingsController(context,
                            () -> navigationController.popController()
                    ))
            ));

            filtersSetting = (LinkSettingView) general.add(new LinkSettingView(this,
                    R.string.settings_filters,
                    0,
                    v -> navigationController.pushController(new FiltersController(context))
            ));

            general.add(new LinkSettingView(this,
                    R.string.settings_experimental_settings_title,
                    R.string.settings_experimental_settings_description,
                    v -> navigationController.pushController(new ExperimentalSettingsController(context))
            ));

            addGroup(general);
        }

        setupAboutGroup();
    }

    private void setupAboutGroup() {
        SettingsGroup about = new SettingsGroup(R.string.settings_group_about);

        about.add(createUpdateSettingView());
        about.add(createReportSettingView());
        about.add(collectCrashLogsSettingView = new BooleanSettingView(this,
                ChanSettings.collectCrashLogs,
                R.string.settings_collect_crash_logs,
                R.string.settings_collect_crash_logs_description
        ));
        about.add(new LinkSettingView(this,
                "Find " + getApplicationLabel() + " on GitHub",
                "View the source code, give feedback, submit bug reports",
                v -> openLink(BuildConfig.GITHUB_ENDPOINT)
        ));

        about.add(new LinkSettingView(this,
                R.string.settings_about_license,
                R.string.settings_about_license_description,
                v -> navigationController.pushController(new LicensesController(context,
                        getString(R.string.settings_about_license),
                        "file:///android_asset/html/license.html"
                ))
        ));

        about.add(new LinkSettingView(this,
                R.string.settings_about_licenses,
                R.string.settings_about_licenses_description,
                v -> navigationController.pushController(new LicensesController(context,
                        getString(R.string.settings_about_licenses),
                        "file:///android_asset/html/licenses.html"
                ))
        ));

        about.add(new LinkSettingView(this,
                R.string.settings_developer,
                0,
                v -> navigationController.pushController(new DeveloperSettingsController(context))
        ));

        addGroup(about);
    }

    private LinkSettingView createReportSettingView() {
        reportSettingView =
                new LinkSettingView(this, R.string.settings_report, R.string.settings_report_description, v -> {
                    onReportSettingClick();
                });

        reportSettingView.setSettingNotificationType(SettingNotificationType.CrashLog);
        return reportSettingView;
    }

    private void onReportSettingClick() {
        int crashLogsCount = reportManager.countCrashLogs();

        if (crashLogsCount > 0) {
            new AlertDialog.Builder(context).setTitle(getString(R.string.settings_report_suggest_sending_logs_title,
                    crashLogsCount
            ))
                    .setMessage(R.string.settings_report_suggest_sending_logs_message)
                    .setPositiveButton(R.string.settings_report_review_button_text, (dialog, which) -> {
                        navigationController.pushController(new ReviewCrashLogsController(context));
                    })
                    .setNeutralButton(R.string.settings_report_review_later_button_text,
                            (dialog, which) -> openReportProblemController()
                    )
                    .setNegativeButton(R.string.settings_report_delete_all_crash_logs, (dialog, which) -> {
                        reportManager.deleteAllCrashLogs();
                        openReportProblemController();
                    })
                    .create()
                    .show();
            return;
        }

        openReportProblemController();
    }

    private void openReportProblemController() {
        navigationController.pushController(new ReportProblemController(context));
    }

    private LinkSettingView createUpdateSettingView() {
        updateSettingView = new LinkSettingView(this,
                getApplicationLabel() + " " + BuildConfig.VERSION_NAME + " " + (getBuildType() == Release ? "✓" : "✗"),
                "Tap to check for updates",
                v -> ((StartActivity) context).getUpdateManager().manualUpdateCheck()
        );

        updateSettingView.setSettingNotificationType(SettingNotificationType.ApkUpdate);
        return updateSettingView;
    }
}
