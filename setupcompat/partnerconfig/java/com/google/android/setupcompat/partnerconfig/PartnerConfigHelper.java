/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.google.android.setupcompat.partnerconfig;

import android.content.ContentResolver;
import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.content.res.Resources.NotFoundException;
import android.database.ContentObserver;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Build.VERSION_CODES;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;
import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import com.google.android.setupcompat.partnerconfig.PartnerConfig.ResourceType;
import com.google.android.setupcompat.util.BuildCompatUtils;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;

/** The helper reads and caches the partner configurations from SUW. */
public class PartnerConfigHelper {

  private static final String TAG = PartnerConfigHelper.class.getSimpleName();

  @VisibleForTesting
  public static final String SUW_AUTHORITY = "com.google.android.setupwizard.partner";

  @VisibleForTesting public static final String SUW_GET_PARTNER_CONFIG_METHOD = "getOverlayConfig";

  @VisibleForTesting public static final String KEY_FALLBACK_CONFIG = "fallbackConfig";

  @VisibleForTesting
  public static final String IS_SUW_DAY_NIGHT_ENABLED_METHOD = "isSuwDayNightEnabled";

  @VisibleForTesting
  public static final String IS_EXTENDED_PARTNER_CONFIG_ENABLED_METHOD =
      "isExtendedPartnerConfigEnabled";

  @VisibleForTesting
  public static final String IS_MATERIAL_YOU_STYLE_ENABLED_METHOD = "IsMaterialYouStyleEnabled";

  @VisibleForTesting
  public static final String IS_DYNAMIC_COLOR_ENABLED_METHOD = "isDynamicColorEnabled";

  @VisibleForTesting
  public static final String IS_NEUTRAL_BUTTON_STYLE_ENABLED_METHOD = "isNeutralButtonStyleEnabled";

  @VisibleForTesting public static final String SUW_PACKAGE_NAME = "com.google.android.setupwizard";
  @VisibleForTesting public static final String MATERIAL_YOU_RESOURCE_SUFFIX = "_material_you";

  @VisibleForTesting static Bundle suwDayNightEnabledBundle = null;

  @VisibleForTesting public static Bundle applyExtendedPartnerConfigBundle = null;

  @VisibleForTesting public static Bundle applyMaterialYouConfigBundle = null;

  @VisibleForTesting public static Bundle applyDynamicColorBundle = null;

  @VisibleForTesting public static Bundle applyNeutralButtonStyleBundle = null;

  private static PartnerConfigHelper instance = null;

  @VisibleForTesting Bundle resultBundle = null;

  @VisibleForTesting
  final EnumMap<PartnerConfig, Object> partnerResourceCache = new EnumMap<>(PartnerConfig.class);

  private static ContentObserver contentObserver;

  private static int savedConfigUiMode;

  @VisibleForTesting
  public static int savedOrientation = Configuration.ORIENTATION_PORTRAIT;

  /**
   * When testing related to fake PartnerConfigHelper instance, should sync the following saved
   * config with testing environment.
   */
  @VisibleForTesting public static int savedScreenHeight = Configuration.SCREEN_HEIGHT_DP_UNDEFINED;

  @VisibleForTesting public static int savedScreenWidth = Configuration.SCREEN_WIDTH_DP_UNDEFINED;

  public static synchronized PartnerConfigHelper get(@NonNull Context context) {
    if (!isValidInstance(context)) {
      instance = new PartnerConfigHelper(context);
    }
    return instance;
  }

  private static boolean isValidInstance(@NonNull Context context) {
    Configuration currentConfig = context.getResources().getConfiguration();
    if (instance == null) {
      savedConfigUiMode = currentConfig.uiMode & Configuration.UI_MODE_NIGHT_MASK;
      savedOrientation = currentConfig.orientation;
      savedScreenWidth = currentConfig.screenWidthDp;
      savedScreenHeight = currentConfig.screenHeightDp;
      return false;
    } else {
      boolean uiModeChanged =
          isSetupWizardDayNightEnabled(context)
              && (currentConfig.uiMode & Configuration.UI_MODE_NIGHT_MASK) != savedConfigUiMode;
      if (uiModeChanged
          || currentConfig.orientation != savedOrientation
          || currentConfig.screenWidthDp != savedScreenWidth
          || currentConfig.screenHeightDp != savedScreenHeight) {
        savedConfigUiMode = currentConfig.uiMode & Configuration.UI_MODE_NIGHT_MASK;
        savedOrientation = currentConfig.orientation;
        savedScreenHeight = currentConfig.screenHeightDp;
        savedScreenWidth = currentConfig.screenWidthDp;
        resetInstance();
        return false;
      }
    }
    return true;
  }

  private PartnerConfigHelper(Context context) {
    getPartnerConfigBundle(context);

    registerContentObserver(context);
  }

  /**
   * Returns whether partner customized config values are available. This is true if setup wizard's
   * content provider returns us a non-empty bundle, even if all the values are default, and none
   * are customized by the overlay APK.
   */
  public boolean isAvailable() {
    return resultBundle != null && !resultBundle.isEmpty();
  }

  /**
   * Returns whether the given {@code resourceConfig} are available. This is true if setup wizard's
   * content provider returns us a non-empty bundle, and this result bundle includes the given
   * {@code resourceConfig} even if all the values are default, and none are customized by the
   * overlay APK.
   */
  public boolean isPartnerConfigAvailable(PartnerConfig resourceConfig) {
    return isAvailable() && resultBundle.containsKey(resourceConfig.getResourceName());
  }

  /**
   * Returns the color of given {@code resourceConfig}, or 0 if the given {@code resourceConfig} is
   * not found. If the {@code ResourceType} of the given {@code resourceConfig} is not color,
   * IllegalArgumentException will be thrown.
   *
   * @param context The context of client activity
   * @param resourceConfig The {@link PartnerConfig} of target resource
   */
  @ColorInt
  public int getColor(@NonNull Context context, PartnerConfig resourceConfig) {
    if (resourceConfig.getResourceType() != ResourceType.COLOR) {
      throw new IllegalArgumentException("Not a color resource");
    }

    if (partnerResourceCache.containsKey(resourceConfig)) {
      return (int) partnerResourceCache.get(resourceConfig);
    }

    int result = 0;
    try {
      ResourceEntry resourceEntry =
          getResourceEntryFromKey(context, resourceConfig.getResourceName());
      Resources resource = resourceEntry.getResources();
      int resId = resourceEntry.getResourceId();

      // for @null
      TypedValue outValue = new TypedValue();
      resource.getValue(resId, outValue, true);
      if (outValue.type == TypedValue.TYPE_REFERENCE && outValue.data == 0) {
        return result;
      }

      if (Build.VERSION.SDK_INT >= VERSION_CODES.M) {
        result = resource.getColor(resId, null);
      } else {
        result = resource.getColor(resId);
      }
      partnerResourceCache.put(resourceConfig, result);
    } catch (NullPointerException exception) {
      // fall through
    }
    return result;
  }

  /**
   * Returns the {@code Drawable} of given {@code resourceConfig}, or {@code null} if the given
   * {@code resourceConfig} is not found. If the {@code ResourceType} of the given {@code
   * resourceConfig} is not drawable, IllegalArgumentException will be thrown.
   *
   * @param context The context of client activity
   * @param resourceConfig The {@code PartnerConfig} of target resource
   */
  @Nullable
  public Drawable getDrawable(@NonNull Context context, PartnerConfig resourceConfig) {
    if (resourceConfig.getResourceType() != ResourceType.DRAWABLE) {
      throw new IllegalArgumentException("Not a drawable resource");
    }

    if (partnerResourceCache.containsKey(resourceConfig)) {
      return (Drawable) partnerResourceCache.get(resourceConfig);
    }

    Drawable result = null;
    try {
      ResourceEntry resourceEntry =
          getResourceEntryFromKey(context, resourceConfig.getResourceName());
      Resources resource = resourceEntry.getResources();
      int resId = resourceEntry.getResourceId();

      // for @null
      TypedValue outValue = new TypedValue();
      resource.getValue(resId, outValue, true);
      if (outValue.type == TypedValue.TYPE_REFERENCE && outValue.data == 0) {
        return result;
      }

      if (Build.VERSION.SDK_INT >= VERSION_CODES.LOLLIPOP) {
        result = resource.getDrawable(resId, null);
      } else {
        result = resource.getDrawable(resId);
      }
      partnerResourceCache.put(resourceConfig, result);
    } catch (NullPointerException | NotFoundException exception) {
      // fall through
    }
    return result;
  }

  /**
   * Returns the string of the given {@code resourceConfig}, or {@code null} if the given {@code
   * resourceConfig} is not found. If the {@code ResourceType} of the given {@code resourceConfig}
   * is not string, IllegalArgumentException will be thrown.
   *
   * @param context The context of client activity
   * @param resourceConfig The {@code PartnerConfig} of target resource
   */
  @Nullable
  public String getString(@NonNull Context context, PartnerConfig resourceConfig) {
    if (resourceConfig.getResourceType() != ResourceType.STRING) {
      throw new IllegalArgumentException("Not a string resource");
    }

    if (partnerResourceCache.containsKey(resourceConfig)) {
      return (String) partnerResourceCache.get(resourceConfig);
    }

    String result = null;
    try {
      ResourceEntry resourceEntry =
          getResourceEntryFromKey(context, resourceConfig.getResourceName());
      Resources resource = resourceEntry.getResources();
      int resId = resourceEntry.getResourceId();

      result = resource.getString(resId);
      partnerResourceCache.put(resourceConfig, result);
    } catch (NullPointerException exception) {
      // fall through
    }
    return result;
  }

  /**
   * Returns the string array of the given {@code resourceConfig}, or {@code null} if the given
   * {@code resourceConfig} is not found. If the {@code ResourceType} of the given {@code
   * resourceConfig} is not string, IllegalArgumentException will be thrown.
   *
   * @param context The context of client activity
   * @param resourceConfig The {@code PartnerConfig} of target resource
   */
  @NonNull
  public List<String> getStringArray(@NonNull Context context, PartnerConfig resourceConfig) {
    if (resourceConfig.getResourceType() != ResourceType.STRING_ARRAY) {
      throw new IllegalArgumentException("Not a string array resource");
    }

    String[] result;
    List<String> listResult = new ArrayList<>();

    try {
      ResourceEntry resourceEntry =
          getResourceEntryFromKey(context, resourceConfig.getResourceName());
      Resources resource = resourceEntry.getResources();
      int resId = resourceEntry.getResourceId();

      result = resource.getStringArray(resId);
      Collections.addAll(listResult, result);
    } catch (NullPointerException exception) {
      // fall through
    }

    return listResult;
  }

  /**
   * Returns the boolean of given {@code resourceConfig}, or {@code defaultValue} if the given
   * {@code resourceName} is not found. If the {@code ResourceType} of the given {@code
   * resourceConfig} is not boolean, IllegalArgumentException will be thrown.
   *
   * @param context The context of client activity
   * @param resourceConfig The {@code PartnerConfig} of target resource
   * @param defaultValue The default value
   */
  public boolean getBoolean(
      @NonNull Context context, PartnerConfig resourceConfig, boolean defaultValue) {
    if (resourceConfig.getResourceType() != ResourceType.BOOL) {
      throw new IllegalArgumentException("Not a bool resource");
    }

    if (partnerResourceCache.containsKey(resourceConfig)) {
      return (boolean) partnerResourceCache.get(resourceConfig);
    }

    boolean result = defaultValue;
    try {
      ResourceEntry resourceEntry =
          getResourceEntryFromKey(context, resourceConfig.getResourceName());
      Resources resource = resourceEntry.getResources();
      int resId = resourceEntry.getResourceId();

      result = resource.getBoolean(resId);
      partnerResourceCache.put(resourceConfig, result);
    } catch (NullPointerException | NotFoundException exception) {
      // fall through
    }
    return result;
  }

  /**
   * Returns the dimension of given {@code resourceConfig}. The default return value is 0.
   *
   * @param context The context of client activity
   * @param resourceConfig The {@code PartnerConfig} of target resource
   */
  public float getDimension(@NonNull Context context, PartnerConfig resourceConfig) {
    return getDimension(context, resourceConfig, 0);
  }

  /**
   * Returns the dimension of given {@code resourceConfig}. If the given {@code resourceConfig} is
   * not found, will return {@code defaultValue}. If the {@code ResourceType} of given {@code
   * resourceConfig} is not dimension, will throw IllegalArgumentException.
   *
   * @param context The context of client activity
   * @param resourceConfig The {@code PartnerConfig} of target resource
   * @param defaultValue The default value
   */
  public float getDimension(
      @NonNull Context context, PartnerConfig resourceConfig, float defaultValue) {
    if (resourceConfig.getResourceType() != ResourceType.DIMENSION) {
      throw new IllegalArgumentException("Not a dimension resource");
    }

    if (partnerResourceCache.containsKey(resourceConfig)) {
      return getDimensionFromTypedValue(
          context, (TypedValue) partnerResourceCache.get(resourceConfig));
    }

    float result = defaultValue;
    try {
      ResourceEntry resourceEntry =
          getResourceEntryFromKey(context, resourceConfig.getResourceName());
      Resources resource = resourceEntry.getResources();
      int resId = resourceEntry.getResourceId();

      result = resource.getDimension(resId);
      TypedValue value = getTypedValueFromResource(resource, resId, TypedValue.TYPE_DIMENSION);
      partnerResourceCache.put(resourceConfig, value);
      result =
          getDimensionFromTypedValue(
              context, (TypedValue) partnerResourceCache.get(resourceConfig));
    } catch (NullPointerException | NotFoundException exception) {
      // fall through
    }
    return result;
  }

  /**
   * Returns the float of given {@code resourceConfig}. The default return value is 0.
   *
   * @param context The context of client activity
   * @param resourceConfig The {@code PartnerConfig} of target resource
   */
  public float getFraction(@NonNull Context context, PartnerConfig resourceConfig) {
    return getFraction(context, resourceConfig, 0.0f);
  }

  /**
   * Returns the float of given {@code resourceConfig}. If the given {@code resourceConfig} not
   * found, will return {@code defaultValue}. If the {@code ResourceType} of given {@code
   * resourceConfig} is not fraction, will throw IllegalArgumentException.
   *
   * @param context The context of client activity
   * @param resourceConfig The {@code PartnerConfig} of target resource
   * @param defaultValue The default value
   */
  public float getFraction(
      @NonNull Context context, PartnerConfig resourceConfig, float defaultValue) {
    if (resourceConfig.getResourceType() != ResourceType.FRACTION) {
      throw new IllegalArgumentException("Not a fraction resource");
    }

    if (partnerResourceCache.containsKey(resourceConfig)) {
      return (float) partnerResourceCache.get(resourceConfig);
    }

    float result = defaultValue;
    try {
      ResourceEntry resourceEntry =
          getResourceEntryFromKey(context, resourceConfig.getResourceName());
      Resources resource = resourceEntry.getResources();
      int resId = resourceEntry.getResourceId();

      result = resource.getFraction(resId, 1, 1);
      partnerResourceCache.put(resourceConfig, result);
    } catch (NullPointerException | NotFoundException exception) {
      // fall through
    }
    return result;
  }

  /**
   * Returns the integer of given {@code resourceConfig}. If the given {@code resourceConfig} is not
   * found, will return {@code defaultValue}. If the {@code ResourceType} of given {@code
   * resourceConfig} is not dimension, will throw IllegalArgumentException.
   *
   * @param context The context of client activity
   * @param resourceConfig The {@code PartnerConfig} of target resource
   * @param defaultValue The default value
   */
  public int getInteger(@NonNull Context context, PartnerConfig resourceConfig, int defaultValue) {
    if (resourceConfig.getResourceType() != ResourceType.INTEGER) {
      throw new IllegalArgumentException("Not a integer resource");
    }

    if (partnerResourceCache.containsKey(resourceConfig)) {
      return (int) partnerResourceCache.get(resourceConfig);
    }

    int result = defaultValue;
    try {
      ResourceEntry resourceEntry =
          getResourceEntryFromKey(context, resourceConfig.getResourceName());
      Resources resource = resourceEntry.getResources();
      int resId = resourceEntry.getResourceId();

      result = resource.getInteger(resId);
      partnerResourceCache.put(resourceConfig, result);
    } catch (NullPointerException | NotFoundException exception) {
      // fall through
    }
    return result;
  }

  /**
   * Returns the {@link ResourceEntry} of given {@code resourceConfig}, or {@code null} if the given
   * {@code resourceConfig} is not found. If the {@link ResourceType} of the given {@code
   * resourceConfig} is not illustration, IllegalArgumentException will be thrown.
   *
   * @param context The context of client activity
   * @param resourceConfig The {@link PartnerConfig} of target resource
   */
  @Nullable
  public ResourceEntry getIllustrationResourceEntry(
      @NonNull Context context, PartnerConfig resourceConfig) {
    if (resourceConfig.getResourceType() != ResourceType.ILLUSTRATION) {
      throw new IllegalArgumentException("Not a illustration resource");
    }

    if (partnerResourceCache.containsKey(resourceConfig)) {
      return (ResourceEntry) partnerResourceCache.get(resourceConfig);
    }

    try {
      ResourceEntry resourceEntry =
          getResourceEntryFromKey(context, resourceConfig.getResourceName());

      Resources resource = resourceEntry.getResources();
      int resId = resourceEntry.getResourceId();

      // TODO: The illustration resource entry validation should validate is it a video
      // resource or not?
      // for @null
      TypedValue outValue = new TypedValue();
      resource.getValue(resId, outValue, true);
      if (outValue.type == TypedValue.TYPE_REFERENCE && outValue.data == 0) {
        return null;
      }

      partnerResourceCache.put(resourceConfig, resourceEntry);
      return resourceEntry;
    } catch (NullPointerException exception) {
      // fall through
    }

    return null;
  }

  private void getPartnerConfigBundle(Context context) {
    if (resultBundle == null || resultBundle.isEmpty()) {
      try {
        resultBundle =
            context
                .getContentResolver()
                .call(
                    getContentUri(),
                    SUW_GET_PARTNER_CONFIG_METHOD,
                    /* arg= */ null,
                    /* extras= */ null);
        partnerResourceCache.clear();
        Log.i(
            TAG, "PartnerConfigsBundle=" + (resultBundle != null ? resultBundle.size() : "(null)"));
      } catch (IllegalArgumentException | SecurityException exception) {
        Log.w(TAG, "Fail to get config from suw provider");
      }
    }
  }

  @Nullable
  @VisibleForTesting
  ResourceEntry getResourceEntryFromKey(Context context, String resourceName) {
    Bundle resourceEntryBundle = resultBundle.getBundle(resourceName);
    Bundle fallbackBundle = resultBundle.getBundle(KEY_FALLBACK_CONFIG);
    if (fallbackBundle != null) {
      resourceEntryBundle.putBundle(KEY_FALLBACK_CONFIG, fallbackBundle.getBundle(resourceName));
    }
    ResourceEntry adjustResourceEntry =
        adjustResourceEntryDefaultValue(
            context, ResourceEntry.fromBundle(context, resourceEntryBundle));
    return adjustResourceEntryDayNightMode(context, adjustResourceEntry);
  }

  /**
   * Force to day mode if setup wizard does not support day/night mode and current system is in
   * night mode.
   */
  private static ResourceEntry adjustResourceEntryDayNightMode(
      Context context, ResourceEntry resourceEntry) {
    Resources resource = resourceEntry.getResources();
    Configuration configuration = resource.getConfiguration();
    if (!isSetupWizardDayNightEnabled(context) && Util.isNightMode(configuration)) {
      if (resourceEntry == null) {
        Log.w(TAG, "resourceEntry is null, skip to force day mode.");
        return resourceEntry;
      }
      configuration.uiMode =
          Configuration.UI_MODE_NIGHT_NO
              | (configuration.uiMode & ~Configuration.UI_MODE_NIGHT_MASK);
      resource.updateConfiguration(configuration, resource.getDisplayMetrics());
    }

    return resourceEntry;
  }

  // Check the MNStyle flag and replace the inputResourceEntry.resourceName &
  // inputResourceEntry.resourceId after T, that means if using Gliv4 before S, will always use
  // glifv3 resources.
  ResourceEntry adjustResourceEntryDefaultValue(Context context, ResourceEntry inputResourceEntry) {
    if (BuildCompatUtils.isAtLeastT() && shouldApplyMaterialYouStyle(context)) {
      // If not overlay resource
      try {
        if (SUW_PACKAGE_NAME.equals(inputResourceEntry.getPackageName())) {
          String resourceTypeName =
              inputResourceEntry
                  .getResources()
                  .getResourceTypeName(inputResourceEntry.getResourceId());
          // try to update resourceName & resourceId
          String materialYouResourceName =
              inputResourceEntry.getResourceName().concat(MATERIAL_YOU_RESOURCE_SUFFIX);
          int materialYouResourceId =
              inputResourceEntry
                  .getResources()
                  .getIdentifier(
                      materialYouResourceName,
                      resourceTypeName,
                      inputResourceEntry.getPackageName());
          if (materialYouResourceId != 0) {
            Log.i(TAG, "use material you resource:" + materialYouResourceName);
            return new ResourceEntry(
                inputResourceEntry.getPackageName(),
                materialYouResourceName,
                materialYouResourceId,
                inputResourceEntry.getResources());
          }
        }
      } catch (NotFoundException ex) {
        // fall through
      }
    }
    return inputResourceEntry;
  }

  @VisibleForTesting
  public static synchronized void resetInstance() {
    instance = null;
    suwDayNightEnabledBundle = null;
    applyExtendedPartnerConfigBundle = null;
    applyMaterialYouConfigBundle = null;
    applyDynamicColorBundle = null;
    applyNeutralButtonStyleBundle = null;
  }

  /**
   * Checks whether SetupWizard supports the DayNight theme during setup flow; if return false setup
   * flow should force to light theme.
   *
   * <p>Returns true if the setupwizard is listening to system DayNight theme setting.
   */
  public static boolean isSetupWizardDayNightEnabled(@NonNull Context context) {
    if (suwDayNightEnabledBundle == null) {
      try {
        suwDayNightEnabledBundle =
            context
                .getContentResolver()
                .call(
                    getContentUri(),
                    IS_SUW_DAY_NIGHT_ENABLED_METHOD,
                    /* arg= */ null,
                    /* extras= */ null);
      } catch (IllegalArgumentException | SecurityException exception) {
        Log.w(TAG, "SetupWizard DayNight supporting status unknown; return as false.");
        suwDayNightEnabledBundle = null;
        return false;
      }
    }

    return (suwDayNightEnabledBundle != null
        && suwDayNightEnabledBundle.getBoolean(IS_SUW_DAY_NIGHT_ENABLED_METHOD, false));
  }

  /** Returns true if the SetupWizard supports the extended partner configs during setup flow. */
  public static boolean shouldApplyExtendedPartnerConfig(@NonNull Context context) {
    if (applyExtendedPartnerConfigBundle == null) {
      try {
        applyExtendedPartnerConfigBundle =
            context
                .getContentResolver()
                .call(
                    getContentUri(),
                    IS_EXTENDED_PARTNER_CONFIG_ENABLED_METHOD,
                    /* arg= */ null,
                    /* extras= */ null);
      } catch (IllegalArgumentException | SecurityException exception) {
        Log.w(
            TAG,
            "SetupWizard extended partner configs supporting status unknown; return as false.");
        applyExtendedPartnerConfigBundle = null;
        return false;
      }
    }

    return (applyExtendedPartnerConfigBundle != null
        && applyExtendedPartnerConfigBundle.getBoolean(
            IS_EXTENDED_PARTNER_CONFIG_ENABLED_METHOD, false));
  }

  /**
   * Returns true if the SetupWizard is flow enabled "Material You(Glifv4)" style, or the result of
   * shouldApplyExtendedPartnerConfig() in SDK S as fallback.
   */
  public static boolean shouldApplyMaterialYouStyle(@NonNull Context context) {
    if (applyMaterialYouConfigBundle == null || applyMaterialYouConfigBundle.isEmpty()) {
      try {
        applyMaterialYouConfigBundle =
            context
                .getContentResolver()
                .call(
                    getContentUri(),
                    IS_MATERIAL_YOU_STYLE_ENABLED_METHOD,
                    /* arg= */ null,
                    /* extras= */ null);
        // The suw version did not support the flag yet, fallback to
        // shouldApplyExtendedPartnerConfig() for SDK S.
        if (applyMaterialYouConfigBundle != null
            && applyMaterialYouConfigBundle.isEmpty()
            && !BuildCompatUtils.isAtLeastT()) {
          return shouldApplyExtendedPartnerConfig(context);
        }
      } catch (IllegalArgumentException | SecurityException exception) {
        Log.w(TAG, "SetupWizard Material You configs supporting status unknown; return as false.");
        applyMaterialYouConfigBundle = null;
        return false;
      }
    }

    return (applyMaterialYouConfigBundle != null
        && applyMaterialYouConfigBundle.getBoolean(IS_MATERIAL_YOU_STYLE_ENABLED_METHOD, false));
  }

  /** Returns true if the SetupWizard supports the dynamic color during setup flow. */
  public static boolean isSetupWizardDynamicColorEnabled(@NonNull Context context) {
    if (applyDynamicColorBundle == null) {
      try {
        applyDynamicColorBundle =
            context
                .getContentResolver()
                .call(
                    getContentUri(),
                    IS_DYNAMIC_COLOR_ENABLED_METHOD,
                    /* arg= */ null,
                    /* extras= */ null);
      } catch (IllegalArgumentException | SecurityException exception) {
        Log.w(TAG, "SetupWizard dynamic color supporting status unknown; return as false.");
        applyDynamicColorBundle = null;
        return false;
      }
    }

    return (applyDynamicColorBundle != null
        && applyDynamicColorBundle.getBoolean(IS_DYNAMIC_COLOR_ENABLED_METHOD, false));
  }

  /** Returns true if the SetupWizard supports the neutral button style during setup flow. */
  public static boolean isNeutralButtonStyleEnabled(@NonNull Context context) {
    if (applyNeutralButtonStyleBundle == null) {
      try {
        applyNeutralButtonStyleBundle =
            context
                .getContentResolver()
                .call(
                    getContentUri(),
                    IS_NEUTRAL_BUTTON_STYLE_ENABLED_METHOD,
                    /* arg= */ null,
                    /* extras= */ null);
      } catch (IllegalArgumentException | SecurityException exception) {
        Log.w(TAG, "Neutral button style supporting status unknown; return as false.");
        applyNeutralButtonStyleBundle = null;
        return false;
      }
    }

    return (applyNeutralButtonStyleBundle != null
        && applyNeutralButtonStyleBundle.getBoolean(IS_NEUTRAL_BUTTON_STYLE_ENABLED_METHOD, false));
  }

  @VisibleForTesting
  static Uri getContentUri() {
    return new Uri.Builder()
        .scheme(ContentResolver.SCHEME_CONTENT)
        .authority(SUW_AUTHORITY)
        .build();
  }

  private static TypedValue getTypedValueFromResource(Resources resource, int resId, int type) {
    TypedValue value = new TypedValue();
    resource.getValue(resId, value, true);
    if (value.type != type) {
      throw new NotFoundException(
          "Resource ID #0x"
              + Integer.toHexString(resId)
              + " type #0x"
              + Integer.toHexString(value.type)
              + " is not valid");
    }
    return value;
  }

  private static float getDimensionFromTypedValue(Context context, TypedValue value) {
    DisplayMetrics displayMetrics = context.getResources().getDisplayMetrics();
    return value.getDimension(displayMetrics);
  }

  private static void registerContentObserver(Context context) {
    if (isSetupWizardDayNightEnabled(context)) {
      if (contentObserver != null) {
        unregisterContentObserver(context);
      }

      Uri contentUri = getContentUri();
      try {
        contentObserver =
            new ContentObserver(null) {
              @Override
              public void onChange(boolean selfChange) {
                super.onChange(selfChange);
                resetInstance();
              }
            };
        context
            .getContentResolver()
            .registerContentObserver(contentUri, /* notifyForDescendants= */ true, contentObserver);
      } catch (SecurityException | NullPointerException | IllegalArgumentException e) {
        Log.w(TAG, "Failed to register content observer for " + contentUri + ": " + e);
      }
    }
  }

  private static void unregisterContentObserver(Context context) {
    try {
      context.getContentResolver().unregisterContentObserver(contentObserver);
      contentObserver = null;
    } catch (SecurityException | NullPointerException | IllegalArgumentException e) {
      Log.w(TAG, "Failed to unregister content observer: " + e);
    }
  }
}
