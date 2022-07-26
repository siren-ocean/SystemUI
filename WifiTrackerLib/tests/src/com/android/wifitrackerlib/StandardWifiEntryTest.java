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

import static com.android.wifitrackerlib.StandardWifiEntry.ssidAndSecurityToStandardWifiEntryKey;
import static com.android.wifitrackerlib.StandardWifiEntry.wifiConfigToStandardWifiEntryKey;
import static com.android.wifitrackerlib.TestUtils.buildScanResult;
import static com.android.wifitrackerlib.WifiEntry.CONNECTED_STATE_CONNECTED;
import static com.android.wifitrackerlib.WifiEntry.CONNECTED_STATE_DISCONNECTED;
import static com.android.wifitrackerlib.WifiEntry.SECURITY_EAP;
import static com.android.wifitrackerlib.WifiEntry.SECURITY_NONE;
import static com.android.wifitrackerlib.WifiEntry.SECURITY_OWE;
import static com.android.wifitrackerlib.WifiEntry.SECURITY_PSK;
import static com.android.wifitrackerlib.WifiEntry.SECURITY_WEP;
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
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiNetworkScoreCache;
import android.os.Handler;
import android.os.test.TestLooper;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
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
    @Mock private WifiInfo mMockWifiInfo;
    @Mock private NetworkInfo mMockNetworkInfo;
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
    }

    /**
     * Tests that constructing with an empty list of scans throws an exception
     */
    @Test
    public void testConstructor_emptyScanList_throwsException() {
        try {
            new StandardWifiEntry(mMockContext, mTestHandler,
                    ssidAndSecurityToStandardWifiEntryKey("ssid", SECURITY_NONE),
                    new ArrayList<>(), mMockWifiManager, mMockScoreCache,
                    false /* forSavedNetworksPage */);
            fail("Empty scan list should have thrown exception");
        } catch (IllegalArgumentException e) {
            // Test succeeded
        }
    }

    /**
     * Tests that constructing with a list of scans with differing SSIDs throws an exception
     */
    @Test
    public void testConstructor_mismatchedSsids_throwsException() {
        try {
            new StandardWifiEntry(mMockContext, mTestHandler,
                    ssidAndSecurityToStandardWifiEntryKey("ssid", SECURITY_NONE),
                    Arrays.asList(
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
        final StandardWifiEntry entry = new StandardWifiEntry(mMockContext, mTestHandler,
                ssidAndSecurityToStandardWifiEntryKey("ssid", SECURITY_NONE),
                Arrays.asList(
                        buildScanResult("ssid", "bssid0", 0, GOOD_RSSI),
                        buildScanResult("ssid", "bssid1", 0, OKAY_RSSI),
                        buildScanResult("ssid", "bssid2", 0, BAD_RSSI)),
                mMockWifiManager, mMockScoreCache, false /* forSavedNetworksPage */);

        assertThat(entry.getLevel()).isEqualTo(GOOD_LEVEL);
    }

    /**
     * Tests that the security is set to the security capabilities of the scan
     */
    @Test
    public void testConstructor_scanResults_setsSecurity() {
        final ScanResult unsecureScan = buildScanResult("ssid", "bssid", 0, GOOD_RSSI);
        final ScanResult secureScan = buildScanResult("ssid", "bssid", 0, GOOD_RSSI);
        secureScan.capabilities = "EAP";

        final StandardWifiEntry unsecureEntry = new StandardWifiEntry(mMockContext, mTestHandler,
                ssidAndSecurityToStandardWifiEntryKey("ssid", SECURITY_NONE),
                Arrays.asList(unsecureScan), mMockWifiManager, mMockScoreCache,
                false /* forSavedNetworksPage */);
        final StandardWifiEntry secureEntry = new StandardWifiEntry(mMockContext, mTestHandler,
                ssidAndSecurityToStandardWifiEntryKey("ssid", SECURITY_EAP),
                Arrays.asList(secureScan), mMockWifiManager, mMockScoreCache,
                false /* forSavedNetworksPage */);

        assertThat(unsecureEntry.getSecurity()).isEqualTo(WifiEntry.SECURITY_NONE);
        assertThat(secureEntry.getSecurity()).isEqualTo(WifiEntry.SECURITY_EAP);
    }

    /**
     * Tests that updating with a list of scans with differing SSIDs throws an exception
     */
    @Test
    public void testUpdateScanResultInfo_mismatchedSsids_throwsException() {
        final StandardWifiEntry entry = new StandardWifiEntry(mMockContext, mTestHandler,
                ssidAndSecurityToStandardWifiEntryKey("ssid0", SECURITY_EAP),
                Arrays.asList(buildScanResult("ssid0", "bssid0", 0, GOOD_RSSI)),
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
        final StandardWifiEntry entry = new StandardWifiEntry(mMockContext, mTestHandler,
                ssidAndSecurityToStandardWifiEntryKey("ssid", SECURITY_NONE),
                Arrays.asList(buildScanResult("ssid", "bssid", 0)),
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
        final StandardWifiEntry entry = new StandardWifiEntry(mMockContext, mTestHandler,
                ssidAndSecurityToStandardWifiEntryKey("ssid", SECURITY_NONE),
                Arrays.asList(buildScanResult("ssid", "bssid", 0, BAD_RSSI)),
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
        final StandardWifiEntry entry = new StandardWifiEntry(mMockContext, mTestHandler,
                ssidAndSecurityToStandardWifiEntryKey("ssid", SECURITY_EAP),
                config, mMockWifiManager, mMockScoreCache, false /* forSavedNetworksPage */);

        assertThat(entry.getTitle()).isEqualTo("ssid");
    }

    @Test
    public void testConstructor_wifiConfig_setsSecurity() {
        final WifiConfiguration config = new WifiConfiguration();
        config.SSID = "\"ssid\"";
        config.setSecurityParams(WifiConfiguration.SECURITY_TYPE_EAP);
        final StandardWifiEntry entry = new StandardWifiEntry(mMockContext, mTestHandler,
                ssidAndSecurityToStandardWifiEntryKey("ssid", SECURITY_EAP),
                config, mMockWifiManager, mMockScoreCache, false /* forSavedNetworksPage */);

        assertThat(entry.getSecurity()).isEqualTo(WifiEntry.SECURITY_EAP);
    }

    @Test
    public void testUpdateConfig_mismatchedSsids_throwsException() {
        final WifiConfiguration config = new WifiConfiguration();
        config.SSID = "\"ssid\"";
        config.setSecurityParams(WifiConfiguration.SECURITY_TYPE_EAP);
        final StandardWifiEntry entry = new StandardWifiEntry(mMockContext, mTestHandler,
                ssidAndSecurityToStandardWifiEntryKey("ssid", SECURITY_EAP),
                config, mMockWifiManager, mMockScoreCache, false /* forSavedNetworksPage */);

        final WifiConfiguration config2 = new WifiConfiguration(config);
        config2.SSID = "\"ssid2\"";
        try {
            entry.updateConfig(config2);
            fail("Updating with wrong SSID config should throw exception");
        } catch (IllegalArgumentException e) {
            // Test Succeeded
        }
    }

    @Test
    public void testUpdateConfig_mismatchedSecurity_throwsException() {
        final WifiConfiguration config = new WifiConfiguration();
        config.SSID = "\"ssid\"";
        config.setSecurityParams(WifiConfiguration.SECURITY_TYPE_WEP);
        final StandardWifiEntry entry = new StandardWifiEntry(mMockContext, mTestHandler,
                ssidAndSecurityToStandardWifiEntryKey("ssid", SECURITY_WEP),
                config, mMockWifiManager, mMockScoreCache, false /* forSavedNetworksPage */);

        final WifiConfiguration config2 = new WifiConfiguration(config);
        config2.setSecurityParams(WifiConfiguration.SECURITY_TYPE_EAP);
        try {
            entry.updateConfig(config2);
            fail("Updating with wrong security config should throw exception");
        } catch (IllegalArgumentException e) {
            // Test Succeeded
        }
    }

    @Test
    public void testUpdateConfig_unsavedToSaved() {
        final ScanResult scan = buildScanResult("ssid", "bssid", 0, GOOD_RSSI);
        scan.capabilities = "EAP";
        final StandardWifiEntry entry = new StandardWifiEntry(mMockContext, mTestHandler,
                ssidAndSecurityToStandardWifiEntryKey("ssid", SECURITY_EAP),
                Arrays.asList(scan), mMockWifiManager, mMockScoreCache,
                false /* forSavedNetworksPage */);

        assertThat(entry.isSaved()).isFalse();

        final WifiConfiguration config = new WifiConfiguration();
        config.SSID = "\"ssid\"";
        config.setSecurityParams(WifiConfiguration.SECURITY_TYPE_EAP);
        config.networkId = 1;
        entry.updateConfig(config);

        assertThat(entry.isSaved()).isTrue();
    }

    @Test
    public void testUpdateConfig_savedToUnsaved() {
        final WifiConfiguration config = new WifiConfiguration();
        config.setSecurityParams(WifiConfiguration.SECURITY_TYPE_EAP);
        config.SSID = "\"ssid\"";
        config.networkId = 1;
        final StandardWifiEntry entry = new StandardWifiEntry(mMockContext, mTestHandler,
                ssidAndSecurityToStandardWifiEntryKey("ssid", SECURITY_EAP),
                config, mMockWifiManager, mMockScoreCache, false /* forSavedNetworksPage */);

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
        final StandardWifiEntry entry = new StandardWifiEntry(mMockContext, mTestHandler,
                ssidAndSecurityToStandardWifiEntryKey("ssid", SECURITY_EAP),
                config, mMockWifiManager, mMockScoreCache, false /* forSavedNetworksPage */);
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
        final StandardWifiEntry entry = new StandardWifiEntry(mMockContext, mTestHandler,
                ssidAndSecurityToStandardWifiEntryKey("ssid", SECURITY_EAP),
                config, mMockWifiManager, mMockScoreCache, false /* forSavedNetworksPage */);
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
        final StandardWifiEntry entry = new StandardWifiEntry(mMockContext, mTestHandler,
                ssidAndSecurityToStandardWifiEntryKey("ssid", SECURITY_NONE),
                Arrays.asList(scan), mMockWifiManager, mMockScoreCache,
                false /* forSavedNetworksPage */);
        final WifiConfiguration config = new WifiConfiguration();
        config.SSID = "\"ssid\"";
        config.networkId = 1;
        entry.updateConfig(config);

        entry.connect(null /* ConnectCallback */);

        verify(mMockWifiManager, times(1)).connect(eq(1), any());
    }

    @Test
    public void testConnect_openNetwork_callsConnect() {
        final StandardWifiEntry entry = new StandardWifiEntry(mMockContext, mTestHandler,
                ssidAndSecurityToStandardWifiEntryKey("ssid", SECURITY_NONE),
                Arrays.asList(buildScanResult("ssid", "bssid0", 0, GOOD_RSSI)),
                mMockWifiManager, mMockScoreCache, false /* forSavedNetworksPage */);

        entry.connect(null /* ConnectCallback */);

        verify(mMockWifiManager, times(1)).connect(any(), any());
    }

    @Test
    public void testConnect_unsavedSecureNetwork_returnsNoConfigFailure() {
        final ScanResult secureScan = buildScanResult("ssid", "bssid0", 0, GOOD_RSSI);
        secureScan.capabilities = "PSK";
        final StandardWifiEntry entry = new StandardWifiEntry(mMockContext, mTestHandler,
                ssidAndSecurityToStandardWifiEntryKey("ssid", SECURITY_PSK),
                Arrays.asList(secureScan), mMockWifiManager, mMockScoreCache,
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
        config.macRandomizationSetting = WifiConfiguration.RANDOMIZATION_PERSISTENT;
        WifiConfiguration spyConfig = spy(config);
        when(spyConfig.getRandomizedMacAddress())
                .thenReturn(MacAddress.fromString(randomizedMac));

        final StandardWifiEntry entry = new StandardWifiEntry(mMockContext, mTestHandler,
                ssidAndSecurityToStandardWifiEntryKey("ssid", SECURITY_EAP),
                spyConfig, mMockWifiManager, mMockScoreCache, false /* forSavedNetworksPage */);

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
        final StandardWifiEntry entry = new StandardWifiEntry(mMockContext, mTestHandler,
                ssidAndSecurityToStandardWifiEntryKey("ssid", SECURITY_EAP),
                config, mMockWifiManager, mMockScoreCache, false /* forSavedNetworksPage */);

        final String macAddress = entry.getMacAddress();

        assertThat(macAddress).isEqualTo(factoryMac);
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
                getSavedStandardWifiEntry(WifiConfiguration.SECURITY_TYPE_EAP_SUITE_B);

        assertThat(eapWifiEntry.canShare()).isFalse();
        assertThat(eapSuiteBWifiEntry.canShare()).isFalse();
    }

    @Test
    public void testCanEasyConnect_deviceNotSupported_shouldReturnFalse() {
        when(mMockWifiManager.isEasyConnectSupported()).thenReturn(false);
        final ScanResult pskScanResult = buildScanResult("ssid", "bssid", 0, GOOD_RSSI);
        pskScanResult.capabilities = "PSK";

        final StandardWifiEntry pskWifiEntry = new StandardWifiEntry(mMockContext, mTestHandler,
                ssidAndSecurityToStandardWifiEntryKey(pskScanResult.SSID, SECURITY_PSK),
                Arrays.asList(pskScanResult), mMockWifiManager, mMockScoreCache,
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
                getSavedStandardWifiEntry(WifiConfiguration.SECURITY_TYPE_EAP_SUITE_B);
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
        final StandardWifiEntry entry = new StandardWifiEntry(mMockContext, mTestHandler,
                ssidAndSecurityToStandardWifiEntryKey("ssid", SECURITY_EAP),
                config, mMockWifiManager, mMockScoreCache, false /* forSavedNetworksPage */);
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
        return new StandardWifiEntry(mMockContext, mTestHandler,
                wifiConfigToStandardWifiEntryKey(config),
                config, mMockWifiManager, mMockScoreCache, false /* forSavedNetworksPage */);
    }

    @Test
    public void testGetSummary_connectedWifiNetwork_showsConnected() {
        final int networkId = 1;
        final String summarySeparator = " / ";
        final String[] wifiStatusArray = new String[]{"", "Scanning", "Connecting",
                "Authenticating", "Obtaining IP address", "Connected"};

        final Resources mockResources = mock(Resources.class);
        when(mMockContext.getResources()).thenReturn(mockResources);
        when(mockResources.getString(R.string.summary_separator)).thenReturn(summarySeparator);
        when(mockResources.getStringArray(R.array.wifi_status)).thenReturn(wifiStatusArray);
        final ConnectivityManager mockConnectivityManager = mock(ConnectivityManager.class);
        when(mMockContext.getSystemService(Context.CONNECTIVITY_SERVICE))
                .thenReturn(mockConnectivityManager);

        final WifiInfo wifiInfo = new WifiInfo.Builder().setNetworkId(networkId).build();
        final NetworkInfo networkInfo =
                new NetworkInfo(ConnectivityManager.TYPE_WIFI, 0 /* subtype */, "WIFI", "");
        networkInfo.setDetailedState(NetworkInfo.DetailedState.CONNECTED, "", "");

        final WifiConfiguration config = new WifiConfiguration();
        config.SSID = "\"ssid\"";
        config.networkId = networkId;
        final StandardWifiEntry entry = new StandardWifiEntry(mMockContext, mTestHandler,
                ssidAndSecurityToStandardWifiEntryKey("ssid", SECURITY_NONE), config,
                mMockWifiManager, mMockScoreCache, false /* forSavedNetworksPage */);

        entry.updateConnectionInfo(wifiInfo, networkInfo);

        assertThat(entry.getSummary()).isEqualTo("Connected");
    }

    @Test
    public void testGetSecurityString_pskTypeWpa2_getWpa2() {
        final StandardWifiEntry entry =
                getSavedStandardWifiEntry(WifiConfiguration.SECURITY_TYPE_PSK);
        final ScanResult bestScanResult = buildScanResult("ssid", "bssid", 0, GOOD_RSSI);
        bestScanResult.capabilities = "RSN-PSK";
        final String wifiSecurityShortWpa2Wpa3 = "WPA2/WPA3";
        final Resources mockResources = mock(Resources.class);
        when(mMockContext.getResources()).thenReturn(mockResources);
        when(mockResources.getString(R.string.wifi_security_short_wpa2_wpa3))
                .thenReturn(wifiSecurityShortWpa2Wpa3);

        entry.updateScanResultInfo(Arrays.asList(bestScanResult));

        assertThat(entry.getSecurityString(true /* concise */))
                .isEqualTo(wifiSecurityShortWpa2Wpa3);
    }

    @Test
    public void testGetSecurityString_eapTypeWpa_getWpa() {
        final StandardWifiEntry entry =
                getSavedStandardWifiEntry(WifiConfiguration.SECURITY_TYPE_EAP);
        final ScanResult bestScanResult = buildScanResult("ssid", "bssid", 0, GOOD_RSSI);
        bestScanResult.capabilities = "WPA-EAP";
        final String wifiSecurityEapWpa = "WPA-Enterprise";
        final Resources mockResources = mock(Resources.class);
        when(mMockContext.getResources()).thenReturn(mockResources);
        when(mockResources.getString(R.string.wifi_security_eap_wpa))
                .thenReturn(wifiSecurityEapWpa);

        entry.updateScanResultInfo(Arrays.asList(bestScanResult));

        assertThat(entry.getSecurityString(false /* concise */)).isEqualTo(wifiSecurityEapWpa);
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
        final StandardWifiEntry entry = new StandardWifiEntry(mMockContext, mTestHandler,
                ssidAndSecurityToStandardWifiEntryKey("ssid", SECURITY_NONE),
                Arrays.asList(
                        buildScanResult("ssid", "bssid0", 0, GOOD_RSSI)),
                mMockWifiManager, mMockScoreCache, false /* forSavedNetworksPage */);
        NetworkCapabilities captivePortalCapabilities = new NetworkCapabilities();
        captivePortalCapabilities.addCapability(NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL);
        entry.updateNetworkCapabilities(captivePortalCapabilities);

        assertThat(entry.canSignIn()).isTrue();
    }

    @Test
    public void testUpdateNetworkCapabilities_userConnect_autoOpenCaptivePortalOnce() {
        when(mMockContext.getSystemService(Context.CONNECTIVITY_SERVICE))
                .thenReturn(mMockConnectivityManager);
        final StandardWifiEntry entry = new StandardWifiEntry(mMockContext, mTestHandler,
                ssidAndSecurityToStandardWifiEntryKey("ssid", SECURITY_NONE),
                Arrays.asList(
                        buildScanResult("ssid", "bssid0", 0, GOOD_RSSI)),
                mMockWifiManager, mMockScoreCache, false /* forSavedNetworksPage */);
        NetworkCapabilities captivePortalCapabilities = new NetworkCapabilities();
        captivePortalCapabilities.addCapability(NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL);

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
        StandardWifiEntry entry = new StandardWifiEntry(mMockContext, mTestHandler,
                ssidAndSecurityToStandardWifiEntryKey("ssid", SECURITY_EAP),
                mMockWifiManager, mMockScoreCache, false /* forSavedNetworksPage */);

        assertThat(entry.shouldEditBeforeConnect()).isFalse();
    }

    @Test
    public void testShouldEditBeforeConnect_openNetwork_returnFalse() {
        // Test open networks.
        WifiConfiguration wifiConfig = new WifiConfiguration();
        wifiConfig.SSID = "\"ssid\"";
        wifiConfig.setSecurityParams(WifiConfiguration.SECURITY_TYPE_OPEN);
        StandardWifiEntry entry = new StandardWifiEntry(mMockContext, mTestHandler,
                ssidAndSecurityToStandardWifiEntryKey("ssid", SECURITY_NONE),
                wifiConfig, mMockWifiManager, mMockScoreCache, false /* forSavedNetworksPage */);

        assertThat(entry.shouldEditBeforeConnect()).isFalse();

        // Test enhanced open networks.
        wifiConfig.setSecurityParams(WifiConfiguration.SECURITY_TYPE_OWE);
        entry = new StandardWifiEntry(mMockContext, mTestHandler,
                ssidAndSecurityToStandardWifiEntryKey("ssid", SECURITY_OWE),
                wifiConfig, mMockWifiManager, mMockScoreCache, false /* forSavedNetworksPage */);

        assertThat(entry.shouldEditBeforeConnect()).isFalse();
    }

    @Test
    public void testShouldEditBeforeConnect_securedNetwork_returnTrueIfNeverConnected() {
        // Test never connected.
        WifiConfiguration wifiConfig = spy(new WifiConfiguration());
        wifiConfig.SSID = "\"ssid\"";
        wifiConfig.setSecurityParams(WifiConfiguration.SECURITY_TYPE_PSK);
        StandardWifiEntry entry = new StandardWifiEntry(mMockContext, mTestHandler,
                ssidAndSecurityToStandardWifiEntryKey("ssid", SECURITY_PSK),
                wifiConfig, mMockWifiManager, mMockScoreCache, false /* forSavedNetworksPage */);
        NetworkSelectionStatus networkSelectionStatus =
                spy(new NetworkSelectionStatus.Builder().build());
        doReturn(networkSelectionStatus).when(wifiConfig).getNetworkSelectionStatus();

        assertThat(entry.shouldEditBeforeConnect()).isTrue();

        // Test ever connected.
        doReturn(true).when(networkSelectionStatus).hasEverConnected();

        assertThat(entry.shouldEditBeforeConnect()).isFalse();
    }

    @Test
    public void testShouldEditBeforeConnect_authenticationFailure_returnTrue() {
        // Test DISABLED_AUTHENTICATION_FAILURE.
        WifiConfiguration wifiConfig = spy(new WifiConfiguration());
        wifiConfig.SSID = "\"ssid\"";
        wifiConfig.setSecurityParams(WifiConfiguration.SECURITY_TYPE_PSK);
        StandardWifiEntry entry = new StandardWifiEntry(mMockContext, mTestHandler,
                ssidAndSecurityToStandardWifiEntryKey("ssid", SECURITY_PSK),
                wifiConfig, mMockWifiManager, mMockScoreCache, false /* forSavedNetworksPage */);
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
        final StandardWifiEntry entry = new StandardWifiEntry(mMockContext, mTestHandler,
                ssidAndSecurityToStandardWifiEntryKey("ssid", SECURITY_NONE),
                Collections.singletonList(buildScanResult("ssid", "bssid0", 0, GOOD_RSSI)),
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
        final WifiConfiguration config = new WifiConfiguration();
        config.SSID = "\"ssid\"";
        config.networkId = 1;
        final StandardWifiEntry entry = new StandardWifiEntry(mMockContext, mTestHandler,
                ssidAndSecurityToStandardWifiEntryKey("ssid", SECURITY_NONE), config,
                mMockWifiManager, mMockScoreCache, false /* forSavedNetworksPage */);
        entry.updateScanResultInfo(Collections.singletonList(
                buildScanResult("ssid", "bssid0", 0, GOOD_RSSI)));

        entry.updateConnectionInfo(mMockWifiInfo, mMockNetworkInfo);

        assertThat(entry.getSpeed()).isEqualTo(SPEED_SLOW);
    }

    @Test
    public void testGetSpeed_newScanResults_speedValueChanges() {
        when(mMockScoredNetwork.calculateBadge(BAD_RSSI)).thenReturn(SPEED_SLOW);
        when(mMockScoredNetwork.calculateBadge(GOOD_RSSI)).thenReturn(SPEED_FAST);
        final StandardWifiEntry entry = new StandardWifiEntry(mMockContext, mTestHandler,
                ssidAndSecurityToStandardWifiEntryKey("ssid", SECURITY_NONE),
                Collections.singletonList(buildScanResult("ssid", "bssid0", 0, GOOD_RSSI)),
                mMockWifiManager, mMockScoreCache, false /* forSavedNetworksPage */);

        entry.updateScanResultInfo(Collections.singletonList(
                buildScanResult("ssid", "bssid0", 0, BAD_RSSI)));

        assertThat(entry.getSpeed()).isEqualTo(SPEED_SLOW);
    }
}
