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

import static com.android.dx.mockito.inline.extended.ExtendedMockito.mockitoSession;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.verify;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.content.res.Resources;
import android.net.ConnectivityDiagnosticsManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.net.wifi.hotspot2.PasspointConfiguration;
import android.net.wifi.hotspot2.pps.Credential;
import android.net.wifi.hotspot2.pps.HomeSp;
import android.os.Handler;
import android.os.test.TestLooper;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.MockitoSession;

import java.util.Arrays;

public class PasspointWifiEntryTest {
    @Mock private WifiTrackerInjector mMockInjector;
    @Mock private Context mMockContext;
    @Mock private WifiManager mMockWifiManager;
    @Mock private Resources mMockResources;
    @Mock private WifiInfo mMockWifiInfo;
    @Mock private Network mMockNetwork;
    @Mock private NetworkCapabilities mMockNetworkCapabilities;
    @Mock private ConnectivityManager mMockConnectivityManager;

    private TestLooper mTestLooper;
    private Handler mTestHandler;

    private static final String FQDN = "fqdn";

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        mTestLooper = new TestLooper();
        mTestHandler = new Handler(mTestLooper.getLooper());

        when(mMockWifiInfo.isPrimary()).thenReturn(true);
        when(mMockWifiInfo.getNetworkId()).thenReturn(WifiConfiguration.INVALID_NETWORK_ID);
        when(mMockWifiInfo.getRssi()).thenReturn(WifiInfo.INVALID_RSSI);
        when(mMockNetworkCapabilities.getTransportInfo()).thenReturn(mMockWifiInfo);
        when(mMockInjector.getContext()).thenReturn(mMockContext);
        when(mMockContext.getResources()).thenReturn(mMockResources);
        when(mMockContext.getString(R.string.wifitrackerlib_summary_separator)).thenReturn("/");

        when(mMockContext.getSystemService(ConnectivityManager.class))
                .thenReturn(mMockConnectivityManager);
    }

    @Test
    public void testGetSummary_expiredTimeNotAvailable_notShowExpired() {
        // default SubscriptionExpirationTimeInMillis is unset
        PasspointConfiguration passpointConfiguration = getPasspointConfiguration();
        String expired = "Expired";
        when(mMockContext.getString(R.string.wifitrackerlib_wifi_passpoint_expired))
                .thenReturn(expired);

        PasspointWifiEntry passpointWifiEntry = new PasspointWifiEntry(mMockInjector,
                mTestHandler, passpointConfiguration, mMockWifiManager,
                false /* forSavedNetworksPage */);

        assertThat(passpointWifiEntry.getSummary()).isNotEqualTo(expired);
    }

    @Test
    public void testGetSummary_expired_showExpired() {
        PasspointConfiguration passpointConfiguration = getPasspointConfiguration();
        String expired = "Expired";
        when(mMockContext.getString(R.string.wifitrackerlib_wifi_passpoint_expired))
                .thenReturn(expired);
        PasspointWifiEntry passpointWifiEntry = new PasspointWifiEntry(mMockInjector,
                mTestHandler, passpointConfiguration, mMockWifiManager,
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
        PasspointWifiEntry entry = new PasspointWifiEntry(mMockInjector, mTestHandler,
                getPasspointConfiguration(), mMockWifiManager,
                false /* forSavedNetworksPage */);

        entry.setMeteredChoice(WifiEntry.METERED_CHOICE_UNMETERED);

        assertThat(entry.getMeteredChoice()).isEqualTo(WifiEntry.METERED_CHOICE_UNMETERED);
    }

    @Test
    public void testGetSummary_connectedWifiNetwork_showsConnected() {
        String summarySeparator = " / ";
        String[] wifiStatusArray = new String[]{"", "Scanning", "Connecting",
                "Authenticating", "Obtaining IP address", "Connected"};

        when(mMockContext.getString(R.string.wifitrackerlib_summary_separator))
                .thenReturn(summarySeparator);
        when(mMockResources.getStringArray(R.array.wifitrackerlib_wifi_status))
                .thenReturn(wifiStatusArray);
        when(mMockWifiInfo.isPasspointAp()).thenReturn(true);
        when(mMockWifiInfo.getPasspointFqdn()).thenReturn(FQDN);
        when(mMockNetworkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED))
                .thenReturn(true);

        PasspointWifiEntry entry = new PasspointWifiEntry(mMockInjector, mTestHandler,
                getPasspointConfiguration(), mMockWifiManager,
                false /* forSavedNetworksPage */);
        entry.onNetworkCapabilitiesChanged(mMockNetwork, mMockNetworkCapabilities);
        entry.onDefaultNetworkCapabilitiesChanged(mMockNetwork, mMockNetworkCapabilities);

        assertThat(entry.getSummary()).isEqualTo("Connected");
    }

    @Test
    public void testGetSummary_connectedButNotDefault_doesNotShowConnected() {
        String summarySeparator = " / ";
        String[] wifiStatusArray = new String[]{"", "Scanning", "Connecting",
                "Authenticating", "Obtaining IP address", "Connected"};

        when(mMockContext.getString(R.string.wifitrackerlib_summary_separator))
                .thenReturn(summarySeparator);
        when(mMockResources.getStringArray(R.array.wifitrackerlib_wifi_status))
                .thenReturn(wifiStatusArray);
        when(mMockWifiInfo.isPasspointAp()).thenReturn(true);
        when(mMockWifiInfo.getPasspointFqdn()).thenReturn(FQDN);
        when(mMockNetworkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED))
                .thenReturn(true);

        PasspointWifiEntry entry = new PasspointWifiEntry(mMockInjector, mTestHandler,
                getPasspointConfiguration(), mMockWifiManager,
                false /* forSavedNetworksPage */);
        entry.onNetworkCapabilitiesChanged(mMockNetwork, mMockNetworkCapabilities);

        assertThat(entry.getSummary()).isEqualTo("");
    }

    @Test
    public void testGetSecurityTypes_connectedWifiNetwork_showsCurrentSecurityType() {
        when(mMockWifiInfo.isPasspointAp()).thenReturn(true);
        when(mMockWifiInfo.getPasspointFqdn()).thenReturn(FQDN);
        when(mMockWifiInfo.getCurrentSecurityType())
                .thenReturn(WifiInfo.SECURITY_TYPE_PASSPOINT_R1_R2);

        PasspointWifiEntry entry = new PasspointWifiEntry(mMockInjector, mTestHandler,
                getPasspointConfiguration(), mMockWifiManager,
                false /* forSavedNetworksPage */);

        assertThat(entry.getSecurityTypes()).containsExactlyElementsIn(Arrays.asList(
                WifiInfo.SECURITY_TYPE_PASSPOINT_R1_R2,
                WifiInfo.SECURITY_TYPE_PASSPOINT_R3));

        entry.onNetworkCapabilitiesChanged(mMockNetwork, mMockNetworkCapabilities);

        assertThat(entry.getSecurityTypes())
                .containsExactly(WifiInfo.SECURITY_TYPE_PASSPOINT_R1_R2);

        when(mMockWifiInfo.getCurrentSecurityType())
                .thenReturn(WifiInfo.SECURITY_TYPE_PASSPOINT_R3);
        entry.onNetworkCapabilitiesChanged(mMockNetwork, mMockNetworkCapabilities);

        assertThat(entry.getSecurityTypes()).containsExactly(WifiInfo.SECURITY_TYPE_PASSPOINT_R3);
    }

    @Test
    public void testGetSecurityString_showsPasspoint() {
        PasspointConfiguration passpointConfiguration = getPasspointConfiguration();
        String passpointSecurity = "Passpoint";
        when(mMockContext.getString(R.string.wifitrackerlib_wifi_security_passpoint))
                .thenReturn(passpointSecurity);

        PasspointWifiEntry passpointWifiEntry = new PasspointWifiEntry(mMockInjector,
                mTestHandler, passpointConfiguration, mMockWifiManager,
                false /* forSavedNetworksPage */);

        assertThat(passpointWifiEntry.getSecurityString(false)).isEqualTo(passpointSecurity);
    }

    @Test
    public void testShouldShowXLevelIcon_unvalidatedOrLowQuality_returnsTrue() {
        when(mMockWifiInfo.isPasspointAp()).thenReturn(true);
        when(mMockWifiInfo.getPasspointFqdn()).thenReturn(FQDN);

        PasspointWifiEntry entry = new PasspointWifiEntry(mMockInjector, mTestHandler,
                getPasspointConfiguration(), mMockWifiManager,
                false /* forSavedNetworksPage */);

        // Disconnected should return false;
        assertThat(entry.shouldShowXLevelIcon()).isEqualTo(false);

        // Connected but validation attempt not complete, should not show X level icon yet.
        entry.onNetworkCapabilitiesChanged(mMockNetwork, mMockNetworkCapabilities);
        assertThat(entry.shouldShowXLevelIcon()).isEqualTo(false);

        // Validation attempt complete, should show X level icon.
        ConnectivityDiagnosticsManager.ConnectivityReport connectivityReport = mock(
                ConnectivityDiagnosticsManager.ConnectivityReport.class);
        when(connectivityReport.getNetwork()).thenReturn(mMockNetwork);
        entry.updateConnectivityReport(connectivityReport);
        assertThat(entry.shouldShowXLevelIcon()).isEqualTo(true);

        // Internet validated, should not show X level icon.
        when(mMockNetworkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED))
                .thenReturn(true);
        entry.onNetworkCapabilitiesChanged(mMockNetwork, mMockNetworkCapabilities);
        assertThat(entry.shouldShowXLevelIcon()).isEqualTo(false);

        // Cell becomes default (i.e. low quality wifi), show X level icon.
        entry.onDefaultNetworkCapabilitiesChanged(Mockito.mock(Network.class),
                new NetworkCapabilities.Builder()
                        .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR).build());
        assertThat(entry.shouldShowXLevelIcon()).isEqualTo(true);
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
        when(mMockWifiInfo.isPasspointAp()).thenReturn(true);
        when(mMockWifiInfo.getPasspointFqdn()).thenReturn(FQDN);
        when(mMockWifiInfo.getMacAddress()).thenReturn(wifiInfoMac);
        PasspointWifiEntry entry = new PasspointWifiEntry(mMockInjector, mTestHandler,
                getPasspointConfiguration(), mMockWifiManager,
                false /* forSavedNetworksPage */);

        entry.onNetworkCapabilitiesChanged(mMockNetwork, mMockNetworkCapabilities);

        assertThat(entry.getMacAddress()).isEqualTo(wifiInfoMac);
    }

    @Test
    public void testIsAutoJoinEnabled_nullConfigs_returnsFalse() {
        PasspointWifiEntry entry = new PasspointWifiEntry(mMockInjector, mTestHandler,
                getPasspointConfiguration(), mMockWifiManager,
                false /* forSavedNetworksPage */);

        entry.updatePasspointConfig(null);

        assertThat(entry.isAutoJoinEnabled()).isFalse();
    }

    @Test
    public void testCanSignIn_captivePortalCapability_returnsTrue() {
        PasspointWifiEntry entry = new PasspointWifiEntry(mMockInjector, mTestHandler,
                getPasspointConfiguration(), mMockWifiManager,
                false /* forSavedNetworksPage */);

        when(mMockWifiInfo.isPasspointAp()).thenReturn(true);
        when(mMockWifiInfo.getPasspointFqdn()).thenReturn(FQDN);
        when(mMockNetworkCapabilities.hasCapability(
                NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL)).thenReturn(true);
        entry.onNetworkCapabilitiesChanged(mMockNetwork, mMockNetworkCapabilities);

        assertThat(entry.canSignIn()).isTrue();
    }

    @Test
    public void testUpdateNetworkCapabilities_userConnect_autoOpenCaptivePortalOnce() {
        PasspointWifiEntry entry = new PasspointWifiEntry(mMockInjector, mTestHandler,
                getPasspointConfiguration(), mMockWifiManager,
                false /* forSavedNetworksPage */);

        MockitoSession session = mockitoSession().spyStatic(NonSdkApiWrapper.class).startMocking();
        try {
            // Simulate user tapping on the network and receiving captive portal capabilities.
            // This should trigger the captive portal app.
            entry.connect(null /* callback */);
            when(mMockWifiInfo.isPasspointAp()).thenReturn(true);
            when(mMockWifiInfo.getPasspointFqdn()).thenReturn(FQDN);
            when(mMockNetworkCapabilities.hasCapability(
                    NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL)).thenReturn(true);
            entry.onNetworkCapabilitiesChanged(mMockNetwork, mMockNetworkCapabilities);

            verify(() -> NonSdkApiWrapper.startCaptivePortalApp(any(), any()), times(1));

            // Update network capabilities again. This should not trigger the captive portal app.
            entry.onNetworkCapabilitiesChanged(mMockNetwork, mMockNetworkCapabilities);

            verify(() -> NonSdkApiWrapper.startCaptivePortalApp(any(), any()), times(1));
        } finally {
            session.finishMocking();
        }

    }

    @Test
    public void testDisconnect_noScansOrWifiConfig_disconnectIsSuccessful() {
        // Setup a connected PasspointWifiEntry
        String summarySeparator = " / ";
        String[] wifiStatusArray = new String[]{"", "Scanning", "Connecting",
                "Authenticating", "Obtaining IP address", "Connected"};
        when(mMockContext.getString(R.string.wifitrackerlib_summary_separator))
                .thenReturn(summarySeparator);
        when(mMockResources.getStringArray(R.array.wifitrackerlib_wifi_status))
                .thenReturn(wifiStatusArray);
        when(mMockWifiInfo.isPasspointAp()).thenReturn(true);
        when(mMockWifiInfo.getPasspointFqdn()).thenReturn(FQDN);
        when(mMockNetworkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED))
                .thenReturn(true);
        PasspointWifiEntry entry = new PasspointWifiEntry(mMockInjector, mTestHandler,
                getPasspointConfiguration(), mMockWifiManager,
                false /* forSavedNetworksPage */);
        entry.onNetworkCapabilitiesChanged(mMockNetwork, mMockNetworkCapabilities);

        // Disconnect the entry before it can be updated with scans and a WifiConfiguration
        entry.disconnect(null);

        verify(mMockWifiManager).disableEphemeralNetwork(FQDN);
        verify(mMockWifiManager).disconnect();
    }
}
