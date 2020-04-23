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
import android.content.res.Configuration;
import android.graphics.PorterDuff;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.widget.AppCompatImageView;

import com.github.adamantcheese.chan.R;
import com.github.adamantcheese.chan.StartActivity;
import com.github.adamantcheese.chan.controller.Controller;
import com.github.adamantcheese.chan.core.manager.SettingsNotificationManager;
import com.github.adamantcheese.chan.ui.helper.RefreshUIMessage;
import com.github.adamantcheese.chan.ui.settings.BooleanSettingView;
import com.github.adamantcheese.chan.ui.settings.IntegerSettingView;
import com.github.adamantcheese.chan.ui.settings.LinkSettingView;
import com.github.adamantcheese.chan.ui.settings.ListSettingView;
import com.github.adamantcheese.chan.ui.settings.SettingNotificationType;
import com.github.adamantcheese.chan.ui.settings.SettingView;
import com.github.adamantcheese.chan.ui.settings.SettingsGroup;
import com.github.adamantcheese.chan.ui.settings.StringSettingView;
import com.github.adamantcheese.chan.utils.AndroidUtils;
import com.github.adamantcheese.chan.utils.Logger;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

import io.reactivex.disposables.CompositeDisposable;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;
import static com.github.adamantcheese.chan.Chan.inject;
import static com.github.adamantcheese.chan.utils.AndroidUtils.dp;
import static com.github.adamantcheese.chan.utils.AndroidUtils.findViewsById;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getRes;
import static com.github.adamantcheese.chan.utils.AndroidUtils.inflate;
import static com.github.adamantcheese.chan.utils.AndroidUtils.isTablet;
import static com.github.adamantcheese.chan.utils.AndroidUtils.postToEventBus;
import static com.github.adamantcheese.chan.utils.AndroidUtils.updatePaddings;
import static com.github.adamantcheese.chan.utils.AndroidUtils.waitForLayout;

public class SettingsController
        extends Controller
        implements AndroidUtils.OnMeasuredCallback {
    private final String TAG = "SettingsController";

    @Inject
    protected SettingsNotificationManager settingsNotificationManager;

    protected LinearLayout content;
    private List<SettingsGroup> groups = new ArrayList<>();
    private List<SettingView> requiresUiRefresh = new ArrayList<>();
    // Very user unfriendly.
    private List<SettingView> requiresRestart = new ArrayList<>();
    protected CompositeDisposable compositeDisposable = new CompositeDisposable();
    private boolean needRestart = false;

    public SettingsController(Context context) {
        super(context);

        inject(this);
    }

    @Override
    public void onShow() {
        super.onShow();

        waitForLayout(view, this);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        disposeAndClearSettings();
        compositeDisposable.clear();

        if (needRestart) {
            ((StartActivity) context).restartApp();
        }
    }

    protected void disposeAndClearSettings() {
        for (SettingsGroup settingsGroup : groups) {
            for (SettingView settingView : settingsGroup.settingViews) {
                settingView.dispose();
            }
        }

        for (SettingView settingView : requiresUiRefresh) {
            settingView.dispose();
        }

        for (SettingView settingView : requiresRestart) {
            settingView.dispose();
        }

        groups.clear();
        requiresUiRefresh.clear();
        requiresRestart.clear();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        waitForLayout(view, this);
    }

    @Override
    public boolean onMeasured(View view) {
        setMargins();
        return false;
    }

    protected void addGroup(SettingsGroup settingsGroup) {
        groups.add(settingsGroup);
    }

    protected void addRequiresRestart(SettingView settingView) {
        requiresRestart.add(settingView);
    }

    protected void addRequiresUiRefresh(SettingView settingView) {
        requiresUiRefresh.add(settingView);
    }

    public void onPreferenceChange(SettingView item) {
        if ((item instanceof ListSettingView) || (item instanceof StringSettingView)
                || (item instanceof IntegerSettingView) || (item instanceof LinkSettingView)) {
            setDescriptionText(item.view, item.getTopDescription(), item.getBottomDescription());
        }

        if (requiresUiRefresh.contains(item)) {
            postToEventBus(new RefreshUIMessage("SettingsController refresh"));
        } else if (requiresRestart.contains(item)) {
            needRestart = true;
        }
    }

    private void setMargins() {
        int margin = 0;
        if (isTablet()) {
            margin = (int) (view.getWidth() * 0.1);
        }

        int itemMargin = 0;
        if (isTablet()) {
            itemMargin = dp(16);
        }

        List<View> groups = findViewsById(content, R.id.group);
        for (View group : groups) {
            LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) group.getLayoutParams();
            params.leftMargin = margin;
            params.rightMargin = margin;
            group.setLayoutParams(params);
        }

        List<View> items = findViewsById(content, R.id.preference_item);
        for (View item : items) {
            updatePaddings(item, itemMargin, itemMargin, -1, -1);
        }
    }

    protected void setSettingViewVisibility(SettingView settingView, boolean visible) {
        settingView.view.setVisibility(visible ? VISIBLE : GONE);

        if (settingView.divider != null) {
            settingView.divider.setVisibility(visible ? VISIBLE : GONE);
        }
    }

    protected void setupLayout() {
        view = inflate(context, R.layout.settings_layout);
        content = view.findViewById(R.id.scrollview_content);
    }

    protected void buildPreferences() {
        boolean firstGroup = true;
        content.removeAllViews();

        for (SettingsGroup group : groups) {
            LinearLayout groupLayout = (LinearLayout) inflate(context, R.layout.setting_group, content, false);
            ((TextView) groupLayout.findViewById(R.id.header)).setText(group.name);

            if (firstGroup) {
                firstGroup = false;
                ((LinearLayout.LayoutParams) groupLayout.getLayoutParams()).topMargin = 0;
            }

            content.addView(groupLayout);

            for (int i = 0; i < group.settingViews.size(); i++) {
                SettingView settingView = group.settingViews.get(i);

                ViewGroup preferenceView = null;
                String topValue = settingView.getTopDescription();
                String bottomValue = settingView.getBottomDescription();

                if ((settingView instanceof ListSettingView) || (settingView instanceof LinkSettingView)
                        || (settingView instanceof StringSettingView) || (settingView instanceof IntegerSettingView)) {
                    preferenceView = (ViewGroup) inflate(context, R.layout.setting_link, groupLayout, false);
                } else if (settingView instanceof BooleanSettingView) {
                    preferenceView = (ViewGroup) inflate(context, R.layout.setting_boolean, groupLayout, false);
                }

                if (preferenceView != null) {
                    setDescriptionText(preferenceView, topValue, bottomValue);

                    groupLayout.addView(preferenceView);
                    settingView.setView(preferenceView);
                }

                if (i < group.settingViews.size() - 1) {
                    settingView.divider = inflate(context, R.layout.setting_divider, groupLayout, false);
                    groupLayout.addView(settingView.divider);
                }
            }
        }
    }

    protected void updateSettingNotificationIcon(
            SettingNotificationType settingNotificationType, ViewGroup preferenceView
    ) {
        AppCompatImageView notificationIcon = preferenceView.findViewById(R.id.setting_notification_icon);

        if (notificationIcon != null) {
            updatePaddings(notificationIcon, dp(16), dp(16), -1, -1);
        } else {
            Logger.e(TAG, "Notification icon is null, can't update setting notification for this view.");
            return;
        }

        boolean hasNotifications = settingsNotificationManager.hasNotifications(settingNotificationType);

        if (settingNotificationType != SettingNotificationType.Default && hasNotifications) {
            int tintColor = getRes().getColor(settingNotificationType.getNotificationIconTintColor());

            notificationIcon.setColorFilter(tintColor, PorterDuff.Mode.SRC_IN);
            notificationIcon.setVisibility(VISIBLE);
        } else {
            notificationIcon.setVisibility(GONE);
        }
    }

    private void setDescriptionText(View view, String topText, String bottomText) {
        ((TextView) view.findViewById(R.id.top)).setText(topText);

        final TextView bottom = view.findViewById(R.id.bottom);
        if (bottom != null) {
            bottom.setText(bottomText);
            bottom.setVisibility(bottomText == null ? GONE : VISIBLE);
        }
    }
}
