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

import static com.android.wifi.flags.Flags.networkProviderBatteryChargingStatus;

import android.app.admin.DevicePolicyManager;
import android.app.admin.WifiSsidPolicy;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.TransportInfo;
import android.net.vcn.VcnTransportInfo;
import android.net.wifi.WifiInfo;
import android.os.UserManager;
import android.text.Annotation;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.style.ClickableSpan;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.os.BuildCompat;

import com.android.settingslib.HelpUtils;

/**
 * Wrapper class to decouple WifiTrackerLibDefaults from non-SDK API usage at build time.
 * This version uses non-SDK APIs for usage within the Android platform.
 *
 * Clients of WifiTrackerLib that can only access SDK APIs should use SdkWifiTrackerLib, which
 * replaces this class with the version found in WifiTrackerLib/sdk_src/../NonSdkApiWrapper.java.
 */
class NonSdkApiWrapper {
    private NonSdkApiWrapper() {
        // Empty constructor to make this class non-instantiable.
    }

    /**
     * Starts the System captive portal app.
     */
    static void startCaptivePortalApp(
            @NonNull ConnectivityManager connectivityManager, @NonNull Network network) {
        connectivityManager.startCaptivePortalApp(network);
    }

    /**
     * Find the annotation of specified id in rawText and linkify it with helpUriString.
     */
    static CharSequence linkifyAnnotation(Context context, CharSequence rawText, String id,
            String helpUriString) {
        // Return original string when helpUriString is empty.
        if (TextUtils.isEmpty(helpUriString)) {
            return rawText;
        }

        SpannableString spannableText = new SpannableString(rawText);
        Annotation[] annotations = spannableText.getSpans(0, spannableText.length(),
                Annotation.class);

        for (Annotation annotation : annotations) {
            if (TextUtils.equals(annotation.getValue(), id)) {
                SpannableStringBuilder builder = new SpannableStringBuilder(spannableText);
                ClickableSpan link = new ClickableSpan() {
                    @Override
                    public void onClick(View view) {
                        view.startActivityForResult(HelpUtils.getHelpIntent(context, helpUriString,
                                view.getClass().getName()), 0);
                    }
                };
                builder.setSpan(link, spannableText.getSpanStart(annotation),
                        spannableText.getSpanEnd(annotation), spannableText.getSpanFlags(link));
                return builder;
            }
        }
        return rawText;
    }

    /**
     * Tries to get WifiInfo from network capabilities if it is VCN-over-Wifi.
     */
    static WifiInfo getWifiInfoIfVcn(@NonNull NetworkCapabilities networkCapabilities) {
        TransportInfo transportInfo = networkCapabilities.getTransportInfo();
        if (transportInfo instanceof VcnTransportInfo) {
            return ((VcnTransportInfo) transportInfo).getWifiInfo();
        }
        return null;
    }

    /**
     * Returns whether or not the device is in retail demo mode.
     */
    static boolean isDemoMode(@NonNull Context context) {
        return UserManager.isDeviceInDemoMode(context);
    }

    /**
     * Returns true if the WifiInfo is for the primary network, false otherwise.
     */
    static boolean isPrimary(@NonNull WifiInfo wifiInfo) {
        return wifiInfo.isPrimary();
    }

    /**
     * Returns true if the NetworkCapabilities is OEM_PAID or OEM_PRIVATE
     */
    static boolean isOemCapabilities(@NonNull NetworkCapabilities capabilities) {
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_OEM_PAID)
                || capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_OEM_PRIVATE);
    }

    /**
     * Returns the {@link WifiSsidPolicy} of the device.
     */
    @Nullable
    static WifiSsidPolicy getWifiSsidPolicy(@NonNull DevicePolicyManager devicePolicyManager) {
        if (BuildCompat.isAtLeastT()) {
            return devicePolicyManager.getWifiSsidPolicy();
        }
        return null;
    }

    /**
     * Whether the hotspot network provider battery charging status flag is enabled.
     */
    static boolean isNetworkProviderBatteryChargingStatusEnabled() {
        return networkProviderBatteryChargingStatus();
    }
}
