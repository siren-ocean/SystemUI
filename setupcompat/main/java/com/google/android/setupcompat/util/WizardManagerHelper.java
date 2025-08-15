/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.google.android.setupcompat.util;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.provider.Settings;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import java.util.Arrays;

/**
 * Helper to interact with Wizard Manager in setup wizard, which should be used when a screen is
 * shown inside the setup flow. This includes things like parsing extras passed by Wizard Manager,
 * and invoking Wizard Manager to start the next action.
 */
public final class WizardManagerHelper {

  @VisibleForTesting public static final String ACTION_NEXT = "com.android.wizard.NEXT";

  // EXTRA_SCRIPT_URI and EXTRA_ACTION_ID are used in setup wizard in versions before M and are
  // kept for backwards compatibility.
  @VisibleForTesting static final String EXTRA_SCRIPT_URI = "scriptUri";
  @VisibleForTesting static final String EXTRA_ACTION_ID = "actionId";

  @VisibleForTesting static final String EXTRA_WIZARD_BUNDLE = "wizardBundle";
  private static final String EXTRA_RESULT_CODE = "com.android.setupwizard.ResultCode";

  /** Extra for notifying an Activity that it is inside the first SetupWizard flow or not. */
  public static final String EXTRA_IS_FIRST_RUN = "firstRun";

  /** Extra for notifying an Activity that it is inside the Deferred SetupWizard flow or not. */
  public static final String EXTRA_IS_DEFERRED_SETUP = "deferredSetup";

  /** Extra for notifying an Activity that it is inside the "Pre-Deferred Setup" flow. */
  public static final String EXTRA_IS_PRE_DEFERRED_SETUP = "preDeferredSetup";

  /** Extra for notifying an Activity that it is inside the "Portal Setup" flow. */
  public static final String EXTRA_IS_PORTAL_SETUP = "portalSetup";

  /**
   * Extra for notifying an Activity that it is inside the any setup flow.
   *
   * <p>Apps that target API levels below {@link android.os.Build.VERSION_CODES#Q} is able to
   * determine whether Activity is inside the any setup flow by one of {@link #EXTRA_IS_FIRST_RUN},
   * {@link #EXTRA_IS_DEFERRED_SETUP}, and {@link #EXTRA_IS_PRE_DEFERRED_SETUP} is true.
   */
  public static final String EXTRA_IS_SETUP_FLOW = "isSetupFlow";

  /** Extra for notifying an activity that was called from suggested action activity. */
  public static final String EXTRA_IS_SUW_SUGGESTED_ACTION_FLOW = "isSuwSuggestedActionFlow";

  public static final String EXTRA_THEME = "theme";
  public static final String EXTRA_USE_IMMERSIVE_MODE = "useImmersiveMode";

  public static final String SETTINGS_GLOBAL_DEVICE_PROVISIONED = "device_provisioned";
  public static final String SETTINGS_SECURE_USER_SETUP_COMPLETE = "user_setup_complete";

  /**
   * Gets an intent that will invoke the next step of setup wizard.
   *
   * @param originalIntent The original intent that was used to start the step, usually via {@link
   *     Activity#getIntent()}.
   * @param resultCode The result code of the step. See {@link ResultCodes}.
   * @return A new intent that can be used with {@link Activity#startActivityForResult(Intent, int)}
   *     to start the next step of the setup flow.
   */
  public static Intent getNextIntent(Intent originalIntent, int resultCode) {
    return getNextIntent(originalIntent, resultCode, null);
  }

  /**
   * Gets an intent that will invoke the next step of setup wizard.
   *
   * @param originalIntent The original intent that was used to start the step, usually via {@link
   *     Activity#getIntent()}.
   * @param resultCode The result code of the step. See {@link ResultCodes}.
   * @param data An intent containing extra result data.
   * @return A new intent that can be used with {@link Activity#startActivityForResult(Intent, int)}
   *     to start the next step of the setup flow.
   */
  public static Intent getNextIntent(Intent originalIntent, int resultCode, Intent data) {
    Intent intent = new Intent(ACTION_NEXT);
    copyWizardManagerExtras(originalIntent, intent);
    intent.putExtra(EXTRA_RESULT_CODE, resultCode);
    if (data != null && data.getExtras() != null) {
      intent.putExtras(data.getExtras());
    }
    intent.putExtra(EXTRA_THEME, originalIntent.getStringExtra(EXTRA_THEME));

    return intent;
  }

  /**
   * Copies the internal extras used by setup wizard from one intent to another. For low-level use
   * only, such as when using {@link Intent#FLAG_ACTIVITY_FORWARD_RESULT} to relay to another
   * intent.
   *
   * @param srcIntent Intent to get the wizard manager extras from.
   * @param dstIntent Intent to copy the wizard manager extras to.
   */
  public static void copyWizardManagerExtras(Intent srcIntent, Intent dstIntent) {
    dstIntent.putExtra(EXTRA_WIZARD_BUNDLE, srcIntent.getBundleExtra(EXTRA_WIZARD_BUNDLE));
    for (String key :
        Arrays.asList(
            EXTRA_IS_FIRST_RUN,
            EXTRA_IS_DEFERRED_SETUP,
            EXTRA_IS_PRE_DEFERRED_SETUP,
            EXTRA_IS_PORTAL_SETUP,
            EXTRA_IS_SETUP_FLOW,
            EXTRA_IS_SUW_SUGGESTED_ACTION_FLOW)) {
      dstIntent.putExtra(key, srcIntent.getBooleanExtra(key, false));
    }

    for (String key : Arrays.asList(EXTRA_THEME, EXTRA_SCRIPT_URI, EXTRA_ACTION_ID)) {
      dstIntent.putExtra(key, srcIntent.getStringExtra(key));
    }
  }

  /** @deprecated Use {@link isInitialSetupWizard} instead. */
  @Deprecated
  public static boolean isSetupWizardIntent(Intent intent) {
    return intent.getBooleanExtra(EXTRA_IS_FIRST_RUN, false);
  }

  /**
   * Checks whether the current user has completed Setup Wizard. This is true if the current user
   * has gone through Setup Wizard. The current user may or may not be the device owner and the
   * device owner may have already completed setup wizard.
   *
   * @param context The context to retrieve the settings.
   * @return true if the current user has completed Setup Wizard.
   * @see #isDeviceProvisioned(Context)
   */
  public static boolean isUserSetupComplete(Context context) {
    if (VERSION.SDK_INT >= VERSION_CODES.JELLY_BEAN_MR1) {
      return Settings.Secure.getInt(
              context.getContentResolver(), SETTINGS_SECURE_USER_SETUP_COMPLETE, 0)
          == 1;
    } else {
      // For versions below JB MR1, there are no user profiles. Just return the global device
      // provisioned state.
      return Settings.Secure.getInt(
              context.getContentResolver(), SETTINGS_GLOBAL_DEVICE_PROVISIONED, 0)
          == 1;
    }
  }

  /**
   * Checks whether the device is provisioned. This means that the device has gone through Setup
   * Wizard at least once. Note that the user can still be in Setup Wizard even if this is true, for
   * a secondary user profile triggered through Settings > Add account.
   *
   * @param context The context to retrieve the settings.
   * @return true if the device is provisioned.
   * @see #isUserSetupComplete(Context)
   */
  public static boolean isDeviceProvisioned(Context context) {
    if (VERSION.SDK_INT >= VERSION_CODES.JELLY_BEAN_MR1) {
      return Settings.Global.getInt(
              context.getContentResolver(), SETTINGS_GLOBAL_DEVICE_PROVISIONED, 0)
          == 1;
    } else {
      return Settings.Secure.getInt(
              context.getContentResolver(), SETTINGS_GLOBAL_DEVICE_PROVISIONED, 0)
          == 1;
    }
  }

  /**
   * Checks whether an intent is running in the portal setup wizard flow.
   *
   * @param originalIntent The original intent that was used to start the step, usually via {@link
   *     Activity#getIntent()}.
   * @return true if the intent passed in was running in portal setup wizard.
   */
  public static boolean isPortalSetupWizard(Intent originalIntent) {
    return originalIntent != null && originalIntent.getBooleanExtra(EXTRA_IS_PORTAL_SETUP, false);
  }

  /**
   * Checks whether an intent is running in the deferred setup wizard flow.
   *
   * @param originalIntent The original intent that was used to start the step, usually via {@link
   *     Activity#getIntent()}.
   * @return true if the intent passed in was running in deferred setup wizard.
   */
  public static boolean isDeferredSetupWizard(Intent originalIntent) {
    return originalIntent != null && originalIntent.getBooleanExtra(EXTRA_IS_DEFERRED_SETUP, false);
  }

  /**
   * Checks whether an intent is running in "pre-deferred" setup wizard flow.
   *
   * @param originalIntent The original intent that was used to start the step, usually via {@link
   *     Activity#getIntent()}.
   * @return true if the intent passed in was running in "pre-deferred" setup wizard.
   */
  public static boolean isPreDeferredSetupWizard(Intent originalIntent) {
    return originalIntent != null
        && originalIntent.getBooleanExtra(EXTRA_IS_PRE_DEFERRED_SETUP, false);
  }

  /**
   * Checks whether an intent is is running in the initial setup wizard flow.
   *
   * @param intent The intent to be checked, usually from {@link Activity#getIntent()}.
   * @return true if the intent passed in was intended to be used with setup wizard.
   */
  public static boolean isInitialSetupWizard(Intent intent) {
    return intent.getBooleanExtra(EXTRA_IS_FIRST_RUN, false);
  }

  /**
   * Returns true if the intent passed in indicates that it is running in any setup wizard flow,
   * including initial setup and deferred setup etc.
   *
   * @param originalIntent The original intent that was used to start the step, usually via {@link
   *     Activity#getIntent()}.
   */
  public static boolean isAnySetupWizard(@Nullable Intent originalIntent) {
    if (originalIntent == null) {
      return false;
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      return originalIntent.getBooleanExtra(EXTRA_IS_SETUP_FLOW, false);
    } else {
      return isInitialSetupWizard(originalIntent)
          || isPreDeferredSetupWizard(originalIntent)
          || isDeferredSetupWizard(originalIntent);
    }
  }

  private WizardManagerHelper() {}
}
