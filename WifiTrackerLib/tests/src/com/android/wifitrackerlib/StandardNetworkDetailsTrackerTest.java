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

import static android.net.wifi.WifiInfo.SECURITY_TYPE_PSK;

import static com.android.wifitrackerlib.StandardWifiEntry.StandardWifiEntryKey;
import static com.android.wifitrackerlib.StandardWifiEntry.ssidAndSecurityTypeToStandardWifiEntryKey;
import static com.android.wifitrackerlib.TestUtils.BAD_LEVEL;
import static com.android.wifitrackerlib.TestUtils.BAD_RSSI;
import static com.android.wifitrackerlib.TestUtils.GOOD_RSSI;
import static com.android.wifitrackerlib.TestUtils.buildScanResult;
import static com.android.wifitrackerlib.TestUtils.buildWifiConfiguration;
import static com.android.wifitrackerlib.WifiEntry.SECURITY_NONE;
import static com.android.wifitrackerlib.WifiEntry.WIFI_LEVEL_UNREACHABLE;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.net.ConnectivityDiagnosticsManager;
import android.net.ConnectivityManager;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiScanner;
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

    @Mock private WifiTrackerInjector mInjector;
    @Mock private Lifecycle mMockLifecycle;
    @Mock private Context mMockContext;
    @Mock private Resources mResources;
    @Mock private WifiManager mMockWifiManager;
    @Mock private WifiScanner mWifiScanner;
    @Mock private ConnectivityManager mMockConnectivityManager;
    @Mock private ConnectivityDiagnosticsManager mMockConnectivityDiagnosticsManager;
    @Mock private Clock mMockClock;

    private TestLooper mTestLooper;

    private final ArgumentCaptor<BroadcastReceiver> mBroadcastReceiverCaptor =
            ArgumentCaptor.forClass(BroadcastReceiver.class);

    private StandardNetworkDetailsTracker createTestStandardNetworkDetailsTracker(
            String key) {
        final Handler testHandler = new Handler(mTestLooper.getLooper());

        return new StandardNetworkDetailsTracker(
                mInjector,
                mMockLifecycle,
                mMockContext,
                mMockWifiManager,
                mMockConnectivityManager,
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

        when(mMockWifiManager.isWpa3SaeSupported()).thenReturn(true);
        when(mMockWifiManager.isWpa3SuiteBSupported()).thenReturn(true);
        when(mMockWifiManager.isEnhancedOpenSupported()).thenReturn(true);
        when(mMockWifiManager.getScanResults()).thenReturn(new ArrayList<>());
        when(mMockWifiManager.getWifiState()).thenReturn(WifiManager.WIFI_STATE_ENABLED);
        when(mMockWifiManager.calculateSignalLevel(TestUtils.GOOD_RSSI))
                .thenReturn(TestUtils.GOOD_LEVEL);
        when(mMockWifiManager.calculateSignalLevel(TestUtils.OKAY_RSSI))
                .thenReturn(TestUtils.OKAY_LEVEL);
        when(mMockWifiManager.calculateSignalLevel(TestUtils.BAD_RSSI))
                .thenReturn(TestUtils.BAD_LEVEL);
        when(mMockContext.getResources()).thenReturn(mResources);
        when(mMockContext.getSystemService(ConnectivityDiagnosticsManager.class))
                .thenReturn(mMockConnectivityDiagnosticsManager);
        when(mMockContext.getSystemService(WifiScanner.class)).thenReturn(mWifiScanner);
        when(mMockClock.millis()).thenReturn(START_MILLIS);
    }

    /**
     * Tests that the key of the created WifiEntry matches the key passed into the constructor.
     */
    @Test
    public void testGetWifiEntry_HasCorrectKey() throws Exception {
        final StandardWifiEntryKey key =
                ssidAndSecurityTypeToStandardWifiEntryKey("ssid", SECURITY_NONE);

        final StandardNetworkDetailsTracker tracker =
                createTestStandardNetworkDetailsTracker(key.toString());

        assertThat(tracker.getWifiEntry().getKey()).isEqualTo(key.toString());
    }

    /**
     * Tests that SCAN_RESULTS_AVAILABLE_ACTION updates the level of the entry.
     */
    @Test
    public void testScanResultsAvailableAction_updates_getLevel() throws Exception {
        // Starting without any scans available should make level WIFI_LEVEL_UNREACHABLE
        final ScanResult scan = buildScanResult("ssid", "bssid", START_MILLIS, -50 /* rssi */);
        final StandardWifiEntryKey key =
                ssidAndSecurityTypeToStandardWifiEntryKey("ssid", SECURITY_NONE);
        final StandardNetworkDetailsTracker tracker =
                createTestStandardNetworkDetailsTracker(key.toString());

        tracker.onStart();
        mTestLooper.dispatchAll();
        verify(mMockContext).registerReceiver(mBroadcastReceiverCaptor.capture(),
                any(), any(), any());
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
    public void testConfiguredNetworksChangedAction_updates_isSaved() throws Exception {
        // Initialize with no config. isSaved() should return false.
        final StandardWifiEntryKey key =
                ssidAndSecurityTypeToStandardWifiEntryKey("ssid", SECURITY_NONE);
        final StandardNetworkDetailsTracker tracker =
                createTestStandardNetworkDetailsTracker(key.toString());

        tracker.onStart();
        mTestLooper.dispatchAll();
        verify(mMockContext).registerReceiver(mBroadcastReceiverCaptor.capture(),
                any(), any(), any());
        final WifiEntry wifiEntry = tracker.getWifiEntry();

        assertThat(wifiEntry.isSaved()).isFalse();

        // Add a config and send a broadcast. isSaved() should return true.
        final WifiConfiguration config = new WifiConfiguration();
        config.SSID = "\"" + "ssid" + "\"";
        when(mMockWifiManager.getPrivilegedConfiguredNetworks())
                .thenReturn(Collections.singletonList(config));
        mBroadcastReceiverCaptor.getValue().onReceive(mMockContext,
                new Intent(WifiManager.CONFIGURED_NETWORKS_CHANGED_ACTION));

        assertThat(wifiEntry.isSaved()).isTrue();

        // Remove the config and send a broadcast. isSaved() should be false.
        when(mMockWifiManager.getPrivilegedConfiguredNetworks())
                .thenReturn(Collections.emptyList());
        mBroadcastReceiverCaptor.getValue().onReceive(mMockContext,
                new Intent(WifiManager.CONFIGURED_NETWORKS_CHANGED_ACTION));

        assertThat(wifiEntry.isSaved()).isFalse();
    }

    /**
     * Tests that WIFI_STATE_DISABLED will clear the scan results of the chosen entry regardless if
     * the scan results are still valid.
     */
    @Test
    public void testWifiStateChanged_disabled_clearsLevel() throws Exception {
        // Start with scan result and wifi state enabled. Level should not be unreachable.
        final ScanResult scan = buildScanResult("ssid", "bssid", START_MILLIS, -50 /* rssi */);
        final StandardWifiEntryKey key =
                ssidAndSecurityTypeToStandardWifiEntryKey("ssid", SECURITY_NONE);
        when(mMockWifiManager.getScanResults()).thenReturn(Collections.singletonList(scan));

        final StandardNetworkDetailsTracker tracker =
                createTestStandardNetworkDetailsTracker(key.toString());
        tracker.onStart();
        mTestLooper.dispatchAll();
        verify(mMockContext).registerReceiver(mBroadcastReceiverCaptor.capture(),
                any(), any(), any());
        final WifiEntry wifiEntry = tracker.getWifiEntry();

        assertThat(wifiEntry.getLevel()).isNotEqualTo(WIFI_LEVEL_UNREACHABLE);

        // Disable wifi. Level should be unreachable.
        when(mMockWifiManager.getWifiState()).thenReturn(WifiManager.WIFI_STATE_DISABLED);

        mBroadcastReceiverCaptor.getValue().onReceive(mMockContext,
                new Intent(WifiManager.WIFI_STATE_CHANGED_ACTION));

        assertThat(wifiEntry.getLevel()).isEqualTo(WIFI_LEVEL_UNREACHABLE);
    }

    @Test
    public void testSecurityTargeting_pskScansWithSaeConfig_correspondsToNewNetworkTargeting() {
        final String ssid = "ssid";
        final WifiConfiguration config = buildWifiConfiguration(ssid);
        config.setSecurityParams(WifiConfiguration.SECURITY_TYPE_SAE);
        when(mMockWifiManager.getPrivilegedConfiguredNetworks())
                .thenReturn(Collections.singletonList(config));
        final ScanResult scan = buildScanResult(ssid, "bssid", START_MILLIS, -50 /* rssi */);
        scan.capabilities = "[PSK]";
        when(mMockWifiManager.getScanResults()).thenReturn(Collections.singletonList(scan));

        // Start without targeting new networks
        StandardNetworkDetailsTracker tracker = createTestStandardNetworkDetailsTracker(
                ssidAndSecurityTypeToStandardWifiEntryKey(ssid, SECURITY_TYPE_PSK).toString());
        tracker.onStart();
        mTestLooper.dispatchAll();

        // WifiEntry should correspond to the saved config
        WifiEntry wifiEntry = tracker.getWifiEntry();
        assertThat(wifiEntry.getSecurityTypes().get(0)).isEqualTo(WifiInfo.SECURITY_TYPE_SAE);
        assertThat(wifiEntry.getLevel()).isEqualTo(WIFI_LEVEL_UNREACHABLE);

        // Now target new networks as if we got the key from WifiPickerTracker
        tracker = createTestStandardNetworkDetailsTracker(
                ssidAndSecurityTypeToStandardWifiEntryKey(ssid, SECURITY_TYPE_PSK,
                        true /* isTargetingNewNetworks */).toString());
        tracker.onStart();
        mTestLooper.dispatchAll();

        // WifiEntry should correspond to the unsaved scan
        wifiEntry = tracker.getWifiEntry();
        assertThat(wifiEntry.getSecurityTypes().get(0)).isEqualTo(SECURITY_TYPE_PSK);
        assertThat(wifiEntry.getLevel()).isNotEqualTo(WIFI_LEVEL_UNREACHABLE);
    }

    /**
     * Tests that we update the chosen entry's ScanResults correctly after a WifiScanner scan.
     */
    @Test
    public void testScanner_wifiScannerResultReceived_scanResultsUpdated() {
        final String ssid = "ssid";
        final WifiConfiguration config = buildWifiConfiguration(ssid);
        when(mMockWifiManager.getPrivilegedConfiguredNetworks())
                .thenReturn(Collections.singletonList(config));
        StandardNetworkDetailsTracker tracker = createTestStandardNetworkDetailsTracker(
                ssidAndSecurityTypeToStandardWifiEntryKey(ssid, SECURITY_NONE).toString());
        tracker.onStart();
        mTestLooper.dispatchAll();
        verify(mMockContext).registerReceiver(mBroadcastReceiverCaptor.capture(),
                any(), any(), any());
        mBroadcastReceiverCaptor.getValue().onReceive(mMockContext,
                new Intent(WifiManager.WIFI_STATE_CHANGED_ACTION).putExtra(
                        WifiManager.EXTRA_WIFI_STATE, WifiManager.WIFI_STATE_ENABLED));
        ArgumentCaptor<WifiScanner.ScanListener> mScanListenerCaptor =
                ArgumentCaptor.forClass(WifiScanner.ScanListener.class);
        verify(mWifiScanner).startScan(any(), mScanListenerCaptor.capture());
        ScanResult[] scanResults = new ScanResult[]{
                buildScanResult(ssid, "bssid", START_MILLIS, BAD_RSSI),
                buildScanResult("different ssid", "bssid", START_MILLIS, GOOD_RSSI),
        };
        WifiScanner.ScanData scanData = mock(WifiScanner.ScanData.class);
        when(scanData.getResults()).thenReturn(scanResults);

        mScanListenerCaptor.getValue().onResults(new WifiScanner.ScanData[]{scanData});
        mTestLooper.dispatchAll();

        // Updated with the correct SSID and ignored the different SSID.
        assertThat(tracker.getWifiEntry().getLevel()).isEqualTo(BAD_LEVEL);
    }
}
