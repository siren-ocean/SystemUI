/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.wifitrackerlib;

import android.app.admin.DevicePolicyManager;
import android.content.Context;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.UserManager;
import android.provider.DeviceConfig;
import android.util.ArraySet;

import androidx.annotation.NonNull;

import java.util.Set;

/**
 * Wrapper class for commonly referenced objects and static data.
 */
public class WifiTrackerInjector {
    private static final String DEVICE_CONFIG_NAMESPACE = "wifi";

    @NonNull private final Context mContext;
    private final boolean mIsDemoMode;
    private final WifiManager mWifiManager;
    private final UserManager mUserManager;
    private final DevicePolicyManager mDevicePolicyManager;
    @NonNull private final Set<String> mNoAttributionAnnotationPackages;
    private boolean mIsUserDebugVerboseLoggingEnabled;
    private boolean mVerboseLoggingDisabledOverride = false;

    // TODO(b/201571677): Migrate the rest of the common objects to WifiTrackerInjector.
    WifiTrackerInjector(@NonNull Context context) {
        mContext = context;
        mWifiManager = context.getSystemService(WifiManager.class);
        mIsDemoMode = NonSdkApiWrapper.isDemoMode(context);
        mUserManager = context.getSystemService(UserManager.class);
        mDevicePolicyManager = context.getSystemService(DevicePolicyManager.class);
        mNoAttributionAnnotationPackages = new ArraySet<>();
        String[] noAttributionAnnotationPackages = context.getString(
                R.string.wifitrackerlib_no_attribution_annotation_packages).split(",");
        for (int i = 0; i < noAttributionAnnotationPackages.length; i++) {
            mNoAttributionAnnotationPackages.add(noAttributionAnnotationPackages[i]);
        }
        mIsUserDebugVerboseLoggingEnabled = context.getResources().getBoolean(
                R.bool.wifitrackerlib_enable_verbose_logging_for_userdebug)
                && Build.TYPE.equals("userdebug");
    }

    @NonNull Context getContext() {
        return mContext;
    }

    boolean isDemoMode() {
        return mIsDemoMode;
    }

    public UserManager getUserManager() {
        return mUserManager;
    }

    public DevicePolicyManager getDevicePolicyManager() {
        return mDevicePolicyManager;
    }

    /**
     * Returns the set of package names which we should not show attribution annotations for.
     */
    @NonNull Set<String> getNoAttributionAnnotationPackages() {
        return mNoAttributionAnnotationPackages;
    }

    public boolean isSharedConnectivityFeatureEnabled() {
        return DeviceConfig.getBoolean(DEVICE_CONFIG_NAMESPACE,
                "shared_connectivity_enabled", false);
    }

    /**
     * Whether verbose logging is enabled.
     */
    public boolean isVerboseLoggingEnabled() {
        return !mVerboseLoggingDisabledOverride
                && (mWifiManager.isVerboseLoggingEnabled() || mIsUserDebugVerboseLoggingEnabled);
    }

    /**
     * Whether verbose summaries should be shown in WifiEntry.
     */
    public boolean isVerboseSummaryEnabled() {
        return !mVerboseLoggingDisabledOverride && mWifiManager.isVerboseLoggingEnabled();
    }

    /**
     * Permanently disables verbose logging.
     */
    public void disableVerboseLogging() {
        mVerboseLoggingDisabledOverride = true;
    }
}
