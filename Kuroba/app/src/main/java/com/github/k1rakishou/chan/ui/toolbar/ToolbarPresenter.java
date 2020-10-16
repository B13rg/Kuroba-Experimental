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
package com.github.k1rakishou.chan.ui.toolbar;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.github.k1rakishou.chan.core.manager.BottomNavBarVisibilityStateManager;
import com.github.k1rakishou.chan.core.settings.ChanSettings;
import com.github.k1rakishou.chan.ui.theme.ChanTheme;
import com.github.k1rakishou.chan.ui.theme.ThemeEngine;

import javax.inject.Inject;

import static com.github.k1rakishou.chan.Chan.inject;

public class ToolbarPresenter {

    @Inject
    BottomNavBarVisibilityStateManager bottomNavBarVisibilityStateManager;

    private Callback callback;
    private ThemeEngine themeEngine;

    private NavigationItem item;
    private NavigationItem transition;

    public ToolbarPresenter(Callback callback, ThemeEngine themeEngine) {
        this.callback = callback;
        this.themeEngine = themeEngine;

        inject(this);
    }

    public void onAttached() {
        // no-op
    }

    public void onDetached() {
        // no-op
    }

    void set(
            NavigationItem newItem,
            ChanTheme theme,
            AnimationStyle animation
    ) {
        set(newItem, theme, animation, null);
    }

    void set(
            NavigationItem newItem,
            ChanTheme theme,
            AnimationStyle animation,
            @Nullable ToolbarContainer.ToolbarTransitionAnimationListener listener
    ) {
        cancelTransitionIfNeeded();
        if (closeSearchIfNeeded()) {
            animation = AnimationStyle.FADE;
        }

        item = newItem;

        callback.showForNavigationItem(item, theme, animation, listener);
    }

    void update(NavigationItem updatedItem) {
        callback.updateViewForItem(updatedItem);
    }

    void startTransition(NavigationItem newItem) {
        cancelTransitionIfNeeded();
        if (closeSearchIfNeeded()) {
            callback.showForNavigationItem(item, themeEngine.getChanTheme(), AnimationStyle.NONE);
        }

        transition = newItem;

        callback.containerStartTransition(transition, TransitionAnimationStyle.POP);
    }

    void stopTransition(boolean didComplete) {
        if (transition == null) {
            return;
        }

        callback.containerStopTransition(didComplete);

        if (didComplete) {
            item = transition;
            callback.showForNavigationItem(item, themeEngine.getChanTheme(), AnimationStyle.NONE);
        }

        transition = null;
    }

    void setTransitionProgress(float progress) {
        if (transition == null) {
            return;
        }

        callback.containerSetTransitionProgress(progress);
    }

    void openSearch() {
        openSearch(null, null);
    }

    void openSearch(@Nullable ToolbarContainer.ToolbarTransitionAnimationListener listener) {
        openSearch(null, listener);
    }

    void openSearch(
            @Nullable String input,
            @Nullable ToolbarContainer.ToolbarTransitionAnimationListener listener
    ) {
        if (item == null || item.search) {
            return;
        }

        cancelTransitionIfNeeded();

        item.searchText = input;
        item.search = true;

        callback.showForNavigationItem(item, themeEngine.getChanTheme(), AnimationStyle.NONE, listener);
        callback.onSearchVisibilityChanged(item, true);
    }

    boolean closeSearch() {
        if (item == null || !item.search) {
            return false;
        }

        item.search = false;
        item.searchText = null;
        set(item, null, AnimationStyle.FADE);

        callback.onSearchVisibilityChanged(item, false);
        return true;
    }

    void enterSelectionMode(String text) {
        if (item == null || item.selectionMode) {
            return;
        }

        cancelTransitionIfNeeded();

        item.selectionMode = true;
        item.selectionStateText = text;

        callback.showForNavigationItem(item, themeEngine.getChanTheme(), AnimationStyle.NONE);
    }

    boolean isInSelectionMode() {
        return item.selectionMode;
    }

    void exitSelectionMode() {
        if (item == null || !item.selectionMode) {
            return;
        }

        cancelTransitionIfNeeded();

        item.selectionMode = false;
        callback.showForNavigationItem(item, themeEngine.getChanTheme(), AnimationStyle.NONE);
    }

    private void cancelTransitionIfNeeded() {
        if (transition != null) {
            callback.containerStopTransition(false);
            transition = null;
        }
    }

    /**
     * Returns true if search was closed, false otherwise
     */
    public boolean closeSearchIfNeeded() {
        // Cancel search, but don't unmark it as a search item so that onback will automatically
        // pull up the search window
        if (item != null && item.search) {
            callback.onSearchVisibilityChanged(item, false);
            return true;
        }
        return false;
    }

    public void closeSearchPhoneMode() {
        if (ChanSettings.layoutMode.get() == ChanSettings.LayoutMode.PHONE) {
            closeSearchIfNeeded();
        } else {
            closeSearch();
        }
    }

    void searchInput(@NonNull String input) {
        if (!item.search) {
            return;
        }

        item.searchText = input;
        callback.onSearchInput(item, input);
    }

    public enum AnimationStyle {
        NONE,
        PUSH,
        POP,
        FADE
    }

    public enum TransitionAnimationStyle {
        PUSH,
        POP
    }

    interface Callback {
        void showForNavigationItem(NavigationItem item, ChanTheme theme, AnimationStyle animation);
        void showForNavigationItem(
                NavigationItem item,
                ChanTheme theme,
                AnimationStyle animation,
                ToolbarContainer.ToolbarTransitionAnimationListener listener
        );
        void containerStartTransition(NavigationItem item, TransitionAnimationStyle animation);
        void containerStopTransition(boolean didComplete);
        void containerSetTransitionProgress(float progress);
        void onSearchVisibilityChanged(NavigationItem item, boolean visible);
        void onSearchInput(NavigationItem item, String input);
        void updateViewForItem(NavigationItem item);
    }
}
