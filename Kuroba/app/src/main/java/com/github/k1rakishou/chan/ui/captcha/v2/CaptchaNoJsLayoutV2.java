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
package com.github.k1rakishou.chan.ui.captcha.v2;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.StyleSpan;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.GridView;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatTextView;
import androidx.constraintlayout.widget.ConstraintLayout;

import com.github.k1rakishou.chan.R;
import com.github.k1rakishou.chan.core.base.okhttp.ProxiedOkHttpClient;
import com.github.k1rakishou.chan.core.site.Site;
import com.github.k1rakishou.chan.core.site.SiteAuthentication;
import com.github.k1rakishou.chan.ui.captcha.AuthenticationLayoutCallback;
import com.github.k1rakishou.chan.ui.captcha.AuthenticationLayoutInterface;
import com.github.k1rakishou.chan.ui.captcha.CaptchaHolder;
import com.github.k1rakishou.chan.ui.theme.ThemeEngine;
import com.github.k1rakishou.chan.ui.theme.widget.ColorizableBarButton;
import com.github.k1rakishou.chan.ui.theme.widget.TouchBlockingFrameLayout;
import com.github.k1rakishou.chan.utils.AndroidUtils;
import com.github.k1rakishou.chan.utils.BackgroundUtils;
import com.github.k1rakishou.chan.utils.Logger;
import com.github.k1rakishou.common.AppConstants;

import java.util.List;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;

import static com.github.k1rakishou.chan.Chan.inject;
import static com.github.k1rakishou.chan.core.site.SiteAuthentication.Type.CAPTCHA2_NOJS;
import static com.github.k1rakishou.chan.utils.AndroidUtils.dp;
import static com.github.k1rakishou.chan.utils.AndroidUtils.showToast;

public class CaptchaNoJsLayoutV2
        extends TouchBlockingFrameLayout
        implements AuthenticationLayoutInterface, CaptchaNoJsPresenterV2.AuthenticationCallbacks, ThemeEngine.ThemeChangesListener {
    private static final String TAG = "CaptchaNoJsLayoutV2";
    private static final long RECAPTCHA_TOKEN_LIVE_TIME = TimeUnit.MINUTES.toMillis(2);

    private AppCompatTextView captchaChallengeTitle;
    private GridView captchaImagesGrid;
    private ColorizableBarButton captchaVerifyButton;

    private CaptchaNoJsV2Adapter adapter;
    private CaptchaNoJsPresenterV2 presenter;
    private AuthenticationLayoutCallback callback;
    private ConstraintLayout captchaButtonsHolder;
    private ColorizableBarButton useOldCaptchaButton;
    private ColorizableBarButton reloadCaptchaButton;

    private boolean isAutoReply = true;

    @Inject
    CaptchaHolder captchaHolder;
    @Inject
    ProxiedOkHttpClient okHttpClient;
    @Inject
    ThemeEngine themeEngine;
    @Inject
    AppConstants appConstants;

    public CaptchaNoJsLayoutV2(@NonNull Context context) {
        this(context, null, 0);
    }

    public CaptchaNoJsLayoutV2(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public CaptchaNoJsLayoutV2(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        inject(this);

        this.presenter = new CaptchaNoJsPresenterV2(this, okHttpClient, appConstants, context);
        this.adapter = new CaptchaNoJsV2Adapter();

        View view = inflate(context, R.layout.layout_captcha_nojs_v2, this);

        LinearLayout topLevel = findViewById(R.id.captcha_layout_v2_top_level);
        topLevel.setGravity(Gravity.BOTTOM);

        captchaChallengeTitle = view.findViewById(R.id.captcha_layout_v2_title);
        captchaImagesGrid = view.findViewById(R.id.captcha_layout_v2_images_grid);
        captchaVerifyButton = view.findViewById(R.id.captcha_layout_v2_verify_button);
        useOldCaptchaButton = view.findViewById(R.id.captcha_layout_v2_use_old_captcha_button);
        reloadCaptchaButton = view.findViewById(R.id.captcha_layout_v2_reload_button);
        captchaButtonsHolder = view.findViewById(R.id.captcha_layout_v2_buttons);

        captchaVerifyButton.setOnClickListener(v -> sendVerificationResponse());
        useOldCaptchaButton.setOnClickListener(v -> callback.onFallbackToV1CaptchaView(isAutoReply));
        reloadCaptchaButton.setOnClickListener(v -> reset());

        onThemeChanged();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        themeEngine.addListener(this);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        themeEngine.removeListener(this);
    }

    @Override
    public void onThemeChanged() {
        int primaryColor = themeEngine.getChanTheme().getPrimaryColor();

        captchaButtonsHolder.setBackgroundColor(primaryColor);
        captchaChallengeTitle.setBackgroundColor(primaryColor);

        int textColor = Color.LTGRAY;
        if (!AndroidUtils.isDarkColor(primaryColor)) {
            textColor = Color.DKGRAY;
        }

        captchaChallengeTitle.setTextColor(textColor);
        captchaVerifyButton.setTextColor(textColor);
        useOldCaptchaButton.setTextColor(textColor);
        reloadCaptchaButton.setTextColor(textColor);
    }

    @Override
    public void onConfigurationChanged(Configuration configuration) {
        // note: this is here because if we kept the captcha images on rotate, a bunch of recycled
        // bitmap errors are thrown instead of dealing with that, just get a new, fresh captcha
        reset();
    }

    @Override
    public void initialize(Site site, AuthenticationLayoutCallback callback, boolean autoReply) {
        this.callback = callback;
        this.isAutoReply = autoReply;

        SiteAuthentication authentication = site.actions().postAuthenticate();
        if (authentication.type != CAPTCHA2_NOJS) {
            callback.onFallbackToV1CaptchaView(isAutoReply);
            return;
        }

        presenter.init(authentication.siteKey, authentication.baseUrl);
    }

    @Override
    public void reset() {
        if (captchaHolder.hasToken() && isAutoReply) {
            callback.onAuthenticationComplete(null, captchaHolder.getToken(), true);
            return;
        }

        hardReset();
    }

    @Override
    public void hardReset() {
        CaptchaNoJsPresenterV2.RequestCaptchaInfoError captchaInfoError = presenter.requestCaptchaInfo();

        switch (captchaInfoError) {
            case OK:
            case ALREADY_SHUTDOWN:
                break;
            case HOLD_YOUR_HORSES:
                showToast(
                        getContext(),
                        R.string.captcha_layout_v2_you_are_requesting_captcha_too_fast,
                        Toast.LENGTH_LONG
                );
                break;
            case ALREADY_IN_PROGRESS:
                showToast(
                        getContext(),
                        R.string.captcha_layout_v2_captcha_request_is_already_in_progress,
                        Toast.LENGTH_LONG
                );
                break;
        }
    }

    @Override
    public void onCaptchaInfoParsed(CaptchaInfo captchaInfo) {
        BackgroundUtils.runOnMainThread(() -> {
            captchaVerifyButton.setEnabled(true);
            renderCaptchaWindow(captchaInfo);
        });
    }

    @Override
    public void onVerificationDone(String verificationToken) {
        BackgroundUtils.runOnMainThread(() -> {
            captchaHolder.addNewToken(verificationToken, RECAPTCHA_TOKEN_LIVE_TIME);

            String token;

            if (isAutoReply) {
                token = captchaHolder.getToken();
            } else {
                token = verificationToken;
            }

            captchaVerifyButton.setEnabled(true);
            callback.onAuthenticationComplete(null, token, isAutoReply);
        });
    }

    // Called when we got response from re-captcha but could not parse some part of it
    @Override
    public void onCaptchaInfoParseError(Throwable error) {
        BackgroundUtils.runOnMainThread(() -> {
            Logger.e(TAG, "CaptchaV2 error", error);
            showToast(getContext(), error.getMessage(), Toast.LENGTH_LONG);
            captchaVerifyButton.setEnabled(true);
            callback.onFallbackToV1CaptchaView(isAutoReply);
        });
    }

    private void renderCaptchaWindow(CaptchaInfo captchaInfo) {
        try {
            setCaptchaTitle(captchaInfo);

            captchaImagesGrid.setAdapter(adapter);
            int columnsCount;
            int imageSize = Math.min(getWidth(), getHeight() - dp(104));
            //40 + 64dp from layout xml; width for left-right full span, height minus for top-bottom full span inc buttons and titlebar
            ViewGroup.LayoutParams layoutParams = captchaImagesGrid.getLayoutParams();
            layoutParams.height = imageSize;
            layoutParams.width = imageSize;
            captchaImagesGrid.setLayoutParams(layoutParams);
            switch (captchaInfo.getCaptchaType()) {
                case CANONICAL:
                    columnsCount = 3;
                    break;
                case NO_CANONICAL:
                    columnsCount = 2;
                    break;
                default:
                    throw new IllegalStateException("Unknown captcha type");
            }
            imageSize /= columnsCount;
            captchaImagesGrid.setNumColumns(columnsCount);

            adapter.setImageSize(imageSize);
            adapter.setImages(captchaInfo.challengeImages);

            captchaImagesGrid.postInvalidate();

            captchaVerifyButton.setEnabled(true);
        } catch (Throwable error) {
            if (callback != null) {
                callback.onFallbackToV1CaptchaView(isAutoReply);
            }
        }
    }

    private void setCaptchaTitle(CaptchaInfo captchaInfo) {
        if (captchaInfo.getCaptchaTitle() != null) {
            if (captchaInfo.getCaptchaTitle().hasBold()) {
                SpannableString spannableString = new SpannableString(captchaInfo.getCaptchaTitle().getTitle());
                spannableString.setSpan(
                        new StyleSpan(Typeface.BOLD),
                        captchaInfo.getCaptchaTitle().getBoldStart(),
                        captchaInfo.getCaptchaTitle().getBoldEnd(),
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                );

                captchaChallengeTitle.setText(spannableString);
            } else {
                captchaChallengeTitle.setText(captchaInfo.getCaptchaTitle().getTitle());
            }
        }
    }

    private void sendVerificationResponse() {
        List<Integer> selectedIds = adapter.getCheckedImageIds();

        try {
            CaptchaNoJsPresenterV2.VerifyError verifyError = presenter.verify(selectedIds);
            switch (verifyError) {
                case OK:
                    captchaVerifyButton.setEnabled(false);
                    break;
                case NO_IMAGES_SELECTED:
                    showToast(
                            getContext(),
                            R.string.captcha_layout_v2_you_have_to_select_at_least_one_image,
                            Toast.LENGTH_LONG
                    );
                    break;
                case ALREADY_IN_PROGRESS:
                    showToast(
                            getContext(),
                            R.string.captcha_layout_v2_verification_already_in_progress,
                            Toast.LENGTH_LONG
                    );
                    break;
                case ALREADY_SHUTDOWN:
                    // do nothing
                    break;
            }
        } catch (Throwable error) {
            onCaptchaInfoParseError(error);
        }
    }

    @Override
    public void onDestroy() {
        adapter.onDestroy();
        presenter.onDestroy();
    }
}
