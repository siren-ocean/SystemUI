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

import static com.android.wifitrackerlib.StandardWifiEntry.ssidAndSecurityToStandardWifiEntryKey;
import static com.android.wifitrackerlib.TestUtils.buildScanResult;
import static com.android.wifitrackerlib.WifiEntry.SECURITY_NONE;
import static com.android.wifitrackerlib.WifiEntry.WIFI_LEVEL_UNREACHABLE;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkScoreManager;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.test.TestLooper;

import androidx.lifecycle.Lifecycle;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Collections;

public class StandardNetworkDetailsTrackerTest {

    private static final long START_MILLIS = 123_456_789;

    private static final long MAX_SCAN_AGE_MILLIS = 15_000;
    private static final long SCAN_INTERVAL_MILLIS = 10_000;

    @Mock
    private Lifecycle mMockLifecycle;
    @Mock
    private Context mMockContext;
    @Mock
    private WifiManager mMockWifiManager;
    @Mock
    private ConnectivityManager mMockConnectivityManager;
    @Mock
    private NetworkScoreManager mMockNetworkScoreManager;
    @Mock
    private Clock mMockClock;

    private TestLooper mTestLooper;

    private final ArgumentCaptor<BroadcastReceiver> mBroadcastReceiverCaptor =
            ArgumentCaptor.forClass(BroadcastReceiver.class);

    private StandardNetworkDetailsTracker createTestStandardNetworkDetailsTracker(String key) {
        final Handler testHandler = new Handler(mTestLooper.getLooper());

        return new StandardNetworkDetailsTracker(mMockLifecycle, mMockContext,
                mMockWifiManager,
                mMockConnectivityManager,
                mMockNetworkScoreManager,
                testHandler,
                testHandler,
                mMockClock,
                MAX_SCAN_AGE_MILLIS,
                SCAN_INTERVAL_MILLIS,
                key);
    }

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        mTestLooper = new TestLooper();

        when(mMockWifiManager.getScanResults()).thenReturn(new ArrayList<>());
        when(mMockWifiManager.getWifiState()).thenReturn(WifiManager.WIFI_STATE_ENABLED);
        when(mMockClock.millis()).thenReturn(START_MILLIS);
        when(mMockContext.getSystemService(Context.NETWORK_SCORE_SERVICE))
                .thenReturn(mMockNetworkScoreManager);
    }

    /**
     * Tests that the key of the created WifiEntry matches the key passed into the constructor.
     */
    @Test
    public void testGetWifiEntry_HasCorrectKey() {
        final String key = ssidAndSecurityToStandardWifiEntryKey("ssid", SECURITY_NONE);

        final StandardNetworkDetailsTracker tracker = createTestStandardNetworkDetailsTracker(key);

        assertThat(tracker.getWifiEntry().getKey()).isEqualTo(key);
    }

    /**
     * Tests that SCAN_RESULTS_AVAILABLE_ACTION updates the level of the entry.
     */
    @Test
    public void testHandleOnStart_scanResultUpdaterUpdateCorrectly() {
        final ScanResult chosen = buildScanResult("ssid", "bssid", START_MILLIS);
        final String key = ssidAndSecurityToStandardWifiEntryKey("ssid", SECURITY_NONE);
        final StandardNetworkDetailsTracker tracker = createTestStandardNetworkDetailsTracker(key);
        final ScanResult other = buildScanResult("ssid2", "bssid", START_MILLIS, -50 /* rssi */);
        when(mMockWifiManager.getScanResults()).thenReturn(Collections.singletonList(other));

        //tracker.onStart();
        tracker.handleOnStart();

        final long invalidCount = tracker.mScanResultUpdater.getScanResults().stream().filter(
                scanResult -> !"ssid".equals(scanResult.SSID)).count();
        assertThat(invalidCount).isEqualTo(0);
    }

    /**
     * Tests that SCAN_RESULTS_AVAILABLE_ACTION updates the level of the entry.
     */
    @Test
    public void testScanResultsAvailableAction_updates_getLevel() {
        // Starting without any scans available should make level WIFI_LEVEL_UNREACHABLE
        final ScanResult scan = buildScanResult("ssid", "bssid", START_MILLIS, -50 /* rssi */);
        final String key = ssidAndSecurityToStandardWifiEntryKey("ssid", SECURITY_NONE);
        final StandardNetworkDetailsTracker tracker = createTestStandardNetworkDetailsTracker(key);

        tracker.onStart();
        verify(mMockContext).registerReceiver(mBroadcastReceiverCaptor.capture(),
                any(), any(), any());
        mTestLooper.dispatchAll();
        final WifiEntry wifiEntry = tracker.getWifiEntry();

        assertThat(wifiEntry.getLevel()).isEqualTo(WIFI_LEVEL_UNREACHABLE);

        // Received fresh scan. Level should not be WIFI_LEVEL_UNREACHABLE anymore
        when(mMockWifiManager.getScanResults()).thenReturn(Collections.singletonList(scan));

        mBroadcastReceiverCaptor.getValue().onReceive(mMockContext,
                new Intent(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
                        .putExtra(WifiManager.EXTRA_RESULTS_UPDATED, true));

        assertThat(wifiEntry.getLevel()).isNotEqualTo(WIFI_LEVEL_UNREACHABLE);

        // Scan returned with no scans, old scans timed out. Level should be WIFI_LEVEL_UNREACHABLE.
        when(mMockWifiManager.getScanResults()).thenReturn(Collections.emptyList());
        when(mMockClock.millis()).thenReturn(START_MILLIS + MAX_SCAN_AGE_MILLIS + 1);

        mBroadcastReceiverCaptor.getValue().onReceive(mMockContext,
                new Intent(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
                        .putExtra(WifiManager.EXTRA_RESULTS_UPDATED, true));

        assertThat(wifiEntry.getLevel()).isEqualTo(WIFI_LEVEL_UNREACHABLE);
    }

    /**
     * Tests that CONFIGURED_NETWORKS_CHANGED_ACTION updates the isSaved() value of the entry.
     */
    @Test
    public void testConfiguredNetworksChangedAction_updates_isSaved() {
        // Initialize with no config. isSaved() should return false.
        final String key = ssidAndSecurityToStandardWifiEntryKey("ssid", SECURITY_NONE);
        final StandardNetworkDetailsTracker tracker = createTestStandardNetworkDetailsTracker(key);

        tracker.onStart();
        verify(mMockContext).registerReceiver(mBroadcastReceiverCaptor.capture(),
                any(), any(), any());
        mTestLooper.dispatchAll();
        final WifiEntry wifiEntry = tracker.getWifiEntry();

        assertThat(wifiEntry.isSaved()).isFalse();

        // Add a config via broadcast. isSaved() should return true.
        final WifiConfiguration config = new WifiConfiguration();
        config.SSID = "\"" + "ssid" + "\"";

        mBroadcastReceiverCaptor.getValue().onReceive(mMockContext,
                new Intent(WifiManager.CONFIGURED_NETWORKS_CHANGED_ACTION)
                        .putExtra(WifiManager.EXTRA_WIFI_CONFIGURATION, config)
                        .putExtra(WifiManager.EXTRA_CHANGE_REASON,
                                WifiManager.CHANGE_REASON_ADDED));

        assertThat(wifiEntry.isSaved()).isTrue();

        // Remove the config via broadcast. isSaved() should be false.
        mBroadcastReceiverCaptor.getValue().onReceive(mMockContext,
                new Intent(WifiManager.CONFIGURED_NETWORKS_CHANGED_ACTION)
                        .putExtra(WifiManager.EXTRA_WIFI_CONFIGURATION, config)
                        .putExtra(WifiManager.EXTRA_CHANGE_REASON,
                                WifiManager.CHANGE_REASON_REMOVED));

        assertThat(wifiEntry.isSaved()).isFalse();
    }

    /**
     * Tests that WIFI_STATE_DISABLED will clear the scan results of the chosen entry regardless if
     * the scan results are still valid.
     */
    @Test
    public void testWifiStateChanged_disabled_clearsLevel() {
        // Start with scan result and wifi state enabled. Level should not be unreachable.
        final ScanResult scan = buildScanResult("ssid", "bssid", START_MILLIS, -50 /* rssi */);
        final String key = ssidAndSecurityToStandardWifiEntryKey("ssid", SECURITY_NONE);
        when(mMockWifiManager.getScanResults()).thenReturn(Collections.singletonList(scan));

        final StandardNetworkDetailsTracker tracker = createTestStandardNetworkDetailsTracker(key);
        tracker.onStart();
        verify(mMockContext).registerReceiver(mBroadcastReceiverCaptor.capture(),
                any(), any(), any());
        mTestLooper.dispatchAll();
        final WifiEntry wifiEntry = tracker.getWifiEntry();

        assertThat(wifiEntry.getLevel()).isNotEqualTo(WIFI_LEVEL_UNREACHABLE);

        // Disable wifi. Level should be unreachable.
        when(mMockWifiManager.getWifiState()).thenReturn(WifiManager.WIFI_STATE_DISABLED);

        mBroadcastReceiverCaptor.getValue().onReceive(mMockContext,
                new Intent(WifiManager.WIFI_STATE_CHANGED_ACTION));

        assertThat(wifiEntry.getLevel()).isEqualTo(WIFI_LEVEL_UNREACHABLE);
    }
}
