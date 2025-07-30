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

import static com.android.wifitrackerlib.TestUtils.buildScanResult;
import static com.android.wifitrackerlib.TestUtils.buildWifiConfiguration;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkScoreManager;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.net.wifi.hotspot2.PasspointConfiguration;
import android.net.wifi.hotspot2.pps.Credential;
import android.net.wifi.hotspot2.pps.HomeSp;
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
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class SavedNetworkTrackerTest {

    private static final long START_MILLIS = 123_456_789;

    private static final long MAX_SCAN_AGE_MILLIS = 15_000;
    private static final long SCAN_INTERVAL_MILLIS = 10_000;

    @Mock private WifiTrackerInjector mInjector;
    @Mock private Lifecycle mMockLifecycle;
    @Mock private Context mMockContext;
    @Mock private WifiManager mMockWifiManager;
    @Mock private ConnectivityManager mMockConnectivityManager;
    @Mock private NetworkScoreManager mMockNetworkScoreManager;
    @Mock private Clock mMockClock;
    @Mock private SavedNetworkTracker.SavedNetworkTrackerCallback mMockCallback;

    private TestLooper mTestLooper;

    private final ArgumentCaptor<BroadcastReceiver> mBroadcastReceiverCaptor =
            ArgumentCaptor.forClass(BroadcastReceiver.class);

    private SavedNetworkTracker createTestSavedNetworkTracker() {
        final Handler testHandler = new Handler(mTestLooper.getLooper());

        return new SavedNetworkTracker(
                mInjector,
                mMockLifecycle,
                mMockContext,
                mMockWifiManager,
                mMockConnectivityManager,
                mMockNetworkScoreManager,
                testHandler,
                testHandler,
                mMockClock,
                MAX_SCAN_AGE_MILLIS,
                SCAN_INTERVAL_MILLIS,
                mMockCallback);
    }

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        mTestLooper = new TestLooper();

        when(mMockWifiManager.getScanResults()).thenReturn(new ArrayList<>());
        when(mMockWifiManager.isWpa3SaeSupported()).thenReturn(true);
        when(mMockWifiManager.isWpa3SuiteBSupported()).thenReturn(true);
        when(mMockWifiManager.isEnhancedOpenSupported()).thenReturn(true);
        when(mMockClock.millis()).thenReturn(START_MILLIS);
        when(mMockContext.getSystemService(Context.NETWORK_SCORE_SERVICE))
                .thenReturn(mMockNetworkScoreManager);
    }

    /**
     * Tests that the wifi state is set correctly after onStart, even if no broadcast was received.
     */
    @Test
    public void testOnStart_setsWifiState() {
        final SavedNetworkTracker savedNetworkTracker = createTestSavedNetworkTracker();

        // Set the wifi state to disabled
        when(mMockWifiManager.getWifiState()).thenReturn(WifiManager.WIFI_STATE_DISABLED);
        savedNetworkTracker.onStart();
        mTestLooper.dispatchAll();

        assertThat(savedNetworkTracker.getWifiState()).isEqualTo(WifiManager.WIFI_STATE_DISABLED);

        // Change the wifi state to enabled
        savedNetworkTracker.onStop();
        when(mMockWifiManager.getWifiState()).thenReturn(WifiManager.WIFI_STATE_ENABLED);
        savedNetworkTracker.onStart();
        mTestLooper.dispatchAll();

        assertThat(savedNetworkTracker.getWifiState()).isEqualTo(WifiManager.WIFI_STATE_ENABLED);
    }

    /**
     * Tests that receiving a wifi state change broadcast updates getWifiState().
     */
    @Test
    public void testWifiStateChangeBroadcast_updatesWifiState() {
        final SavedNetworkTracker wifiPickerTracker = createTestSavedNetworkTracker();
        wifiPickerTracker.onStart();
        verify(mMockContext).registerReceiver(mBroadcastReceiverCaptor.capture(),
                any(), any(), any());

        // Set the wifi state to disabled
        when(mMockWifiManager.getWifiState()).thenReturn(WifiManager.WIFI_STATE_DISABLED);
        mBroadcastReceiverCaptor.getValue().onReceive(mMockContext,
                new Intent(WifiManager.WIFI_STATE_CHANGED_ACTION));

        assertThat(wifiPickerTracker.getWifiState()).isEqualTo(WifiManager.WIFI_STATE_DISABLED);

        // Change the wifi state to enabled
        when(mMockWifiManager.getWifiState()).thenReturn(WifiManager.WIFI_STATE_ENABLED);
        mBroadcastReceiverCaptor.getValue().onReceive(mMockContext,
                new Intent(WifiManager.WIFI_STATE_CHANGED_ACTION));

        assertThat(wifiPickerTracker.getWifiState()).isEqualTo(WifiManager.WIFI_STATE_ENABLED);
    }

    /**
     * Tests that receiving a wifi state change broadcast notifies the listener.
     */
    @Test
    public void testWifiStateChangeBroadcast_notifiesListener() {
        final SavedNetworkTracker savedNetworkTracker = createTestSavedNetworkTracker();
        savedNetworkTracker.onStart();
        verify(mMockContext).registerReceiver(mBroadcastReceiverCaptor.capture(),
                any(), any(), any());

        mBroadcastReceiverCaptor.getValue().onReceive(mMockContext,
                new Intent(WifiManager.WIFI_STATE_CHANGED_ACTION));
        mTestLooper.dispatchAll();

        verify(mMockCallback, atLeastOnce()).onWifiStateChanged();
    }

    /**
     * Tests that a CONFIGURED_NETWORKS_CHANGED broadcast notifies the listener for
     * onSavedWifiEntriesChanged().
     */
    @Test
    public void testConfiguredNetworksChanged_notifiesListener() {
        final SavedNetworkTracker savedNetworkTracker = createTestSavedNetworkTracker();
        savedNetworkTracker.onStart();
        verify(mMockContext).registerReceiver(mBroadcastReceiverCaptor.capture(),
                any(), any(), any());

        mBroadcastReceiverCaptor.getValue().onReceive(mMockContext,
                new Intent(WifiManager.CONFIGURED_NETWORKS_CHANGED_ACTION));
        mTestLooper.dispatchAll();

        verify(mMockCallback, atLeastOnce()).onSavedWifiEntriesChanged();
    }

    /**
     * Tests that a WifiEntry is created for each configured network for getSavedWifiEntries().
     */
    @Test
    public void testGetSavedWifiEntries_onStart_entryForEachConfiguredNetwork() {
        when(mMockWifiManager.getConfiguredNetworks()).thenReturn(Arrays.asList(
                buildWifiConfiguration("ssid0"),
                buildWifiConfiguration("ssid1"),
                buildWifiConfiguration("ssid2")
        ));
        final SavedNetworkTracker savedNetworkTracker = createTestSavedNetworkTracker();
        savedNetworkTracker.onStart();
        verify(mMockContext).registerReceiver(mBroadcastReceiverCaptor.capture(),
                any(), any(), any());
        mTestLooper.dispatchAll();

        assertThat(savedNetworkTracker.getSavedWifiEntries().stream()
                .filter(entry -> entry.mForSavedNetworksPage)
                .map(WifiEntry::getTitle)
                .collect(Collectors.toSet()))
                .containsExactly("ssid0", "ssid1", "ssid2");
    }

    /**
     * Tests that a CONFIGURED_NETWORKS_CHANGED broadcast after adding a config
     * adds the corresponding WifiEntry from getSavedWifiEntries().
     */
    @Test
    public void testGetSavedWifiEntries_configuredNetworksChanged_addsEntry() {
        final SavedNetworkTracker savedNetworkTracker = createTestSavedNetworkTracker();
        savedNetworkTracker.onStart();
        verify(mMockContext).registerReceiver(mBroadcastReceiverCaptor.capture(),
                any(), any(), any());
        mTestLooper.dispatchAll();

        assertThat(savedNetworkTracker.getSavedWifiEntries()).isEmpty();

        final WifiConfiguration config = buildWifiConfiguration("ssid");
        when(mMockWifiManager.getConfiguredNetworks())
                .thenReturn(Collections.singletonList(config));
        mBroadcastReceiverCaptor.getValue().onReceive(mMockContext,
                new Intent(WifiManager.CONFIGURED_NETWORKS_CHANGED_ACTION));

        assertThat(savedNetworkTracker.getSavedWifiEntries().stream()
                .filter(entry -> entry.mForSavedNetworksPage)
                .collect(Collectors.toSet()))
                .hasSize(1);
    }

    /**
     * Tests that a CONFIGURED_NETWORKS_CHANGED broadcast after removing a config
     * removes the corresponding WifiEntry from getSavedWifiEntries().
     */
    @Test
    public void testGetSavedWifiEntries_configuredNetworksChanged_removesEntry() {
        final SavedNetworkTracker savedNetworkTracker = createTestSavedNetworkTracker();
        final WifiConfiguration config = buildWifiConfiguration("ssid");
        when(mMockWifiManager.getConfiguredNetworks())
                .thenReturn(Collections.singletonList(config));
        savedNetworkTracker.onStart();
        verify(mMockContext).registerReceiver(mBroadcastReceiverCaptor.capture(),
                any(), any(), any());
        mTestLooper.dispatchAll();

        assertThat(savedNetworkTracker.getSavedWifiEntries()).hasSize(1);

        when(mMockWifiManager.getConfiguredNetworks())
                .thenReturn(Collections.emptyList());
        mBroadcastReceiverCaptor.getValue().onReceive(mMockContext,
                new Intent(WifiManager.CONFIGURED_NETWORKS_CHANGED_ACTION));

        assertThat(savedNetworkTracker.getSavedWifiEntries()).isEmpty();
    }

    /**
     * Tests that receiving a scan results available broadcast notifies the listener.
     */
    @Test
    public void testScanResultsAvailableAction_notifiesListener() {
        final SavedNetworkTracker savedNetworkTracker = createTestSavedNetworkTracker();
        savedNetworkTracker.onStart();
        verify(mMockContext).registerReceiver(mBroadcastReceiverCaptor.capture(),
                any(), any(), any());

        mBroadcastReceiverCaptor.getValue().onReceive(mMockContext,
                new Intent(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION));
        mTestLooper.dispatchAll();

        verify(mMockCallback, atLeastOnce()).onSavedWifiEntriesChanged();
    }

    /**
     * Tests that the scan results available broadcast changes the level of saved WifiEntries.
     */
    @Test
    public void testGetSavedWifiEntries_scanResultsAvailableAction_changesLevel() {
        final SavedNetworkTracker savedNetworkTracker = createTestSavedNetworkTracker();
        final WifiConfiguration config = buildWifiConfiguration("ssid");
        when(mMockWifiManager.getConfiguredNetworks())
                .thenReturn(Collections.singletonList(config));
        savedNetworkTracker.onStart();
        verify(mMockContext).registerReceiver(mBroadcastReceiverCaptor.capture(),
                any(), any(), any());
        mTestLooper.dispatchAll();
        final WifiEntry entry = savedNetworkTracker.getSavedWifiEntries().get(0);

        assertThat(entry.getLevel()).isEqualTo(WifiEntry.WIFI_LEVEL_UNREACHABLE);

        when(mMockWifiManager.getScanResults()).thenReturn(Collections.singletonList(
                buildScanResult("ssid", "bssid", START_MILLIS, -50 /* rssi */)));
        mBroadcastReceiverCaptor.getValue().onReceive(mMockContext,
                new Intent(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION));

        assertThat(entry.getLevel()).isNotEqualTo(WifiEntry.WIFI_LEVEL_UNREACHABLE);

        when(mMockClock.millis()).thenReturn(START_MILLIS + MAX_SCAN_AGE_MILLIS + 1);
        mBroadcastReceiverCaptor.getValue().onReceive(mMockContext,
                new Intent(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION));

        assertThat(entry.getLevel()).isEqualTo(WifiEntry.WIFI_LEVEL_UNREACHABLE);
    }

    @Test
    public void testGetSubscriptionWifiEntries_returnsPasspointEntries() {
        final SavedNetworkTracker savedNetworkTracker = createTestSavedNetworkTracker();
        final PasspointConfiguration passpointConfig = new PasspointConfiguration();
        final HomeSp homeSp = new HomeSp();
        homeSp.setFqdn("fqdn");
        homeSp.setFriendlyName("friendlyName");
        passpointConfig.setHomeSp(homeSp);
        passpointConfig.setCredential(new Credential());
        when(mMockWifiManager.getPasspointConfigurations())
                .thenReturn(Collections.singletonList(passpointConfig));

        savedNetworkTracker.onStart();
        verify(mMockContext).registerReceiver(mBroadcastReceiverCaptor.capture(),
                any(), any(), any());
        mTestLooper.dispatchAll();

        final WifiEntry entry = savedNetworkTracker.getSubscriptionWifiEntries().get(0);
        assertThat(savedNetworkTracker.getSubscriptionWifiEntries()).isNotEmpty();
        assertThat(savedNetworkTracker.getSubscriptionWifiEntries().get(0).getTitle())
                .isEqualTo("friendlyName");
    }

    @Test
    public void testGetSavedNetworks_splitConfigs_entriesMergedBySecurityFamily() {
        final String ssid = "ssid";
        WifiConfiguration openConfig = buildWifiConfiguration(ssid);
        openConfig.setSecurityParams(WifiConfiguration.SECURITY_TYPE_OPEN);
        openConfig.networkId = 1;
        WifiConfiguration oweConfig = buildWifiConfiguration(ssid);
        oweConfig.setSecurityParams(WifiConfiguration.SECURITY_TYPE_OWE);
        oweConfig.networkId = 1;
        WifiConfiguration wepConfig = buildWifiConfiguration(ssid);
        wepConfig.setSecurityParams(WifiConfiguration.SECURITY_TYPE_WEP);
        wepConfig.wepKeys = new String[]{"key"};
        wepConfig.networkId = 2;
        WifiConfiguration pskConfig = buildWifiConfiguration(ssid);
        pskConfig.setSecurityParams(WifiConfiguration.SECURITY_TYPE_PSK);
        pskConfig.networkId = 3;
        WifiConfiguration saeConfig = buildWifiConfiguration(ssid);
        saeConfig.setSecurityParams(WifiConfiguration.SECURITY_TYPE_SAE);
        saeConfig.networkId = 3;
        WifiConfiguration eapConfig = buildWifiConfiguration(ssid);
        eapConfig.setSecurityParams(WifiConfiguration.SECURITY_TYPE_EAP);
        eapConfig.networkId = 4;
        WifiConfiguration eapWpa3Config = buildWifiConfiguration(ssid);
        eapWpa3Config.setSecurityParams(WifiConfiguration.SECURITY_TYPE_EAP_WPA3_ENTERPRISE);
        eapWpa3Config.networkId = 4;
        WifiConfiguration eapWpa3SuiteBConfig = buildWifiConfiguration(ssid);
        eapWpa3SuiteBConfig.setSecurityParams(
                WifiConfiguration.SECURITY_TYPE_EAP_WPA3_ENTERPRISE_192_BIT);
        eapWpa3SuiteBConfig.networkId = 5;
        when(mMockWifiManager.getConfiguredNetworks()).thenReturn(Arrays.asList(
                openConfig, oweConfig, wepConfig, pskConfig, saeConfig, eapConfig, eapWpa3Config,
                eapWpa3SuiteBConfig
        ));
        final SavedNetworkTracker savedNetworkTracker = createTestSavedNetworkTracker();
        savedNetworkTracker.onStart();
        mTestLooper.dispatchAll();

        final List<WifiEntry> savedWifiEntries = savedNetworkTracker.getSavedWifiEntries();
        assertThat(savedWifiEntries.size()).isEqualTo(5);
        assertThat(savedWifiEntries.stream()
                .map(entry -> entry.getSecurityTypes())
                .collect(Collectors.toList()))
                .containsExactly(
                        Arrays.asList(WifiInfo.SECURITY_TYPE_OPEN, WifiInfo.SECURITY_TYPE_OWE),
                        Arrays.asList(WifiInfo.SECURITY_TYPE_WEP),
                        Arrays.asList(WifiInfo.SECURITY_TYPE_PSK, WifiInfo.SECURITY_TYPE_SAE),
                        Arrays.asList(WifiInfo.SECURITY_TYPE_EAP,
                                WifiInfo.SECURITY_TYPE_EAP_WPA3_ENTERPRISE),
                        Arrays.asList(WifiInfo.SECURITY_TYPE_EAP_WPA3_ENTERPRISE_192_BIT));
    }

    /**
     * Tests that entries with configs that have scans matching the security family but NOT the
     * actual configs on hand will ignore the scans and be returned as saved with the configs.
     */
    @Test
    public void testGetSavedNetworks_mismatchedScans_returnsCorrectEntries() {
        // Set up scans for Open, PSK, WPA2-Enterprise
        final ArrayList scanList = new ArrayList();
        final String ssid = "ssid";
        final String bssid = "bssid";
        int bssidNum = 0;
        for (String capabilities : Arrays.asList(
                "",
                "[PSK]",
                "[EAP/SHA1]"
        )) {
            final ScanResult scan = buildScanResult(ssid, bssid + bssidNum++, START_MILLIS);
            scan.capabilities = capabilities;
            scanList.add(scan);
        }
        when(mMockWifiManager.getScanResults()).thenReturn(scanList);
        // Set up configs for OWE, SAE, WPA3-Enterprise
        WifiConfiguration oweConfig = buildWifiConfiguration(ssid);
        oweConfig.setSecurityParams(WifiConfiguration.SECURITY_TYPE_OWE);
        oweConfig.networkId = 1;
        WifiConfiguration saeConfig = buildWifiConfiguration(ssid);
        saeConfig.setSecurityParams(WifiConfiguration.SECURITY_TYPE_SAE);
        saeConfig.networkId = 2;
        WifiConfiguration eapWpa3Config = buildWifiConfiguration(ssid);
        eapWpa3Config.setSecurityParams(WifiConfiguration.SECURITY_TYPE_EAP_WPA3_ENTERPRISE);
        eapWpa3Config.networkId = 3;
        when(mMockWifiManager.getConfiguredNetworks())
                .thenReturn(Arrays.asList(oweConfig, saeConfig, eapWpa3Config));

        final SavedNetworkTracker savedNetworkTracker = createTestSavedNetworkTracker();
        savedNetworkTracker.onStart();
        mTestLooper.dispatchAll();

        // Entries should appear correctly in the saved entry list with the security type of their
        // configs, ignoring the scans present.
        final List<WifiEntry> savedWifiEntries = savedNetworkTracker.getSavedWifiEntries();
        assertThat(savedWifiEntries.size()).isEqualTo(3);
        assertThat(savedWifiEntries.stream()
                .map(entry -> entry.getSecurityTypes())
                .collect(Collectors.toList()))
                .containsExactly(
                        Arrays.asList(WifiInfo.SECURITY_TYPE_OWE),
                        Arrays.asList(WifiInfo.SECURITY_TYPE_SAE),
                        Arrays.asList(WifiInfo.SECURITY_TYPE_EAP_WPA3_ENTERPRISE));
    }
}
