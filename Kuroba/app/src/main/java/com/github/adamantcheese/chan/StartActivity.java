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
package com.github.adamantcheese.chan;

import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.net.Uri;
import android.nfc.NdefMessage;
import android.nfc.NfcAdapter;
import android.nfc.NfcEvent;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.util.Pair;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.OnLifecycleEvent;

import com.airbnb.epoxy.EpoxyController;
import com.github.adamantcheese.chan.controller.Controller;
import com.github.adamantcheese.chan.controller.NavigationController;
import com.github.adamantcheese.chan.core.database.DatabaseLoadableManager;
import com.github.adamantcheese.chan.core.database.DatabaseManager;
import com.github.adamantcheese.chan.core.manager.UpdateManager;
import com.github.adamantcheese.chan.core.manager.WatchManager;
import com.github.adamantcheese.chan.core.model.orm.Board;
import com.github.adamantcheese.chan.core.model.orm.Loadable;
import com.github.adamantcheese.chan.core.model.orm.Pin;
import com.github.adamantcheese.chan.core.repository.SiteRepository;
import com.github.adamantcheese.chan.core.settings.ChanSettings;
import com.github.adamantcheese.chan.core.site.Site;
import com.github.adamantcheese.chan.core.site.SiteResolver;
import com.github.adamantcheese.chan.core.site.SiteService;
import com.github.adamantcheese.chan.ui.controller.BrowseController;
import com.github.adamantcheese.chan.ui.controller.DoubleNavigationController;
import com.github.adamantcheese.chan.ui.controller.DrawerController;
import com.github.adamantcheese.chan.ui.controller.SplitNavigationController;
import com.github.adamantcheese.chan.ui.controller.StyledToolbarNavigationController;
import com.github.adamantcheese.chan.ui.controller.ThreadSlideController;
import com.github.adamantcheese.chan.ui.controller.ViewThreadController;
import com.github.adamantcheese.chan.ui.helper.ImagePickDelegate;
import com.github.adamantcheese.chan.ui.helper.RuntimePermissionsHelper;
import com.github.adamantcheese.chan.ui.theme.ThemeHelper;
import com.github.adamantcheese.chan.utils.BackgroundUtils;
import com.github.adamantcheese.chan.utils.FullScreenUtilsKt;
import com.github.adamantcheese.chan.utils.Logger;
import com.github.k1rakishou.fsaf.FileChooser;
import com.github.k1rakishou.fsaf.callback.FSAFActivityCallbacks;

import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Stack;

import javax.inject.Inject;

import kotlin.jvm.functions.Function1;

import static android.content.Intent.FLAG_ACTIVITY_NEW_TASK;
import static com.github.adamantcheese.chan.Chan.inject;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getApplicationLabel;
import static com.github.adamantcheese.chan.utils.AndroidUtils.inflate;
import static com.github.adamantcheese.chan.utils.AndroidUtils.isTablet;
import static com.github.adamantcheese.chan.utils.AndroidUtils.openLink;
import static com.github.adamantcheese.chan.utils.AndroidUtils.showToast;

public class StartActivity
        extends AppCompatActivity
        implements NfcAdapter.CreateNdefMessageCallback, FSAFActivityCallbacks {
    private static final String TAG = "StartActivity";
    private static final String STATE_KEY = "chan_state";

    private ViewGroup contentView;
    private Stack<Controller> stack = new Stack<>();

    private DrawerController drawerController;
    private NavigationController mainNavigationController;
    private BrowseController browseController;

    private ImagePickDelegate imagePickDelegate;
    private RuntimePermissionsHelper runtimePermissionsHelper;
    private UpdateManager updateManager;

    private boolean intentMismatchWorkaroundActive = false;
    private boolean exitFlag = false;

    public static boolean loadedFromURL = false;

    @Inject
    DatabaseManager databaseManager;
    @Inject
    WatchManager watchManager;
    @Inject
    SiteResolver siteResolver;
    @Inject
    SiteService siteService;
    @Inject
    FileChooser fileChooser;
    @Inject
    ThemeHelper themeHelper;
    @Inject
    SiteRepository siteRepository;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        long start = System.currentTimeMillis();
        onCreateInternal(savedInstanceState);

        long diff = System.currentTimeMillis() - start;
        Logger.d(TAG, "StartActivity initialization took " + diff + "ms");
    }

    private void onCreateInternal(Bundle savedInstanceState) {
        if (intentMismatchWorkaround()) {
            return;
        }

        inject(this);

        if (BuildConfig.DEV_BUILD) {
            EpoxyController.setGlobalDebugLoggingEnabled(true);
        }

        themeHelper.setupContext(this);
        fileChooser.setCallbacks(this);
        imagePickDelegate = new ImagePickDelegate(this);
        runtimePermissionsHelper = new RuntimePermissionsHelper(this);
        updateManager = new UpdateManager(this);

        contentView = findViewById(android.R.id.content);
        FullScreenUtilsKt.setupFullscreen(this);

        // Setup base controllers, and decide if to use the split layout for tablets
        drawerController = new DrawerController(this);
        drawerController.onCreate();
        drawerController.onShow();

        setupLayout();

        setContentView(drawerController.view);
        pushController(drawerController);

        // Prevent overdraw
        // Do this after setContentView, or the decor creating will reset the background to a
        // default non-null drawable
        getWindow().setBackgroundDrawable(null);

        NfcAdapter adapter = NfcAdapter.getDefaultAdapter(this);
        if (adapter != null) {
            adapter.setNdefPushMessageCallback(this, this);
        }

        setupFromStateOrFreshLaunch(savedInstanceState);
        updateManager.autoUpdateCheck();

        if (ChanSettings.fullUserRotationEnable.get()) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_FULL_USER);
        }
    }

    private void setupFromStateOrFreshLaunch(Bundle savedInstanceState) {
        boolean handled;
        if (savedInstanceState != null) {
            handled = restoreFromSavedState(savedInstanceState);
        } else {
            handled = restoreFromUrl();
        }

        // Not from a state or from an url, launch the setup controller if no boards are setup up yet,
        // otherwise load the default saved board.
        if (!handled) {
            restoreFresh();
        }
    }

    private void restoreFresh() {
        if (!siteService.areSitesSetup()) {
            browseController.showSitesNotSetup();
        } else {
            browseController.loadWithDefaultBoard();
        }
    }

    private boolean restoreFromUrl() {
        boolean handled = false;

        final Uri data = getIntent().getData();
        // Start from an url launch.
        if (data != null) {
            final SiteResolver.LoadableResult loadableResult = siteResolver.resolveLoadableForUrl(data.toString());

            if (loadableResult != null) {
                handled = true;
                loadedFromURL = true;

                Loadable loadable = loadableResult.loadable;
                browseController.setBoard(loadable.board);

                if (loadable.isThreadMode()) {
                    browseController.showThread(loadable, false);
                }
            } else {
                new AlertDialog.Builder(this).setMessage(getString(R.string.open_link_not_matched,
                        getApplicationLabel()
                ))
                        .setPositiveButton(R.string.ok, (dialog, which) -> openLink(data.toString()))
                        .show();
            }
        }

        return handled;
    }

    private boolean restoreFromSavedState(Bundle savedInstanceState) {
        boolean handled = false;

        // Restore the activity state from the previously saved state.
        ChanState chanState = savedInstanceState.getParcelable(STATE_KEY);
        if (chanState == null) {
            Logger.w(TAG, "savedInstanceState was not null, but no ChanState was found!");
        } else {
            Pair<Loadable, Loadable> boardThreadPair = resolveChanState(chanState);

            if (boardThreadPair.first != null) {
                handled = true;

                browseController.setBoard(boardThreadPair.first.board);

                if (boardThreadPair.second != null) {
                    browseController.showThread(boardThreadPair.second, false);
                }
            }
        }

        return handled;
    }

    private Pair<Loadable, Loadable> resolveChanState(ChanState state) {
        Loadable boardLoadable = resolveLoadable(state.board, false);
        Loadable threadLoadable = resolveLoadable(state.thread, true);

        return new Pair<>(boardLoadable, threadLoadable);
    }

    private Loadable resolveLoadable(Loadable stateLoadable, boolean forThread) {
        // invalid (no state saved).
        if (stateLoadable.mode != (forThread ? Loadable.Mode.THREAD : Loadable.Mode.CATALOG)) {
            return null;
        }

        Site site = siteRepository.forId(stateLoadable.siteId);
        if (site != null) {
            Board board = site.board(stateLoadable.boardCode);
            if (board != null) {
                stateLoadable.site = site;
                stateLoadable.board = board;

                if (forThread) {
                    // When restarting the parcelable isn't actually deserialized, but the same
                    // object instance is reused. This means that the loadables we gave to the
                    // state are the same instance, and also have the id set etc. We don't need to
                    // query these from the loadablemanager.
                    DatabaseLoadableManager loadableManager = databaseManager.getDatabaseLoadableManager();
                    if (stateLoadable.id == 0) {
                        stateLoadable = loadableManager.get(stateLoadable);
                    }
                }

                return stateLoadable;
            }
        }

        return null;
    }

    private void setupLayout() {
        mainNavigationController = new StyledToolbarNavigationController(this);

        ChanSettings.LayoutMode layoutMode = ChanSettings.layoutMode.get();
        if (layoutMode == ChanSettings.LayoutMode.AUTO) {
            if (isTablet()) {
                layoutMode = ChanSettings.LayoutMode.SPLIT;
            } else {
                layoutMode = ChanSettings.LayoutMode.SLIDE;
            }
        }

        switch (layoutMode) {
            case SPLIT:
                SplitNavigationController split = new SplitNavigationController(this);
                split.setEmptyView(inflate(this, R.layout.layout_split_empty));

                drawerController.setChildController(split);

                split.setLeftController(mainNavigationController);
                break;
            case PHONE:
            case SLIDE:
                drawerController.setChildController(mainNavigationController);
                break;
        }

        browseController = new BrowseController(this);

        if (layoutMode == ChanSettings.LayoutMode.SLIDE) {
            ThreadSlideController slideController = new ThreadSlideController(this);
            slideController.setEmptyView(inflate(this, R.layout.layout_split_empty));
            mainNavigationController.pushController(slideController, false);
            slideController.setLeftController(browseController);
        } else {
            mainNavigationController.pushController(browseController, false);
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);

        // Handle WatchNotification clicks
        if (intent.getExtras() != null) {
            int pinId = intent.getExtras().getInt("pin_id", -2);
            if (pinId != -2 && mainNavigationController.getTop() instanceof BrowseController) {
                passIntentToBrowseController(pinId);
            } else if (pinId != -2 && mainNavigationController.getTop() instanceof ThreadSlideController) {
                passIntentToThreadSlideController(pinId);
            }
        }
    }

    private void passIntentToThreadSlideController(int pinId) {
        if (pinId == -1) {
            drawerController.onMenuClicked();
            return;
        }

        Pin pin = watchManager.findPinById(pinId);
        if (pin == null) {
            return;
        }

        List<Controller> controllers = mainNavigationController.childControllers;
        for (Controller controller : controllers) {
            if (controller instanceof ViewThreadController) {
                ((ViewThreadController) controller).loadThread(pin.loadable);
                break;
            } else if (controller instanceof ThreadSlideController) {
                ThreadSlideController slideNav = (ThreadSlideController) controller;
                if (slideNav.getRightController() instanceof ViewThreadController) {
                    ((ViewThreadController) slideNav.getRightController()).loadThread(pin.loadable);
                    slideNav.switchToController(false);
                    break;
                } else {
                    ViewThreadController v = new ViewThreadController(this, pin.loadable);
                    slideNav.setRightController(v);
                    slideNav.switchToController(false);
                    break;
                }
            }
        }
    }

    private void passIntentToBrowseController(int pinId) {
        if (pinId == -1) {
            drawerController.onMenuClicked();
            return;
        }

        Pin pin = watchManager.findPinById(pinId);
        if (pin != null) {
            browseController.showThread(pin.loadable, false);
        }
    }

    @Override
    public boolean dispatchKeyEvent(@NonNull KeyEvent event) {
        if (event.getKeyCode() == KeyEvent.KEYCODE_MENU && event.getAction() == KeyEvent.ACTION_DOWN) {
            drawerController.onMenuClicked();
            return true;
        }

        return stack.peek().dispatchKeyEvent(event) || super.dispatchKeyEvent(event);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        Loadable board = browseController.getLoadable();
        if (board == null) {
            Logger.w(TAG, "Can not save instance state, the board loadable is null");
            return;
        }

        Loadable thread = null;

        if (drawerController.childControllers.get(0) instanceof SplitNavigationController) {
            SplitNavigationController dblNav = (SplitNavigationController) drawerController.childControllers.get(0);
            if (dblNav.getRightController() instanceof NavigationController) {
                NavigationController rightNavigationController = (NavigationController) dblNav.getRightController();
                for (Controller controller : rightNavigationController.childControllers) {
                    if (controller instanceof ViewThreadController) {
                        thread = ((ViewThreadController) controller).getLoadable();
                        break;
                    }
                }
            }
        } else {
            List<Controller> controllers = mainNavigationController.childControllers;
            for (Controller controller : controllers) {
                if (controller instanceof ViewThreadController) {
                    thread = ((ViewThreadController) controller).getLoadable();
                    break;
                } else if (controller instanceof ThreadSlideController) {
                    ThreadSlideController slideNav = (ThreadSlideController) controller;
                    if (slideNav.getRightController() instanceof ViewThreadController) {
                        thread = ((ViewThreadController) slideNav.getRightController()).getLoadable();
                        break;
                    }
                }
            }
        }

        if (thread == null) {
            // Make the parcel happy
            thread = Loadable.emptyLoadable();
        }

        outState.putParcelable(STATE_KEY, new ChanState(board.clone(), thread.clone()));
    }

    @Override
    public NdefMessage createNdefMessage(NfcEvent event) {
        Controller threadController = null;
        if (drawerController.childControllers.get(0) instanceof DoubleNavigationController) {
            SplitNavigationController splitNavigationController =
                    (SplitNavigationController) drawerController.childControllers.get(0);
            if (splitNavigationController.rightController instanceof NavigationController) {
                NavigationController rightNavigationController =
                        (NavigationController) splitNavigationController.rightController;
                for (Controller controller : rightNavigationController.childControllers) {
                    if (controller instanceof NfcAdapter.CreateNdefMessageCallback) {
                        threadController = controller;
                        break;
                    }
                }
            }
        }

        if (threadController == null) {
            threadController = mainNavigationController.getTop();
        }

        if (threadController instanceof NfcAdapter.CreateNdefMessageCallback) {
            return ((NfcAdapter.CreateNdefMessageCallback) threadController).createNdefMessage(event);
        } else {
            return null;
        }
    }

    public void pushController(Controller controller) {
        stack.push(controller);
    }

    public boolean isControllerAdded(Function1<Controller, Boolean> predicate) {
        for (Controller controller : stack) {
            if (predicate.invoke(controller)) {
                return true;
            }
        }

        return false;
    }

    public void popController(Controller controller) {
        // we permit removal of things not on the top of the stack, but everything gets shifted down
        // so the top of the stack remains the same
        stack.remove(controller);
    }

    public ViewGroup getContentView() {
        return contentView;
    }

    public ImagePickDelegate getImagePickDelegate() {
        return imagePickDelegate;
    }

    public UpdateManager getUpdateManager() {
        return updateManager;
    }

    public RuntimePermissionsHelper getRuntimePermissionsHelper() {
        return runtimePermissionsHelper;
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        for (Controller controller : stack) {
            controller.onConfigurationChanged(newConfig);
        }
    }

    @Override
    public void onBackPressed() {
        if (!stack.peek().onBack()) {
            if (!exitFlag) {
                showToast(this, R.string.action_confirm_exit);
                exitFlag = true;
                BackgroundUtils.runOnMainThread(() -> exitFlag = false, 650);
            } else {
                exitFlag = false;
                StartActivity.super.onBackPressed();
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        runtimePermissionsHelper.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (intentMismatchWorkaround()) {
            return;
        }

        updateManager.onDestroy();
        imagePickDelegate.onDestroy();
        fileChooser.removeCallbacks();

        while (!stack.isEmpty()) {
            Controller controller = stack.pop();

            controller.onHide();
            controller.onDestroy();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (fileChooser.onActivityResult(requestCode, resultCode, data)) {
            return;
        }

        imagePickDelegate.onActivityResult(requestCode, resultCode, data);
    }

    private boolean intentMismatchWorkaround() {
        // Workaround for an intent mismatch that causes a new activity instance to be started
        // every time the app is launched from the launcher.
        // See https://issuetracker.google.com/issues/36907463
        // Still unfixed as of 5/15/2019
        if (intentMismatchWorkaroundActive) {
            return true;
        }

        if (!isTaskRoot()) {
            Intent intent = getIntent();
            if (intent.hasCategory(Intent.CATEGORY_LAUNCHER) && Intent.ACTION_MAIN.equals(intent.getAction())) {
                Logger.w(TAG, "Workaround for intent mismatch.");
                intentMismatchWorkaroundActive = true;
                finish();
                return true;
            }
        }
        return false;
    }

    public void restartApp() {
        Intent intent = new Intent(this, StartActivity.class);
        intent.addFlags(FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        finish();

        Runtime.getRuntime().exit(0);
    }

    @Override
    public void fsafStartActivityForResult(@NotNull Intent intent, int requestCode) {
        startActivityForResult(intent, requestCode);
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_START)
    public void onStart() {
        super.onStart();
        Logger.d(TAG, "start");
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_STOP)
    public void onStop() {
        super.onStop();
        Logger.d(TAG, "stop");
    }
}
