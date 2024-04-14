package com.github.k1rakishou.chan.ui.controller;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;

import android.content.Context;
import android.widget.ProgressBar;

import com.github.k1rakishou.chan.R;
import com.github.k1rakishou.chan.core.di.component.activity.ActivityComponent;
import com.github.k1rakishou.chan.ui.theme.widget.ColorizableBarButton;
import com.github.k1rakishou.chan.ui.theme.widget.ColorizableTextView;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import kotlin.Unit;
import kotlin.jvm.functions.Function0;

public class LoadingViewController extends BaseFloatingController {
    private ColorizableTextView loadingControllerTitle;
    private ColorizableTextView loadingControllerMessage;
    private ColorizableBarButton cancelButton;
    private ProgressBar progressBar;

    private String title;
    private boolean indeterminate;
    private boolean cancelAllowed = false;
    private @Nullable Function0<Unit> cancellationFunc;

    @Override
    protected void injectDependencies(@NotNull ActivityComponent component) {
        component.inject(this);
    }

    @Override
    protected int getLayoutId() {
        return R.layout.controller_loading_view;
    }

    public LoadingViewController(Context context, boolean indeterminate) {
        super(context);

        this.indeterminate = indeterminate;
        this.title = getString(R.string.doing_heavy_lifting_please_wait);
    }

    public LoadingViewController(Context context, boolean indeterminate, String title) {
        super(context);

        this.indeterminate = indeterminate;
        this.title = title;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        loadingControllerTitle = view.findViewById(R.id.loading_controller_title);
        progressBar = view.findViewById(R.id.progress_bar);
        cancelButton = view.findViewById(R.id.loading_controller_cancel_button);

        loadingControllerTitle.setText(title);
        loadingControllerMessage = view.findViewById(R.id.loading_controller_message);

        if (cancelAllowed) {
            cancelButton.setVisibility(VISIBLE);
        } else {
            cancelButton.setVisibility(GONE);
        }

        cancelButton.setOnClickListener(view -> {
            if (cancelButton.getVisibility() != VISIBLE || !cancelAllowed) {
                return;
            }

            if (cancellationFunc != null) {
                cancellationFunc.invoke();
                cancellationFunc = null;
            }

            pop();
        });
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        cancellationFunc = null;
    }

    public void enableCancellation(@NotNull Function0<Unit> cancellationFunc) {
        this.cancellationFunc = cancellationFunc;

        cancelAllowed = true;
    }

    // Disable the back button for this controller unless otherwise requested by the above
    @Override
    public boolean onBack() {
        if (cancelAllowed) {
            if (cancellationFunc != null) {
                cancellationFunc.invoke();
                cancellationFunc = null;
            }

            pop();
        }

        return true;
    }

    /**
     * Shows a progress bar with percentage in the center (cannot be used with indeterminate)
     */
    public void updateProgress(int percent) {
        if (indeterminate) {
            return;
        }

        loadingControllerMessage.setVisibility(VISIBLE);
        progressBar.setVisibility(VISIBLE);
        loadingControllerMessage.setText(String.valueOf(percent > 0 ? percent : "0"));
    }

    /**
     * Hide a progress bar and instead of percentage any text may be shown
     * (cannot be used with indeterminate)
     */
    public void updateWithText(String text) {
        if (indeterminate) {
            return;
        }

        loadingControllerMessage.setVisibility(VISIBLE);
        progressBar.setVisibility(GONE);
        loadingControllerMessage.setText(text);
    }

}
