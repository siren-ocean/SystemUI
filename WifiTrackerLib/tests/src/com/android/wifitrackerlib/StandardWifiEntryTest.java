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

import static android.net.wifi.WifiConfiguration.NetworkSelectionStatus.DISABLED_AUTHENTICATION_FAILURE;
import static android.net.wifi.WifiConfiguration.NetworkSelectionStatus.DISABLED_AUTHENTICATION_NO_CREDENTIALS;
import static android.net.wifi.WifiConfiguration.NetworkSelectionStatus.DISABLED_BY_WRONG_PASSWORD;
import static android.net.wifi.WifiConfiguration.NetworkSelectionStatus.NETWORK_SELECTION_PERMANENTLY_DISABLED;
import static android.net.wifi.WifiConfiguration.NetworkSelectionStatus.NETWORK_SELECTION_TEMPORARY_DISABLED;
import static android.net.wifi.WifiInfo.SECURITY_TYPE_EAP;
import static android.net.wifi.WifiInfo.SECURITY_TYPE_OPEN;
import static android.net.wifi.WifiInfo.SECURITY_TYPE_OWE;
import static android.net.wifi.WifiInfo.SECURITY_TYPE_PSK;
import static android.net.wifi.WifiInfo.SECURITY_TYPE_SAE;
import static android.net.wifi.WifiInfo.SECURITY_TYPE_WEP;

import static com.android.wifitrackerlib.StandardWifiEntry.ScanResultKey;
import static com.android.wifitrackerlib.StandardWifiEntry.StandardWifiEntryKey;
import static com.android.wifitrackerlib.StandardWifiEntry.ssidAndSecurityTypeToStandardWifiEntryKey;
import static com.android.wifitrackerlib.TestUtils.buildScanResult;
import static com.android.wifitrackerlib.WifiEntry.CONNECTED_STATE_CONNECTED;
import static com.android.wifitrackerlib.WifiEntry.CONNECTED_STATE_DISCONNECTED;
import static com.android.wifitrackerlib.WifiEntry.SPEED_FAST;
import static com.android.wifitrackerlib.WifiEntry.SPEED_SLOW;
import static com.android.wifitrackerlib.WifiEntry.WIFI_LEVEL_UNREACHABLE;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.fail;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.content.res.Resources;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.MacAddress;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.net.NetworkKey;
import android.net.NetworkScoreManager;
import android.net.ScoredNetwork;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiConfiguration.NetworkSelectionStatus;
import android.net.wifi.WifiEnterpriseConfig;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiNetworkScoreCache;
import android.os.Handler;
import android.os.test.TestLooper;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Arrays;
import java.util.Collections;

public class StandardWifiEntryTest {
    public static final int GOOD_RSSI = -50;
    public static final int OKAY_RSSI = -60;
    public static final int BAD_RSSI = -70;

    public static final int GOOD_LEVEL = 5;
    public static final int OKAY_LEVEL = 3;
    public static final int BAD_LEVEL = 1;

    @Mock private WifiEntry.WifiEntryCallback mMockListener;
    @Mock private WifiEntry.ConnectCallback mMockConnectCallback;
    @Mock private WifiManager mMockWifiManager;
    @Mock private ConnectivityManager mMockConnectivityManager;
    @Mock private SubscriptionManager mSubscriptionManager;
    @Mock private WifiInfo mMockWifiInfo;
    @Mock private NetworkInfo mMockNetworkInfo;
    @Mock private WifiTrackerInjector mMockInjector;
    @Mock private Context mMockContext;
    @Mock private NetworkScoreManager mMockNetworkScoreManager;
    @Mock private WifiNetworkScoreCache mMockScoreCache;
    @Mock private ScoredNetwork mMockScoredNetwork;

    private TestLooper mTestLooper;
    private Handler mTestHandler;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        mTestLooper = new TestLooper();
        mTestHandler = new Handler(mTestLooper.getLooper());

        when(mMockWifiInfo.getNetworkId()).thenReturn(WifiConfiguration.INVALID_NETWORK_ID);
        when(mMockWifiInfo.getRssi()).thenReturn(WifiInfo.INVALID_RSSI);
        when(mMockNetworkInfo.getDetailedState()).thenReturn(
                NetworkInfo.DetailedState.DISCONNECTED);
        when(mMockWifiManager.isWpa3SaeSupported()).thenReturn(true);
        when(mMockWifiManager.isEnhancedOpenSupported()).thenReturn(true);
        when(mMockWifiManager.isWpa3SuiteBSupported()).thenReturn(true);
        when(mMockWifiManager.calculateSignalLevel(GOOD_RSSI))
                .thenReturn(GOOD_LEVEL);
        when(mMockWifiManager.calculateSignalLevel(OKAY_RSSI))
                .thenReturn(OKAY_LEVEL);
        when(mMockWifiManager.calculateSignalLevel(BAD_RSSI))
                .thenReturn(BAD_LEVEL);
        when(mMockContext.getSystemService(Context.NETWORK_SCORE_SERVICE))
                .thenReturn(mMockNetworkScoreManager);
        when(mMockScoreCache.getScoredNetwork((ScanResult) any())).thenReturn(mMockScoredNetwork);
        when(mMockScoreCache.getScoredNetwork((NetworkKey) any())).thenReturn(mMockScoredNetwork);

        when(mMockContext.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE))
                .thenReturn(mSubscriptionManager);
    }

    /**
     * Tests that constructing with a list of scans with differing SSIDs throws an exception
     */
    @Test
    public void testConstructor_mismatchedSsids_throwsException() {
        try {
            new StandardWifiEntry(
                mMockInjector, mMockContext, mTestHandler,
                    ssidAndSecurityTypeToStandardWifiEntryKey("ssid", SECURITY_TYPE_OPEN),
                    null, Arrays.asList(
                            buildScanResult("ssid0", "bssid0", 0, GOOD_RSSI),
                            buildScanResult("ssid1", "bssid1", 0, GOOD_RSSI)),
                    mMockWifiManager, mMockScoreCache, false /* forSavedNetworksPage */);
            fail("Scan list with different SSIDs should have thrown exception");
        } catch (IllegalArgumentException e) {
            // Test succeeded
        }
    }

    /**
     * Tests that the level is set to the level of the strongest scan
     */
    @Test
    public void testConstructor_scanResults_setsBestLevel() {
        final StandardWifiEntry entry = new StandardWifiEntry(
                mMockInjector, mMockContext, mTestHandler,
                ssidAndSecurityTypeToStandardWifiEntryKey("ssid", SECURITY_TYPE_OPEN),
                null, Arrays.asList(
                        buildScanResult("ssid", "bssid0", 0, GOOD_RSSI),
                        buildScanResult("ssid", "bssid1", 0, OKAY_RSSI),
                        buildScanResult("ssid", "bssid2", 0, BAD_RSSI)),
                mMockWifiManager, mMockScoreCache, false /* forSavedNetworksPage */);

        assertThat(entry.getLevel()).isEqualTo(GOOD_LEVEL);
    }

    /**
     * Tests that the security is set to the security capabilities of the scan results if
     * the entry is targeting new networks.
     */
    @Test
    public void testConstructor_targetingNewSecurity_scanResultsSetSecurity() {
        final ScanResult unsecureScan = buildScanResult("ssid", "bssid", 0, GOOD_RSSI);
        final ScanResult secureScan = buildScanResult("ssid", "bssid", 0, GOOD_RSSI);
        secureScan.capabilities = "EAP/SHA1";

        final StandardWifiEntry unsecureEntry = new StandardWifiEntry(
                mMockInjector, mMockContext, mTestHandler,
                ssidAndSecurityTypeToStandardWifiEntryKey("ssid", SECURITY_TYPE_OPEN,
                        true /* isTargetingNewNetworks */),
                null, Arrays.asList(unsecureScan), mMockWifiManager, mMockScoreCache,
                false /* forSavedNetworksPage */);
        final StandardWifiEntry secureEntry = new StandardWifiEntry(
                mMockInjector, mMockContext, mTestHandler,
                ssidAndSecurityTypeToStandardWifiEntryKey("ssid", SECURITY_TYPE_EAP,
                        true /* isTargetingNewNetworks */),
                null, Arrays.asList(secureScan), mMockWifiManager, mMockScoreCache,
                false /* forSavedNetworksPage */);

        assertThat(unsecureEntry.getSecurity()).isEqualTo(WifiEntry.SECURITY_NONE);
        assertThat(secureEntry.getSecurity()).isEqualTo(WifiEntry.SECURITY_EAP);
    }

    /**
     * Tests that updating with a list of scans with differing SSIDs throws an exception
     */
    @Test
    public void testUpdateScanResultInfo_mismatchedSsids_throwsException() {
        final StandardWifiEntry entry = new StandardWifiEntry(
                mMockInjector, mMockContext, mTestHandler,
                ssidAndSecurityTypeToStandardWifiEntryKey("ssid0", SECURITY_TYPE_EAP),
                null, Arrays.asList(buildScanResult("ssid0", "bssid0", 0, GOOD_RSSI)),
                mMockWifiManager, mMockScoreCache, false /* forSavedNetworksPage */);

        try {
            entry.updateScanResultInfo(Arrays.asList(
                    buildScanResult("ssid1", "bssid1", 0, GOOD_RSSI)));
            fail("Scan list with different SSIDs should have thrown exception");
        } catch (IllegalArgumentException e) {
            // Test succeeded
        }
    }

    /**
     * Tests that the listener is notified after an update to the scan results
     */
    @Test
    public void testUpdateScanResultInfo_notifiesListener() {
        final StandardWifiEntry entry = new StandardWifiEntry(
                mMockInjector, mMockContext, mTestHandler,
                ssidAndSecurityTypeToStandardWifiEntryKey("ssid", SECURITY_TYPE_OPEN),
                null, Arrays.asList(buildScanResult("ssid", "bssid", 0)),
                mMockWifiManager, mMockScoreCache, false /* forSavedNetworksPage */);
        entry.setListener(mMockListener);

        entry.updateScanResultInfo(Arrays.asList(buildScanResult("ssid", "bssid", 1)));
        mTestLooper.dispatchAll();

        verify(mMockListener).onUpdated();
    }

    /**
     * Tests that the level is updated after an update to the scan results
     */
    @Test
    public void testUpdateScanResultInfo_updatesLevel() {
        final StandardWifiEntry entry = new StandardWifiEntry(
                mMockInjector, mMockContext, mTestHandler,
                ssidAndSecurityTypeToStandardWifiEntryKey("ssid", SECURITY_TYPE_OPEN),
                null, Arrays.asList(buildScanResult("ssid", "bssid", 0, BAD_RSSI)),
                mMockWifiManager, mMockScoreCache, false /* forSavedNetworksPage */);

        assertThat(entry.getLevel()).isEqualTo(BAD_LEVEL);

        entry.updateScanResultInfo(Arrays.asList(buildScanResult("ssid", "bssid", 0, GOOD_RSSI)));

        assertThat(entry.getLevel()).isEqualTo(GOOD_LEVEL);
    }

    @Test
    public void testConstructor_wifiConfig_setsTitle() {
        final WifiConfiguration config = new WifiConfiguration();
        config.SSID = "\"ssid\"";
        config.setSecurityParams(WifiConfiguration.SECURITY_TYPE_EAP);
        final StandardWifiEntry entry = new StandardWifiEntry(
                mMockInjector, mMockContext, mTestHandler,
                ssidAndSecurityTypeToStandardWifiEntryKey("ssid", SECURITY_TYPE_EAP),
                Collections.singletonList(config), null, mMockWifiManager, mMockScoreCache,
                false /* forSavedNetworksPage */);

        assertThat(entry.getTitle()).isEqualTo("ssid");
    }

    @Test
    public void testConstructor_wifiConfig_setsSecurity() {
        final WifiConfiguration config = new WifiConfiguration();
        config.SSID = "\"ssid\"";
        config.setSecurityParams(WifiConfiguration.SECURITY_TYPE_EAP);
        final StandardWifiEntry entry = new StandardWifiEntry(
                mMockInjector, mMockContext, mTestHandler,
                ssidAndSecurityTypeToStandardWifiEntryKey("ssid", SECURITY_TYPE_EAP),
                Collections.singletonList(config), null, mMockWifiManager, mMockScoreCache,
                false /* forSavedNetworksPage */);

        assertThat(entry.getSecurity()).isEqualTo(WifiEntry.SECURITY_EAP);
    }

    @Test
    public void testUpdateConfig_mismatchedSsids_throwsException() {
        final WifiConfiguration config = new WifiConfiguration();
        config.SSID = "\"ssid\"";
        config.setSecurityParams(WifiConfiguration.SECURITY_TYPE_EAP);
        final StandardWifiEntry entry = new StandardWifiEntry(
                mMockInjector, mMockContext, mTestHandler,
                ssidAndSecurityTypeToStandardWifiEntryKey("ssid", SECURITY_TYPE_EAP),
                Collections.singletonList(config), null, mMockWifiManager, mMockScoreCache,
                false /* forSavedNetworksPage */);

        final WifiConfiguration config2 = new WifiConfiguration(config);
        config2.SSID = "\"ssid2\"";
        try {
            entry.updateConfig(Collections.singletonList(config2));
            fail("Updating with wrong SSID config should throw exception");
        } catch (IllegalArgumentException e) {
            // Test Succeeded
        }
    }

    @Test
    public void testUpdateConfig_mismatchedSecurity_throwsException() {
        final WifiConfiguration config = new WifiConfiguration();
        config.SSID = "\"ssid\"";
        config.setSecurityParams(WifiConfiguration.SECURITY_TYPE_PSK);
        final StandardWifiEntry entry = new StandardWifiEntry(
                mMockInjector, mMockContext, mTestHandler,
                ssidAndSecurityTypeToStandardWifiEntryKey("ssid", SECURITY_TYPE_PSK),
                Collections.singletonList(config), null, mMockWifiManager, mMockScoreCache,
                false /* forSavedNetworksPage */);

        final WifiConfiguration config2 = new WifiConfiguration(config);
        config2.setSecurityParams(WifiConfiguration.SECURITY_TYPE_EAP);
        try {
            entry.updateConfig(Collections.singletonList(config2));
            fail("Updating with wrong security config should throw exception");
        } catch (IllegalArgumentException e) {
            // Test Succeeded
        }
    }

    @Test
    public void testUpdateConfig_unsavedToSaved() {
        final ScanResult scan = buildScanResult("ssid", "bssid", 0, GOOD_RSSI);
        scan.capabilities = "EAP/SHA1";
        final StandardWifiEntry entry = new StandardWifiEntry(
                mMockInjector, mMockContext, mTestHandler,
                ssidAndSecurityTypeToStandardWifiEntryKey("ssid", SECURITY_TYPE_EAP),
                null, Arrays.asList(scan), mMockWifiManager, mMockScoreCache,
                false /* forSavedNetworksPage */);

        assertThat(entry.isSaved()).isFalse();

        final WifiConfiguration config = new WifiConfiguration();
        config.SSID = "\"ssid\"";
        config.setSecurityParams(WifiConfiguration.SECURITY_TYPE_EAP);
        config.networkId = 1;
        entry.updateConfig(Collections.singletonList(config));

        assertThat(entry.isSaved()).isTrue();
    }

    @Test
    public void testUpdateConfig_savedToUnsaved() {
        final WifiConfiguration config = new WifiConfiguration();
        config.setSecurityParams(WifiConfiguration.SECURITY_TYPE_EAP);
        config.SSID = "\"ssid\"";
        config.networkId = 1;
        final StandardWifiEntry entry = new StandardWifiEntry(
                mMockInjector, mMockContext, mTestHandler,
                ssidAndSecurityTypeToStandardWifiEntryKey("ssid", SECURITY_TYPE_EAP),
                Collections.singletonList(config), null, mMockWifiManager, mMockScoreCache,
                false /* forSavedNetworksPage */);

        assertThat(entry.isSaved()).isTrue();

        entry.updateConfig(null);

        assertThat(entry.isSaved()).isFalse();
    }

    @Test
    public void testUpdateConnectionInfo_matchingNetId_updatesConnectionInfo() {
        final WifiConfiguration config = new WifiConfiguration();
        config.setSecurityParams(WifiConfiguration.SECURITY_TYPE_EAP);
        config.SSID = "\"ssid\"";
        config.networkId = 1;
        final StandardWifiEntry entry = new StandardWifiEntry(
                mMockInjector, mMockContext, mTestHandler,
                ssidAndSecurityTypeToStandardWifiEntryKey("ssid", SECURITY_TYPE_EAP),
                Collections.singletonList(config), null, mMockWifiManager, mMockScoreCache,
                false /* forSavedNetworksPage */);
        when(mMockWifiInfo.getNetworkId()).thenReturn(1);
        when(mMockWifiInfo.getRssi()).thenReturn(GOOD_RSSI);
        when(mMockNetworkInfo.getDetailedState()).thenReturn(NetworkInfo.DetailedState.CONNECTED);

        entry.updateConnectionInfo(mMockWifiInfo, mMockNetworkInfo);

        assertThat(entry.getLevel()).isEqualTo(GOOD_LEVEL);
        assertThat(entry.getConnectedState()).isEqualTo(CONNECTED_STATE_CONNECTED);
    }

    @Test
    public void testUpdateConnectionInfo_nonMatchingNetId_doesNotUpdateConnectionInfo() {
        final WifiConfiguration config = new WifiConfiguration();
        config.setSecurityParams(WifiConfiguration.SECURITY_TYPE_EAP);
        config.SSID = "\"ssid\"";
        config.networkId = 1;
        final StandardWifiEntry entry = new StandardWifiEntry(
                mMockInjector, mMockContext, mTestHandler,
                ssidAndSecurityTypeToStandardWifiEntryKey("ssid", SECURITY_TYPE_EAP),
                Collections.singletonList(config), null, mMockWifiManager, mMockScoreCache,
                false /* forSavedNetworksPage */);
        when(mMockWifiInfo.getNetworkId()).thenReturn(2);
        when(mMockWifiInfo.getRssi()).thenReturn(-50);
        when(mMockNetworkInfo.getDetailedState()).thenReturn(NetworkInfo.DetailedState.CONNECTED);

        entry.updateConnectionInfo(mMockWifiInfo, mMockNetworkInfo);

        assertThat(entry.getLevel()).isEqualTo(WIFI_LEVEL_UNREACHABLE);
        assertThat(entry.getConnectedState()).isEqualTo(CONNECTED_STATE_DISCONNECTED);
    }

    @Test
    public void testConnect_savedNetwork_usesSavedConfig() {
        final ScanResult scan = buildScanResult("ssid", "bssid", 0, GOOD_RSSI);
        final StandardWifiEntry entry = new StandardWifiEntry(
                mMockInjector, mMockContext, mTestHandler,
                ssidAndSecurityTypeToStandardWifiEntryKey("ssid", SECURITY_TYPE_OPEN),
                null, Arrays.asList(scan), mMockWifiManager, mMockScoreCache,
                false /* forSavedNetworksPage */);
        final WifiConfiguration config = new WifiConfiguration();
        config.SSID = "\"ssid\"";
        config.networkId = 1;
        entry.updateConfig(Collections.singletonList(config));

        entry.connect(null /* ConnectCallback */);

        verify(mMockWifiManager, times(1)).connect(eq(1), any());
    }

    @Test
    public void testConnect_savedNetwork_usesSavedConfig_withOutSim() {
        final ScanResult scan = buildScanResult("ssid", "bssid", 0, GOOD_RSSI);
        final StandardWifiEntry entry = new StandardWifiEntry(
                mMockInjector, mMockContext, mTestHandler,
                ssidAndSecurityTypeToStandardWifiEntryKey("ssid", SECURITY_TYPE_EAP),
                null, Arrays.asList(scan), mMockWifiManager, mMockScoreCache,
                false /* forSavedNetworksPage */);
        final WifiConfiguration config = new WifiConfiguration();
        config.SSID = "\"ssid\"";
        config.networkId = 1;
        config.setSecurityParams(WifiConfiguration.SECURITY_TYPE_EAP);
        config.enterpriseConfig.setEapMethod(WifiEnterpriseConfig.Eap.SIM);
        config.enterpriseConfig.setPhase2Method(WifiEnterpriseConfig.Phase2.NONE);
        entry.updateConfig(Collections.singletonList(config));
        when(mSubscriptionManager.getActiveSubscriptionInfoList()).thenReturn(null);

        entry.setListener(mMockListener);
        entry.connect(mMockConnectCallback);
        mTestLooper.dispatchAll();
        verify(mMockConnectCallback, times(1))
                .onConnectResult(WifiEntry.ConnectCallback.CONNECT_STATUS_FAILURE_SIM_ABSENT);
    }

    @Test
    public void testConnect_openNetwork_callsConnect() {
        final StandardWifiEntry entry = new StandardWifiEntry(
                mMockInjector, mMockContext, mTestHandler,
                ssidAndSecurityTypeToStandardWifiEntryKey("ssid", SECURITY_TYPE_OPEN),
                null, Arrays.asList(buildScanResult("ssid", "bssid0", 0, GOOD_RSSI)),
                mMockWifiManager, mMockScoreCache, false /* forSavedNetworksPage */);

        entry.connect(null /* ConnectCallback */);

        verify(mMockWifiManager, times(1)).connect(any(), any());
    }

    @Test
    public void testConnect_unsavedSecureNetwork_returnsNoConfigFailure() {
        final ScanResult secureScan = buildScanResult("ssid", "bssid0", 0, GOOD_RSSI);
        secureScan.capabilities = "PSK";
        final StandardWifiEntry entry = new StandardWifiEntry(
                mMockInjector, mMockContext, mTestHandler,
                ssidAndSecurityTypeToStandardWifiEntryKey("ssid", SECURITY_TYPE_PSK),
                null, Arrays.asList(secureScan), mMockWifiManager, mMockScoreCache,
                false /* forSavedNetworksPage */);
        entry.setListener(mMockListener);

        entry.connect(mMockConnectCallback);
        mTestLooper.dispatchAll();

        verify(mMockConnectCallback, times(1))
                .onConnectResult(WifiEntry.ConnectCallback.CONNECT_STATUS_FAILURE_NO_CONFIG);
    }

    @Test
    public void testGetMacAddress_randomizationOn_usesRandomizedValue() {
        final String randomizedMac = "01:23:45:67:89:ab";
        final WifiConfiguration config = new WifiConfiguration();
        config.setSecurityParams(WifiConfiguration.SECURITY_TYPE_EAP);
        config.SSID = "\"ssid\"";
        config.networkId = 1;
        config.macRandomizationSetting = WifiConfiguration.RANDOMIZATION_AUTO;
        WifiConfiguration spyConfig = spy(config);
        when(spyConfig.getRandomizedMacAddress())
                .thenReturn(MacAddress.fromString(randomizedMac));

        final StandardWifiEntry entry = new StandardWifiEntry(
                mMockInjector, mMockContext, mTestHandler,
                ssidAndSecurityTypeToStandardWifiEntryKey("ssid", SECURITY_TYPE_EAP),
                Collections.singletonList(spyConfig), null, mMockWifiManager, mMockScoreCache,
                false /* forSavedNetworksPage */);

        final String macAddress = entry.getMacAddress();

        assertThat(macAddress).isEqualTo(randomizedMac);
    }

    @Test
    public void testGetMacAddress_randomizationOff_usesDeviceMac() {
        final String factoryMac = "01:23:45:67:89:ab";
        final WifiConfiguration config = new WifiConfiguration();
        config.setSecurityParams(WifiConfiguration.SECURITY_TYPE_EAP);
        config.SSID = "\"ssid\"";
        config.networkId = 1;
        config.macRandomizationSetting = WifiConfiguration.RANDOMIZATION_NONE;
        when(mMockWifiManager.getFactoryMacAddresses()).thenReturn(new String[]{factoryMac});
        final StandardWifiEntry entry = new StandardWifiEntry(
                mMockInjector, mMockContext, mTestHandler,
                ssidAndSecurityTypeToStandardWifiEntryKey("ssid", SECURITY_TYPE_EAP),
                Collections.singletonList(config), null, mMockWifiManager, mMockScoreCache,
                false /* forSavedNetworksPage */);

        final String macAddress = entry.getMacAddress();

        assertThat(macAddress).isEqualTo(factoryMac);
    }

    @Test
    public void testGetMacAddress_wifiInfoAvailable_usesWifiInfoMacAddress() {
        final int networkId = 1;
        final String factoryMac = "01:23:45:67:89:ab";
        final String wifiInfoMac = "11:23:45:67:89:ab";

        final NetworkInfo networkInfo =
                new NetworkInfo(ConnectivityManager.TYPE_WIFI, 0 /* subtype */, "WIFI", "");
        networkInfo.setDetailedState(NetworkInfo.DetailedState.CONNECTED, "", "");

        when(mMockWifiInfo.getNetworkId()).thenReturn(networkId);
        when(mMockWifiInfo.getMacAddress()).thenReturn(wifiInfoMac);
        final WifiConfiguration config = new WifiConfiguration();
        config.SSID = "\"ssid\"";
        config.networkId = networkId;
        config.macRandomizationSetting = WifiConfiguration.RANDOMIZATION_NONE;
        when(mMockWifiManager.getFactoryMacAddresses()).thenReturn(new String[]{factoryMac});
        final StandardWifiEntry entry = new StandardWifiEntry(
                mMockInjector, mMockContext, mTestHandler,
                ssidAndSecurityTypeToStandardWifiEntryKey("ssid", SECURITY_TYPE_OPEN),
                Collections.singletonList(config), null, mMockWifiManager, mMockScoreCache,
                false /* forSavedNetworksPage */);

        entry.updateConnectionInfo(mMockWifiInfo, networkInfo);

        assertThat(entry.getMacAddress()).isEqualTo(wifiInfoMac);
    }

    @Test
    public void testCanShare_securityCanShare_shouldReturnTrue() {
        final StandardWifiEntry pskWifiEntry =
                getSavedStandardWifiEntry(WifiConfiguration.SECURITY_TYPE_PSK);
        final StandardWifiEntry wepWifiEntry =
                getSavedStandardWifiEntry(WifiConfiguration.SECURITY_TYPE_WEP);
        final StandardWifiEntry openWifiEntry =
                getSavedStandardWifiEntry(WifiConfiguration.SECURITY_TYPE_OPEN);
        final StandardWifiEntry saeWifiEntry =
                getSavedStandardWifiEntry(WifiConfiguration.SECURITY_TYPE_SAE);
        final StandardWifiEntry oweWifiEntry =
                getSavedStandardWifiEntry(WifiConfiguration.SECURITY_TYPE_OWE);

        assertThat(pskWifiEntry.canShare()).isTrue();
        assertThat(wepWifiEntry.canShare()).isTrue();
        assertThat(openWifiEntry.canShare()).isTrue();
        assertThat(saeWifiEntry.canShare()).isTrue();
        assertThat(oweWifiEntry.canShare()).isTrue();
    }

    @Test
    public void testCanShare_securityCanNotShare_shouldReturnFalse() {
        final StandardWifiEntry eapWifiEntry =
                getSavedStandardWifiEntry(WifiConfiguration.SECURITY_TYPE_EAP);
        final StandardWifiEntry eapSuiteBWifiEntry =
                getSavedStandardWifiEntry(
                        WifiConfiguration.SECURITY_TYPE_EAP_WPA3_ENTERPRISE_192_BIT);

        assertThat(eapWifiEntry.canShare()).isFalse();
        assertThat(eapSuiteBWifiEntry.canShare()).isFalse();
    }

    @Test
    public void testCanEasyConnect_deviceNotSupported_shouldReturnFalse() {
        when(mMockWifiManager.isEasyConnectSupported()).thenReturn(false);
        final ScanResult pskScanResult = buildScanResult("ssid", "bssid", 0, GOOD_RSSI);
        pskScanResult.capabilities = "PSK";

        final StandardWifiEntry pskWifiEntry = new StandardWifiEntry(
                mMockInjector, mMockContext, mTestHandler,
                ssidAndSecurityTypeToStandardWifiEntryKey(pskScanResult.SSID, SECURITY_TYPE_PSK),
                null, Arrays.asList(pskScanResult), mMockWifiManager, mMockScoreCache,
                false /* forSavedNetworksPage */);

        assertThat(pskWifiEntry.canEasyConnect()).isFalse();
    }

    @Test
    public void testCanEasyConnect_securityCanEasyConnect_shouldReturnTrue() {
        when(mMockWifiManager.isEasyConnectSupported()).thenReturn(true);
        final StandardWifiEntry pskWifiEntry =
                getSavedStandardWifiEntry(WifiConfiguration.SECURITY_TYPE_PSK);
        final StandardWifiEntry saeWifiEntry =
                getSavedStandardWifiEntry(WifiConfiguration.SECURITY_TYPE_SAE);

        assertThat(pskWifiEntry.canEasyConnect()).isTrue();
        assertThat(saeWifiEntry.canEasyConnect()).isTrue();
    }

    @Test
    public void testCanEasyConnect_securityCanNotEasyConnect_shouldReturnFalse() {
        when(mMockWifiManager.isEasyConnectSupported()).thenReturn(true);
        final StandardWifiEntry openWifiEntry =
                getSavedStandardWifiEntry(WifiConfiguration.SECURITY_TYPE_OPEN);
        final StandardWifiEntry wepWifiEntry =
                getSavedStandardWifiEntry(WifiConfiguration.SECURITY_TYPE_WEP);
        final StandardWifiEntry wpa2EnterpriseWifiEntry =
                getSavedStandardWifiEntry(WifiConfiguration.SECURITY_TYPE_EAP);
        final StandardWifiEntry wpa3EnterpriseWifiEntry =
                getSavedStandardWifiEntry(
                        WifiConfiguration.SECURITY_TYPE_EAP_WPA3_ENTERPRISE_192_BIT);
        final StandardWifiEntry oweWifiEntry =
                getSavedStandardWifiEntry(WifiConfiguration.SECURITY_TYPE_OWE);

        assertThat(openWifiEntry.canEasyConnect()).isFalse();
        assertThat(wepWifiEntry.canEasyConnect()).isFalse();
        assertThat(wpa2EnterpriseWifiEntry.canEasyConnect()).isFalse();
        assertThat(wpa3EnterpriseWifiEntry.canEasyConnect()).isFalse();
        assertThat(oweWifiEntry.canEasyConnect()).isFalse();
    }

    @Test
    public void testUpdateLinkProperties_updatesConnectedInfo() {
        final WifiConfiguration config = new WifiConfiguration();
        config.setSecurityParams(WifiConfiguration.SECURITY_TYPE_EAP);
        config.SSID = "\"ssid\"";
        config.networkId = 1;
        final StandardWifiEntry entry = new StandardWifiEntry(
                mMockInjector, mMockContext, mTestHandler,
                ssidAndSecurityTypeToStandardWifiEntryKey("ssid", SECURITY_TYPE_EAP),
                Collections.singletonList(config), null, mMockWifiManager, mMockScoreCache,
                false /* forSavedNetworksPage */);
        when(mMockWifiInfo.getNetworkId()).thenReturn(1);
        when(mMockWifiInfo.getRssi()).thenReturn(GOOD_RSSI);
        when(mMockNetworkInfo.getDetailedState()).thenReturn(NetworkInfo.DetailedState.CONNECTED);
        entry.updateConnectionInfo(mMockWifiInfo, mMockNetworkInfo);

        entry.updateLinkProperties(new LinkProperties());

        assertThat(entry.getConnectedInfo()).isNotNull();
    }

    private StandardWifiEntry getSavedStandardWifiEntry(int wifiConfigurationSecureType) {
        final WifiConfiguration config = new WifiConfiguration();
        config.SSID = "\"ssid\"";
        config.setSecurityParams(wifiConfigurationSecureType);
        return new StandardWifiEntry(
                mMockInjector, mMockContext, mTestHandler,
                new StandardWifiEntryKey(config), Collections.singletonList(config), null,
                mMockWifiManager, mMockScoreCache, false /* forSavedNetworksPage */);
    }

    @Test
    public void testGetSummary_connectedWifiNetwork_showsConnected() {
        final int networkId = 1;
        final String summarySeparator = " / ";
        final String[] wifiStatusArray = new String[]{"", "Scanning", "Connecting",
                "Authenticating", "Obtaining IP address", "Connected"};

        final Resources mockResources = mock(Resources.class);
        when(mMockContext.getResources()).thenReturn(mockResources);
        when(mockResources.getString(R.string.wifitrackerlib_summary_separator))
                .thenReturn(summarySeparator);
        when(mockResources.getStringArray(R.array.wifitrackerlib_wifi_status))
                .thenReturn(wifiStatusArray);
        final ConnectivityManager mockConnectivityManager = mock(ConnectivityManager.class);
        final NetworkCapabilities networkCapabilities = new NetworkCapabilities.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED).build();
        when(mMockContext.getSystemService(Context.CONNECTIVITY_SERVICE))
                .thenReturn(mockConnectivityManager);

        final WifiInfo wifiInfo = new WifiInfo.Builder().setNetworkId(networkId).build();
        final NetworkInfo networkInfo =
                new NetworkInfo(ConnectivityManager.TYPE_WIFI, 0 /* subtype */, "WIFI", "");
        networkInfo.setDetailedState(NetworkInfo.DetailedState.CONNECTED, "", "");

        final WifiConfiguration config = new WifiConfiguration();
        config.SSID = "\"ssid\"";
        config.networkId = networkId;
        final StandardWifiEntry entry = new StandardWifiEntry(
                mMockInjector, mMockContext, mTestHandler,
                ssidAndSecurityTypeToStandardWifiEntryKey("ssid", SECURITY_TYPE_OPEN),
                Collections.singletonList(config), null, mMockWifiManager, mMockScoreCache,
                false /* forSavedNetworksPage */);

        entry.updateConnectionInfo(wifiInfo, networkInfo);
        entry.updateNetworkCapabilities(networkCapabilities);
        entry.setIsDefaultNetwork(true);

        assertThat(entry.getSummary()).isEqualTo("Connected");
    }

    @Test
    public void testGetSummary_connectedButNotDefault_doesNotShowConnected() {
        final int networkId = 1;
        final String summarySeparator = " / ";
        final String[] wifiStatusArray = new String[]{"", "Scanning", "Connecting",
                "Authenticating", "Obtaining IP address", "Connected"};

        final Resources mockResources = mock(Resources.class);
        when(mMockContext.getResources()).thenReturn(mockResources);
        when(mockResources.getString(R.string.wifitrackerlib_summary_separator))
                .thenReturn(summarySeparator);
        when(mockResources.getStringArray(R.array.wifitrackerlib_wifi_status))
                .thenReturn(wifiStatusArray);
        final ConnectivityManager mockConnectivityManager = mock(ConnectivityManager.class);
        final NetworkCapabilities networkCapabilities = new NetworkCapabilities.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED).build();
        when(mMockContext.getSystemService(Context.CONNECTIVITY_SERVICE))
                .thenReturn(mockConnectivityManager);

        final WifiInfo wifiInfo = new WifiInfo.Builder().setNetworkId(networkId).build();
        final NetworkInfo networkInfo =
                new NetworkInfo(ConnectivityManager.TYPE_WIFI, 0 /* subtype */, "WIFI", "");
        networkInfo.setDetailedState(NetworkInfo.DetailedState.CONNECTED, "", "");

        final WifiConfiguration config = new WifiConfiguration();
        config.SSID = "\"ssid\"";
        config.networkId = networkId;
        final StandardWifiEntry entry = new StandardWifiEntry(
                mMockInjector, mMockContext, mTestHandler,
                ssidAndSecurityTypeToStandardWifiEntryKey("ssid", SECURITY_TYPE_OPEN),
                Collections.singletonList(config), null, mMockWifiManager, mMockScoreCache,
                false /* forSavedNetworksPage */);

        entry.updateConnectionInfo(wifiInfo, networkInfo);
        entry.updateNetworkCapabilities(networkCapabilities);
        entry.setIsDefaultNetwork(false);

        assertThat(entry.getSummary()).isEqualTo("");
    }

    @Test
    public void testShouldShowXLevelIcon_unvalidatedOrNotDefault_returnsTrue() {
        final int networkId = 1;
        when(mMockContext.getSystemService(Context.CONNECTIVITY_SERVICE))
                .thenReturn(mMockConnectivityManager);
        final NetworkCapabilities networkCapabilities = new NetworkCapabilities.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED).build();
        final WifiInfo wifiInfo = new WifiInfo.Builder().setNetworkId(networkId).build();
        final NetworkInfo networkInfo =
                new NetworkInfo(ConnectivityManager.TYPE_WIFI, 0 /* subtype */, "WIFI", "");
        networkInfo.setDetailedState(NetworkInfo.DetailedState.CONNECTED, "", "");
        final WifiConfiguration config = new WifiConfiguration();
        config.SSID = "\"ssid\"";
        config.networkId = networkId;

        final StandardWifiEntry entry = new StandardWifiEntry(
                mMockInjector, mMockContext, mTestHandler,
                ssidAndSecurityTypeToStandardWifiEntryKey("ssid", SECURITY_TYPE_OPEN),
                Collections.singletonList(config), null, mMockWifiManager, mMockScoreCache,
                false /* forSavedNetworksPage */);

        // Disconnected should return false;
        assertThat(entry.shouldShowXLevelIcon()).isEqualTo(false);

        // Not validated, Not Default
        entry.updateConnectionInfo(wifiInfo, networkInfo);

        assertThat(entry.shouldShowXLevelIcon()).isEqualTo(true);

        // Not Validated, Default
        entry.setIsDefaultNetwork(true);

        assertThat(entry.shouldShowXLevelIcon()).isEqualTo(true);

        // Validated, Default
        entry.updateNetworkCapabilities(networkCapabilities);

        assertThat(entry.shouldShowXLevelIcon()).isEqualTo(false);

        // Validated, Not Default
        entry.setIsDefaultNetwork(false);

        assertThat(entry.shouldShowXLevelIcon()).isEqualTo(true);
    }

    @Test
    public void testGetSecurityString_pskAndSae_getWpaWpa2Wpa3Personal() {
        final String wifiSecurityShortWpaWpa2Wpa3 = "WPA/WPA2/WPA3";
        final Resources mockResources = mock(Resources.class);
        when(mMockContext.getResources()).thenReturn(mockResources);
        when(mockResources.getString(R.string.wifitrackerlib_wifi_security_short_wpa_wpa2_wpa3))
                .thenReturn(wifiSecurityShortWpaWpa2Wpa3);

        WifiConfiguration pskConfig = new WifiConfiguration();
        pskConfig.SSID = "\"ssid\"";
        pskConfig.setSecurityParams(WifiConfiguration.SECURITY_TYPE_PSK);
        WifiConfiguration saeConfig = new WifiConfiguration();
        saeConfig.SSID = "\"ssid\"";
        saeConfig.setSecurityParams(WifiConfiguration.SECURITY_TYPE_SAE);

        ScanResult pskScan = buildScanResult("ssid", "bssid0", 0, GOOD_RSSI);
        pskScan.capabilities = "PSK";
        ScanResult saeScan = buildScanResult("ssid", "bssid0", 0, BAD_RSSI);
        saeScan.capabilities = "SAE";

        StandardWifiEntry entry = new StandardWifiEntry(
                mMockInjector, mMockContext, mTestHandler,
                ssidAndSecurityTypeToStandardWifiEntryKey("ssid", SECURITY_TYPE_PSK),
                Arrays.asList(pskConfig, saeConfig), Arrays.asList(pskScan, saeScan),
                mMockWifiManager, mMockScoreCache, false /* forSavedNetworksPage */);

        assertThat(entry.getSecurityString(true /* concise */))
                .isEqualTo(wifiSecurityShortWpaWpa2Wpa3);
    }

    @Test
    public void testGetSecurityString_connected_getConnectionSecurityType() {
        final String wifiSecurityShortWpaWpa2 = "WPA/WPA2";
        final String wifiSecurityShortWpa3 = "WPA3";
        final Resources mockResources = mock(Resources.class);
        when(mMockContext.getResources()).thenReturn(mockResources);
        when(mockResources.getString(R.string.wifitrackerlib_wifi_security_short_wpa_wpa2))
                .thenReturn(wifiSecurityShortWpaWpa2);
        when(mockResources.getString(R.string.wifitrackerlib_wifi_security_short_sae))
                .thenReturn(wifiSecurityShortWpa3);

        WifiConfiguration pskConfig = new WifiConfiguration();
        pskConfig.networkId = 1;
        pskConfig.SSID = "\"ssid\"";
        pskConfig.setSecurityParams(WifiConfiguration.SECURITY_TYPE_PSK);
        WifiConfiguration saeConfig = new WifiConfiguration();
        saeConfig.networkId = 1;
        saeConfig.SSID = "\"ssid\"";
        saeConfig.setSecurityParams(WifiConfiguration.SECURITY_TYPE_SAE);

        ScanResult pskScan = buildScanResult("ssid", "bssid0", 0, GOOD_RSSI);
        pskScan.capabilities = "PSK";
        ScanResult saeScan = buildScanResult("ssid", "bssid0", 0, BAD_RSSI);
        saeScan.capabilities = "SAE";

        when(mMockWifiInfo.getNetworkId()).thenReturn(1);
        when(mMockWifiInfo.getRssi()).thenReturn(GOOD_RSSI);
        when(mMockNetworkInfo.getDetailedState()).thenReturn(NetworkInfo.DetailedState.CONNECTED);

        StandardWifiEntry entry = new StandardWifiEntry(
                mMockInjector, mMockContext, mTestHandler,
                ssidAndSecurityTypeToStandardWifiEntryKey("ssid", SECURITY_TYPE_PSK),
                Arrays.asList(pskConfig, saeConfig), Arrays.asList(pskScan, saeScan),
                mMockWifiManager, mMockScoreCache, false /* forSavedNetworksPage */);

        when(mMockWifiInfo.getCurrentSecurityType()).thenReturn(SECURITY_TYPE_PSK);
        entry.updateConnectionInfo(mMockWifiInfo, mMockNetworkInfo);

        // Both PSK and SAE in range, but connected to PSK so show PSK security string
        assertThat(entry.getSecurityString(true /* concise */))
                .isEqualTo(wifiSecurityShortWpaWpa2);

        when(mMockWifiInfo.getCurrentSecurityType()).thenReturn(SECURITY_TYPE_SAE);
        entry.updateConnectionInfo(mMockWifiInfo, mMockNetworkInfo);

        // Both PSK and SAE in range, but connected to SAE so show SAE security string
        assertThat(entry.getSecurityString(true /* concise */))
                .isEqualTo(wifiSecurityShortWpa3);
    }

    @Test
    public void testGetSecurityString_eapAndEapWpa3_getWpaWpa2Wpa3Enterprise() {
        final String wifiSecurityEapWpaWpa2Wpa3 = "WPA/WPA2/WPA3-Enterprise";
        final Resources mockResources = mock(Resources.class);
        when(mMockContext.getResources()).thenReturn(mockResources);
        when(mockResources.getString(R.string.wifitrackerlib_wifi_security_short_eap_wpa_wpa2_wpa3))
                .thenReturn(wifiSecurityEapWpaWpa2Wpa3);

        WifiConfiguration eapConfig = new WifiConfiguration();
        eapConfig.SSID = "\"ssid\"";
        eapConfig.setSecurityParams(WifiConfiguration.SECURITY_TYPE_EAP);
        WifiConfiguration eapWpa3Config = new WifiConfiguration();
        eapWpa3Config.SSID = "\"ssid\"";
        eapWpa3Config.setSecurityParams(WifiConfiguration.SECURITY_TYPE_EAP_WPA3_ENTERPRISE);

        final ScanResult eapScan = buildScanResult("ssid", "bssid", 0, GOOD_RSSI);
        eapScan.capabilities = "[RSN-EAP/SHA1]";
        final ScanResult eapWpa3Scan = buildScanResult("ssid", "bssid", 0, GOOD_RSSI);
        eapWpa3Scan.capabilities = "[RSN-EAP/SHA256][MFPR][MFPC]";

        StandardWifiEntry entry = new StandardWifiEntry(
                mMockInjector, mMockContext, mTestHandler,
                ssidAndSecurityTypeToStandardWifiEntryKey("ssid", SECURITY_TYPE_EAP),
                Arrays.asList(eapConfig, eapWpa3Config), Arrays.asList(eapScan, eapWpa3Scan),
                mMockWifiManager, mMockScoreCache, false /* forSavedNetworksPage */);

        assertThat(entry.getSecurityString(true /* concise */))
                .isEqualTo(wifiSecurityEapWpaWpa2Wpa3);
    }

    @Test
    public void testGetMeteredChoice_afterSetMeteredChoice_getCorrectValue() {
        StandardWifiEntry entry =
                getSavedStandardWifiEntry(WifiConfiguration.SECURITY_TYPE_PSK);

        entry.setMeteredChoice(WifiEntry.METERED_CHOICE_METERED);

        assertThat(entry.getMeteredChoice()).isEqualTo(WifiEntry.METERED_CHOICE_METERED);
    }

    @Test
    public void testCanSignIn_captivePortalCapability_returnsTrue() {
        final StandardWifiEntry entry = new StandardWifiEntry(
                mMockInjector, mMockContext, mTestHandler,
                ssidAndSecurityTypeToStandardWifiEntryKey("ssid", SECURITY_TYPE_OPEN),
                null, Arrays.asList(
                        buildScanResult("ssid", "bssid0", 0, GOOD_RSSI)),
                mMockWifiManager, mMockScoreCache, false /* forSavedNetworksPage */);
        NetworkCapabilities captivePortalCapabilities = new NetworkCapabilities.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL).build();
        entry.updateNetworkCapabilities(captivePortalCapabilities);

        assertThat(entry.canSignIn()).isTrue();
    }

    @Test
    public void testUpdateNetworkCapabilities_userConnect_autoOpenCaptivePortalOnce() {
        when(mMockContext.getSystemService(Context.CONNECTIVITY_SERVICE))
                .thenReturn(mMockConnectivityManager);
        final StandardWifiEntry entry = new StandardWifiEntry(
                mMockInjector, mMockContext, mTestHandler,
                ssidAndSecurityTypeToStandardWifiEntryKey("ssid", SECURITY_TYPE_OPEN),
                null, Arrays.asList(
                        buildScanResult("ssid", "bssid0", 0, GOOD_RSSI)),
                mMockWifiManager, mMockScoreCache, false /* forSavedNetworksPage */);
        NetworkCapabilities captivePortalCapabilities = new NetworkCapabilities.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL).build();

        // Simulate user tapping on the network and receiving captive portal capabilities.
        // This should trigger the captive portal app.
        entry.connect(null /* callback */);
        entry.updateNetworkCapabilities(captivePortalCapabilities);

        verify(mMockConnectivityManager, times(1)).startCaptivePortalApp(any());

        // Update network capabilities again. This should not trigger the captive portal app.
        entry.updateNetworkCapabilities(captivePortalCapabilities);

        verify(mMockConnectivityManager, times(1)).startCaptivePortalApp(any());
    }

    @Test
    public void testShouldEditBeforeConnect_nullWifiConfig_returnFalse() {
        StandardWifiEntry entry = new StandardWifiEntry(
                mMockInjector, mMockContext, mTestHandler,
                ssidAndSecurityTypeToStandardWifiEntryKey("ssid", SECURITY_TYPE_EAP),
                mMockWifiManager, mMockScoreCache, false /* forSavedNetworksPage */);

        assertThat(entry.shouldEditBeforeConnect()).isFalse();
    }

    @Test
    public void testShouldEditBeforeConnect_openNetwork_returnFalse() {
        // Test open networks.
        WifiConfiguration wifiConfig = new WifiConfiguration();
        wifiConfig.SSID = "\"ssid\"";
        wifiConfig.setSecurityParams(WifiConfiguration.SECURITY_TYPE_OPEN);
        StandardWifiEntry entry = new StandardWifiEntry(
                mMockInjector, mMockContext, mTestHandler,
                ssidAndSecurityTypeToStandardWifiEntryKey("ssid", SECURITY_TYPE_OPEN),
                Collections.singletonList(wifiConfig), null, mMockWifiManager, mMockScoreCache,
                false /* forSavedNetworksPage */);

        assertThat(entry.shouldEditBeforeConnect()).isFalse();

        // Test enhanced open networks.
        wifiConfig.setSecurityParams(WifiConfiguration.SECURITY_TYPE_OWE);
        entry = new StandardWifiEntry(
                mMockInjector, mMockContext, mTestHandler,
                ssidAndSecurityTypeToStandardWifiEntryKey("ssid", SECURITY_TYPE_OWE),
                Collections.singletonList(wifiConfig), null, mMockWifiManager, mMockScoreCache,
                false /* forSavedNetworksPage */);

        assertThat(entry.shouldEditBeforeConnect()).isFalse();
    }

    @Test
    public void testShouldEditBeforeConnect_authenticationFailure_returnTrue() {
        // Test DISABLED_AUTHENTICATION_FAILURE.
        WifiConfiguration wifiConfig = spy(new WifiConfiguration());
        wifiConfig.SSID = "\"ssid\"";
        wifiConfig.setSecurityParams(WifiConfiguration.SECURITY_TYPE_PSK);
        StandardWifiEntry entry = new StandardWifiEntry(
                mMockInjector, mMockContext, mTestHandler,
                ssidAndSecurityTypeToStandardWifiEntryKey("ssid", SECURITY_TYPE_PSK),
                Collections.singletonList(wifiConfig), null, mMockWifiManager, mMockScoreCache,
                false /* forSavedNetworksPage */);
        NetworkSelectionStatus.Builder statusBuilder = new NetworkSelectionStatus.Builder();
        NetworkSelectionStatus networkSelectionStatus = spy(statusBuilder.setNetworkSelectionStatus(
                NETWORK_SELECTION_TEMPORARY_DISABLED)
                .setNetworkSelectionDisableReason(
                        DISABLED_AUTHENTICATION_FAILURE)
                .build());
        doReturn(1).when(networkSelectionStatus).getDisableReasonCounter(
                DISABLED_AUTHENTICATION_FAILURE);
        doReturn(true).when(networkSelectionStatus).hasEverConnected();
        doReturn(networkSelectionStatus).when(wifiConfig).getNetworkSelectionStatus();

        assertThat(entry.shouldEditBeforeConnect()).isTrue();

        // Test DISABLED_BY_WRONG_PASSWORD.
        networkSelectionStatus = spy(statusBuilder.setNetworkSelectionStatus(
                NETWORK_SELECTION_PERMANENTLY_DISABLED)
                .setNetworkSelectionDisableReason(DISABLED_BY_WRONG_PASSWORD)
                .build());
        doReturn(1).when(networkSelectionStatus).getDisableReasonCounter(
                DISABLED_BY_WRONG_PASSWORD);

        assertThat(entry.shouldEditBeforeConnect()).isTrue();

        // Test DISABLED_AUTHENTICATION_NO_CREDENTIALS.
        networkSelectionStatus = spy(statusBuilder.setNetworkSelectionStatus(
                NETWORK_SELECTION_PERMANENTLY_DISABLED)
                .setNetworkSelectionDisableReason(DISABLED_AUTHENTICATION_NO_CREDENTIALS)
                .build());
        doReturn(1).when(networkSelectionStatus).getDisableReasonCounter(
                DISABLED_AUTHENTICATION_NO_CREDENTIALS);

        assertThat(entry.shouldEditBeforeConnect()).isTrue();
    }

    @Test
    public void testGetSpeed_cacheUpdated_speedValueChanges() {
        when(mMockScoredNetwork.calculateBadge(GOOD_RSSI)).thenReturn(SPEED_FAST);
        final StandardWifiEntry entry = new StandardWifiEntry(
                mMockInjector, mMockContext, mTestHandler,
                ssidAndSecurityTypeToStandardWifiEntryKey("ssid", SECURITY_TYPE_OPEN),
                null, Collections.singletonList(buildScanResult("ssid", "bssid0", 0, GOOD_RSSI)),
                mMockWifiManager, mMockScoreCache, false /* forSavedNetworksPage */);

        when(mMockScoredNetwork.calculateBadge(GOOD_RSSI)).thenReturn(SPEED_SLOW);
        entry.onScoreCacheUpdated();

        assertThat(entry.getSpeed()).isEqualTo(SPEED_SLOW);
    }

    @Test
    public void testGetSpeed_connected_useWifiInfoRssiForSpeed() {
        when(mMockScoredNetwork.calculateBadge(BAD_RSSI)).thenReturn(SPEED_SLOW);
        when(mMockScoredNetwork.calculateBadge(GOOD_RSSI)).thenReturn(SPEED_FAST);
        when(mMockWifiInfo.getNetworkId()).thenReturn(1);
        when(mMockWifiInfo.getRssi()).thenReturn(BAD_RSSI);
        when(mMockWifiInfo.getSSID()).thenReturn("\"ssid\"");
        when(mMockWifiInfo.getBSSID()).thenReturn("01:23:45:67:89:ab");
        final WifiConfiguration config = new WifiConfiguration();
        config.SSID = "\"ssid\"";
        config.networkId = 1;
        final StandardWifiEntry entry = new StandardWifiEntry(
                mMockInjector, mMockContext, mTestHandler,
                ssidAndSecurityTypeToStandardWifiEntryKey("ssid", SECURITY_TYPE_OPEN),
                Collections.singletonList(config), Collections.singletonList(
                        buildScanResult("ssid", "bssid0", 0, GOOD_RSSI)), mMockWifiManager,
                mMockScoreCache, false /* forSavedNetworksPage */);

        entry.updateConnectionInfo(mMockWifiInfo, mMockNetworkInfo);

        assertThat(entry.getSpeed()).isEqualTo(SPEED_SLOW);
    }

    @Test
    public void testGetSpeed_newScanResults_speedValueChanges() {
        when(mMockScoredNetwork.calculateBadge(BAD_RSSI)).thenReturn(SPEED_SLOW);
        when(mMockScoredNetwork.calculateBadge(GOOD_RSSI)).thenReturn(SPEED_FAST);
        final StandardWifiEntry entry = new StandardWifiEntry(
                mMockInjector, mMockContext, mTestHandler,
                ssidAndSecurityTypeToStandardWifiEntryKey("ssid", SECURITY_TYPE_OPEN),
                null, Collections.singletonList(buildScanResult("ssid", "bssid0", 0, GOOD_RSSI)),
                mMockWifiManager, mMockScoreCache, false /* forSavedNetworksPage */);

        entry.updateScanResultInfo(Collections.singletonList(
                buildScanResult("ssid", "bssid0", 0, BAD_RSSI)));

        assertThat(entry.getSpeed()).isEqualTo(SPEED_SLOW);
    }

    @Test
    public void testCanConnect_nonEapMethod_returnTrueIfReachable() {
        ScanResult scan = buildScanResult("ssid", "bssid0", 0, GOOD_RSSI);
        StandardWifiEntry spyEntry = spy(new StandardWifiEntry(
                mMockInjector, mMockContext, mTestHandler,
                ssidAndSecurityTypeToStandardWifiEntryKey("ssid", SECURITY_TYPE_OPEN),
                null, Arrays.asList(buildScanResult("ssid", "bssid0", 0, GOOD_RSSI)),
                mMockWifiManager, mMockScoreCache, false /* forSavedNetworksPage */));
        when(spyEntry.getConnectedState()).thenReturn(CONNECTED_STATE_DISCONNECTED);

        assertThat(spyEntry.canConnect()).isEqualTo(true);

        scan.capabilities = "OWE";
        spyEntry = spy(new StandardWifiEntry(
                mMockInjector, mMockContext, mTestHandler,
                ssidAndSecurityTypeToStandardWifiEntryKey("ssid", SECURITY_TYPE_OWE),
                null, Collections.singletonList(scan),
                mMockWifiManager, mMockScoreCache, false /* forSavedNetworksPage */));
        when(spyEntry.getConnectedState()).thenReturn(CONNECTED_STATE_DISCONNECTED);

        assertThat(spyEntry.canConnect()).isEqualTo(true);

        scan.capabilities = "WEP";
        spyEntry = spy(new StandardWifiEntry(
                mMockInjector, mMockContext, mTestHandler,
                ssidAndSecurityTypeToStandardWifiEntryKey("ssid", SECURITY_TYPE_WEP),
                null, Collections.singletonList(scan),
                mMockWifiManager, mMockScoreCache, false /* forSavedNetworksPage */));
        when(spyEntry.getConnectedState()).thenReturn(CONNECTED_STATE_DISCONNECTED);

        assertThat(spyEntry.canConnect()).isEqualTo(true);

        scan.capabilities = "PSK";
        spyEntry = spy(new StandardWifiEntry(
                mMockInjector, mMockContext, mTestHandler,
                ssidAndSecurityTypeToStandardWifiEntryKey("ssid", SECURITY_TYPE_PSK),
                null, Collections.singletonList(scan),
                mMockWifiManager, mMockScoreCache, false /* forSavedNetworksPage */));
        when(spyEntry.getConnectedState()).thenReturn(CONNECTED_STATE_DISCONNECTED);

        assertThat(spyEntry.canConnect()).isEqualTo(true);

        scan.capabilities = "SAE";
        spyEntry = spy(new StandardWifiEntry(
                mMockInjector, mMockContext, mTestHandler,
                ssidAndSecurityTypeToStandardWifiEntryKey("ssid", SECURITY_TYPE_SAE),
                null, Collections.singletonList(scan),
                mMockWifiManager, mMockScoreCache, false /* forSavedNetworksPage */));
        when(spyEntry.getConnectedState()).thenReturn(CONNECTED_STATE_DISCONNECTED);

        assertThat(spyEntry.canConnect()).isEqualTo(true);
    }

    @Test
    public void testCanConnect_nonSimMethod_returnTrueIfReachable() {
        WifiConfiguration config = new WifiConfiguration();
        config.SSID = "\"ssid\"";
        WifiEnterpriseConfig mockWifiEnterpriseConfig = mock(WifiEnterpriseConfig.class);
        when(mockWifiEnterpriseConfig.isAuthenticationSimBased()).thenReturn(false);
        config.enterpriseConfig = mockWifiEnterpriseConfig;
        config.setSecurityParams(WifiConfiguration.SECURITY_TYPE_EAP);
        ScanResult scan = buildScanResult("ssid", "bssid0", 0, GOOD_RSSI);
        scan.capabilities = "EAP/SHA1";
        StandardWifiEntry spyEntry = spy(new StandardWifiEntry(
                mMockInjector, mMockContext, mTestHandler,
                ssidAndSecurityTypeToStandardWifiEntryKey("ssid", SECURITY_TYPE_EAP),
                Collections.singletonList(config), Collections.singletonList(scan),
                mMockWifiManager, mMockScoreCache, false /* forSavedNetworksPage */));
        when(spyEntry.getConnectedState()).thenReturn(CONNECTED_STATE_DISCONNECTED);

        assertThat(spyEntry.canConnect()).isEqualTo(true);
    }

    @Test
    public void testCanConnect_unknownCarrierId_returnTrueIfActiveSubscriptionAvailable() {
        WifiConfiguration config = new WifiConfiguration();
        config.SSID = "\"ssid\"";
        config.carrierId = TelephonyManager.UNKNOWN_CARRIER_ID;
        WifiEnterpriseConfig mockWifiEnterpriseConfig = mock(WifiEnterpriseConfig.class);
        when(mockWifiEnterpriseConfig.isAuthenticationSimBased()).thenReturn(true);
        config.enterpriseConfig = mockWifiEnterpriseConfig;
        config.setSecurityParams(WifiConfiguration.SECURITY_TYPE_EAP);
        ScanResult scan = buildScanResult("ssid", "bssid0", 0, GOOD_RSSI);
        scan.capabilities = "EAP/SHA1";
        StandardWifiEntry spyEntry = spy(new StandardWifiEntry(
                mMockInjector, mMockContext, mTestHandler,
                ssidAndSecurityTypeToStandardWifiEntryKey("ssid", SECURITY_TYPE_EAP),
                Collections.singletonList(config), Collections.singletonList(scan),
                mMockWifiManager, mMockScoreCache, false /* forSavedNetworksPage */));
        when(spyEntry.getConnectedState()).thenReturn(CONNECTED_STATE_DISCONNECTED);
        when(mSubscriptionManager.getActiveSubscriptionInfoList())
                .thenReturn(Arrays.asList(mock(SubscriptionInfo.class)));

        assertThat(spyEntry.canConnect()).isEqualTo(true);
    }

    @Test
    public void testCanConnect_specifiedCarrierIdMatched_returnTrue() {
        WifiConfiguration config = new WifiConfiguration();
        config.SSID = "\"ssid\"";
        int carrierId = 6;
        config.carrierId = carrierId;
        WifiEnterpriseConfig mockWifiEnterpriseConfig = mock(WifiEnterpriseConfig.class);
        when(mockWifiEnterpriseConfig.isAuthenticationSimBased()).thenReturn(true);
        config.enterpriseConfig = mockWifiEnterpriseConfig;
        config.setSecurityParams(WifiConfiguration.SECURITY_TYPE_EAP);
        ScanResult scan = buildScanResult("ssid", "bssid0", 0, GOOD_RSSI);
        scan.capabilities = "EAP/SHA1";
        StandardWifiEntry spyEntry = spy(new StandardWifiEntry(
                mMockInjector, mMockContext, mTestHandler,
                ssidAndSecurityTypeToStandardWifiEntryKey("ssid", SECURITY_TYPE_EAP),
                Collections.singletonList(config), Collections.singletonList(scan),
                mMockWifiManager, mMockScoreCache, false /* forSavedNetworksPage */));
        when(spyEntry.getConnectedState()).thenReturn(CONNECTED_STATE_DISCONNECTED);
        SubscriptionInfo mockSubscriptionInfo = mock(SubscriptionInfo.class);
        when(mockSubscriptionInfo.getCarrierId()).thenReturn(carrierId);
        when(mSubscriptionManager.getActiveSubscriptionInfoList())
                .thenReturn(Arrays.asList(mockSubscriptionInfo));

        assertThat(spyEntry.canConnect()).isEqualTo(true);
    }

    @Test
    public void testCanConnect_specifiedCarrierIdNotMatched_returnFalse() {
        WifiConfiguration config = new WifiConfiguration();
        config.SSID = "\"ssid\"";
        int specifiedCarrierId = 6;
        int simCarrierId = 7;
        config.carrierId = specifiedCarrierId;
        WifiEnterpriseConfig mockWifiEnterpriseConfig = mock(WifiEnterpriseConfig.class);
        when(mockWifiEnterpriseConfig.isAuthenticationSimBased()).thenReturn(true);
        config.enterpriseConfig = mockWifiEnterpriseConfig;
        config.setSecurityParams(WifiConfiguration.SECURITY_TYPE_EAP);
        ScanResult scan = buildScanResult("ssid", "bssid0", 0, GOOD_RSSI);
        scan.capabilities = "EAP/SHA1";
        StandardWifiEntry spyEntry = spy(new StandardWifiEntry(
                mMockInjector, mMockContext, mTestHandler,
                ssidAndSecurityTypeToStandardWifiEntryKey("ssid", SECURITY_TYPE_EAP),
                Collections.singletonList(config), Collections.singletonList(scan),
                mMockWifiManager, mMockScoreCache, false /* forSavedNetworksPage */));
        when(spyEntry.getConnectedState()).thenReturn(CONNECTED_STATE_DISCONNECTED);
        SubscriptionInfo mockSubscriptionInfo = mock(SubscriptionInfo.class);
        when(mockSubscriptionInfo.getCarrierId()).thenReturn(simCarrierId);
        when(mSubscriptionManager.getActiveSubscriptionInfoList())
                .thenReturn(Arrays.asList(mockSubscriptionInfo));

        assertThat(spyEntry.canConnect()).isEqualTo(false);
    }


    @Test
    public void testStandardWifiEntryKeyConstructor_fromConfig_matchesFromScanResultKey() {
        WifiConfiguration config = new WifiConfiguration();
        config.SSID = "\"ssid\"";
        config.setSecurityParams(WifiConfiguration.SECURITY_TYPE_OPEN);
        assertThat(new StandardWifiEntryKey(config, true /* isTargetingNewNetworks */))
                .isEqualTo(new StandardWifiEntryKey(
                        new ScanResultKey(config), true /* isTargetingNewNetworks */));
    }

    @Test
    public void testStandardWifiEntryKey_toAndFromJson_matches() throws Exception {
        WifiConfiguration mockConfig = spy(new WifiConfiguration());
        mockConfig.SSID = "\"ssid\"";
        when(mockConfig.getProfileKey()).thenReturn("profileKey");
        mockConfig.fromWifiNetworkSpecifier = true;
        final StandardWifiEntryKey entryKey = new StandardWifiEntryKey(
                mockConfig, true /* isTargetingNewNetworks */);

        assertThat(new StandardWifiEntryKey(entryKey.toString())).isEqualTo(entryKey);
    }

    @Test
    public void testGetLevel_multipleSecurities_configAndScansMatch() {
        WifiConfiguration pskConfig = new WifiConfiguration();
        pskConfig.SSID = "\"ssid\"";
        pskConfig.setSecurityParams(WifiConfiguration.SECURITY_TYPE_PSK);
        WifiConfiguration saeConfig = new WifiConfiguration();
        saeConfig.SSID = "\"ssid\"";
        saeConfig.setSecurityParams(WifiConfiguration.SECURITY_TYPE_SAE);

        ScanResult pskScan = buildScanResult("ssid", "bssid0", 0, GOOD_RSSI);
        pskScan.capabilities = "PSK";
        ScanResult saeScan = buildScanResult("ssid", "bssid0", 0, BAD_RSSI);
        saeScan.capabilities = "SAE";
        StandardWifiEntry entry = new StandardWifiEntry(
                mMockInjector, mMockContext, mTestHandler,
                ssidAndSecurityTypeToStandardWifiEntryKey("ssid", SECURITY_TYPE_PSK,
                        true /* isTargetingNewNetwork */),
                Collections.singletonList(pskConfig), Arrays.asList(pskScan, saeScan),
                mMockWifiManager, mMockScoreCache, false /* forSavedNetworksPage */);

        // Only PSK config, so use PSK scan level
        assertThat(entry.getLevel()).isEqualTo(GOOD_LEVEL);
        assertThat(entry.isSaved()).isTrue();

        entry.updateConfig(Collections.singletonList(saeConfig));

        // Only SAE config, so use SAE scan level
        assertThat(entry.getLevel()).isEqualTo(BAD_LEVEL);
        assertThat(entry.isSaved()).isTrue();

        entry.updateScanResultInfo(Collections.singletonList(pskScan));

        // SAE config and PSK scan do not match, so entry is unsaved with PSK scan level
        assertThat(entry.getLevel()).isEqualTo(GOOD_LEVEL);
        assertThat(entry.isSaved()).isFalse();
    }

    @Test
    public void testConnect_unsavedOpen_configuresOpenNetwork() {
        ScanResult openScan = buildScanResult("ssid", "bssid0", 0, GOOD_RSSI);
        openScan.capabilities = "";
        StandardWifiEntry entry = new StandardWifiEntry(
                mMockInjector, mMockContext, mTestHandler,
                ssidAndSecurityTypeToStandardWifiEntryKey("ssid", SECURITY_TYPE_OPEN,
                        true /* isTargetingNewNetworks */),
                null, Collections.singletonList(openScan),
                mMockWifiManager, mMockScoreCache, false /* forSavedNetworksPage */);
        ArgumentCaptor<WifiConfiguration> connectConfigCaptor =
                ArgumentCaptor.forClass(WifiConfiguration.class);

        entry.connect(null);
        verify(mMockWifiManager).connect(connectConfigCaptor.capture(), any());

        assertThat(Utils.getSecurityTypesFromWifiConfiguration(connectConfigCaptor.getValue()))
                .isEqualTo(Collections.singletonList(SECURITY_TYPE_OPEN));
    }

    @Test
    public void testConnect_unsavedOwe_configuresOweNetwork() {
        ScanResult oweScan = buildScanResult("ssid", "bssid0", 0, BAD_RSSI);
        oweScan.capabilities = "OWE";
        StandardWifiEntry entry = new StandardWifiEntry(
                mMockInjector, mMockContext, mTestHandler,
                ssidAndSecurityTypeToStandardWifiEntryKey("ssid", SECURITY_TYPE_OWE,
                        true /* isTargetingNewNetworks */),
                null, Collections.singletonList(oweScan),
                mMockWifiManager, mMockScoreCache, false /* forSavedNetworksPage */);
        ArgumentCaptor<WifiConfiguration> connectConfigCaptor =
                ArgumentCaptor.forClass(WifiConfiguration.class);

        entry.connect(null);
        verify(mMockWifiManager).connect(connectConfigCaptor.capture(), any());

        assertThat(Utils.getSecurityTypesFromWifiConfiguration(connectConfigCaptor.getValue()))
                .isEqualTo(Collections.singletonList(SECURITY_TYPE_OWE));
    }

    @Test
    public void testConnect_unsavedOpenOwe_configuresOweAndOpenNetwork() {
        ScanResult oweTransitionScan = buildScanResult("ssid", "bssid0", 0, BAD_RSSI);
        oweTransitionScan.capabilities = "OWE_TRANSITION";
        StandardWifiEntry entry = new StandardWifiEntry(
                mMockInjector, mMockContext, mTestHandler,
                ssidAndSecurityTypeToStandardWifiEntryKey("ssid", SECURITY_TYPE_OPEN,
                        true /* isTargetingNewNetworks */),
                null, Collections.singletonList(oweTransitionScan),
                mMockWifiManager, mMockScoreCache, false /* forSavedNetworksPage */);
        ArgumentCaptor<WifiConfiguration> connectConfigCaptor =
                ArgumentCaptor.forClass(WifiConfiguration.class);
        ArgumentCaptor<WifiConfiguration> savedConfigCaptor =
                ArgumentCaptor.forClass(WifiConfiguration.class);

        entry.connect(null);
        verify(mMockWifiManager).connect(connectConfigCaptor.capture(), any());
        verify(mMockWifiManager).save(savedConfigCaptor.capture(), any());

        assertThat(Utils.getSecurityTypesFromWifiConfiguration(connectConfigCaptor.getValue()))
                .isEqualTo(Collections.singletonList(SECURITY_TYPE_OWE));
        assertThat(Utils.getSecurityTypesFromWifiConfiguration(savedConfigCaptor.getValue()))
                .isEqualTo(Collections.singletonList(SECURITY_TYPE_OPEN));
    }

    @Test
    public void testGetSecurity_openAndOwe_returnsOpen() {
        WifiConfiguration openConfig = new WifiConfiguration();
        openConfig.SSID = "\"ssid\"";
        openConfig.setSecurityParams(WifiConfiguration.SECURITY_TYPE_OPEN);
        WifiConfiguration oweConfig = new WifiConfiguration();
        oweConfig.SSID = "\"ssid\"";
        oweConfig.setSecurityParams(WifiConfiguration.SECURITY_TYPE_OWE);

        StandardWifiEntry entry = new StandardWifiEntry(
                mMockInjector, mMockContext, mTestHandler,
                ssidAndSecurityTypeToStandardWifiEntryKey("ssid", SECURITY_TYPE_OPEN,
                        true /* isTargetingNewNetwork */),
                Arrays.asList(openConfig, oweConfig), null,
                mMockWifiManager, mMockScoreCache, false /* forSavedNetworksPage */);

        assertThat(entry.getSecurity()).isEqualTo(WifiEntry.SECURITY_NONE);
        assertThat(entry.getWifiConfiguration()).isEqualTo(openConfig);
    }

    @Test
    public void testGetSecurity_pskAndSae_returnsPsk() {
        WifiConfiguration pskConfig = new WifiConfiguration();
        pskConfig.SSID = "\"ssid\"";
        pskConfig.setSecurityParams(WifiConfiguration.SECURITY_TYPE_PSK);
        WifiConfiguration saeConfig = new WifiConfiguration();
        saeConfig.SSID = "\"ssid\"";
        saeConfig.setSecurityParams(WifiConfiguration.SECURITY_TYPE_SAE);

        StandardWifiEntry entry = new StandardWifiEntry(
                mMockInjector, mMockContext, mTestHandler,
                ssidAndSecurityTypeToStandardWifiEntryKey("ssid", SECURITY_TYPE_PSK,
                        true /* isTargetingNewNetwork */),
                Arrays.asList(pskConfig, saeConfig), null,
                mMockWifiManager, mMockScoreCache, false /* forSavedNetworksPage */);

        assertThat(entry.getSecurity()).isEqualTo(WifiEntry.SECURITY_PSK);
        assertThat(entry.getWifiConfiguration()).isEqualTo(pskConfig);
    }

    @Test
    public void testGetSecurity_eapAndEapWpa3_returnsEap() {
        WifiConfiguration eapConfig = new WifiConfiguration();
        eapConfig.SSID = "\"ssid\"";
        eapConfig.setSecurityParams(WifiConfiguration.SECURITY_TYPE_EAP);
        WifiConfiguration eapWpa3Config = new WifiConfiguration();
        eapWpa3Config.SSID = "\"ssid\"";
        eapWpa3Config.setSecurityParams(WifiConfiguration.SECURITY_TYPE_EAP_WPA3_ENTERPRISE);

        StandardWifiEntry entry = new StandardWifiEntry(
                mMockInjector, mMockContext, mTestHandler,
                ssidAndSecurityTypeToStandardWifiEntryKey("ssid", SECURITY_TYPE_EAP,
                        true /* isTargetingNewNetwork */),
                Arrays.asList(eapConfig, eapWpa3Config), null,
                mMockWifiManager, mMockScoreCache, false /* forSavedNetworksPage */);

        assertThat(entry.getSecurity()).isEqualTo(WifiEntry.SECURITY_EAP);
        assertThat(entry.getWifiConfiguration()).isEqualTo(eapConfig);
    }

    @Test
    public void testCanShare_isDemoMode_returnsFalse() {
        when(mMockInjector.isDemoMode()).thenReturn(true);

        final StandardWifiEntry pskWifiEntry =
                getSavedStandardWifiEntry(WifiConfiguration.SECURITY_TYPE_PSK);

        assertThat(pskWifiEntry.canShare()).isFalse();
    }

    @Test
    public void testCanEasyConnect_isDemoMode_returnsFalse() {
        when(mMockInjector.isDemoMode()).thenReturn(true);

        final StandardWifiEntry pskWifiEntry =
                getSavedStandardWifiEntry(WifiConfiguration.SECURITY_TYPE_PSK);

        assertThat(pskWifiEntry.canEasyConnect()).isFalse();
    }

}
