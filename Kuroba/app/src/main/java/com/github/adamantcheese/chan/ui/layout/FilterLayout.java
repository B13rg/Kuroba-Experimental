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
package com.github.adamantcheese.chan.ui.layout;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.text.Editable;
import android.text.Html;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.style.BackgroundColorSpan;
import android.text.style.StyleSpan;
import android.text.style.TypefaceSpan;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.AppCompatCheckBox;

import com.github.adamantcheese.chan.R;
import com.github.adamantcheese.chan.core.manager.BoardManager;
import com.github.adamantcheese.chan.core.manager.FilterEngine;
import com.github.adamantcheese.chan.core.manager.FilterEngine.FilterAction;
import com.github.adamantcheese.chan.core.manager.FilterType;
import com.github.adamantcheese.chan.core.model.orm.Filter;
import com.github.adamantcheese.chan.ui.helper.BoardHelper;
import com.github.adamantcheese.chan.ui.theme.DropdownArrowDrawable;
import com.github.adamantcheese.chan.ui.theme.ThemeHelper;
import com.github.adamantcheese.chan.ui.view.ColorPickerView;
import com.github.adamantcheese.chan.ui.view.FloatingMenu;
import com.github.adamantcheese.chan.ui.view.FloatingMenuItem;
import com.github.adamantcheese.chan.utils.AndroidUtils;
import com.github.adamantcheese.model.data.board.ChanBoard;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import javax.inject.Inject;

import kotlin.Unit;

import static com.github.adamantcheese.chan.Chan.inject;
import static com.github.adamantcheese.chan.utils.AndroidUtils.dp;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getAttrColor;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getString;

public class FilterLayout
        extends LinearLayout
        implements View.OnClickListener {
    private TextView typeText;
    private TextView boardsSelector;
    private boolean patternContainerErrorShowing = false;
    private TextView pattern;
    private TextView patternPreview;
    private TextView patternPreviewStatus;
    private CheckBox enabled;
    private ImageView help;
    private TextView actionText;
    private LinearLayout colorContainer;
    private View colorPreview;
    private AppCompatCheckBox applyToReplies;
    private AppCompatCheckBox onlyOnOP;
    private AppCompatCheckBox applyToSaved;

    @Inject
    BoardManager boardManager;
    @Inject
    FilterEngine filterEngine;
    @Inject
    ThemeHelper themeHelper;

    private FilterLayoutCallback callback;
    private Filter filter;

    public FilterLayout(Context context) {
        super(context);
    }

    public FilterLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public FilterLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        inject(this);

        typeText = findViewById(R.id.type);
        boardsSelector = findViewById(R.id.boards);
        actionText = findViewById(R.id.action);
        pattern = findViewById(R.id.pattern);
        pattern.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                filter.pattern = s.toString();
                updateFilterValidity();
                updatePatternPreview();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
        patternPreview = findViewById(R.id.pattern_preview);
        patternPreview.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                updatePatternPreview();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
        patternPreviewStatus = findViewById(R.id.pattern_preview_status);
        enabled = findViewById(R.id.enabled);

        help = findViewById(R.id.help);
        themeHelper.getTheme().helpDrawable.apply(help);
        help.setOnClickListener(this);

        colorContainer = findViewById(R.id.color_container);
        colorContainer.setOnClickListener(this);
        colorPreview = findViewById(R.id.color_preview);
        applyToReplies = findViewById(R.id.apply_to_replies_checkbox);
        onlyOnOP = findViewById(R.id.only_on_op_checkbox);
        applyToSaved = findViewById(R.id.apply_to_saved_checkbox);

        typeText.setOnClickListener(this);
        typeText.setCompoundDrawablesWithIntrinsicBounds(null, null, new DropdownArrowDrawable(
                dp(12),
                dp(12),
                true,
                getAttrColor(getContext(), R.attr.dropdown_dark_color),
                getAttrColor(getContext(), R.attr.dropdown_dark_pressed_color)
        ), null);

        boardsSelector.setOnClickListener(this);
        boardsSelector.setCompoundDrawablesWithIntrinsicBounds(null, null, new DropdownArrowDrawable(
                dp(12),
                dp(12),
                true,
                getAttrColor(getContext(), R.attr.dropdown_dark_color),
                getAttrColor(getContext(), R.attr.dropdown_dark_pressed_color)
        ), null);

        actionText.setOnClickListener(this);
        actionText.setCompoundDrawablesWithIntrinsicBounds(null, null, new DropdownArrowDrawable(
                dp(12),
                dp(12),
                true,
                getAttrColor(getContext(), R.attr.dropdown_dark_color),
                getAttrColor(getContext(), R.attr.dropdown_dark_pressed_color)
        ), null);

        enabled.setButtonTintList(ColorStateList.valueOf(themeHelper.getTheme().textPrimary));
        enabled.setTextColor(ColorStateList.valueOf(themeHelper.getTheme().textPrimary));
        applyToReplies.setButtonTintList(ColorStateList.valueOf(themeHelper.getTheme().textPrimary));
        applyToReplies.setTextColor(ColorStateList.valueOf(themeHelper.getTheme().textPrimary));
        onlyOnOP.setButtonTintList(ColorStateList.valueOf(themeHelper.getTheme().textPrimary));
        onlyOnOP.setTextColor(ColorStateList.valueOf(themeHelper.getTheme().textPrimary));
        applyToSaved.setButtonTintList(ColorStateList.valueOf(themeHelper.getTheme().textPrimary));
        applyToSaved.setTextColor(ColorStateList.valueOf(themeHelper.getTheme().textPrimary));
    }

    public void setFilter(Filter filter) {
        this.filter = filter;

        pattern.setText(filter.pattern);

        updateFilterValidity();
        updateFilterType();
        updateFilterAction();
        updateCheckboxes();
        updateBoardsSummary();
        updatePatternPreview();
    }

    public void setCallback(FilterLayoutCallback callback) {
        this.callback = callback;
    }

    public Filter getFilter() {
        filter.enabled = enabled.isChecked();
        filter.applyToReplies = applyToReplies.isChecked();
        filter.onlyOnOP = onlyOnOP.isChecked();
        filter.applyToSaved = applyToSaved.isChecked();

        return filter;
    }

    @Override
    public void onClick(View v) {
        if (v == typeText) {
            @SuppressWarnings("unchecked")
            final SelectLayout<FilterType> selectLayout =
                    (SelectLayout<FilterType>) AndroidUtils.inflate(getContext(), R.layout.layout_select, null);

            List<SelectLayout.SelectItem<FilterType>> items = new ArrayList<>();
            for (FilterType filterType : FilterType.values()) {
                String name = FilterType.filterTypeName(filterType);
                boolean checked = filter.hasFilter(filterType);

                items.add(new SelectLayout.SelectItem<>(filterType, filterType.flag, name, null, name, checked));
            }

            selectLayout.setItems(items);

            new AlertDialog.Builder(getContext()).setView(selectLayout)
                    .setPositiveButton(R.string.ok, (dialog, which) -> {
                        List<SelectLayout.SelectItem<FilterType>> items12 = selectLayout.getItems();
                        int flags = 0;
                        for (SelectLayout.SelectItem<FilterType> item : items12) {
                            if (item.checked) {
                                flags |= item.item.flag;
                            }
                        }

                        filter.type = flags;
                        updateFilterType();
                        updatePatternPreview();
                    })
                    .show();
        } else if (v == boardsSelector) {
            @SuppressLint("InflateParams")
            @SuppressWarnings("unchecked")
            final SelectLayout<ChanBoard> selectLayout =
                    (SelectLayout<ChanBoard>) AndroidUtils.inflate(getContext(), R.layout.layout_select, null);

            List<SelectLayout.SelectItem<ChanBoard>> items = new ArrayList<>();

            boardManager.viewAllActiveBoards(chanBoard -> {
                String name = BoardHelper.getName(chanBoard);
                boolean checked = filterEngine.matchesBoard(filter, chanBoard);

                items.add(
                        new SelectLayout.SelectItem<>(
                                chanBoard,
                                chanBoard.getBoardDescriptor().hashCode(),
                                name,
                                "",
                                name,
                                checked
                        )
                );

                return Unit.INSTANCE;
            });

            selectLayout.setItems(items);

            new AlertDialog.Builder(getContext()).setView(selectLayout)
                    .setPositiveButton(R.string.ok, (dialog, which) -> {
                        List<SelectLayout.SelectItem<ChanBoard>> items1 = selectLayout.getItems();
                        boolean all = selectLayout.areAllChecked();
                        List<ChanBoard> boardList = new ArrayList<>(items1.size());
                        if (!all) {
                            for (SelectLayout.SelectItem<ChanBoard> item : items1) {
                                if (item.checked) {
                                    boardList.add(item.item);
                                }
                            }
                            if (boardList.isEmpty()) {
                                all = true;
                            }
                        }

                        filterEngine.saveBoardsToFilter(boardList, all, filter);

                        updateBoardsSummary();
                    })
                    .show();
        } else if (v == actionText) {
            List<FloatingMenuItem> menuItems = new ArrayList<>(6);

            for (FilterAction action : FilterAction.values()) {
                menuItems.add(new FloatingMenuItem(action, FilterAction.actionName(action)));
            }

            FloatingMenu menu = new FloatingMenu(v.getContext());
            menu.setAnchor(v, Gravity.LEFT, -dp(5), -dp(5));
            menu.setCallback(new FloatingMenu.FloatingMenuCallback() {
                @Override
                public void onFloatingMenuItemClicked(FloatingMenu menu, FloatingMenuItem item) {
                    FilterAction action = (FilterAction) item.getId();
                    filter.action = action.id;
                    updateFilterAction();
                }

                @Override
                public void onFloatingMenuDismissed(FloatingMenu menu) {
                }
            });
            menu.setItems(menuItems);
            menu.show();
        } else if (v == help) {
            SpannableStringBuilder message = (SpannableStringBuilder) Html.fromHtml(getString(R.string.filter_help));
            TypefaceSpan[] typefaceSpans = message.getSpans(0, message.length(), TypefaceSpan.class);
            for (TypefaceSpan span : typefaceSpans) {
                if (span.getFamily().equals("monospace")) {
                    int start = message.getSpanStart(span);
                    int end = message.getSpanEnd(span);
                    message.setSpan(new BackgroundColorSpan(0x22000000), start, end, 0);
                }
            }

            StyleSpan[] styleSpans = message.getSpans(0, message.length(), StyleSpan.class);
            for (StyleSpan span : styleSpans) {
                if (span.getStyle() == Typeface.ITALIC) {
                    int start = message.getSpanStart(span);
                    int end = message.getSpanEnd(span);
                    message.setSpan(new BackgroundColorSpan(0x22000000), start, end, 0);
                }
            }

            new AlertDialog.Builder(getContext()).setTitle(R.string.filter_help_title)
                    .setMessage(message)
                    .setPositiveButton(R.string.ok, null)
                    .show();
        } else if (v == colorContainer) {
            final ColorPickerView colorPickerView = new ColorPickerView(getContext());
            colorPickerView.setColor(filter.color);

            AlertDialog dialog = new AlertDialog.Builder(getContext()).setTitle(R.string.filter_color_pick)
                    .setView(colorPickerView)
                    .setNegativeButton(R.string.cancel, null)
                    .setPositiveButton(R.string.ok, (dialog1, which) -> {
                        filter.color = colorPickerView.getColor();
                        updateFilterAction();
                    })
                    .show();
            dialog.getWindow().setLayout(dp(300), dp(300));
        }
    }

    private void updateFilterValidity() {
        int extraFlags = (filter.type & FilterType.COUNTRY_CODE.flag) != 0 ? Pattern.CASE_INSENSITIVE : 0;
        boolean valid = !TextUtils.isEmpty(filter.pattern) && filterEngine.compile(filter.pattern, extraFlags) != null;

        if (valid != patternContainerErrorShowing) {
            patternContainerErrorShowing = valid;
            pattern.setError(valid ? null : getString(R.string.filter_invalid_pattern));
        }

        if (callback != null) {
            callback.setSaveButtonEnabled(valid);
        }
    }

    private void updateBoardsSummary() {
        String text = getString(R.string.filter_boards) + " (";
        if (filter.allBoards) {
            text += getString(R.string.filter_all);
        } else {
            text += filterEngine.getFilterBoardCount(filter);
        }
        text += ")";
        boardsSelector.setText(text);
    }

    private void updateCheckboxes() {
        enabled.setChecked(filter.enabled);
        applyToReplies.setChecked(filter.applyToReplies);
        onlyOnOP.setChecked(filter.onlyOnOP);
        applyToSaved.setChecked(filter.applyToSaved);
        if (filter.action == FilterAction.WATCH.id) {
            applyToReplies.setEnabled(false);
            onlyOnOP.setChecked(true);
            onlyOnOP.setEnabled(false);
            applyToSaved.setEnabled(false);
        }
    }

    private void updateFilterAction() {
        FilterAction action = FilterAction.forId(filter.action);
        actionText.setText(FilterAction.actionName(action));
        colorContainer.setVisibility(action == FilterAction.COLOR ? VISIBLE : GONE);
        if (filter.color == 0) {
            filter.color = 0xffff0000;
        }
        colorPreview.setBackgroundColor(filter.color);
        if (filter.action != FilterAction.WATCH.id) {
            applyToReplies.setEnabled(true);
            onlyOnOP.setEnabled(true);
            onlyOnOP.setChecked(false);
            applyToSaved.setEnabled(true);
        } else {
            applyToReplies.setEnabled(false);
            onlyOnOP.setEnabled(false);
            applyToSaved.setEnabled(false);
            if (applyToReplies.isChecked()) {
                applyToReplies.toggle();
                filter.applyToReplies = false;
            }
            if (!onlyOnOP.isChecked()) {
                onlyOnOP.toggle();
                filter.onlyOnOP = true;
            }
            if (applyToSaved.isChecked()) {
                applyToSaved.toggle();
                filter.applyToSaved = false;
            }
        }
    }

    private void updateFilterType() {
        int types = FilterType.forFlags(filter.type).size();
        String text = getString(R.string.filter_types) + " (" + types + ")";
        typeText.setText(text);
    }

    private void updatePatternPreview() {
        String text = patternPreview.getText().toString();
        boolean matches = text.length() > 0 && filterEngine.matches(filter, text, true);
        patternPreviewStatus.setText(matches ? R.string.filter_matches : R.string.filter_no_matches);
    }

    public interface FilterLayoutCallback {
        void setSaveButtonEnabled(boolean enabled);
    }
}
