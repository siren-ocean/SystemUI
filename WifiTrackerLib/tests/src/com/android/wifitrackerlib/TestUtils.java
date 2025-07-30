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

/**
 * Utility methods for testing purposes.
 */
class TestUtils {
    /**
     * Creates a mock scan result with SSID, BSSID, and timestamp.
     */
    static ScanResult buildScanResult(String ssid, String bssid, long timestampMillis) {
        final ScanResult result = new ScanResult();
        result.SSID = ssid;
        result.BSSID = bssid;
        result.timestamp = timestampMillis * 1000;
        result.capabilities = "";
        return result;
    }

    static ScanResult buildScanResult(String ssid, String bssid, long timestampMillis, int rssi) {
        final ScanResult result = buildScanResult(ssid, bssid, timestampMillis);
        result.level = rssi;
        result.capabilities = "";
        return result;
    }

    static WifiConfiguration buildWifiConfiguration(String ssid) {
        final WifiConfiguration config = new WifiConfiguration();
        config.SSID = "\"" + ssid + "\"";
        return config;
    }
}
