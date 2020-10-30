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

import android.content.Context;
import android.view.ViewGroup;
import android.widget.ScrollView;

import androidx.annotation.Nullable;

import com.github.k1rakishou.chan.R;
import com.github.k1rakishou.chan.controller.Controller;
import com.github.k1rakishou.chan.core.di.component.activity.StartActivityComponent;
import com.github.k1rakishou.chan.ui.theme.ThemeEngine;
import com.github.k1rakishou.chan.ui.theme.widget.ColorizableTextView;
import com.github.k1rakishou.chan.ui.toolbar.ToolbarMenuSubItem;
import com.github.k1rakishou.chan.utils.IOUtils;
import com.github.k1rakishou.chan.utils.Logger;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;

import javax.inject.Inject;

import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static com.github.k1rakishou.chan.utils.AndroidUtils.getApplicationLabel;
import static com.github.k1rakishou.chan.utils.AndroidUtils.setClipboardContent;

public class LogsController extends Controller {
    private static final String TAG = "LogsController";
    private static final int DEFAULT_LINES_COUNT = 250;
    private static final int ACTION_LOGS_COPY = 1;

    @Inject
    ThemeEngine themeEngine;

    private ColorizableTextView logTextView;
    private String logText;

    @Override
    protected void injectDependencies(@NotNull StartActivityComponent component) {
        component.inject(this);
    }

    public LogsController(Context context) {
        super(context);
    }

    @Override
    public void onCreate() {
        super.onCreate();

        navigation.setTitle(R.string.settings_logs_screen);

        navigation.buildMenu()
                .withOverflow(navigationController)
                .withSubItem(
                        ACTION_LOGS_COPY,
                        R.string.settings_logs_copy,
                        this::copyLogsClicked
                )
                .build()
                .build();

        ScrollView container = new ScrollView(context);
        container.setBackgroundColor(themeEngine.getChanTheme().getBackColor());
        logTextView = new ColorizableTextView(context);
        container.addView(logTextView, new ViewGroup.LayoutParams(MATCH_PARENT, MATCH_PARENT));

        view = container;

        String logs = loadLogs();
        if (logs != null) {
            logText = logs;
            logTextView.setText(logText);
        }
    }

    private void copyLogsClicked(ToolbarMenuSubItem item) {
        setClipboardContent("Logs", logText);
        showToast(R.string.settings_logs_copied_to_clipboard);
    }

    @Nullable
    public static String loadLogs() {
        return loadLogs(DEFAULT_LINES_COUNT);
    }

    @Nullable
    public static String loadLogs(int linesCount) {
        Process process;
        try {
            process = new ProcessBuilder().command("logcat",
                    "-v",
                    "tag",
                    "-t",
                    String.valueOf(linesCount),
                    "StrictMode:S"
            ).start();
        } catch (IOException e) {
            Logger.e(TAG, "Error starting logcat", e);
            return null;
        }

        InputStream outputStream = process.getInputStream();
        //This filters our log output to just stuff we care about in-app (and if a crash happens, the uncaught handler gets it and this will still allow it through)
        String filtered = "";
        for (String line : IOUtils.readString(outputStream).split("\n")) {
            if (line.contains(getApplicationLabel())) filtered = filtered.concat(line).concat("\n");
        }

        return filtered;
    }
}
