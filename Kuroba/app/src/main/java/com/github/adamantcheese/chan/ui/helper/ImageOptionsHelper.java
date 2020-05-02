package com.github.adamantcheese.chan.ui.helper;

import android.content.Context;
import android.graphics.Bitmap;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.core.util.Pair;

import com.github.adamantcheese.chan.R;
import com.github.adamantcheese.chan.controller.Controller;
import com.github.adamantcheese.chan.core.model.orm.Loadable;
import com.github.adamantcheese.chan.core.presenter.ImageReencodingPresenter;
import com.github.adamantcheese.chan.core.settings.ChanSettings;
import com.github.adamantcheese.chan.core.site.http.Reply;
import com.github.adamantcheese.chan.ui.controller.ImageOptionsController;
import com.github.adamantcheese.chan.ui.controller.ImageReencodeOptionsController;
import com.google.gson.Gson;

import javax.inject.Inject;

import static com.github.adamantcheese.chan.Chan.inject;
import static com.github.adamantcheese.chan.utils.AndroidUtils.showToast;

public class ImageOptionsHelper
        implements ImageOptionsController.ImageOptionsControllerCallbacks,
        ImageReencodeOptionsController.ImageReencodeOptionsCallbacks {

    @Inject
    Gson gson;

    private Context context;
    private ImageOptionsController imageOptionsController = null;
    private ImageReencodeOptionsController imageReencodeOptionsController = null;
    private final ImageReencodingHelperCallback callbacks;

    private ImageReencodingPresenter.ImageOptions lastImageOptions;

    public ImageOptionsHelper(Context context, ImageReencodingHelperCallback callbacks) {
        this.context = context;
        this.callbacks = callbacks;

        inject(this);
    }

    public void showController(Loadable loadable, boolean supportsReencode) {
        if (imageOptionsController == null) {
            try {
                // load up the last image options every time this controller is created
                lastImageOptions = gson.fromJson(
                        ChanSettings.lastImageOptions.get(),
                        ImageReencodingPresenter.ImageOptions.class
                );

            } catch (Exception ignored) {
                lastImageOptions = null;
            }

            imageOptionsController = new ImageOptionsController(
                    context,
                    this,
                    this,
                    loadable,
                    lastImageOptions,
                    supportsReencode
            );

            callbacks.presentReencodeOptionsController(imageOptionsController);
        }
    }

    public void pop() {
        // first we have to pop the imageReencodeOptionsController
        if (imageReencodeOptionsController != null) {
            imageReencodeOptionsController.stopPresenting();
            imageReencodeOptionsController = null;
            return;
        }

        if (imageOptionsController != null) {
            imageOptionsController.stopPresenting();
            imageOptionsController = null;
        }

        callbacks.onImageOptionsComplete();
    }

    @Override
    public void onReencodeOptionClicked(
            @Nullable Bitmap.CompressFormat imageFormat,
            @Nullable Pair<Integer, Integer> dims
    ) {
        if (imageReencodeOptionsController != null || imageFormat == null || dims == null) {
            showToast(context, R.string.image_reencode_format_error, Toast.LENGTH_LONG);
            return;
        }

        ImageReencodingPresenter.ReencodeSettings reencodeSettings = lastImageOptions != null
                ? lastImageOptions.getReencodeSettings()
                : null;

        imageReencodeOptionsController = new ImageReencodeOptionsController(
                context,
                this,
                this,
                imageFormat,
                dims,
                reencodeSettings
        );

        callbacks.presentReencodeOptionsController(imageReencodeOptionsController);
    }

    @Override
    public void onImageOptionsApplied(Reply reply, boolean filenameRemoved) {
        callbacks.onImageOptionsApplied(reply, filenameRemoved);
    }

    @Override
    public void onCanceled() {
        if (imageOptionsController != null) {
            imageOptionsController.onReencodingCanceled();
        }

        pop();
    }

    @Override
    public void onOk(ImageReencodingPresenter.ReencodeSettings reencodeSettings) {
        if (imageOptionsController != null) {
            if (reencodeSettings.isDefault()) {
                imageOptionsController.onReencodingCanceled();
            } else {
                imageOptionsController.onReencodeOptionsSet(reencodeSettings);
            }
        }

        pop();
    }

    public interface ImageReencodingHelperCallback {
        void presentReencodeOptionsController(Controller controller);

        void onImageOptionsApplied(Reply reply, boolean filenameRemoved);

        void onImageOptionsComplete();
    }
}
