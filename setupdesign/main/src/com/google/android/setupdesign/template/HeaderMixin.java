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

package com.google.android.setupdesign.template;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.TypedArray;
import android.os.Build;
import android.util.AttributeSet;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.ViewTreeObserver;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.AttrRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import com.google.android.setupcompat.internal.TemplateLayout;
import com.google.android.setupcompat.partnerconfig.PartnerConfig;
import com.google.android.setupcompat.partnerconfig.PartnerConfigHelper;
import com.google.android.setupcompat.template.Mixin;
import com.google.android.setupdesign.R;
import com.google.android.setupdesign.util.HeaderAreaStyler;
import com.google.android.setupdesign.util.LayoutStyler;
import com.google.android.setupdesign.util.PartnerStyleHelper;
import com.google.errorprone.annotations.CanIgnoreReturnValue;

/**
 * A {@link com.google.android.setupcompat.template.Mixin} for setting and getting the header text.
 */
public class HeaderMixin implements Mixin {

  private final TemplateLayout templateLayout;
  @VisibleForTesting boolean autoTextSizeEnabled = false;
  private float headerAutoSizeMaxTextSizeInPx;
  private float headerAutoSizeMinTextSizeInPx;
  private float headerAutoSizeLineExtraSpacingInPx;
  private int headerAutoSizeMaxLineOfMaxSize;
  private static final int AUTO_SIZE_DEFAULT_MAX_LINES = 6;

  /**
   * A {@link com.google.android.setupcompat.template.Mixin} for setting and getting the Header.
   *
   * @param layout The layout this Mixin belongs to
   * @param attrs XML attributes given to the layout
   * @param defStyleAttr The default style attribute as given to the constructor of the layout
   */
  @CanIgnoreReturnValue
  public HeaderMixin(
      @NonNull TemplateLayout layout, @Nullable AttributeSet attrs, @AttrRes int defStyleAttr) {
    templateLayout = layout;

    final TypedArray a =
        layout
            .getContext()
            .obtainStyledAttributes(attrs, R.styleable.SucHeaderMixin, defStyleAttr, 0);

    final CharSequence headerText = a.getText(R.styleable.SucHeaderMixin_sucHeaderText);
    final ColorStateList headerTextColor =
        a.getColorStateList(R.styleable.SucHeaderMixin_sucHeaderTextColor);

    a.recycle();

    // Try to update the flag of the uto size config settings
    tryUpdateAutoTextSizeFlagWithPartnerConfig();

    // Set the header text
    if (headerText != null) {
      setText(headerText);
    }
    // Set the header text color
    if (headerTextColor != null) {
      setTextColor(headerTextColor);
    }
  }

  private void tryUpdateAutoTextSizeFlagWithPartnerConfig() {
    Context context = templateLayout.getContext();
    if (!PartnerStyleHelper.shouldApplyPartnerResource(templateLayout)) {
      autoTextSizeEnabled = false;
      return;
    }
    // overridden by partner resource
    if (PartnerConfigHelper.get(context)
        .isPartnerConfigAvailable(PartnerConfig.CONFIG_HEADER_AUTO_SIZE_ENABLED)) {
      autoTextSizeEnabled =
          PartnerConfigHelper.get(context)
              .getBoolean(
                  context, PartnerConfig.CONFIG_HEADER_AUTO_SIZE_ENABLED, autoTextSizeEnabled);
    }
    if (!autoTextSizeEnabled) {
      return;
    }

    if (PartnerConfigHelper.get(context)
        .isPartnerConfigAvailable(PartnerConfig.CONFIG_HEADER_AUTO_SIZE_MAX_TEXT_SIZE)) {
      headerAutoSizeMaxTextSizeInPx =
          PartnerConfigHelper.get(context)
              .getDimension(context, PartnerConfig.CONFIG_HEADER_AUTO_SIZE_MAX_TEXT_SIZE);
    }
    if (PartnerConfigHelper.get(context)
        .isPartnerConfigAvailable(PartnerConfig.CONFIG_HEADER_AUTO_SIZE_MIN_TEXT_SIZE)) {
      headerAutoSizeMinTextSizeInPx =
          PartnerConfigHelper.get(context)
              .getDimension(context, PartnerConfig.CONFIG_HEADER_AUTO_SIZE_MIN_TEXT_SIZE);
    }
    if (PartnerConfigHelper.get(context)
        .isPartnerConfigAvailable(PartnerConfig.CONFIG_HEADER_AUTO_SIZE_LINE_SPACING_EXTRA)) {
      headerAutoSizeLineExtraSpacingInPx =
          PartnerConfigHelper.get(context)
              .getDimension(context, PartnerConfig.CONFIG_HEADER_AUTO_SIZE_LINE_SPACING_EXTRA);
    }
    if (PartnerConfigHelper.get(context)
        .isPartnerConfigAvailable(PartnerConfig.CONFIG_HEADER_AUTO_SIZE_MAX_LINE_OF_MAX_SIZE)) {
      headerAutoSizeMaxLineOfMaxSize =
          PartnerConfigHelper.get(context)
              .getInteger(context, PartnerConfig.CONFIG_HEADER_AUTO_SIZE_MAX_LINE_OF_MAX_SIZE, 0);
    }
    if ((headerAutoSizeMaxLineOfMaxSize < 1)
        || (headerAutoSizeMinTextSizeInPx <= 0)
        || (headerAutoSizeMaxTextSizeInPx < headerAutoSizeMinTextSizeInPx)) {
      Log.w("HeaderMixin", "Invalid configs, disable auto text size.");
      autoTextSizeEnabled = false;
    }
  }

  /**
   * Applies the partner customizations to the header text (contains text alignment), background,
   * and margin. If apply heavy theme resource, it will apply all partner customizations, otherwise,
   * only apply alignment style. In addition, if only enable extended customized flag, the margin
   * style will be applied.
   */
  public void tryApplyPartnerCustomizationStyle() {
    TextView header = templateLayout.findManagedViewById(R.id.suc_layout_title);
    if (PartnerStyleHelper.shouldApplyPartnerResource(templateLayout)) {
      View headerAreaView = templateLayout.findManagedViewById(R.id.sud_layout_header);
      LayoutStyler.applyPartnerCustomizationExtraPaddingStyle(headerAreaView);
      HeaderAreaStyler.applyPartnerCustomizationHeaderStyle(header);
      HeaderAreaStyler.applyPartnerCustomizationHeaderAreaStyle((ViewGroup) headerAreaView);
    }
    // Try to update the flag of the uto size config settings
    tryUpdateAutoTextSizeFlagWithPartnerConfig();
    if (autoTextSizeEnabled) {
      // Override the text size setting of the header
      autoAdjustTextSize(header);
    }
  }

  /** Returns the TextView displaying the header. */
  public TextView getTextView() {
    return (TextView) templateLayout.findManagedViewById(R.id.suc_layout_title);
  }

  /**
   * Sets the header text. This can also be set via the XML attribute {@code app:sucHeaderText}.
   *
   * @param title The resource ID of the text to be set as header
   */
  public void setText(int title) {
    final TextView titleView = getTextView();
    if (titleView != null) {
      if (autoTextSizeEnabled) {
        // Override the text size setting of the header
        autoAdjustTextSize(titleView);
      }
      titleView.setText(title);
    }
  }

  /**
   * Sets the header text. This can also be set via the XML attribute {@code app:sucHeaderText}.
   *
   * @param title The text to be set as header
   */
  public void setText(CharSequence title) {
    final TextView titleView = getTextView();
    if (titleView != null) {
      if (autoTextSizeEnabled) {
        // Override the text size setting of the header
        autoAdjustTextSize(titleView);
      }
      titleView.setText(title);
    }
  }

  private void autoAdjustTextSize(TextView titleView) {
    if (titleView == null) {
      return;
    }
    // preset as the max size
    titleView.setTextSize(TypedValue.COMPLEX_UNIT_PX, headerAutoSizeMaxTextSizeInPx);
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
      titleView.setLineHeight(
          Math.round(headerAutoSizeLineExtraSpacingInPx + headerAutoSizeMaxTextSizeInPx));
    }
    titleView.setMaxLines(AUTO_SIZE_DEFAULT_MAX_LINES);

    // reset text size if the line count for max text size > headerAutoSizeMaxLineOfMaxTextSize
    titleView
        .getViewTreeObserver()
        .addOnPreDrawListener(
            new ViewTreeObserver.OnPreDrawListener() {
              @Override
              public boolean onPreDraw() {
                // Remove listener to avoid this called every frame
                titleView.getViewTreeObserver().removeOnPreDrawListener(this);
                int lineCount = titleView.getLineCount();
                if (lineCount > headerAutoSizeMaxLineOfMaxSize) {
                  // reset text size
                  titleView.setTextSize(TypedValue.COMPLEX_UNIT_PX, headerAutoSizeMinTextSizeInPx);
                  if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    titleView.setLineHeight(
                        Math.round(
                            headerAutoSizeLineExtraSpacingInPx + headerAutoSizeMinTextSizeInPx));
                  }
                  titleView.invalidate();
                  return false; // false to skip this frame
                }
                return true;
              }
            });
  }

  /** Returns the current header text. */
  public CharSequence getText() {
    final TextView titleView = getTextView();
    return titleView != null ? titleView.getText() : null;
  }

  /** Sets the visibility of header text */
  public void setVisibility(int visibility) {
    final TextView titleView = getTextView();
    if (titleView != null) {
      titleView.setVisibility(visibility);
    }
  }

  /**
   * Sets the color of the header text. This can also be set via XML using {@code
   * app:sucHeaderTextColor}.
   *
   * @param color The text color of the header
   */
  public void setTextColor(ColorStateList color) {
    final TextView titleView = getTextView();
    if (titleView != null) {
      titleView.setTextColor(color);
    }
  }

  /**
   * Sets the background color of the header's parent LinearLayout.
   *
   * @param color The background color of the header's parent LinearLayout
   */
  public void setBackgroundColor(int color) {
    final TextView titleView = getTextView();
    if (titleView != null) {
      ViewParent parent = titleView.getParent();
      if (parent instanceof LinearLayout) {
        ((LinearLayout) parent).setBackgroundColor(color);
      }
    }
  }

  /** Returns the current text color of the header. */
  public ColorStateList getTextColor() {
    final TextView titleView = getTextView();
    return titleView != null ? titleView.getTextColors() : null;
  }
}
