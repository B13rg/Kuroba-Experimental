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
import android.widget.Toast;

import com.github.adamantcheese.chan.R;
import com.github.adamantcheese.chan.core.database.DatabaseManager;
import com.github.adamantcheese.chan.core.settings.ChanSettings;
import com.github.adamantcheese.chan.ui.controller.SitesSetupController;
import com.github.adamantcheese.chan.ui.controller.settings.captcha.JsCaptchaCookiesEditorController;
import com.github.adamantcheese.chan.ui.helper.RefreshUIMessage;
import com.github.adamantcheese.chan.ui.settings.BooleanSettingView;
import com.github.adamantcheese.chan.ui.settings.IntegerSettingView;
import com.github.adamantcheese.chan.ui.settings.LinkSettingView;
import com.github.adamantcheese.chan.ui.settings.SettingsGroup;
import com.github.adamantcheese.chan.ui.settings.StringSettingView;

import javax.inject.Inject;

import static com.github.adamantcheese.chan.Chan.inject;
import static com.github.adamantcheese.chan.utils.AndroidUtils.postToEventBus;
import static com.github.adamantcheese.chan.utils.AndroidUtils.showToast;

public class BehaviourSettingsController
        extends SettingsController {
    @Inject
    DatabaseManager databaseManager;

    public BehaviourSettingsController(Context context) {
        super(context);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        navigation.setTitle(R.string.settings_screen_behavior);

        inject(this);
        setupLayout();
        rebuildPreferences();
    }

    private void rebuildPreferences() {
        populatePreferences();
        buildPreferences();
    }

    private void populatePreferences() {
        disposeAndClearSettings();

        // General group (general application behavior)
        {
            SettingsGroup general = new SettingsGroup(R.string.settings_group_general);

            general.add(new BooleanSettingView(this,
                    ChanSettings.autoRefreshThread,
                    R.string.setting_auto_refresh_thread,
                    0
            ));

            addRequiresRestart(general.add(new BooleanSettingView(this,
                    ChanSettings.controllerSwipeable,
                    R.string.setting_controller_swipeable,
                    0
            )));

            general.add(new BooleanSettingView(this,
                    ChanSettings.openLinkConfirmation,
                    R.string.setting_open_link_confirmation,
                    0
            ));

            general.add(new BooleanSettingView(this,
                    ChanSettings.openLinkBrowser,
                    R.string.setting_open_link_browser,
                    0
            ));

            general.add(new BooleanSettingView(this,
                    ChanSettings.imageViewerGestures,
                    R.string.setting_image_viewer_gestures,
                    R.string.setting_image_viewer_gestures_description
            ));

            general.add(new BooleanSettingView(this,
                    ChanSettings.alwaysOpenDrawer,
                    R.string.settings_always_open_drawer,
                    0
            ));

            general.add(new LinkSettingView(this,
                    R.string.settings_captcha_setup,
                    R.string.settings_captcha_setup_description,
                    v -> navigationController.pushController(new SitesSetupController(context))
            ));
            general.add(new LinkSettingView(this,
                    R.string.settings_js_captcha_cookies_title,
                    R.string.settings_js_captcha_cookies_description,
                    v -> {
                        navigationController.pushController(new JsCaptchaCookiesEditorController(context));
                    }
            ));

            general.add(new LinkSettingView(this, R.string.setting_clear_thread_hides, 0, v -> {
                // TODO: don't do this here.
                databaseManager.runTask(databaseManager.getDatabaseHideManager().clearAllThreadHides());
                showToast(context, R.string.setting_cleared_thread_hides, Toast.LENGTH_LONG);
                postToEventBus(new RefreshUIMessage("clearhides"));
            }));

            addGroup(general);
        }

        // Reply group (reply input specific behavior)
        {
            SettingsGroup reply = new SettingsGroup(R.string.settings_group_reply);

            reply.add(new BooleanSettingView(this, ChanSettings.postPinThread, R.string.setting_post_pin, 0));

            reply.add(new StringSettingView(this,
                    ChanSettings.postDefaultName,
                    R.string.setting_post_default_name,
                    R.string.setting_post_default_name
            ));

            addGroup(reply);
        }

        // Post group (post/thread specific behavior)
        {
            SettingsGroup post = new SettingsGroup(R.string.settings_group_post);

            post.add(new BooleanSettingView(this,
                    ChanSettings.repliesButtonsBottom,
                    R.string.setting_buttons_bottom,
                    0
            ));

            post.add(new BooleanSettingView(this,
                    ChanSettings.volumeKeysScrolling,
                    R.string.setting_volume_key_scrolling,
                    0
            ));

            post.add(new BooleanSettingView(this, ChanSettings.tapNoReply, R.string.setting_tap_no_rely, 0));

            post.add(new BooleanSettingView(this,
                    ChanSettings.enableLongPressURLCopy,
                    R.string.settings_image_long_url,
                    R.string.settings_image_long_url_description
            ));

            post.add(new BooleanSettingView(this,
                    ChanSettings.shareUrl,
                    R.string.setting_share_url,
                    R.string.setting_share_url_description
            ));

            //this is also in Appearance settings
            post.add(new BooleanSettingView(this,
                    ChanSettings.enableEmoji,
                    R.string.setting_enable_emoji,
                    R.string.setting_enable_emoji_description
            ));

            addRequiresUiRefresh(
                    post.add(
                            new BooleanSettingView(
                                    this,
                                    ChanSettings.markUnseenPosts,
                                    R.string.setting_mark_unseen_posts_title,
                                    R.string.setting_mark_unseen_posts_duration
                            )
                    )
            );

            addGroup(post);
        }

        {
            SettingsGroup other = new SettingsGroup("Other Options");

            other.add(new StringSettingView(this,
                    ChanSettings.parseYoutubeAPIKey,
                    "Youtube API Key",
                    "Youtube API Key"
            ));

            addRequiresRestart(other.add(new BooleanSettingView(this,
                    ChanSettings.fullUserRotationEnable,
                    R.string.setting_full_screen_rotation,
                    0
            )));

            other.add(new BooleanSettingView(this,
                    ChanSettings.allowFilePickChooser,
                    "Allow alternate file pickers",
                    "If you'd prefer to use a different file chooser, turn this on"
            ));

            other.add(new BooleanSettingView(this,
                    ChanSettings.allowMediaScannerToScanLocalThreads,
                    R.string.settings_allow_media_scanner_scan_local_threads_title,
                    R.string.settings_allow_media_scanner_scan_local_threads_description
            ));

            other.add(new BooleanSettingView(this,
                    ChanSettings.showCopyApkUpdateDialog,
                    R.string.settings_show_copy_apk_dialog_title,
                    R.string.settings_show_copy_apk_dialog_message
            ));

            addGroup(other);
        }

        // Proxy group (proxy settings)
        {
            SettingsGroup proxy = new SettingsGroup(R.string.settings_group_proxy);

            addRequiresRestart(proxy.add(new BooleanSettingView(this,
                    ChanSettings.proxyEnabled,
                    R.string.setting_proxy_enabled,
                    0
            )));

            addRequiresRestart(proxy.add(new StringSettingView(this,
                    ChanSettings.proxyAddress,
                    R.string.setting_proxy_address,
                    R.string.setting_proxy_address
            )));

            addRequiresRestart(proxy.add(new IntegerSettingView(this,
                    ChanSettings.proxyPort,
                    R.string.setting_proxy_port,
                    R.string.setting_proxy_port
            )));

            addGroup(proxy);
        }
    }
}
