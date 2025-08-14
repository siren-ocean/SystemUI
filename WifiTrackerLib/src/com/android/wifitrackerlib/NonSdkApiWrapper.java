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

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.TransportInfo;
import android.net.vcn.VcnTransportInfo;
import android.net.wifi.WifiInfo;
import android.os.Handler;
import android.os.UserManager;
import android.text.Annotation;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.style.ClickableSpan;
import android.view.View;

import androidx.annotation.NonNull;

import com.android.settingslib.HelpUtils;

/**
 * Wrapper class to decouple WifiTrackerLibDefaults from non-SDK API usage at build time.
 * This version uses non-SDK APIs for usage within the Android platform.
 *
 * Clients of WifiTrackerLib that can only access SDK APIs should use SdkWifiTrackerLib, which
 * replaces this class with the version found in WifiTrackerLib/sdk_src/../NonSdkApiWrapper.java.
 */
class NonSdkApiWrapper {
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
     * Returns whether or not the network capabilities is determined to be VCN over Wi-Fi or not.
     */
    static boolean isVcnOverWifi(@NonNull NetworkCapabilities networkCapabilities) {
        TransportInfo transportInfo = networkCapabilities.getTransportInfo();
        return transportInfo != null
                && transportInfo instanceof VcnTransportInfo
                && ((VcnTransportInfo) transportInfo).getWifiInfo() != null;
    }

    /**
     * Returns whether or not the device is in retail demo mode.
     */
    static boolean isDemoMode(@NonNull Context context) {
        return UserManager.isDeviceInDemoMode(context);
    }

    /**
     * Registers the default network callback.
     */
    static void registerSystemDefaultNetworkCallback(
            @NonNull ConnectivityManager connectivityManager,
            @NonNull ConnectivityManager.NetworkCallback callback,
            @NonNull Handler handler) {
        connectivityManager.registerSystemDefaultNetworkCallback(callback, handler);
    }

    /**
     * Returns true if the WifiInfo is for the primary network, false otherwise.
     */
    static boolean isPrimary(@NonNull WifiInfo wifiInfo) {
        return wifiInfo.isPrimary();
    }
}
