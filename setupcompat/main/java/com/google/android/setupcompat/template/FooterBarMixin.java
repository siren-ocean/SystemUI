/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.android.setupcompat.template;

import static com.google.android.setupcompat.internal.Preconditions.ensureOnMainThread;
import static java.lang.Math.max;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.Context;
import android.content.res.Configuration;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.os.Build.VERSION_CODES;
import android.os.PersistableBundle;
import android.util.AttributeSet;
import android.view.ContextThemeWrapper;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewStub;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.LinearLayout.LayoutParams;
import androidx.annotation.AttrRes;
import androidx.annotation.CallSuper;
import androidx.annotation.ColorInt;
import androidx.annotation.IdRes;
import androidx.annotation.LayoutRes;
import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StyleRes;
import androidx.annotation.VisibleForTesting;
import com.google.android.setupcompat.PartnerCustomizationLayout;
import com.google.android.setupcompat.R;
import com.google.android.setupcompat.internal.FooterButtonPartnerConfig;
import com.google.android.setupcompat.internal.TemplateLayout;
import com.google.android.setupcompat.logging.LoggingObserver;
import com.google.android.setupcompat.logging.LoggingObserver.SetupCompatUiEvent.ButtonInflatedEvent;
import com.google.android.setupcompat.logging.internal.FooterBarMixinMetrics;
import com.google.android.setupcompat.partnerconfig.PartnerConfig;
import com.google.android.setupcompat.partnerconfig.PartnerConfigHelper;
import com.google.android.setupcompat.template.FooterButton.ButtonType;
import java.util.Locale;

/**
 * A {@link Mixin} for managing buttons. By default, the button bar expects that buttons on the
 * start (left for LTR) are "secondary" borderless buttons, while buttons on the end (right for LTR)
 * are "primary" accent-colored buttons.
 */
public class FooterBarMixin implements Mixin {

  private final Context context;

  @Nullable private final ViewStub footerStub;

  @VisibleForTesting final boolean applyPartnerResources;
  @VisibleForTesting final boolean applyDynamicColor;
  @VisibleForTesting final boolean useFullDynamicColor;
  @VisibleForTesting final boolean footerButtonAlignEnd;

  @VisibleForTesting public LinearLayout buttonContainer;
  private FooterButton primaryButton;
  private FooterButton secondaryButton;
  private LoggingObserver loggingObserver;
  @IdRes private int primaryButtonId;
  @IdRes private int secondaryButtonId;
  @VisibleForTesting public FooterButtonPartnerConfig primaryButtonPartnerConfigForTesting;
  @VisibleForTesting public FooterButtonPartnerConfig secondaryButtonPartnerConfigForTesting;

  private int footerBarPaddingTop;
  private int footerBarPaddingBottom;
  @VisibleForTesting int footerBarPaddingStart;
  @VisibleForTesting int footerBarPaddingEnd;
  @VisibleForTesting int defaultPadding;
  @ColorInt private final int footerBarPrimaryBackgroundColor;
  @ColorInt private final int footerBarSecondaryBackgroundColor;
  private boolean removeFooterBarWhenEmpty = true;
  private boolean isSecondaryButtonInPrimaryStyle = false;

  @VisibleForTesting public final FooterBarMixinMetrics metrics = new FooterBarMixinMetrics();

  private FooterButton.OnButtonEventListener createButtonEventListener(@IdRes int id) {

    return new FooterButton.OnButtonEventListener() {

      @Override
      public void onEnabledChanged(boolean enabled) {
        if (buttonContainer != null) {
          Button button = buttonContainer.findViewById(id);
          if (button != null) {
            button.setEnabled(enabled);
            if (applyPartnerResources && !applyDynamicColor) {

              updateButtonTextColorWithStates(
                  button,
                  (id == primaryButtonId || isSecondaryButtonInPrimaryStyle)
                      ? PartnerConfig.CONFIG_FOOTER_PRIMARY_BUTTON_TEXT_COLOR
                      : PartnerConfig.CONFIG_FOOTER_SECONDARY_BUTTON_TEXT_COLOR,
                  (id == primaryButtonId || isSecondaryButtonInPrimaryStyle)
                      ? PartnerConfig.CONFIG_FOOTER_PRIMARY_BUTTON_DISABLED_TEXT_COLOR
                      : PartnerConfig.CONFIG_FOOTER_SECONDARY_BUTTON_DISABLED_TEXT_COLOR);
            }
          }
        }
      }

      @Override
      public void onVisibilityChanged(int visibility) {
        if (buttonContainer != null) {
          Button button = buttonContainer.findViewById(id);
          if (button != null) {
            button.setVisibility(visibility);
            autoSetButtonBarVisibility();
          }
        }
      }

      @Override
      public void onTextChanged(CharSequence text) {
        if (buttonContainer != null) {
          Button button = buttonContainer.findViewById(id);
          if (button != null) {
            button.setText(text);
          }
        }
      }

      @Override
      public void onLocaleChanged(Locale locale) {
        if (buttonContainer != null) {
          Button button = buttonContainer.findViewById(id);
          if (button != null && locale != null) {
            button.setTextLocale(locale);
          }
        }
      }

      @Override
      public void onDirectionChanged(int direction) {
        if (buttonContainer != null && direction != -1) {
          buttonContainer.setLayoutDirection(direction);
        }
      }
    };
  }

  /**
   * Creates a mixin for managing buttons on the footer.
   *
   * @param layout The {@link TemplateLayout} containing this mixin.
   * @param attrs XML attributes given to the layout.
   * @param defStyleAttr The default style attribute as given to the constructor of the layout.
   */
  public FooterBarMixin(
      TemplateLayout layout, @Nullable AttributeSet attrs, @AttrRes int defStyleAttr) {
    context = layout.getContext();
    footerStub = layout.findManagedViewById(R.id.suc_layout_footer);
    FooterButtonStyleUtils.clearSavedDefaultTextColor();

    this.applyPartnerResources =
        layout instanceof PartnerCustomizationLayout
            && ((PartnerCustomizationLayout) layout).shouldApplyPartnerResource();

    applyDynamicColor =
        layout instanceof PartnerCustomizationLayout
            && ((PartnerCustomizationLayout) layout).shouldApplyDynamicColor();

    useFullDynamicColor =
        layout instanceof PartnerCustomizationLayout
            && ((PartnerCustomizationLayout) layout).useFullDynamicColor();

    TypedArray a =
        context.obtainStyledAttributes(attrs, R.styleable.SucFooterBarMixin, defStyleAttr, 0);
    defaultPadding =
        a.getDimensionPixelSize(R.styleable.SucFooterBarMixin_sucFooterBarPaddingVertical, 0);
    footerBarPaddingTop =
        a.getDimensionPixelSize(
            R.styleable.SucFooterBarMixin_sucFooterBarPaddingTop, defaultPadding);
    footerBarPaddingBottom =
        a.getDimensionPixelSize(
            R.styleable.SucFooterBarMixin_sucFooterBarPaddingBottom, defaultPadding);
    footerBarPaddingStart =
        a.getDimensionPixelSize(R.styleable.SucFooterBarMixin_sucFooterBarPaddingStart, 0);
    footerBarPaddingEnd =
        a.getDimensionPixelSize(R.styleable.SucFooterBarMixin_sucFooterBarPaddingEnd, 0);
    footerBarPrimaryBackgroundColor =
        a.getColor(R.styleable.SucFooterBarMixin_sucFooterBarPrimaryFooterBackground, 0);
    footerBarSecondaryBackgroundColor =
        a.getColor(R.styleable.SucFooterBarMixin_sucFooterBarSecondaryFooterBackground, 0);
    footerButtonAlignEnd =
        a.getBoolean(R.styleable.SucFooterBarMixin_sucFooterBarButtonAlignEnd, false);

    int primaryBtn =
        a.getResourceId(R.styleable.SucFooterBarMixin_sucFooterBarPrimaryFooterButton, 0);
    int secondaryBtn =
        a.getResourceId(R.styleable.SucFooterBarMixin_sucFooterBarSecondaryFooterButton, 0);
    a.recycle();

    FooterButtonInflater inflater = new FooterButtonInflater(context);

    if (secondaryBtn != 0) {
      setSecondaryButton(inflater.inflate(secondaryBtn));
      metrics.logPrimaryButtonInitialStateVisibility(/* isVisible= */ true, /* isUsingXml= */ true);
    }

    if (primaryBtn != 0) {
      setPrimaryButton(inflater.inflate(primaryBtn));
      metrics.logSecondaryButtonInitialStateVisibility(
          /* isVisible= */ true, /* isUsingXml= */ true);
    }
  }

  public void setLoggingObserver(LoggingObserver observer) {
    loggingObserver = observer;

    // If primary button is already created, it's likely that {@code setPrimaryButton()} was called
    // before an {@link LoggingObserver} is set, we need to set an observer and call the right
    // logging method here.
    if (primaryButtonId != 0) {
      loggingObserver.log(
          new ButtonInflatedEvent(getPrimaryButtonView(), LoggingObserver.ButtonType.PRIMARY));
      getPrimaryButton().setLoggingObserver(observer);
    }
    // Same for secondary button.
    if (secondaryButtonId != 0) {
      loggingObserver.log(
          new ButtonInflatedEvent(getSecondaryButtonView(), LoggingObserver.ButtonType.SECONDARY));
      getSecondaryButton().setLoggingObserver(observer);
    }
  }

  protected boolean isFooterButtonAlignedEnd() {
    if (PartnerConfigHelper.get(context)
        .isPartnerConfigAvailable(PartnerConfig.CONFIG_FOOTER_BUTTON_ALIGNED_END)) {
      return PartnerConfigHelper.get(context)
          .getBoolean(context, PartnerConfig.CONFIG_FOOTER_BUTTON_ALIGNED_END, false);
    } else {
      return footerButtonAlignEnd;
    }
  }

  protected boolean isFooterButtonsEvenlyWeighted() {
    if (!isSecondaryButtonInPrimaryStyle) {
      return false;
    }
    PartnerConfigHelper.get(context);
    return PartnerConfigHelper.isNeutralButtonStyleEnabled(context);
  }

  private View addSpace() {
    LinearLayout buttonContainerlayout = ensureFooterInflated();
    View space = new View(context);
    space.setLayoutParams(new LayoutParams(0, 0, 1.0f));
    space.setVisibility(View.INVISIBLE);
    buttonContainerlayout.addView(space);
    return space;
  }

  @NonNull
  private LinearLayout ensureFooterInflated() {
    if (buttonContainer == null) {
      if (footerStub == null) {
        throw new IllegalStateException("Footer stub is not found in this template");
      }
      buttonContainer = (LinearLayout) inflateFooter(R.layout.suc_footer_button_bar);
      onFooterBarInflated(buttonContainer);
      onFooterBarApplyPartnerResource(buttonContainer);
    }
    return buttonContainer;
  }

  /**
   * Notifies that the footer bar has been inflated to the view hierarchy. Calling super is
   * necessary while subclass implement it.
   */
  @CallSuper
  protected void onFooterBarInflated(LinearLayout buttonContainer) {
    if (buttonContainer == null) {
      // Ignore action since buttonContainer is null
      return;
    }
    buttonContainer.setId(View.generateViewId());
    updateFooterBarPadding(
        buttonContainer,
        footerBarPaddingStart,
        footerBarPaddingTop,
        footerBarPaddingEnd,
        footerBarPaddingBottom);
    if (isFooterButtonAlignedEnd()) {
      buttonContainer.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
    }
  }

  /**
   * Notifies while the footer bar apply Partner Resource. Calling super is necessary while subclass
   * implement it.
   */
  @CallSuper
  protected void onFooterBarApplyPartnerResource(LinearLayout buttonContainer) {
    if (buttonContainer == null) {
      // Ignore action since buttonContainer is null
      return;
    }
    if (!applyPartnerResources) {
      return;
    }

    // skip apply partner resources on footerbar background if dynamic color enabled
    if (!useFullDynamicColor) {
      @ColorInt
      int color =
          PartnerConfigHelper.get(context)
              .getColor(context, PartnerConfig.CONFIG_FOOTER_BAR_BG_COLOR);
      buttonContainer.setBackgroundColor(color);
    }

    if (PartnerConfigHelper.get(context)
        .isPartnerConfigAvailable(PartnerConfig.CONFIG_FOOTER_BUTTON_PADDING_TOP)) {
      footerBarPaddingTop =
          (int)
              PartnerConfigHelper.get(context)
                  .getDimension(context, PartnerConfig.CONFIG_FOOTER_BUTTON_PADDING_TOP);
    }
    if (PartnerConfigHelper.get(context)
        .isPartnerConfigAvailable(PartnerConfig.CONFIG_FOOTER_BUTTON_PADDING_BOTTOM)) {
      footerBarPaddingBottom =
          (int)
              PartnerConfigHelper.get(context)
                  .getDimension(context, PartnerConfig.CONFIG_FOOTER_BUTTON_PADDING_BOTTOM);
    }
    if (PartnerConfigHelper.get(context)
        .isPartnerConfigAvailable(PartnerConfig.CONFIG_FOOTER_BAR_PADDING_START)) {
      footerBarPaddingStart =
          (int)
              PartnerConfigHelper.get(context)
                  .getDimension(context, PartnerConfig.CONFIG_FOOTER_BAR_PADDING_START);
    }
    if (PartnerConfigHelper.get(context)
        .isPartnerConfigAvailable(PartnerConfig.CONFIG_FOOTER_BAR_PADDING_END)) {
      footerBarPaddingEnd =
          (int)
              PartnerConfigHelper.get(context)
                  .getDimension(context, PartnerConfig.CONFIG_FOOTER_BAR_PADDING_END);
    }
    updateFooterBarPadding(
        buttonContainer,
        footerBarPaddingStart,
        footerBarPaddingTop,
        footerBarPaddingEnd,
        footerBarPaddingBottom);

    if (PartnerConfigHelper.get(context)
        .isPartnerConfigAvailable(PartnerConfig.CONFIG_FOOTER_BAR_MIN_HEIGHT)) {
      int minHeight =
          (int)
              PartnerConfigHelper.get(context)
                  .getDimension(context, PartnerConfig.CONFIG_FOOTER_BAR_MIN_HEIGHT);
      if (minHeight > 0) {
        buttonContainer.setMinimumHeight(minHeight);
      }
    }
  }

  /**
   * Inflate FooterActionButton with layout "suc_button". Subclasses can implement this method to
   * modify the footer button layout as necessary.
   */
  @SuppressLint("InflateParams")
  protected FooterActionButton createThemedButton(Context context, @StyleRes int theme) {
    // Inflate a single button from XML, which when using support lib, will take advantage of
    // the injected layout inflater and give us AppCompatButton instead.
    LayoutInflater inflater = LayoutInflater.from(new ContextThemeWrapper(context, theme));
    return (FooterActionButton) inflater.inflate(R.layout.suc_button, null, false);
  }

  /** Sets primary button for footer. */
  @MainThread
  public void setPrimaryButton(FooterButton footerButton) {
    ensureOnMainThread("setPrimaryButton");
    ensureFooterInflated();

    // Setup button partner config
    FooterButtonPartnerConfig footerButtonPartnerConfig =
        new FooterButtonPartnerConfig.Builder(footerButton)
            .setPartnerTheme(
                getPartnerTheme(
                    footerButton,
                    /* defaultPartnerTheme= */ R.style.SucPartnerCustomizationButton_Primary,
                    /* buttonBackgroundColorConfig= */ PartnerConfig
                        .CONFIG_FOOTER_PRIMARY_BUTTON_BG_COLOR))
            .setButtonBackgroundConfig(PartnerConfig.CONFIG_FOOTER_PRIMARY_BUTTON_BG_COLOR)
            .setButtonDisableAlphaConfig(PartnerConfig.CONFIG_FOOTER_BUTTON_DISABLED_ALPHA)
            .setButtonDisableBackgroundConfig(PartnerConfig.CONFIG_FOOTER_BUTTON_DISABLED_BG_COLOR)
            .setButtonDisableTextColorConfig(
                PartnerConfig.CONFIG_FOOTER_PRIMARY_BUTTON_DISABLED_TEXT_COLOR)
            .setButtonIconConfig(getDrawablePartnerConfig(footerButton.getButtonType()))
            .setButtonRadiusConfig(PartnerConfig.CONFIG_FOOTER_BUTTON_RADIUS)
            .setButtonRippleColorAlphaConfig(PartnerConfig.CONFIG_FOOTER_BUTTON_RIPPLE_COLOR_ALPHA)
            .setTextColorConfig(PartnerConfig.CONFIG_FOOTER_PRIMARY_BUTTON_TEXT_COLOR)
            .setMarginStartConfig(PartnerConfig.CONFIG_FOOTER_PRIMARY_BUTTON_MARGIN_START)
            .setTextSizeConfig(PartnerConfig.CONFIG_FOOTER_BUTTON_TEXT_SIZE)
            .setButtonMinHeight(PartnerConfig.CONFIG_FOOTER_BUTTON_MIN_HEIGHT)
            .setTextTypeFaceConfig(PartnerConfig.CONFIG_FOOTER_BUTTON_FONT_FAMILY)
            .setTextWeightConfig(PartnerConfig.CONFIG_FOOTER_BUTTON_FONT_WEIGHT)
            .setTextStyleConfig(PartnerConfig.CONFIG_FOOTER_BUTTON_TEXT_STYLE)
            .build();

    FooterActionButton button = inflateButton(footerButton, footerButtonPartnerConfig);
    // update information for primary button. Need to update as long as the button inflated.
    primaryButtonId = button.getId();
    button.setPrimaryButtonStyle(/* isPrimaryButtonStyle= */ true);
    primaryButton = footerButton;
    primaryButtonPartnerConfigForTesting = footerButtonPartnerConfig;
    onFooterButtonInflated(button, footerBarPrimaryBackgroundColor);
    onFooterButtonApplyPartnerResource(button, footerButtonPartnerConfig);
    if (loggingObserver != null) {
      loggingObserver.log(
          new ButtonInflatedEvent(getPrimaryButtonView(), LoggingObserver.ButtonType.PRIMARY));
      footerButton.setLoggingObserver(loggingObserver);
    }

    // Make sure the position of buttons are correctly and prevent primary button create twice or
    // more.
    repopulateButtons();
  }

  /** Returns the {@link FooterButton} of primary button. */
  public FooterButton getPrimaryButton() {
    return primaryButton;
  }

  /**
   * Returns the {@link Button} of primary button.
   *
   * @apiNote It is not recommended to apply style to the view directly. The setup library will
   *     handle the button style. There is no guarantee that changes made directly to the button
   *     style will not cause unexpected behavior.
   */
  public Button getPrimaryButtonView() {
    return buttonContainer == null ? null : buttonContainer.findViewById(primaryButtonId);
  }

  @VisibleForTesting
  boolean isPrimaryButtonVisible() {
    return getPrimaryButtonView() != null && getPrimaryButtonView().getVisibility() == View.VISIBLE;
  }

  /** Sets secondary button for footer. */
  @MainThread
  public void setSecondaryButton(FooterButton footerButton) {
    setSecondaryButton(footerButton, /* usePrimaryStyle= */ false);
  }

  /** Sets secondary button for footer. Allow to use the primary button style. */
  @MainThread
  public void setSecondaryButton(FooterButton footerButton, boolean usePrimaryStyle) {
    ensureOnMainThread("setSecondaryButton");
    isSecondaryButtonInPrimaryStyle = usePrimaryStyle;
    ensureFooterInflated();

    // Setup button partner config
    FooterButtonPartnerConfig footerButtonPartnerConfig =
        new FooterButtonPartnerConfig.Builder(footerButton)
            .setPartnerTheme(
                getPartnerTheme(
                    footerButton,
                    /* defaultPartnerTheme= */ usePrimaryStyle
                        ? R.style.SucPartnerCustomizationButton_Primary
                        : R.style.SucPartnerCustomizationButton_Secondary,
                    /* buttonBackgroundColorConfig= */ usePrimaryStyle
                        ? PartnerConfig.CONFIG_FOOTER_PRIMARY_BUTTON_BG_COLOR
                        : PartnerConfig.CONFIG_FOOTER_SECONDARY_BUTTON_BG_COLOR))
            .setButtonBackgroundConfig(
                usePrimaryStyle
                    ? PartnerConfig.CONFIG_FOOTER_PRIMARY_BUTTON_BG_COLOR
                    : PartnerConfig.CONFIG_FOOTER_SECONDARY_BUTTON_BG_COLOR)
            .setButtonDisableAlphaConfig(PartnerConfig.CONFIG_FOOTER_BUTTON_DISABLED_ALPHA)
            .setButtonDisableBackgroundConfig(PartnerConfig.CONFIG_FOOTER_BUTTON_DISABLED_BG_COLOR)
            .setButtonDisableTextColorConfig(
                usePrimaryStyle
                    ? PartnerConfig.CONFIG_FOOTER_PRIMARY_BUTTON_DISABLED_TEXT_COLOR
                    : PartnerConfig.CONFIG_FOOTER_SECONDARY_BUTTON_DISABLED_TEXT_COLOR)
            .setButtonIconConfig(getDrawablePartnerConfig(footerButton.getButtonType()))
            .setButtonRadiusConfig(PartnerConfig.CONFIG_FOOTER_BUTTON_RADIUS)
            .setButtonRippleColorAlphaConfig(PartnerConfig.CONFIG_FOOTER_BUTTON_RIPPLE_COLOR_ALPHA)
            .setTextColorConfig(
                usePrimaryStyle
                    ? PartnerConfig.CONFIG_FOOTER_PRIMARY_BUTTON_TEXT_COLOR
                    : PartnerConfig.CONFIG_FOOTER_SECONDARY_BUTTON_TEXT_COLOR)
            .setMarginStartConfig(PartnerConfig.CONFIG_FOOTER_SECONDARY_BUTTON_MARGIN_START)
            .setTextSizeConfig(PartnerConfig.CONFIG_FOOTER_BUTTON_TEXT_SIZE)
            .setButtonMinHeight(PartnerConfig.CONFIG_FOOTER_BUTTON_MIN_HEIGHT)
            .setTextTypeFaceConfig(PartnerConfig.CONFIG_FOOTER_BUTTON_FONT_FAMILY)
            .setTextWeightConfig(PartnerConfig.CONFIG_FOOTER_BUTTON_FONT_WEIGHT)
            .setTextStyleConfig(PartnerConfig.CONFIG_FOOTER_BUTTON_TEXT_STYLE)
            .build();

    FooterActionButton button = inflateButton(footerButton, footerButtonPartnerConfig);
    // update information for secondary button. Need to update as long as the button inflated.
    secondaryButtonId = button.getId();
    button.setPrimaryButtonStyle(usePrimaryStyle);
    secondaryButton = footerButton;
    secondaryButtonPartnerConfigForTesting = footerButtonPartnerConfig;

    onFooterButtonInflated(button, footerBarSecondaryBackgroundColor);
    onFooterButtonApplyPartnerResource(button, footerButtonPartnerConfig);
    if (loggingObserver != null) {
      loggingObserver.log(new ButtonInflatedEvent(button, LoggingObserver.ButtonType.SECONDARY));
      footerButton.setLoggingObserver(loggingObserver);
    }

    // Make sure the position of buttons are correctly and prevent secondary button create twice or
    // more.
    repopulateButtons();
  }

  /**
   * Corrects the order of footer buttons after the button has been inflated to the view hierarchy.
   * Subclasses can implement this method to modify the order of footer buttons as necessary.
   */
  protected void repopulateButtons() {
    LinearLayout buttonContainer = ensureFooterInflated();
    Button tempPrimaryButton = getPrimaryButtonView();
    Button tempSecondaryButton = getSecondaryButtonView();
    buttonContainer.removeAllViews();

    boolean isEvenlyWeightedButtons = isFooterButtonsEvenlyWeighted();
    boolean isLandscape =
        context.getResources().getConfiguration().orientation
            == Configuration.ORIENTATION_LANDSCAPE;
    if (isLandscape && isEvenlyWeightedButtons && isFooterButtonAlignedEnd()) {
      addSpace();
    }

    if (tempSecondaryButton != null) {
      if (isSecondaryButtonInPrimaryStyle) {
        // Since the secondary button has the same style (with background) as the primary button,
        // we need to have the left padding equal to the right padding.
        updateFooterBarPadding(
            buttonContainer,
            buttonContainer.getPaddingRight(),
            buttonContainer.getPaddingTop(),
            buttonContainer.getPaddingRight(),
            buttonContainer.getPaddingBottom());
      }
      buttonContainer.addView(tempSecondaryButton);
    }
    if (!isFooterButtonAlignedEnd()) {
      addSpace();
    }
    if (tempPrimaryButton != null) {
      buttonContainer.addView(tempPrimaryButton);
    }

    setEvenlyWeightedButtons(tempPrimaryButton, tempSecondaryButton, isEvenlyWeightedButtons);
  }

  private void setEvenlyWeightedButtons(
      Button primaryButton, Button secondaryButton, boolean isEvenlyWeighted) {
    if (primaryButton != null && secondaryButton != null && isEvenlyWeighted) {
      primaryButton.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
      int primaryButtonMeasuredWidth = primaryButton.getMeasuredWidth();
      secondaryButton.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);

      int secondaryButtonMeasuredWidth = secondaryButton.getMeasuredWidth();
      int maxButtonMeasureWidth = max(primaryButtonMeasuredWidth, secondaryButtonMeasuredWidth);

      primaryButton.getLayoutParams().width = maxButtonMeasureWidth;
      secondaryButton.getLayoutParams().width = maxButtonMeasureWidth;
    } else {
      if (primaryButton != null) {
        LinearLayout.LayoutParams primaryLayoutParams =
            (LinearLayout.LayoutParams) primaryButton.getLayoutParams();
        if (null != primaryLayoutParams) {
          primaryLayoutParams.width = ViewGroup.LayoutParams.WRAP_CONTENT;
          primaryLayoutParams.weight = 0;
          primaryButton.setLayoutParams(primaryLayoutParams);
        }
      }
      if (secondaryButton != null) {
        LinearLayout.LayoutParams secondaryLayoutParams =
            (LinearLayout.LayoutParams) secondaryButton.getLayoutParams();
        if (null != secondaryLayoutParams) {
          secondaryLayoutParams.width = ViewGroup.LayoutParams.WRAP_CONTENT;
          secondaryLayoutParams.weight = 0;
          secondaryButton.setLayoutParams(secondaryLayoutParams);
        }
      }
    }
  }

  /**
   * Notifies that the footer button has been inInflated and add to the view hierarchy. Calling
   * super is necessary while subclass implement it.
   */
  @CallSuper
  protected void onFooterButtonInflated(Button button, @ColorInt int defaultButtonBackgroundColor) {
    // Try to set default background
    if (!applyDynamicColor) {
      if (defaultButtonBackgroundColor != 0) {
        FooterButtonStyleUtils.updateButtonBackground(button, defaultButtonBackgroundColor);
      } else {
        // TODO: get button background color from activity theme
      }
    }
    buttonContainer.addView(button);
    autoSetButtonBarVisibility();
  }

  private int getPartnerTheme(
      FooterButton footerButton,
      int defaultPartnerTheme,
      PartnerConfig buttonBackgroundColorConfig) {
    int overrideTheme = footerButton.getTheme();

    // Set the default theme if theme is not set, or when running in setup flow.
    if (footerButton.getTheme() == 0 || applyPartnerResources) {
      overrideTheme = defaultPartnerTheme;
    }
    // TODO: Make sure customize attributes in theme can be applied during setup flow.
    // If sets background color to full transparent, the button changes to colored borderless ink
    // button style.
    if (applyPartnerResources) {
      int color = PartnerConfigHelper.get(context).getColor(context, buttonBackgroundColorConfig);
      if (color == Color.TRANSPARENT) {
        overrideTheme = R.style.SucPartnerCustomizationButton_Secondary;
      } else {
        overrideTheme = R.style.SucPartnerCustomizationButton_Primary;
      }
    }
    return overrideTheme;
  }

  /** Returns the {@link LinearLayout} of button container. */
  public LinearLayout getButtonContainer() {
    return buttonContainer;
  }

  /** Returns the {@link FooterButton} of secondary button. */
  public FooterButton getSecondaryButton() {
    return secondaryButton;
  }

  /**
   * Sets whether the footer bar should be removed when there are no footer buttons in the bar.
   *
   * @param value True if footer bar is gone, false otherwise.
   */
  public void setRemoveFooterBarWhenEmpty(boolean value) {
    removeFooterBarWhenEmpty = value;
    autoSetButtonBarVisibility();
  }

  /**
   * Checks the visibility state of footer buttons to set the visibility state of this footer bar
   * automatically.
   */
  private void autoSetButtonBarVisibility() {
    Button primaryButton = getPrimaryButtonView();
    Button secondaryButton = getSecondaryButtonView();
    boolean primaryVisible = primaryButton != null && primaryButton.getVisibility() == View.VISIBLE;
    boolean secondaryVisible =
        secondaryButton != null && secondaryButton.getVisibility() == View.VISIBLE;

    if (buttonContainer != null) {
      buttonContainer.setVisibility(
          primaryVisible || secondaryVisible
              ? View.VISIBLE
              : removeFooterBarWhenEmpty ? View.GONE : View.INVISIBLE);
    }
  }

  /** Returns the visibility status for this footer bar. */
  @VisibleForTesting
  public int getVisibility() {
    return buttonContainer.getVisibility();
  }

  /**
   * Returns the {@link Button} of secondary button.
   *
   * @apiNote It is not recommended to apply style to the view directly. The setup library will
   *     handle the button style. There is no guarantee that changes made directly to the button
   *     style will not cause unexpected behavior.
   */
  public Button getSecondaryButtonView() {
    return buttonContainer == null ? null : buttonContainer.findViewById(secondaryButtonId);
  }

  @VisibleForTesting
  boolean isSecondaryButtonVisible() {
    return getSecondaryButtonView() != null
        && getSecondaryButtonView().getVisibility() == View.VISIBLE;
  }

  private FooterActionButton inflateButton(
      FooterButton footerButton, FooterButtonPartnerConfig footerButtonPartnerConfig) {
    FooterActionButton button =
        createThemedButton(context, footerButtonPartnerConfig.getPartnerTheme());
    button.setId(View.generateViewId());

    // apply initial configuration into button view.
    button.setText(footerButton.getText());
    button.setOnClickListener(footerButton);
    button.setVisibility(footerButton.getVisibility());
    button.setEnabled(footerButton.isEnabled());
    button.setFooterButton(footerButton);

    footerButton.setOnButtonEventListener(createButtonEventListener(button.getId()));
    return button;
  }

  // TODO: Make sure customize attributes in theme can be applied during setup flow.
  @TargetApi(VERSION_CODES.Q)
  private void onFooterButtonApplyPartnerResource(
      Button button, FooterButtonPartnerConfig footerButtonPartnerConfig) {
    if (!applyPartnerResources) {
      return;
    }
    FooterButtonStyleUtils.applyButtonPartnerResources(
        context,
        button,
        applyDynamicColor,
        /* isButtonIconAtEnd= */ (button.getId() == primaryButtonId),
        footerButtonPartnerConfig);
    if (!applyDynamicColor) {
      // adjust text color based on enabled state
      updateButtonTextColorWithStates(
          button,
          footerButtonPartnerConfig.getButtonTextColorConfig(),
          footerButtonPartnerConfig.getButtonDisableTextColorConfig());
    }
  }

  private void updateButtonTextColorWithStates(
      Button button,
      PartnerConfig buttonTextColorConfig,
      PartnerConfig buttonTextDisabledColorConfig) {
    if (button.isEnabled()) {
      FooterButtonStyleUtils.updateButtonTextEnabledColorWithPartnerConfig(
          context, button, buttonTextColorConfig);
    } else {
      FooterButtonStyleUtils.updateButtonTextDisabledColorWithPartnerConfig(
          context, button, buttonTextDisabledColorConfig);
    }
  }

  private static PartnerConfig getDrawablePartnerConfig(@ButtonType int buttonType) {
    PartnerConfig result;
    switch (buttonType) {
      case ButtonType.ADD_ANOTHER:
        result = PartnerConfig.CONFIG_FOOTER_BUTTON_ICON_ADD_ANOTHER;
        break;
      case ButtonType.CANCEL:
        result = PartnerConfig.CONFIG_FOOTER_BUTTON_ICON_CANCEL;
        break;
      case ButtonType.CLEAR:
        result = PartnerConfig.CONFIG_FOOTER_BUTTON_ICON_CLEAR;
        break;
      case ButtonType.DONE:
        result = PartnerConfig.CONFIG_FOOTER_BUTTON_ICON_DONE;
        break;
      case ButtonType.NEXT:
        result = PartnerConfig.CONFIG_FOOTER_BUTTON_ICON_NEXT;
        break;
      case ButtonType.OPT_IN:
        result = PartnerConfig.CONFIG_FOOTER_BUTTON_ICON_OPT_IN;
        break;
      case ButtonType.SKIP:
        result = PartnerConfig.CONFIG_FOOTER_BUTTON_ICON_SKIP;
        break;
      case ButtonType.STOP:
        result = PartnerConfig.CONFIG_FOOTER_BUTTON_ICON_STOP;
        break;
      case ButtonType.OTHER:
      default:
        result = null;
        break;
    }
    return result;
  }

  protected View inflateFooter(@LayoutRes int footer) {
    LayoutInflater inflater =
        LayoutInflater.from(
            new ContextThemeWrapper(context, R.style.SucPartnerCustomizationButtonBar_Stackable));
    footerStub.setLayoutInflater(inflater);

    footerStub.setLayoutResource(footer);
    return footerStub.inflate();
  }

  private void updateFooterBarPadding(
      LinearLayout buttonContainer, int left, int top, int right, int bottom) {
    if (buttonContainer == null) {
      // Ignore action since buttonContainer is null
      return;
    }
    buttonContainer.setPadding(left, top, right, bottom);
  }

  /** Returns the paddingTop of footer bar. */
  @VisibleForTesting
  int getPaddingTop() {
    return (buttonContainer != null) ? buttonContainer.getPaddingTop() : footerStub.getPaddingTop();
  }

  /** Returns the paddingBottom of footer bar. */
  @VisibleForTesting
  int getPaddingBottom() {
    return (buttonContainer != null)
        ? buttonContainer.getPaddingBottom()
        : footerStub.getPaddingBottom();
  }

  /** Uses for notify mixin the view already attached to window. */
  public void onAttachedToWindow() {
    metrics.logPrimaryButtonInitialStateVisibility(
        /* isVisible= */ isPrimaryButtonVisible(), /* isUsingXml= */ false);
    metrics.logSecondaryButtonInitialStateVisibility(
        /* isVisible= */ isSecondaryButtonVisible(), /* isUsingXml= */ false);
  }

  /** Uses for notify mixin the view already detached from window. */
  public void onDetachedFromWindow() {
    metrics.updateButtonVisibility(isPrimaryButtonVisible(), isSecondaryButtonVisible());
  }

  /**
   * Assigns logging metrics to bundle for PartnerCustomizationLayout to log metrics to SetupWizard.
   */
  @TargetApi(VERSION_CODES.Q)
  public PersistableBundle getLoggingMetrics() {
    return metrics.getMetrics();
  }
}
