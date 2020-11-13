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
package com.github.k1rakishou.chan.ui.controller;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.text.Html;
import android.text.TextUtils;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.github.k1rakishou.chan.R;
import com.github.k1rakishou.chan.controller.Controller;
import com.github.k1rakishou.chan.core.di.component.activity.StartActivityComponent;
import com.github.k1rakishou.chan.core.helper.DialogFactory;
import com.github.k1rakishou.chan.core.helper.FilterEngine;
import com.github.k1rakishou.chan.core.helper.FilterEngine.FilterAction;
import com.github.k1rakishou.chan.ui.controller.navigation.ToolbarNavigationController;
import com.github.k1rakishou.chan.ui.helper.RefreshUIMessage;
import com.github.k1rakishou.chan.ui.layout.FilterLayout;
import com.github.k1rakishou.chan.ui.theme.widget.ColorizableFloatingActionButton;
import com.github.k1rakishou.chan.ui.theme.widget.ColorizableRecyclerView;
import com.github.k1rakishou.chan.ui.toolbar.ToolbarMenuItem;
import com.github.k1rakishou.chan.utils.BackgroundUtils;
import com.github.k1rakishou.core_themes.ThemeEngine;
import com.github.k1rakishou.model.data.filter.ChanFilter;
import com.github.k1rakishou.model.data.filter.ChanFilterMutable;
import com.github.k1rakishou.model.data.filter.FilterType;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javax.inject.Inject;

import kotlin.Unit;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;
import static com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.openLink;
import static com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.postToEventBus;
import static com.github.k1rakishou.common.AndroidUtils.getQuantityString;
import static com.github.k1rakishou.common.AndroidUtils.getString;
import static com.github.k1rakishou.common.AndroidUtils.inflate;

public class FiltersController
        extends Controller
        implements ToolbarNavigationController.ToolbarSearchCallback,
        View.OnClickListener,
        ThemeEngine.ThemeChangesListener {

    @Inject
    FilterEngine filterEngine;
    @Inject
    ThemeEngine themeEngine;
    @Inject
    DialogFactory dialogFactory;

    private ColorizableRecyclerView recyclerView;
    private ColorizableFloatingActionButton add;
    private ColorizableFloatingActionButton enable;
    private FilterAdapter adapter;
    private boolean locked;

    private ItemTouchHelper itemTouchHelper;
    private boolean attached;

    private ItemTouchHelper.SimpleCallback touchHelperCallback =
            new ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP | ItemTouchHelper.DOWN,
                    ItemTouchHelper.RIGHT | ItemTouchHelper.LEFT
            ) {
                @Override
                public boolean onMove(
                        RecyclerView recyclerView,
                        RecyclerView.ViewHolder viewHolder,
                        RecyclerView.ViewHolder target
                ) {
                    int from = viewHolder.getAdapterPosition();
                    int to = target.getAdapterPosition();

                    if (from == RecyclerView.NO_POSITION || to == RecyclerView.NO_POSITION
                            || !TextUtils.isEmpty(adapter.searchQuery)) {
                        //require that no search is going on while we do the sorting
                        return false;
                    }

                    adapter.move(from, to);
                    return true;
                }

                @Override
                public void onSwiped(RecyclerView.ViewHolder viewHolder, int direction) {
                    if (direction == ItemTouchHelper.LEFT || direction == ItemTouchHelper.RIGHT) {
                        int position = viewHolder.getAdapterPosition();
                        deleteFilter(adapter.displayList.get(position));
                    }
                }
            };

    @Override
    protected void injectDependencies(@NotNull StartActivityComponent component) {
        component.inject(this);
    }

    public FiltersController(Context context) {
        super(context);
    }

    @Override
    public void onCreate() {
        super.onCreate();

        view = inflate(context, R.layout.controller_filters);

        navigation.setTitle(R.string.filters_screen);
        navigation.swipeable = false;

        navigation.buildMenu()
                .withItem(R.drawable.ic_search_white_24dp, this::searchClicked)
                .withItem(R.drawable.ic_help_outline_white_24dp, this::helpClicked)
                .build();

        adapter = new FilterAdapter();

        recyclerView = view.findViewById(R.id.recycler_view);
        recyclerView.setLayoutManager(new LinearLayoutManager(context));
        recyclerView.setAdapter(adapter);

        itemTouchHelper = new ItemTouchHelper(touchHelperCallback);
        itemTouchHelper.attachToRecyclerView(recyclerView);
        attached = true;

        add = view.findViewById(R.id.add);
        add.setOnClickListener(this);

        enable = view.findViewById(R.id.enable);
        enable.setOnClickListener(this);

        themeEngine.addListener(this);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        themeEngine.removeListener(this);
    }

    @Override
    public void onThemeChanged() {
        if (adapter != null) {
            adapter.reload();
        }
    }

    @Override
    public void onClick(View v) {
        if (v == add) {
            ChanFilterMutable chanFilterMutable = new ChanFilterMutable();
            showFilterDialog(chanFilterMutable);
        } else if (v == enable && !locked) {
            ColorizableFloatingActionButton enableButton = (ColorizableFloatingActionButton) v;
            locked = true;

            // if every filter is disabled, enable all of them and set the drawable to be an x
            // if every filter is enabled, disable all of them and set the drawable to be a checkmark
            // if some filters are enabled, disable them and set the drawable to be a checkmark
            List<ChanFilter> enabledFilters = filterEngine.getEnabledFilters();
            List<ChanFilter> allFilters = filterEngine.getAllFilters();

            if (enabledFilters.isEmpty()) {
                setFilters(allFilters, true);
                enableButton.setImageResource(R.drawable.ic_clear_white_24dp);
            } else if (enabledFilters.size() == allFilters.size()) {
                setFilters(allFilters, false);
                enableButton.setImageResource(R.drawable.ic_done_white_24dp);
            } else {
                setFilters(enabledFilters, false);
                enableButton.setImageResource(R.drawable.ic_done_white_24dp);
            }
        }
    }

    private void setFilters(List<ChanFilter> filters, boolean enabled) {
        filterEngine.enableDisableFilters(filters, enabled, () -> {
            BackgroundUtils.ensureMainThread();

            adapter.reload();
            return Unit.INSTANCE;
        });
    }

    private void searchClicked(ToolbarMenuItem item) {
        ((ToolbarNavigationController) navigationController).showSearch();
    }

    private void helpClicked(ToolbarMenuItem item) {
        DialogFactory.Builder
                .newBuilder(context, dialogFactory)
                .withTitle(R.string.help)
                .withDescription(Html.fromHtml(getString(R.string.filters_controller_help_message)))
                .withCancelable(true)
                .withNegativeButtonTextId(R.string.filters_controller_open_regex101)
                .withOnNegativeButtonClickListener(dialog -> {
                    openLink("https://regex101.com/");
                    return Unit.INSTANCE;
                })
                .create();
    }

    public void showFilterDialog(final ChanFilterMutable chanFilterMutable) {
        final FilterLayout filterLayout = (FilterLayout) inflate(context, R.layout.layout_filter, null);

        AlertDialog alertDialog = DialogFactory.Builder.newBuilder(context, dialogFactory)
                .withCustomView(filterLayout)
                .withPositiveButtonTextId(R.string.save)
                .withOnPositiveButtonClickListener((dialog) -> {
                    filterEngine.createOrUpdateFilter(filterLayout.getFilter(), () -> {
                        BackgroundUtils.ensureMainThread();

                        if (filterEngine.getEnabledFilters().isEmpty()) {
                            enable.setImageResource(R.drawable.ic_done_white_24dp);
                        } else {
                            enable.setImageResource(R.drawable.ic_clear_white_24dp);
                        }

                        postToEventBus(new RefreshUIMessage("filters"));
                        adapter.reload();

                        return Unit.INSTANCE;
                    });

                    return Unit.INSTANCE;
                })
                .create();

        if (alertDialog == null) {
            // App is in background
            return;
        }

        filterLayout
                .setCallback(enabled -> {
                    alertDialog
                            .getButton(AlertDialog.BUTTON_POSITIVE)
                            .setEnabled(enabled);
                });

        filterLayout.setFilter(chanFilterMutable);
    }

    private void deleteFilter(ChanFilter filter) {
        filterEngine.deleteFilter(filter, () -> {
            BackgroundUtils.ensureMainThread();

            postToEventBus(new RefreshUIMessage("filters"));
            adapter.reload();

            return Unit.INSTANCE;
        });
    }

    @SuppressLint("RestrictedApi")
    @Override
    public void onSearchVisibilityChanged(boolean visible) {
        if (!visible) {
            //search off, turn on buttons and touch listener
            adapter.searchQuery = null;
            adapter.filter();
            add.setVisibility(VISIBLE);
            enable.setVisibility(VISIBLE);
            itemTouchHelper.attachToRecyclerView(recyclerView);
            attached = true;
        } else {
            //search on, turn off buttons and touch listener
            add.setVisibility(GONE);
            enable.setVisibility(GONE);
            itemTouchHelper.attachToRecyclerView(null);
            attached = false;
        }
    }

    @Override
    public void onSearchEntered(@NonNull String entered) {
        adapter.searchQuery = entered;
        adapter.filter();
    }

    private class FilterAdapter extends RecyclerView.Adapter<FilterCell> {
        private List<ChanFilter> sourceList = new ArrayList<>();
        private List<ChanFilter> displayList = new ArrayList<>();
        private String searchQuery;

        public FilterAdapter() {
            setHasStableIds(true);
            reload();
            filter();
        }

        @Override
        public FilterCell onCreateViewHolder(ViewGroup parent, int viewType) {
            return new FilterCell(inflate(parent.getContext(), R.layout.cell_filter, parent, false));
        }

        @Override
        public void onBindViewHolder(FilterCell holder, int position) {
            ChanFilter filter = displayList.get(position);
            holder.text.setText(filter.getPattern());

            int textColor = filter.getEnabled()
                    ? themeEngine.getChanTheme().getTextColorPrimary()
                    : themeEngine.getChanTheme().getTextColorHint();

            holder.text.setTextColor(textColor);
            holder.subtext.setTextColor(textColor);


            int types = FilterType.forFlags(filter.getType()).size();
            String subText = getQuantityString(R.plurals.type, types, types);

            subText += " \u2013 ";
            if (filter.allBoards()) {
                subText += getString(R.string.filter_summary_all_boards);
            } else {
                int size = filterEngine.getFilterBoardCount(filter);
                subText += getQuantityString(R.plurals.board, size, size);
            }

            subText += " \u2013 " + FilterAction.actionName(FilterAction.forId(filter.getAction()));

            holder.subtext.setText(subText);
        }

        @Override
        public int getItemCount() {
            return displayList.size();
        }

        @Override
        public long getItemId(int position) {
            return displayList.get(position).getDatabaseId();
        }

        public void reload() {
            sourceList.clear();
            sourceList.addAll(filterEngine.getAllFilters());
            filter();
        }

        public void move(int from, int to) {
            filterEngine.onFilterMoved(from, to, () -> {
                BackgroundUtils.ensureMainThread();

                reload();
                return Unit.INSTANCE;
            });
        }

        public void filter() {
            displayList.clear();

            if (!TextUtils.isEmpty(searchQuery)) {
                String query = searchQuery.toLowerCase(Locale.ENGLISH);
                for (ChanFilter filter : sourceList) {
                    if (filter.getPattern().toLowerCase().contains(query)) {
                        displayList.add(filter);
                    }
                }
            } else {
                displayList.addAll(sourceList);
            }

            notifyDataSetChanged();
            locked = false;
        }
    }

    private class FilterCell extends RecyclerView.ViewHolder implements View.OnClickListener {
        private TextView text;
        private TextView subtext;

        @SuppressLint("ClickableViewAccessibility")
        public FilterCell(View itemView) {
            super(itemView);

            text = itemView.findViewById(R.id.text);
            subtext = itemView.findViewById(R.id.subtext);

            ImageView reorder = itemView.findViewById(R.id.reorder);
            Drawable drawable = ContextCompat.getDrawable(context, R.drawable.ic_reorder_white_24dp);
            Drawable drawableMutable = DrawableCompat.wrap(drawable).mutate();
            DrawableCompat.setTint(drawableMutable, themeEngine.getChanTheme().getTextColorHint());
            reorder.setImageDrawable(drawableMutable);

            reorder.setOnTouchListener((v, event) -> {
                if (!locked && event.getActionMasked() == MotionEvent.ACTION_DOWN && attached) {
                    itemTouchHelper.startDrag(FilterCell.this);
                }

                return false;
            });

            itemView.setOnClickListener(this);
        }

        @Override
        public void onClick(View v) {
            int position = getAdapterPosition();
            if (!locked && position >= 0 && position < adapter.getItemCount() && v == itemView) {
                ChanFilterMutable chanFilterMutable = ChanFilterMutable.from(adapter.displayList.get(position));
                showFilterDialog(chanFilterMutable);
            }
        }
    }
}
