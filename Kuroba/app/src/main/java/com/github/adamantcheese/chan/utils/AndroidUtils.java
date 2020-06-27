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
package com.github.adamantcheese.chan.utils;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Application;
import android.app.Dialog;
import android.app.NotificationManager;
import android.app.job.JobScheduler;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.Signature;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.graphics.Point;
import android.graphics.drawable.Drawable;
import android.media.AudioManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Build;
import android.os.StatFs;
import android.preference.PreferenceManager;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.browser.customtabs.CustomTabsIntent;

import com.github.adamantcheese.chan.BuildConfig;
import com.github.adamantcheese.chan.R;
import com.github.adamantcheese.chan.core.manager.GlobalWindowInsetsManager;
import com.github.adamantcheese.chan.ui.theme.Theme;
import com.google.android.material.snackbar.Snackbar;

import org.greenrobot.eventbus.EventBus;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import static android.content.Context.AUDIO_SERVICE;
import static android.content.Context.CLIPBOARD_SERVICE;
import static android.content.Context.INPUT_METHOD_SERVICE;
import static android.content.Context.JOB_SCHEDULER_SERVICE;
import static android.content.Context.MODE_PRIVATE;
import static android.content.Context.NOTIFICATION_SERVICE;
import static android.content.res.Configuration.ORIENTATION_LANDSCAPE;
import static android.content.res.Configuration.ORIENTATION_PORTRAIT;
import static android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT;
import static com.github.adamantcheese.chan.utils.AndroidUtils.VerifiedBuildType.Debug;
import static com.github.adamantcheese.chan.utils.AndroidUtils.VerifiedBuildType.Release;
import static com.github.adamantcheese.chan.utils.AndroidUtils.VerifiedBuildType.Unknown;

public class AndroidUtils {
    private static final String TAG = "AndroidUtils";
    private static final String CHAN_STATE_PREFS_NAME = "chan_state";

    @SuppressLint("StaticFieldLeak")
    private static Application application;

    public static void init(Application application) {
        if (AndroidUtils.application == null) {
            AndroidUtils.application = application;
        }
    }

    public static Resources getRes() {
        return application.getResources();
    }

    public static Context getAppContext() {
        return application;
    }

    public static String getString(int res) {
        try {
            return getRes().getString(res);
        } catch (Exception e) {
            return null;
        }
    }

    public static String getString(int res, Object... formatArgs) {
        return getRes().getString(res, formatArgs);
    }

    public static String getQuantityString(int res, int quantity) {
        return getRes().getQuantityString(res, quantity);
    }

    public static String getQuantityString(int res, int quantity, Object... formatArgs) {
        return getRes().getQuantityString(res, quantity, formatArgs);
    }

    public static CharSequence getApplicationLabel() {
        return application.getPackageManager().getApplicationLabel(application.getApplicationInfo());
    }

    public static String getAppFileProvider() {
        return application.getPackageName() + ".fileprovider";
    }

    public static SharedPreferences getPreferences() {
        return PreferenceManager.getDefaultSharedPreferences(application);
    }

    public static SharedPreferences getAppState() {
        return getAppContext().getSharedPreferences(CHAN_STATE_PREFS_NAME, MODE_PRIVATE);
    }

    public static VerifiedBuildType getVerifiedBuildType() {
        try {
            @SuppressLint("PackageManagerGetSignatures")
            Signature sig = application.getPackageManager().getPackageInfo(
                    application.getPackageName(),
                    PackageManager.GET_SIGNING_CERTIFICATES
            ).signingInfo.getApkContentsSigners()[0];

            String signatureHexString =
                    HashingUtil.byteArrayHashSha256HexString(sig.toByteArray()).toUpperCase();
            Logger.d(TAG, "Build Signature: " + signatureHexString);

            boolean isOfficialRelease = BuildConfig.RELEASE_SIGNATURE.equals(signatureHexString);
            if (isOfficialRelease) {
                return Release;
            }

            boolean isOfficialBeta = BuildConfig.DEBUG_SIGNATURE.equals(signatureHexString);
            if (isOfficialBeta) {
                return Debug;
            }

            return Unknown;
        } catch (Exception ignored) {
            return Unknown;
        }
    }

    public static FlavorType getFlavorType() {
        switch (BuildConfig.FLAVOR_TYPE) {
            case 0:
                return FlavorType.Release;
            case 1:
                return FlavorType.Beta;
            case 2:
                return FlavorType.Dev;
            default:
                throw new RuntimeException("Unknown flavor type " + BuildConfig.FLAVOR_TYPE);
        }
    }

    /**
     * Tries to open an app that can open the specified URL.<br>
     * If this app will open the link then show a chooser to the user without this app.<br>
     * Else allow the default logic to run with startActivity.
     *
     * @param link url to open
     */
    public static void openLink(String link) {
        if (TextUtils.isEmpty(link)) {
            showToast(application, R.string.open_link_failed, Toast.LENGTH_LONG);
            return;
        }
        PackageManager pm = application.getPackageManager();

        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(link));

        ComponentName resolvedActivity = intent.resolveActivity(pm);
        if (resolvedActivity == null) {
            showToast(application, R.string.open_link_failed, Toast.LENGTH_LONG);
        } else {
            boolean thisAppIsDefault = resolvedActivity.getPackageName().equals(application.getPackageName());
            if (!thisAppIsDefault) {
                openIntent(intent);
            } else {
                // Get all intents that match, and filter out this app
                List<ResolveInfo> resolveInfos = pm.queryIntentActivities(intent, 0);
                List<Intent> filteredIntents = new ArrayList<>(resolveInfos.size());
                for (ResolveInfo info : resolveInfos) {
                    if (!info.activityInfo.packageName.equals(application.getPackageName())) {
                        Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(link));
                        i.setPackage(info.activityInfo.packageName);
                        filteredIntents.add(i);
                    }
                }

                if (filteredIntents.size() > 0) {
                    // Create a chooser for the last app in the list, and add the rest with EXTRA_INITIAL_INTENTS that get placed above
                    Intent chooser = Intent.createChooser(filteredIntents.remove(filteredIntents.size() - 1), null);
                    chooser.putExtra(Intent.EXTRA_INITIAL_INTENTS, filteredIntents.toArray(new Intent[0]));
                    openIntent(chooser);
                } else {
                    showToast(application, R.string.open_link_failed, Toast.LENGTH_LONG);
                }
            }
        }
    }

    public static void openLinkInBrowser(Context context, String link, Theme theme) {
        if (TextUtils.isEmpty(link)) {
            showToast(context, R.string.open_link_failed);
            return;
        }

        // Hack that's sort of the same as openLink
        // The link won't be opened in a custom tab if this app is the default handler for that link.
        // Manually check if this app opens it instead of a custom tab, and use the logic of
        // openLink to avoid that and show a chooser instead.
        boolean openWithCustomTabs = true;
        Intent urlIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(link));
        PackageManager pm = application.getPackageManager();
        ComponentName resolvedActivity = urlIntent.resolveActivity(pm);

        if (resolvedActivity != null) {
            openWithCustomTabs = !resolvedActivity.getPackageName().equals(application.getPackageName());
        }

        if (openWithCustomTabs) {
            CustomTabsIntent tabsIntent = new CustomTabsIntent.Builder().setToolbarColor(
                    theme.primaryColor.color
            ).build();

            try {
                tabsIntent.launchUrl(context, Uri.parse(link));
            } catch (ActivityNotFoundException e) {
                // Can't check it beforehand so catch the exception
                showToast(context, R.string.open_link_failed, Toast.LENGTH_LONG);
            }
        } else {
            openLink(link);
        }
    }

    public static void shareLink(String link) {
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_TEXT, link);
        Intent chooser = Intent.createChooser(intent, getString(R.string.action_share));
        openIntent(chooser);
    }

    public static void openIntent(Intent intent) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (intent.resolveActivity(application.getPackageManager()) != null) {
            application.startActivity(intent);
        } else {
            showToast(application, R.string.open_link_failed, Toast.LENGTH_LONG);
        }
    }

    public static int getAttrColor(Context context, int attr) {
        TypedArray typedArray = context.getTheme().obtainStyledAttributes(new int[]{attr});
        int color = typedArray.getColor(0, 0);
        typedArray.recycle();
        return color;
    }

    public static Drawable getAttrDrawable(Context context, int attr) {
        TypedArray typedArray = context.obtainStyledAttributes(new int[]{attr});
        Drawable drawable = typedArray.getDrawable(0);
        typedArray.recycle();
        return drawable;
    }

    public static boolean isTablet() {
        return getRes().getBoolean(R.bool.is_tablet);
    }

    public static int getDimen(int dimen) {
        return getRes().getDimensionPixelSize(dimen);
    }

    public static File getAppDir() {
        return application.getFilesDir().getParentFile();
    }

    public static int dp(float dp) {
        return (int) (dp * getRes().getDisplayMetrics().density);
    }

    public static int sp(float sp) {
        return (int) (sp * getRes().getDisplayMetrics().scaledDensity);
    }

    public static void requestKeyboardFocus(Dialog dialog, final View view) {
        view.requestFocus();
        dialog.setOnShowListener(dialog1 -> requestKeyboardFocus(view));
    }

    public static void requestKeyboardFocus(final View view) {
        getInputManager().showSoftInput(view, SHOW_IMPLICIT);
    }

    public static void hideKeyboard(View view) {
        if (view != null) {
            getInputManager().hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }

    public static void requestViewAndKeyboardFocus(View view) {
        view.setFocusable(false);
        view.setFocusableInTouchMode(true);
        if (view.requestFocus()) {
            getInputManager().showSoftInput(view, SHOW_IMPLICIT);
        }
    }

    public static void updatePaddings(View view, int left, int right, int top, int bottom) {
        int newLeft = left;
        if (newLeft < 0) {
            newLeft = view.getPaddingLeft();
        }

        int newRight = right;
        if (newRight < 0) {
            newRight = view.getPaddingRight();
        }

        int newTop = top;
        if (newTop < 0) {
            newTop = view.getPaddingTop();
        }

        int newBottom = bottom;
        if (newBottom < 0) {
            newBottom = view.getPaddingBottom();
        }

        view.setPadding(newLeft, newTop, newRight, newBottom);
    }

    public interface OnMeasuredCallback {
        /**
         * Called when the layout is done.
         *
         * @param view same view as the argument.
         * @return true to continue with rendering, false to cancel and redo the layout.
         */
        boolean onMeasured(View view);
    }

    /**
     * Waits for a measure. Calls callback immediately if the view width and height are more than 0.
     * Otherwise it registers an onpredrawlistener.
     * <b>Warning: the view you give must be attached to the view root!</b>
     */
    public static void waitForMeasure(final View view, final OnMeasuredCallback callback) {
        if (view.getWindowToken() == null) {
            // If you call getViewTreeObserver on a view when it's not attached to a window will result in the creation of a temporarily viewtreeobserver.
            view.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
                @Override
                public void onViewAttachedToWindow(View v) {
                    waitForLayoutInternal(true, view.getViewTreeObserver(), view, callback);
                    view.removeOnAttachStateChangeListener(this);
                }

                @Override
                public void onViewDetachedFromWindow(View v) {
                    view.removeOnAttachStateChangeListener(this);
                }
            });
            return;
        }

        waitForLayoutInternal(true, view.getViewTreeObserver(), view, callback);
    }

    /**
     * Always registers an onpredrawlistener.
     * <b>Warning: the view you give must be attached to the view root!</b>
     */
    public static void waitForLayout(final View view, final OnMeasuredCallback callback) {
        if (view.getWindowToken() == null) {
            // See comment above
            view.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
                @Override
                public void onViewAttachedToWindow(View v) {
                    waitForLayoutInternal(true, view.getViewTreeObserver(), view, callback);
                    view.removeOnAttachStateChangeListener(this);
                }

                @Override
                public void onViewDetachedFromWindow(View v) {
                    view.removeOnAttachStateChangeListener(this);
                }
            });
            return;
        }

        waitForLayoutInternal(false, view.getViewTreeObserver(), view, callback);
    }

    /**
     * Always registers an onpredrawlistener. The given ViewTreeObserver will be used.
     */
    public static void waitForLayout(
            final ViewTreeObserver viewTreeObserver, final View view, final OnMeasuredCallback callback
    ) {
        waitForLayoutInternal(false, viewTreeObserver, view, callback);
    }

    private static void waitForLayoutInternal(
            boolean returnIfNotZero,
            final ViewTreeObserver viewTreeObserver,
            final View view,
            final OnMeasuredCallback callback
    ) {
        int width = view.getWidth();
        int height = view.getHeight();

        if (returnIfNotZero && width > 0 && height > 0) {
            callback.onMeasured(view);
        } else {
            viewTreeObserver.addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
                @Override
                public boolean onPreDraw() {
                    ViewTreeObserver usingViewTreeObserver = viewTreeObserver;
                    if (viewTreeObserver != view.getViewTreeObserver()) {
                        Logger.e(
                                TAG,
                                "view.getViewTreeObserver() is another viewtreeobserver! replacing with the new one"
                        );
                        usingViewTreeObserver = view.getViewTreeObserver();
                    }

                    if (usingViewTreeObserver.isAlive()) {
                        usingViewTreeObserver.removeOnPreDrawListener(this);
                    } else {
                        Logger.e(
                                TAG,
                                "ViewTreeObserver not alive, could not remove onPreDrawListener! This will probably not end well"
                        );
                    }

                    boolean ret;
                    try {
                        ret = callback.onMeasured(view);
                    } catch (Exception e) {
                        Logger.i(TAG, "Exception in onMeasured", e);
                        throw e;
                    }

                    if (!ret) {
                        Logger.d(TAG, "waitForLayout requested a re-layout by returning false");
                    }

                    return ret;
                }
            });
        }
    }

    public static void setBoundlessRoundRippleBackground(View view) {
        TypedValue outValue = new TypedValue();
        view.getContext().getTheme().resolveAttribute(
                android.R.attr.selectableItemBackgroundBorderless,
                outValue,
                true
        );

        view.setBackgroundResource(outValue.resourceId);
    }

    public static List<View> findViewsById(ViewGroup root, int id) {
        List<View> views = new ArrayList<>();
        int childCount = root.getChildCount();
        for (int i = 0; i < childCount; i++) {
            View child = root.getChildAt(i);
            if (child instanceof ViewGroup) {
                views.addAll(findViewsById((ViewGroup) child, id));
            }

            if (child.getId() == id) {
                views.add(child);
            }
        }

        return views;
    }

    public static boolean removeFromParentView(View view) {
        if (view.getParent() instanceof ViewGroup && ((ViewGroup) view.getParent()).indexOfChild(view) >= 0) {
            ((ViewGroup) view.getParent()).removeView(view);
            return true;
        } else {
            return false;
        }
    }

    public static void fixSnackbarText(Context context, Snackbar snackbar) {
        ((TextView) snackbar.getView().findViewById(R.id.snackbar_text)).setTextColor(Color.WHITE);
        snackbar.setActionTextColor(getAttrColor(context, R.attr.colorAccent));
        snackbar.getView().setBackgroundColor(getAttrColor(context, R.attr.backcolor_secondary));
    }

    /**
     * We only need to use this method in DrawerController. Snackbar "should" work normally in all
     * other places.
     * */
    public static void fixSnackbarInsets(
            @NonNull Snackbar snackbar,
            @NonNull GlobalWindowInsetsManager globalWindowInsetsManager
    ) {
        snackbar.getView().setPadding(
                globalWindowInsetsManager.left(),
                0,
                0,
                globalWindowInsetsManager.bottom()
        );
    }

    public static boolean isConnected(int type) {
        ConnectivityManager connectivityManager =
                (ConnectivityManager) application.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = connectivityManager.getNetworkInfo(type);
        return networkInfo != null && networkInfo.isConnected();
    }

    public static Point getDisplaySize() {
        Point displaySize = new Point();
        WindowManager windowManager = (WindowManager) application.getSystemService(Activity.WINDOW_SERVICE);
        windowManager.getDefaultDisplay().getRealSize(displaySize);
        return displaySize;
    }

    /**
     * These two methods get the screen size ignoring the current screen orientation.
     */
    public static int getMinScreenSize() {
        Point displaySize = getDisplaySize();
        return Math.min(displaySize.x, displaySize.y);
    }

    public static int getMaxScreenSize() {
        Point displaySize = getDisplaySize();
        return Math.max(displaySize.x, displaySize.y);
    }

    public static Window getWindow(Context context) {
        if (context instanceof Activity) {
            return ((Activity) context).getWindow();
        } else {
            return null;
        }
    }

    public static void showToast(Context context, String message, int duration) {
        BackgroundUtils.runOnMainThread(() -> Toast.makeText(context, message, duration).show());
    }

    public static void showToast(Context context, String message) {
        showToast(context, message, Toast.LENGTH_SHORT);
    }

    public static void showToast(Context context, int resId, int duration) {
        showToast(context, getString(resId), duration);
    }

    public static void showToast(Context context, int resId) {
        showToast(context, getString(resId));
    }

    private static InputMethodManager getInputManager() {
        return (InputMethodManager) application.getSystemService(INPUT_METHOD_SERVICE);
    }

    public static ClipboardManager getClipboardManager() {
        return (ClipboardManager) application.getSystemService(CLIPBOARD_SERVICE);
    }

    public static String getClipboardContent() {
        ClipData primary = getClipboardManager().getPrimaryClip();
        if (primary != null) {
            return primary.getItemAt(0).getText().toString();
        } else {
            return "";
        }
    }

    public static void setClipboardContent(String label, String content) {
        getClipboardManager().setPrimaryClip(ClipData.newPlainText(label, content));
    }

    public static NotificationManager getNotificationManager() {
        return (NotificationManager) application.getSystemService(NOTIFICATION_SERVICE);
    }

    public static JobScheduler getJobScheduler() {
        return (JobScheduler) application.getSystemService(JOB_SCHEDULER_SERVICE);
    }

    public static AudioManager getAudioManager() {
        return (AudioManager) getAppContext().getSystemService(AUDIO_SERVICE);
    }

    public static View inflate(Context context, int resId, ViewGroup root) {
        return LayoutInflater.from(context).inflate(resId, root);
    }

    public static View inflate(Context context, int resId, ViewGroup root, boolean attachToRoot) {
        return LayoutInflater.from(context).inflate(resId, root, attachToRoot);
    }

    public static ViewGroup inflate(Context context, int resId) {
        return (ViewGroup) LayoutInflater.from(context).inflate(resId, null);
    }

    public static void postToEventBus(Object message) {
        EventBus.getDefault().post(message);
    }

    public static boolean isAndroid10() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q;
    }

    public static int getScreenOrientation() {
        int screenOrientation = getAppContext().getResources().getConfiguration().orientation;
        if (screenOrientation != ORIENTATION_LANDSCAPE && screenOrientation != ORIENTATION_PORTRAIT) {
            throw new IllegalStateException("Illegal screen orientation value! value = " + screenOrientation);
        }

        return screenOrientation;
    }

    /**
     * Change to ConnectivityManager#registerDefaultNetworkCallback when minSdk == 24, basically never
     */
    public static String getNetworkClass(@NonNull ConnectivityManager connectivityManager) {
        NetworkInfo info = connectivityManager.getActiveNetworkInfo();
        if (info == null || !info.isConnected()) {
            return "No connected"; // not connected
        }

        if (info.getType() == ConnectivityManager.TYPE_WIFI) {
            return "WIFI";
        }

        if (info.getType() == ConnectivityManager.TYPE_MOBILE) {
            int networkType = info.getSubtype();
            switch (networkType) {
                case TelephonyManager.NETWORK_TYPE_GPRS:
                case TelephonyManager.NETWORK_TYPE_EDGE:
                case TelephonyManager.NETWORK_TYPE_CDMA:
                case TelephonyManager.NETWORK_TYPE_1xRTT:
                case TelephonyManager.NETWORK_TYPE_IDEN:     // api< 8: replace by 11
                case TelephonyManager.NETWORK_TYPE_GSM:      // api<25: replace by 16
                    return "2G";
                case TelephonyManager.NETWORK_TYPE_UMTS:
                case TelephonyManager.NETWORK_TYPE_EVDO_0:
                case TelephonyManager.NETWORK_TYPE_EVDO_A:
                case TelephonyManager.NETWORK_TYPE_HSDPA:
                case TelephonyManager.NETWORK_TYPE_HSUPA:
                case TelephonyManager.NETWORK_TYPE_HSPA:
                case TelephonyManager.NETWORK_TYPE_EVDO_B:   // api< 9: replace by 12
                case TelephonyManager.NETWORK_TYPE_EHRPD:    // api<11: replace by 14
                case TelephonyManager.NETWORK_TYPE_HSPAP:    // api<13: replace by 15
                case TelephonyManager.NETWORK_TYPE_TD_SCDMA: // api<25: replace by 17
                    return "3G";
                case TelephonyManager.NETWORK_TYPE_LTE:      // api<11: replace by 13
                case TelephonyManager.NETWORK_TYPE_IWLAN:    // api<25: replace by 18
                case 19: // LTE_CA
                    return "4G";
                case TelephonyManager.NETWORK_TYPE_NR:       // api<29: replace by 20
                    return "5G";
            }
        }

        return "Unknown";
    }

    public static long getAvailableSpaceInBytes(File file) {
        long availableSpace = -1L;
        StatFs stat = new StatFs(file.getPath());
        availableSpace = stat.getAvailableBlocksLong() * stat.getBlockSizeLong();

        return availableSpace;
    }

    public enum FlavorType {
        Release,
        Beta,
        Dev
    }

    public enum VerifiedBuildType {
        Debug,
        Release,
        Unknown
    }
}
