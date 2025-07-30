/*
 * Copyright (C) 2020 The Android Open Source Project
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
import static com.android.wifitrackerlib.WifiEntry.SPEED_FAST;
import static com.android.wifitrackerlib.WifiEntry.SPEED_SLOW;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.content.res.Resources;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.net.NetworkKey;
import android.net.ScoredNetwork;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiNetworkScoreCache;
import android.net.wifi.hotspot2.PasspointConfiguration;
import android.net.wifi.hotspot2.pps.Credential;
import android.net.wifi.hotspot2.pps.HomeSp;
import android.os.Handler;
import android.os.test.TestLooper;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Arrays;
import java.util.Collections;

public class PasspointWifiEntryTest {
    public static final int GOOD_RSSI = -50;
    public static final int OKAY_RSSI = -60;
    public static final int BAD_RSSI = -70;

    @Mock private WifiTrackerInjector mMockInjector;
    @Mock private Context mMockContext;
    @Mock private WifiManager mMockWifiManager;
    @Mock private Resources mMockResources;
    @Mock private WifiInfo mMockWifiInfo;
    @Mock private NetworkInfo mMockNetworkInfo;
    @Mock private WifiNetworkScoreCache mMockScoreCache;
    @Mock private ScoredNetwork mMockScoredNetwork;
    @Mock private ConnectivityManager mMockConnectivityManager;

    private TestLooper mTestLooper;
    private Handler mTestHandler;

    private static final String FQDN = "fqdn";

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        mTestLooper = new TestLooper();
        mTestHandler = new Handler(mTestLooper.getLooper());

        when(mMockWifiInfo.getNetworkId()).thenReturn(WifiConfiguration.INVALID_NETWORK_ID);
        when(mMockWifiInfo.getRssi()).thenReturn(WifiInfo.INVALID_RSSI);
        when(mMockNetworkInfo.getDetailedState()).thenReturn(
                NetworkInfo.DetailedState.DISCONNECTED);
        when(mMockContext.getResources()).thenReturn(mMockResources);
        when(mMockResources.getString(R.string.wifitrackerlib_summary_separator)).thenReturn("/");
        when(mMockScoreCache.getScoredNetwork((ScanResult) any())).thenReturn(mMockScoredNetwork);
        when(mMockScoreCache.getScoredNetwork((NetworkKey) any())).thenReturn(mMockScoredNetwork);
    }

    @Test
    public void testGetSummary_expiredTimeNotAvailable_notShowExpired() {
        // default SubscriptionExpirationTimeInMillis is unset
        PasspointConfiguration passpointConfiguration = getPasspointConfiguration();
        String expired = "Expired";
        when(mMockResources.getString(R.string.wifitrackerlib_wifi_passpoint_expired))
                .thenReturn(expired);

        PasspointWifiEntry passpointWifiEntry = new PasspointWifiEntry(mMockInjector, mMockContext,
                mTestHandler, passpointConfiguration, mMockWifiManager, mMockScoreCache,
                false /* forSavedNetworksPage */);

        assertThat(passpointWifiEntry.getSummary()).isNotEqualTo(expired);
    }

    @Test
    public void testGetSummary_expired_showExpired() {
        PasspointConfiguration passpointConfiguration = getPasspointConfiguration();
        String expired = "Expired";
        when(mMockResources.getString(R.string.wifitrackerlib_wifi_passpoint_expired))
                .thenReturn(expired);
        PasspointWifiEntry passpointWifiEntry = new PasspointWifiEntry(mMockInjector, mMockContext,
                mTestHandler, passpointConfiguration, mMockWifiManager, mMockScoreCache,
                false /* forSavedNetworksPage */);
        PasspointWifiEntry spyEntry = spy(passpointWifiEntry);
        when(spyEntry.isExpired()).thenReturn(true);

        assertThat(spyEntry.getSummary()).isEqualTo(expired);
    }

    private PasspointConfiguration getPasspointConfiguration() {
        PasspointConfiguration passpointConfiguration = new PasspointConfiguration();
        HomeSp homeSp = new HomeSp();
        homeSp.setFqdn(FQDN);
        passpointConfiguration.setHomeSp(homeSp);
        passpointConfiguration.setCredential(new Credential());
        return passpointConfiguration;
    }

    @Test
    public void testGetMeteredChoice_afterSetMeteredChoice_getCorrectValue() {
        PasspointWifiEntry entry = new PasspointWifiEntry(mMockInjector, mMockContext, mTestHandler,
                getPasspointConfiguration(), mMockWifiManager, mMockScoreCache,
                false /* forSavedNetworksPage */);

        entry.setMeteredChoice(WifiEntry.METERED_CHOICE_UNMETERED);

        assertThat(entry.getMeteredChoice()).isEqualTo(WifiEntry.METERED_CHOICE_UNMETERED);
    }

    @Test
    public void testGetSummary_connectedWifiNetwork_showsConnected() {
        String summarySeparator = " / ";
        String[] wifiStatusArray = new String[]{"", "Scanning", "Connecting",
                "Authenticating", "Obtaining IP address", "Connected"};

        Resources mockResources = mock(Resources.class);
        when(mMockContext.getResources()).thenReturn(mockResources);
        when(mockResources.getString(R.string.wifitrackerlib_summary_separator))
                .thenReturn(summarySeparator);
        when(mockResources.getStringArray(R.array.wifitrackerlib_wifi_status))
                .thenReturn(wifiStatusArray);
        ConnectivityManager mockConnectivityManager = mock(ConnectivityManager.class);
        when(mMockContext.getSystemService(Context.CONNECTIVITY_SERVICE))
                .thenReturn(mockConnectivityManager);
        final NetworkCapabilities networkCapabilities = new NetworkCapabilities.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED).build();
        WifiInfo wifiInfo = mock(WifiInfo.class);
        when(wifiInfo.isPasspointAp()).thenReturn(true);
        when(wifiInfo.getPasspointFqdn()).thenReturn(FQDN);
        NetworkInfo networkInfo =
                new NetworkInfo(ConnectivityManager.TYPE_WIFI, 0 /* subtype */, "WIFI", "");
        networkInfo.setDetailedState(NetworkInfo.DetailedState.CONNECTED, "", "");

        PasspointWifiEntry entry = new PasspointWifiEntry(mMockInjector, mMockContext, mTestHandler,
                getPasspointConfiguration(), mMockWifiManager, mMockScoreCache,
                false /* forSavedNetworksPage */);
        entry.updateConnectionInfo(wifiInfo, networkInfo);
        entry.updateNetworkCapabilities(networkCapabilities);
        entry.setIsDefaultNetwork(true);

        assertThat(entry.getSummary()).isEqualTo("Connected");
    }

    @Test
    public void testGetSummary_connectedButNotDefault_doesNotShowConnected() {
        String summarySeparator = " / ";
        String[] wifiStatusArray = new String[]{"", "Scanning", "Connecting",
                "Authenticating", "Obtaining IP address", "Connected"};

        Resources mockResources = mock(Resources.class);
        when(mMockContext.getResources()).thenReturn(mockResources);
        when(mockResources.getString(R.string.wifitrackerlib_summary_separator))
                .thenReturn(summarySeparator);
        when(mockResources.getStringArray(R.array.wifitrackerlib_wifi_status))
                .thenReturn(wifiStatusArray);
        ConnectivityManager mockConnectivityManager = mock(ConnectivityManager.class);
        when(mMockContext.getSystemService(Context.CONNECTIVITY_SERVICE))
                .thenReturn(mockConnectivityManager);
        final NetworkCapabilities networkCapabilities = new NetworkCapabilities.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED).build();
        WifiInfo wifiInfo = mock(WifiInfo.class);
        when(wifiInfo.isPasspointAp()).thenReturn(true);
        when(wifiInfo.getPasspointFqdn()).thenReturn(FQDN);
        NetworkInfo networkInfo =
                new NetworkInfo(ConnectivityManager.TYPE_WIFI, 0 /* subtype */, "WIFI", "");
        networkInfo.setDetailedState(NetworkInfo.DetailedState.CONNECTED, "", "");

        PasspointWifiEntry entry = new PasspointWifiEntry(mMockInjector, mMockContext, mTestHandler,
                getPasspointConfiguration(), mMockWifiManager, mMockScoreCache,
                false /* forSavedNetworksPage */);
        entry.updateConnectionInfo(wifiInfo, networkInfo);
        entry.updateNetworkCapabilities(networkCapabilities);
        entry.setIsDefaultNetwork(false);

        assertThat(entry.getSummary()).isEqualTo("");
    }

    @Test
    public void testGetSecurityTypes_connectedWifiNetwork_showsCurrentSecurityType() {
        WifiInfo wifiInfo = mock(WifiInfo.class);
        when(wifiInfo.isPasspointAp()).thenReturn(true);
        when(wifiInfo.getPasspointFqdn()).thenReturn(FQDN);
        when(wifiInfo.getCurrentSecurityType()).thenReturn(WifiInfo.SECURITY_TYPE_PASSPOINT_R1_R2);
        NetworkInfo networkInfo =
                new NetworkInfo(ConnectivityManager.TYPE_WIFI, 0 /* subtype */, "WIFI", "");
        networkInfo.setDetailedState(NetworkInfo.DetailedState.CONNECTED, "", "");

        PasspointWifiEntry entry = new PasspointWifiEntry(mMockInjector, mMockContext, mTestHandler,
                getPasspointConfiguration(), mMockWifiManager, mMockScoreCache,
                false /* forSavedNetworksPage */);

        assertThat(entry.getSecurityTypes()).containsExactlyElementsIn(Arrays.asList(
                WifiInfo.SECURITY_TYPE_PASSPOINT_R1_R2,
                WifiInfo.SECURITY_TYPE_PASSPOINT_R3));

        entry.updateConnectionInfo(wifiInfo, networkInfo);

        assertThat(entry.getSecurityTypes())
                .containsExactly(WifiInfo.SECURITY_TYPE_PASSPOINT_R1_R2);

        when(wifiInfo.getCurrentSecurityType()).thenReturn(WifiInfo.SECURITY_TYPE_PASSPOINT_R3);
        entry.updateConnectionInfo(wifiInfo, networkInfo);

        assertThat(entry.getSecurityTypes()).containsExactly(WifiInfo.SECURITY_TYPE_PASSPOINT_R3);
    }

    @Test
    public void testGetSecurityString_showsPasspoint() {
        PasspointConfiguration passpointConfiguration = getPasspointConfiguration();
        String passpointSecurity = "Passpoint";
        when(mMockResources.getString(R.string.wifitrackerlib_wifi_security_passpoint))
                .thenReturn(passpointSecurity);

        PasspointWifiEntry passpointWifiEntry = new PasspointWifiEntry(mMockInjector, mMockContext,
                mTestHandler, passpointConfiguration, mMockWifiManager, mMockScoreCache,
                false /* forSavedNetworksPage */);

        assertThat(passpointWifiEntry.getSecurityString(false)).isEqualTo(passpointSecurity);
    }

    @Test
    public void testShouldShowXLevelIcon_unvalidatedOrNotDefault_returnsTrue() {
        ConnectivityManager mockConnectivityManager = mock(ConnectivityManager.class);
        when(mMockContext.getSystemService(Context.CONNECTIVITY_SERVICE))
                .thenReturn(mockConnectivityManager);
        final NetworkCapabilities networkCapabilities = new NetworkCapabilities.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED).build();
        WifiInfo wifiInfo = mock(WifiInfo.class);
        when(wifiInfo.isPasspointAp()).thenReturn(true);
        when(wifiInfo.getPasspointFqdn()).thenReturn(FQDN);
        final NetworkInfo networkInfo =
                new NetworkInfo(ConnectivityManager.TYPE_WIFI, 0 /* subtype */, "WIFI", "");
        networkInfo.setDetailedState(NetworkInfo.DetailedState.CONNECTED, "", "");

        PasspointWifiEntry entry = new PasspointWifiEntry(mMockInjector, mMockContext, mTestHandler,
                getPasspointConfiguration(), mMockWifiManager, mMockScoreCache,
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
    public void testGetSpeed_cacheUpdated_speedValueChanges() {
        when(mMockScoredNetwork.calculateBadge(GOOD_RSSI)).thenReturn(SPEED_FAST);
        PasspointWifiEntry entry = new PasspointWifiEntry(mMockInjector, mMockContext, mTestHandler,
                getPasspointConfiguration(), mMockWifiManager, mMockScoreCache,
                false /* forSavedNetworksPage */);
        WifiConfiguration wifiConfig = new WifiConfiguration();
        wifiConfig.FQDN = FQDN;
        entry.updateScanResultInfo(wifiConfig,
                Collections.singletonList(buildScanResult("ssid", "bssid0", 0, GOOD_RSSI)),
                null);

        when(mMockScoredNetwork.calculateBadge(GOOD_RSSI)).thenReturn(SPEED_SLOW);
        entry.onScoreCacheUpdated();

        assertThat(entry.getSpeed()).isEqualTo(SPEED_SLOW);
    }

    @Test
    public void testGetSpeed_connected_useWifiInfoRssiForSpeed() {
        when(mMockScoredNetwork.calculateBadge(BAD_RSSI)).thenReturn(SPEED_SLOW);
        when(mMockScoredNetwork.calculateBadge(GOOD_RSSI)).thenReturn(SPEED_FAST);
        when(mMockWifiInfo.isPasspointAp()).thenReturn(true);
        when(mMockWifiInfo.getPasspointFqdn()).thenReturn(FQDN);
        when(mMockWifiInfo.getRssi()).thenReturn(BAD_RSSI);
        when(mMockWifiInfo.getSSID()).thenReturn("\"ssid\"");
        when(mMockWifiInfo.getBSSID()).thenReturn("01:23:45:67:89:ab");
        PasspointWifiEntry entry = new PasspointWifiEntry(mMockInjector, mMockContext, mTestHandler,
                getPasspointConfiguration(), mMockWifiManager, mMockScoreCache,
                false /* forSavedNetworksPage */);
        WifiConfiguration wifiConfig = new WifiConfiguration();
        wifiConfig.FQDN = FQDN;
        entry.updateScanResultInfo(wifiConfig,
                Collections.singletonList(buildScanResult("ssid", "bssid0", 0, GOOD_RSSI)),
                null);

        entry.updateConnectionInfo(mMockWifiInfo, mMockNetworkInfo);

        assertThat(entry.getSpeed()).isEqualTo(SPEED_SLOW);
    }

    @Test
    public void testGetSpeed_newScanResults_speedValueChanges() {
        when(mMockScoredNetwork.calculateBadge(BAD_RSSI)).thenReturn(SPEED_SLOW);
        when(mMockScoredNetwork.calculateBadge(GOOD_RSSI)).thenReturn(SPEED_FAST);
        PasspointWifiEntry entry = new PasspointWifiEntry(mMockInjector, mMockContext, mTestHandler,
                getPasspointConfiguration(), mMockWifiManager, mMockScoreCache,
                false /* forSavedNetworksPage */);
        WifiConfiguration wifiConfig = new WifiConfiguration();
        wifiConfig.FQDN = FQDN;
        entry.updateScanResultInfo(wifiConfig,
                Collections.singletonList(buildScanResult("ssid", "bssid0", 0, GOOD_RSSI)),
                null);

        entry.updateScanResultInfo(wifiConfig,
                Collections.singletonList(buildScanResult("ssid", "bssid0", 0, BAD_RSSI)),
                null);

        assertThat(entry.getSpeed()).isEqualTo(SPEED_SLOW);
    }

    @Test
    public void testGetMacAddress_wifiInfoAvailable_usesWifiInfoMacAddress() {
        final String factoryMac = "01:23:45:67:89:ab";
        final String wifiInfoMac = "11:23:45:67:89:ab";
        final WifiConfiguration config = new WifiConfiguration();
        config.SSID = "\"ssid\"";
        config.macRandomizationSetting = WifiConfiguration.RANDOMIZATION_NONE;
        config.FQDN = FQDN;
        when(mMockWifiManager.getFactoryMacAddresses()).thenReturn(new String[]{factoryMac});
        WifiInfo wifiInfo = mock(WifiInfo.class);
        when(wifiInfo.isPasspointAp()).thenReturn(true);
        when(wifiInfo.getPasspointFqdn()).thenReturn(FQDN);
        when(wifiInfo.getMacAddress()).thenReturn(wifiInfoMac);
        NetworkInfo networkInfo =
                new NetworkInfo(ConnectivityManager.TYPE_WIFI, 0 /* subtype */, "WIFI", "");
        networkInfo.setDetailedState(NetworkInfo.DetailedState.CONNECTED, "", "");
        PasspointWifiEntry entry = new PasspointWifiEntry(mMockInjector, mMockContext, mTestHandler,
                getPasspointConfiguration(), mMockWifiManager, mMockScoreCache,
                false /* forSavedNetworksPage */);

        entry.updateConnectionInfo(wifiInfo, networkInfo);

        assertThat(entry.getMacAddress()).isEqualTo(wifiInfoMac);
    }

    @Test
    public void testIsAutoJoinEnabled_nullConfigs_returnsFalse() {
        PasspointWifiEntry entry = new PasspointWifiEntry(mMockInjector, mMockContext, mTestHandler,
                getPasspointConfiguration(), mMockWifiManager, mMockScoreCache,
                false /* forSavedNetworksPage */);

        entry.updatePasspointConfig(null);

        assertThat(entry.isAutoJoinEnabled()).isFalse();
    }

    @Test
    public void testCanSignIn_captivePortalCapability_returnsTrue() {
        PasspointWifiEntry entry = new PasspointWifiEntry(mMockInjector, mMockContext, mTestHandler,
                getPasspointConfiguration(), mMockWifiManager, mMockScoreCache,
                false /* forSavedNetworksPage */);

        NetworkCapabilities captivePortalCapabilities = new NetworkCapabilities.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL).build();
        entry.updateNetworkCapabilities(captivePortalCapabilities);

        assertThat(entry.canSignIn()).isTrue();
    }

    @Test
    public void testUpdateNetworkCapabilities_userConnect_autoOpenCaptivePortalOnce() {
        when(mMockContext.getSystemService(Context.CONNECTIVITY_SERVICE))
                .thenReturn(mMockConnectivityManager);
        PasspointWifiEntry entry = new PasspointWifiEntry(mMockInjector, mMockContext, mTestHandler,
                getPasspointConfiguration(), mMockWifiManager, mMockScoreCache,
                false /* forSavedNetworksPage */);
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
    public void testDisconnect_noScansOrWifiConfig_disconnectIsSuccessful() {
        // Setup a connected PasspointWifiEntry
        String summarySeparator = " / ";
        String[] wifiStatusArray = new String[]{"", "Scanning", "Connecting",
                "Authenticating", "Obtaining IP address", "Connected"};
        Resources mockResources = mock(Resources.class);
        when(mMockContext.getResources()).thenReturn(mockResources);
        when(mockResources.getString(R.string.wifitrackerlib_summary_separator))
                .thenReturn(summarySeparator);
        when(mockResources.getStringArray(R.array.wifitrackerlib_wifi_status))
                .thenReturn(wifiStatusArray);
        ConnectivityManager mockConnectivityManager = mock(ConnectivityManager.class);
        when(mMockContext.getSystemService(Context.CONNECTIVITY_SERVICE))
                .thenReturn(mockConnectivityManager);
        final NetworkCapabilities networkCapabilities = new NetworkCapabilities.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED).build();
        WifiInfo wifiInfo = mock(WifiInfo.class);
        when(wifiInfo.isPasspointAp()).thenReturn(true);
        when(wifiInfo.getPasspointFqdn()).thenReturn(FQDN);
        NetworkInfo networkInfo =
                new NetworkInfo(ConnectivityManager.TYPE_WIFI, 0 /* subtype */, "WIFI", "");
        networkInfo.setDetailedState(NetworkInfo.DetailedState.CONNECTED, "", "");
        PasspointWifiEntry entry = new PasspointWifiEntry(mMockInjector, mMockContext, mTestHandler,
                getPasspointConfiguration(), mMockWifiManager, mMockScoreCache,
                false /* forSavedNetworksPage */);
        entry.updateConnectionInfo(wifiInfo, networkInfo);
        entry.updateNetworkCapabilities(networkCapabilities);

        // Disconnect the entry before it can be updated with scans and a WifiConfiguration
        entry.disconnect(null);

        verify(mMockWifiManager).disableEphemeralNetwork(FQDN);
        verify(mMockWifiManager).disconnect();
    }
}
