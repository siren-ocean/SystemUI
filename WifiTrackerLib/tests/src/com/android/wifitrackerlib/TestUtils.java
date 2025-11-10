/*
 * Copyright (C) 2019 The Android Open Source Project
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

import android.net.wifi.ScanResult;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiSsid;

import java.nio.charset.StandardCharsets;

/**
 * Utility methods for testing purposes.
 */
class TestUtils {
    public static final int GOOD_RSSI = -50;
    public static final int OKAY_RSSI = -60;
    public static final int BAD_RSSI = -70;

    public static final int GOOD_LEVEL = 5;
    public static final int OKAY_LEVEL = 3;
    public static final int BAD_LEVEL = 1;

    /**
     * Creates a mock scan result with SSID, BSSID, and timestamp.
     */
    static ScanResult buildScanResult(String utf8Ssid, String bssid, long timestampMillis) {
        final ScanResult result = new ScanResult();
        result.SSID = utf8Ssid;
        if (utf8Ssid != null) {
            result.setWifiSsid(WifiSsid.fromBytes(utf8Ssid.getBytes(StandardCharsets.UTF_8)));
        }
        result.BSSID = bssid;
        result.timestamp = timestampMillis * 1000;
        result.capabilities = "";
        return result;
    }

    static ScanResult buildScanResult(
            String utf8Ssid, String bssid, long timestampMillis, int rssi) {
        final ScanResult result = buildScanResult(utf8Ssid, bssid, timestampMillis);
        result.level = rssi;
        result.capabilities = "";
        return result;
    }

    static ScanResult buildScanResult(
            String utf8Ssid, String bssid, long timestampMillis, String securityTypesString) {
        final ScanResult result = buildScanResult(utf8Ssid, bssid, timestampMillis);
        result.capabilities = securityTypesString;
        return result;
    }

    static WifiConfiguration buildWifiConfiguration(String utf8Ssid) {
        final WifiConfiguration config = new WifiConfiguration();
        config.SSID = "\"" + utf8Ssid + "\"";
        return config;
    }
}
