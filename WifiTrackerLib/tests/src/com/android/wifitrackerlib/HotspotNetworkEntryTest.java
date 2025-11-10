/*
 * Copyright (C) 2023 The Android Open Source Project
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

import static android.net.wifi.ScanResult.WIFI_STANDARD_11N;
import static android.net.wifi.WifiInfo.SECURITY_TYPE_PSK;
import static android.net.wifi.WifiInfo.SECURITY_TYPE_SAE;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.mockitoSession;
import static com.android.wifitrackerlib.WifiEntry.CONNECTED_STATE_CONNECTED;
import static com.android.wifitrackerlib.WifiEntry.CONNECTED_STATE_DISCONNECTED;
import static com.android.wifitrackerlib.WifiEntry.MIN_FREQ_24GHZ;
import static com.android.wifitrackerlib.WifiEntry.WIFI_LEVEL_MAX;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.content.res.Resources;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.net.wifi.sharedconnectivity.app.HotspotNetwork;
import android.net.wifi.sharedconnectivity.app.HotspotNetworkConnectionStatus;
import android.net.wifi.sharedconnectivity.app.NetworkProviderInfo;
import android.net.wifi.sharedconnectivity.app.SharedConnectivityManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.test.TestLooper;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.MockitoSession;

public class HotspotNetworkEntryTest {
    @Rule
    public final CheckFlagsRule mCheckFlagsRule =
            DeviceFlagsValueProvider.createCheckFlagsRule();

    @Mock private WifiEntry.WifiEntryCallback mMockListener;
    @Mock private WifiEntry.ConnectCallback mMockConnectCallback;
    @Mock private WifiEntry.DisconnectCallback mMockDisconnectCallback;
    @Mock private WifiTrackerInjector mMockInjector;
    @Mock private Context mMockContext;
    @Mock private Resources mMockResources;
    @Mock private WifiManager mMockWifiManager;
    @Mock private SharedConnectivityManager mMockSharedConnectivityManager;
    @Mock private WifiInfo mMockWifiInfo;
    @Mock private Network mMockNetwork;
    @Mock private NetworkCapabilities mMockNetworkCapabilities;

    private TestLooper mTestLooper;
    private Handler mTestHandler;

    private static final HotspotNetwork TEST_HOTSPOT_NETWORK_DATA = new HotspotNetwork.Builder()
            .setDeviceId(1)
            .setNetworkProviderInfo(new NetworkProviderInfo
                    .Builder("My Pixel", "Pixel 7")
                    .setDeviceType(NetworkProviderInfo.DEVICE_TYPE_PHONE)
                    .setBatteryPercentage(100)
                    .setConnectionStrength(3)
                    .build())
            .setHostNetworkType(HotspotNetwork.NETWORK_TYPE_CELLULAR)
            .setNetworkName("Google Fi")
            .setHotspotSsid("Instant Hotspot abcde")
            .addHotspotSecurityType(SECURITY_TYPE_PSK)
            .build();

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        mTestLooper = new TestLooper();
        mTestHandler = new Handler(mTestLooper.getLooper());

        when(mMockNetworkCapabilities.getTransportInfo()).thenReturn(mMockWifiInfo);
        when(mMockWifiInfo.isPrimary()).thenReturn(true);
        when(mMockWifiInfo.getSSID()).thenReturn("Instant Hotspot abcde");
        when(mMockWifiInfo.getCurrentSecurityType()).thenReturn(SECURITY_TYPE_PSK);
        when(mMockWifiInfo.getRssi()).thenReturn(TestUtils.GOOD_RSSI);
        when(mMockWifiInfo.getMacAddress()).thenReturn("01:02:03:04:05:06");
        when(mMockWifiInfo.getWifiStandard()).thenReturn(WIFI_STANDARD_11N);
        when(mMockWifiInfo.getFrequency()).thenReturn(MIN_FREQ_24GHZ);

        when(mMockContext.getString(R.string.wifitrackerlib_hotspot_network_connecting))
                .thenReturn("Connecting…");
        when(mMockContext.getString(eq(R.string.wifitrackerlib_hotspot_network_summary),
                anyString(), anyString()))
                .thenAnswer(invocation -> {
                    Object[] args = invocation.getArguments();
                    return args[1] + " from " + args[2];
                });
        when(mMockContext.getString(R.string.wifitrackerlib_hotspot_network_summary_new))
                .thenReturn(
                        "{DEVICE_TYPE, select, PHONE {{NETWORK_NAME} from your phone} TABLET "
                                + "{{NETWORK_NAME} from your tablet} COMPUTER {{NETWORK_NAME} "
                                + "from your computer} WATCH {{NETWORK_NAME} from your watch} "
                                + "VEHICLE {{NETWORK_NAME} from your vehicle} other "
                                + "{{NETWORK_NAME} from your device}}");
        when(mMockContext.getString(R.string.wifitrackerlib_hotspot_network_summary_error_generic))
                .thenReturn("Can't connect. Try connecting again.");
        when(mMockContext.getString(R.string.wifitrackerlib_hotspot_network_summary_error_settings))
                .thenReturn(
                        "{DEVICE_TYPE, select, PHONE {Can't connect. Check phone settings and try"
                                + " again.} TABLET {Can't connect. Check tablet settings and try "
                                + "again.} COMPUTER {Can't connect. Check computer settings and "
                                + "try again.} WATCH {Can't connect. Check watch settings and try"
                                + " again.} VEHICLE {Can't connect. Check vehicle settings and "
                                + "try again.} other {Can't connect. Check device settings and "
                                + "try again.}}");
        when(mMockContext.getString(
                eq(R.string.wifitrackerlib_hotspot_network_summary_error_carrier_block),
                anyString())).thenAnswer(invocation -> {
                    Object[] args = invocation.getArguments();
                    return args[1] + " doesn't allow this connection";
                });
        when(mMockContext.getString(
                eq(R.string.wifitrackerlib_hotspot_network_summary_error_carrier_incomplete),
                anyString())).thenAnswer(invocation -> {
                    Object[] args = invocation.getArguments();
                    return "Can't connect. Contact " + args[1] + " for help.";
                });
        when(mMockContext.getString(eq(R.string.wifitrackerlib_hotspot_network_alternate),
                anyString(), anyString())).thenAnswer(invocation -> {
                    Object[] args = invocation.getArguments();
                    return args[1] + " from " + args[2];
                });
        when(mMockContext.getString(R.string.wifitrackerlib_wifi_security_wpa_wpa2))
                .thenReturn("WPA/WPA2-Personal");
        when(mMockContext.getString(R.string.wifitrackerlib_wifi_standard_11n))
                .thenReturn("Wi‑Fi 4");
        when(mMockContext.getResources()).thenReturn(mMockResources);
        when(mMockResources.getString(R.string.wifitrackerlib_wifi_band_24_ghz)).thenReturn(
                "2.4 GHz");
        when(mMockResources.getString(R.string.wifitrackerlib_multiband_separator)).thenReturn(
                ", ");
    }

    @Test
    public void testConnectionInfoMatches_matchesSsidAndSecurity() {
        final HotspotNetworkEntry entry = new HotspotNetworkEntry(
                mMockInjector, mMockContext, mTestHandler,
                mMockWifiManager, mMockSharedConnectivityManager, TEST_HOTSPOT_NETWORK_DATA);
        when(mMockWifiInfo.getSSID()).thenReturn("Instant Hotspot fghij");
        when(mMockWifiInfo.getCurrentSecurityType()).thenReturn(SECURITY_TYPE_SAE);

        assertThat(entry.connectionInfoMatches(mMockWifiInfo)).isFalse();

        when(mMockWifiInfo.getSSID()).thenReturn("Instant Hotspot abcde");
        when(mMockWifiInfo.getCurrentSecurityType()).thenReturn(SECURITY_TYPE_PSK);

        assertThat(entry.connectionInfoMatches(mMockWifiInfo)).isTrue();
    }

    @Test
    public void testOnNetworkCapabilitiesChanged_matchingSsidAndSecurity_becomesConnected() {
        final HotspotNetworkEntry entry = new HotspotNetworkEntry(
                mMockInjector, mMockContext, mTestHandler,
                mMockWifiManager, mMockSharedConnectivityManager, TEST_HOTSPOT_NETWORK_DATA);

        // Ignore non-matching SSID and security type
        when(mMockWifiInfo.getSSID()).thenReturn("Instant Hotspot fghij");
        when(mMockWifiInfo.getCurrentSecurityType()).thenReturn(SECURITY_TYPE_SAE);
        entry.onNetworkCapabilitiesChanged(mMockNetwork, mMockNetworkCapabilities);
        assertThat(entry.getConnectedState()).isEqualTo(CONNECTED_STATE_DISCONNECTED);
        assertThat(entry.canConnect()).isTrue();
        assertThat(entry.canDisconnect()).isFalse();

        // Matching SSID and security type should result in connected
        when(mMockWifiInfo.getSSID()).thenReturn("Instant Hotspot abcde");
        when(mMockWifiInfo.getCurrentSecurityType()).thenReturn(SECURITY_TYPE_PSK);
        entry.onNetworkCapabilitiesChanged(mMockNetwork, mMockNetworkCapabilities);
        assertThat(entry.getConnectedState()).isEqualTo(CONNECTED_STATE_CONNECTED);
        assertThat(entry.canConnect()).isFalse();
        assertThat(entry.canDisconnect()).isTrue();
    }

    @Test
    public void testOnNetworkLost_matchingNetwork_becomesDisconnected() {
        final HotspotNetworkEntry entry = new HotspotNetworkEntry(
                mMockInjector, mMockContext, mTestHandler,
                mMockWifiManager, mMockSharedConnectivityManager, TEST_HOTSPOT_NETWORK_DATA);
        entry.onNetworkCapabilitiesChanged(mMockNetwork, mMockNetworkCapabilities);

        // Non-matching network loss should be ignored
        entry.onNetworkLost(mock(Network.class));
        assertThat(entry.getConnectedState()).isEqualTo(CONNECTED_STATE_CONNECTED);
        assertThat(entry.canConnect()).isFalse();
        assertThat(entry.canDisconnect()).isTrue();

        // Matching network loss should result in disconnected
        entry.onNetworkLost(mMockNetwork);
        assertThat(entry.getConnectedState()).isEqualTo(CONNECTED_STATE_DISCONNECTED);
        assertThat(entry.canConnect()).isTrue();
        assertThat(entry.canDisconnect()).isFalse();
    }

    @Test
    public void testGetTitle_usesDeviceName() {
        final HotspotNetworkEntry entry = new HotspotNetworkEntry(
                mMockInjector, mMockContext, mTestHandler,
                mMockWifiManager, mMockSharedConnectivityManager, TEST_HOTSPOT_NETWORK_DATA);

        assertThat(entry.getTitle()).isEqualTo("My Pixel");
    }

    @Test
    public void testGetSummary_phone_usesHotspotNetworkData() {
        final HotspotNetworkEntry entry = new HotspotNetworkEntry(
                mMockInjector, mMockContext, mTestHandler,
                mMockWifiManager, mMockSharedConnectivityManager, TEST_HOTSPOT_NETWORK_DATA);

        assertThat(entry.getSummary()).isEqualTo("Google Fi from your phone");
    }

    @Test
    public void testGetSummary_tablet_usesHotspotNetworkData() {
        HotspotNetwork testNetwork = new HotspotNetwork.Builder()
                .setDeviceId(1)
                .setNetworkProviderInfo(new NetworkProviderInfo
                        .Builder("My Pixel", "Pixel 7")
                        .setDeviceType(NetworkProviderInfo.DEVICE_TYPE_TABLET)
                        .setBatteryPercentage(100)
                        .setConnectionStrength(3)
                        .build())
                .setHostNetworkType(HotspotNetwork.NETWORK_TYPE_CELLULAR)
                .setNetworkName("Google Fi")
                .setHotspotSsid("Instant Hotspot abcde")
                .addHotspotSecurityType(SECURITY_TYPE_PSK)
                .build();
        final HotspotNetworkEntry entry = new HotspotNetworkEntry(
                mMockInjector, mMockContext, mTestHandler, mMockWifiManager,
                mMockSharedConnectivityManager, testNetwork);

        assertThat(entry.getSummary()).isEqualTo("Google Fi from your tablet");
    }

    @Test
    public void testGetSummary_computer_usesHotspotNetworkData() {
        HotspotNetwork testNetwork = new HotspotNetwork.Builder()
                .setDeviceId(1)
                .setNetworkProviderInfo(new NetworkProviderInfo
                        .Builder("My Pixel", "Pixel 7")
                        .setDeviceType(NetworkProviderInfo.DEVICE_TYPE_LAPTOP)
                        .setBatteryPercentage(100)
                        .setConnectionStrength(3)
                        .build())
                .setHostNetworkType(HotspotNetwork.NETWORK_TYPE_CELLULAR)
                .setNetworkName("Google Fi")
                .setHotspotSsid("Instant Hotspot abcde")
                .addHotspotSecurityType(SECURITY_TYPE_PSK)
                .build();
        final HotspotNetworkEntry entry = new HotspotNetworkEntry(
                mMockInjector, mMockContext, mTestHandler, mMockWifiManager,
                mMockSharedConnectivityManager, testNetwork);

        assertThat(entry.getSummary()).isEqualTo("Google Fi from your computer");
    }

    @Test
    public void testGetSummary_watch_usesHotspotNetworkData() {
        HotspotNetwork testNetwork = new HotspotNetwork.Builder()
                .setDeviceId(1)
                .setNetworkProviderInfo(new NetworkProviderInfo
                        .Builder("My Pixel", "Pixel 7")
                        .setDeviceType(NetworkProviderInfo.DEVICE_TYPE_WATCH)
                        .setBatteryPercentage(100)
                        .setConnectionStrength(3)
                        .build())
                .setHostNetworkType(HotspotNetwork.NETWORK_TYPE_CELLULAR)
                .setNetworkName("Google Fi")
                .setHotspotSsid("Instant Hotspot abcde")
                .addHotspotSecurityType(SECURITY_TYPE_PSK)
                .build();
        final HotspotNetworkEntry entry = new HotspotNetworkEntry(
                mMockInjector, mMockContext, mTestHandler, mMockWifiManager,
                mMockSharedConnectivityManager, testNetwork);

        assertThat(entry.getSummary()).isEqualTo("Google Fi from your watch");
    }

    @Test
    public void testGetSummary_vehicle_usesHotspotNetworkData() {
        HotspotNetwork testNetwork = new HotspotNetwork.Builder()
                .setDeviceId(1)
                .setNetworkProviderInfo(new NetworkProviderInfo
                        .Builder("My Pixel", "Pixel 7")
                        .setDeviceType(NetworkProviderInfo.DEVICE_TYPE_AUTO)
                        .setBatteryPercentage(100)
                        .setConnectionStrength(3)
                        .build())
                .setHostNetworkType(HotspotNetwork.NETWORK_TYPE_CELLULAR)
                .setNetworkName("Google Fi")
                .setHotspotSsid("Instant Hotspot abcde")
                .addHotspotSecurityType(SECURITY_TYPE_PSK)
                .build();
        final HotspotNetworkEntry entry = new HotspotNetworkEntry(
                mMockInjector, mMockContext, mTestHandler, mMockWifiManager,
                mMockSharedConnectivityManager, testNetwork);

        assertThat(entry.getSummary()).isEqualTo("Google Fi from your vehicle");
    }

    @Test
    public void testGetSummary_unknown_usesHotspotNetworkData() {
        HotspotNetwork testNetwork = new HotspotNetwork.Builder()
                .setDeviceId(1)
                .setNetworkProviderInfo(new NetworkProviderInfo
                        .Builder("My Pixel", "Pixel 7")
                        .setDeviceType(NetworkProviderInfo.DEVICE_TYPE_UNKNOWN)
                        .setBatteryPercentage(100)
                        .setConnectionStrength(3)
                        .build())
                .setHostNetworkType(HotspotNetwork.NETWORK_TYPE_CELLULAR)
                .setNetworkName("Google Fi")
                .setHotspotSsid("Instant Hotspot abcde")
                .addHotspotSecurityType(SECURITY_TYPE_PSK)
                .build();
        final HotspotNetworkEntry entry = new HotspotNetworkEntry(
                mMockInjector, mMockContext, mTestHandler, mMockWifiManager,
                mMockSharedConnectivityManager, testNetwork);

        assertThat(entry.getSummary()).isEqualTo("Google Fi from your device");
    }

    @Test
    public void testGetAlternateSummary_usesHotspotNetworkData() {
        final HotspotNetworkEntry entry = new HotspotNetworkEntry(
                mMockInjector, mMockContext, mTestHandler,
                mMockWifiManager, mMockSharedConnectivityManager, TEST_HOTSPOT_NETWORK_DATA);

        assertThat(entry.getAlternateSummary()).isEqualTo("Google Fi from My Pixel");
    }

    @Test
    public void testGetSummary_connectionStatusEnabling_returnsConnectingString() {
        final HotspotNetworkEntry entry = new HotspotNetworkEntry(
                mMockInjector, mMockContext, mTestHandler,
                mMockWifiManager, mMockSharedConnectivityManager, TEST_HOTSPOT_NETWORK_DATA);

        entry.setListener(mMockListener);
        entry.connect(mMockConnectCallback);
        entry.onConnectionStatusChanged(
                HotspotNetworkConnectionStatus.CONNECTION_STATUS_ENABLING_HOTSPOT);
        mTestLooper.dispatchAll();

        verify(mMockConnectCallback, never()).onConnectResult(anyInt());

        assertThat(entry.getSummary()).isEqualTo("Connecting…");
    }

    @Test
    public void testGetSummary_connectionStatusFailure_resetsConnectingString() {
        final HotspotNetworkEntry entry = new HotspotNetworkEntry(
                mMockInjector, mMockContext, mTestHandler,
                mMockWifiManager, mMockSharedConnectivityManager, TEST_HOTSPOT_NETWORK_DATA);
        entry.setListener(mMockListener);
        entry.connect(mMockConnectCallback);
        entry.onConnectionStatusChanged(
                HotspotNetworkConnectionStatus.CONNECTION_STATUS_ENABLING_HOTSPOT);
        mTestLooper.dispatchAll();
        assertThat(entry.getSummary()).isEqualTo("Connecting…");

        entry.onConnectionStatusChanged(
                HotspotNetworkConnectionStatus.CONNECTION_STATUS_UNKNOWN_ERROR);
        mTestLooper.dispatchAll();

        assertThat(entry.getSummary()).isNotEqualTo("Connecting…");
    }

    @Test
    public void testGetSummary_connectionSuccess_resetsConnectingString() {
        final HotspotNetworkEntry entry = new HotspotNetworkEntry(
                mMockInjector, mMockContext, mTestHandler,
                mMockWifiManager, mMockSharedConnectivityManager, TEST_HOTSPOT_NETWORK_DATA);
        entry.setListener(mMockListener);
        entry.connect(mMockConnectCallback);
        entry.onConnectionStatusChanged(
                HotspotNetworkConnectionStatus.CONNECTION_STATUS_ENABLING_HOTSPOT);
        mTestLooper.dispatchAll();

        entry.onNetworkCapabilitiesChanged(mMockNetwork, mMockNetworkCapabilities);
        entry.onNetworkLost(mMockNetwork);

        assertThat(entry.getSummary()).isNotEqualTo("Connecting…");
    }

    @Test
    public void testGetSsid_usesHotspotNetworkData() {
        final HotspotNetworkEntry entry = new HotspotNetworkEntry(
                mMockInjector, mMockContext, mTestHandler,
                mMockWifiManager, mMockSharedConnectivityManager, TEST_HOTSPOT_NETWORK_DATA);

        assertThat(entry.getSsid()).isEqualTo("Instant Hotspot abcde");
    }

    @Test
    public void testGetMacAddress_usesWifiInfo() {
        final HotspotNetworkEntry entry = new HotspotNetworkEntry(
                mMockInjector, mMockContext, mTestHandler,
                mMockWifiManager, mMockSharedConnectivityManager, TEST_HOTSPOT_NETWORK_DATA);
        entry.onNetworkCapabilitiesChanged(mMockNetwork, mMockNetworkCapabilities);

        assertThat(entry.getMacAddress()).isEqualTo("01:02:03:04:05:06");
    }

    @Test
    public void testGetPrivacy_returnsRandomized() {
        final HotspotNetworkEntry entry = new HotspotNetworkEntry(
                mMockInjector, mMockContext, mTestHandler,
                mMockWifiManager, mMockSharedConnectivityManager, TEST_HOTSPOT_NETWORK_DATA);

        assertThat(entry.getPrivacy()).isEqualTo(HotspotNetworkEntry.PRIVACY_RANDOMIZED_MAC);
    }

    @Test
    public void testGetSecurityString_usesHotspotNetworkData() {
        final HotspotNetworkEntry entry = new HotspotNetworkEntry(
                mMockInjector, mMockContext, mTestHandler,
                mMockWifiManager, mMockSharedConnectivityManager, TEST_HOTSPOT_NETWORK_DATA);

        assertThat(entry.getSecurityString(false)).isEqualTo("WPA/WPA2-Personal");
    }

    @Test
    public void testGetStandardString_usesWifiInfo() {
        final HotspotNetworkEntry entry = new HotspotNetworkEntry(
                mMockInjector, mMockContext, mTestHandler,
                mMockWifiManager, mMockSharedConnectivityManager, TEST_HOTSPOT_NETWORK_DATA);
        entry.onNetworkCapabilitiesChanged(mMockNetwork, mMockNetworkCapabilities);

        assertThat(entry.getStandardString()).isEqualTo("Wi‑Fi 4");
    }

    @Test
    public void testGetBandString_usesWifiInfo() {
        final HotspotNetworkEntry entry = new HotspotNetworkEntry(
                mMockInjector, mMockContext, mTestHandler,
                mMockWifiManager, mMockSharedConnectivityManager, TEST_HOTSPOT_NETWORK_DATA);
        entry.onNetworkCapabilitiesChanged(mMockNetwork, mMockNetworkCapabilities);

        assertThat(entry.getBandString()).isEqualTo("2.4 GHz");
    }

    @Test
    public void testGetUpstreamConnectionStrength_usesHotspotNetworkData() {
        final HotspotNetworkEntry entry = new HotspotNetworkEntry(
                mMockInjector, mMockContext, mTestHandler,
                mMockWifiManager, mMockSharedConnectivityManager, TEST_HOTSPOT_NETWORK_DATA);

        assertThat(entry.getUpstreamConnectionStrength()).isEqualTo(3);
    }

    @Test
    public void testGetNetworkType_usesHotspotNetworkData() {
        final HotspotNetworkEntry entry = new HotspotNetworkEntry(
                mMockInjector, mMockContext, mTestHandler,
                mMockWifiManager, mMockSharedConnectivityManager, TEST_HOTSPOT_NETWORK_DATA);

        assertThat(entry.getNetworkType()).isEqualTo(HotspotNetwork.NETWORK_TYPE_CELLULAR);
    }

    @Test
    public void testGetDeviceType_usesHotspotNetworkData() {
        final HotspotNetworkEntry entry = new HotspotNetworkEntry(
                mMockInjector, mMockContext, mTestHandler,
                mMockWifiManager, mMockSharedConnectivityManager, TEST_HOTSPOT_NETWORK_DATA);

        assertThat(entry.getDeviceType()).isEqualTo(NetworkProviderInfo.DEVICE_TYPE_PHONE);
    }

    @Test
    public void testGetBatteryPercentage_usesHotspotNetworkData() {
        final HotspotNetworkEntry entry = new HotspotNetworkEntry(
                mMockInjector, mMockContext, mTestHandler,
                mMockWifiManager, mMockSharedConnectivityManager, TEST_HOTSPOT_NETWORK_DATA);

        assertThat(entry.getBatteryPercentage()).isEqualTo(100);
    }

    @Test
    public void testIsBatteryCharging_apiFlagOn_usesHotspotNetworkDataApi() {
        final HotspotNetworkEntry entry = new HotspotNetworkEntry(
                mMockInjector, mMockContext, mTestHandler,
                mMockWifiManager, mMockSharedConnectivityManager,
                new HotspotNetwork.Builder()
                        .setDeviceId(1)
                        .setNetworkProviderInfo(new NetworkProviderInfo
                                .Builder("My Phone", "Pixel 7")
                                .setDeviceType(NetworkProviderInfo.DEVICE_TYPE_PHONE)
                                .setBatteryPercentage(100)
                                .setConnectionStrength(3)
                                .setBatteryCharging(true)
                                .build())
                        .setHostNetworkType(HotspotNetwork.NETWORK_TYPE_CELLULAR)
                        .setNetworkName("Google Fi")
                        .setHotspotSsid("Instant Hotspot abcde")
                        .addHotspotSecurityType(SECURITY_TYPE_PSK)
                        .build());

        MockitoSession session = mockitoSession().spyStatic(NonSdkApiWrapper.class).startMocking();
        try {
            doReturn(true).when(() ->
                    NonSdkApiWrapper.isNetworkProviderBatteryChargingStatusEnabled());
            assertThat(entry.isBatteryCharging()).isTrue();
        } finally {
            session.finishMocking();
        }
    }

    @Test
    public void testIsBatteryCharging_apiFlagOn_usesHotspotNetworkDataExtras() {
        final Bundle extras = new Bundle();
        extras.putBoolean(HotspotNetworkEntry.EXTRA_KEY_IS_BATTERY_CHARGING, true);
        final HotspotNetworkEntry entry = new HotspotNetworkEntry(
                mMockInjector, mMockContext, mTestHandler,
                mMockWifiManager, mMockSharedConnectivityManager,
                new HotspotNetwork.Builder()
                        .setDeviceId(1)
                        .setNetworkProviderInfo(new NetworkProviderInfo
                                .Builder("My Phone", "Pixel 7")
                                .setDeviceType(NetworkProviderInfo.DEVICE_TYPE_PHONE)
                                .setBatteryPercentage(100)
                                .setConnectionStrength(3)
                                .setBatteryCharging(false)
                                .setExtras(extras)
                                .build())
                        .setHostNetworkType(HotspotNetwork.NETWORK_TYPE_CELLULAR)
                        .setNetworkName("Google Fi")
                        .setHotspotSsid("Instant Hotspot abcde")
                        .addHotspotSecurityType(SECURITY_TYPE_PSK)
                        .build());

        MockitoSession session = mockitoSession().spyStatic(NonSdkApiWrapper.class).startMocking();
        try {
            doReturn(true).when(() ->
                    NonSdkApiWrapper.isNetworkProviderBatteryChargingStatusEnabled());
            assertThat(entry.isBatteryCharging()).isTrue();
        } finally {
            session.finishMocking();
        }
    }

    @Test
    public void testIsBatteryCharging_apiFlagOff_usesHotspotNetworkDataExtras() {
        final Bundle extras = new Bundle();
        extras.putBoolean(HotspotNetworkEntry.EXTRA_KEY_IS_BATTERY_CHARGING, true);
        final HotspotNetworkEntry entry = new HotspotNetworkEntry(
                mMockInjector, mMockContext, mTestHandler,
                mMockWifiManager, mMockSharedConnectivityManager,
                new HotspotNetwork.Builder()
                        .setDeviceId(1)
                        .setNetworkProviderInfo(new NetworkProviderInfo
                                .Builder("My Phone", "Pixel 7")
                                .setDeviceType(NetworkProviderInfo.DEVICE_TYPE_PHONE)
                                .setBatteryPercentage(100)
                                .setConnectionStrength(3)
                                .setExtras(extras)
                                .build())
                        .setHostNetworkType(HotspotNetwork.NETWORK_TYPE_CELLULAR)
                        .setNetworkName("Google Fi")
                        .setHotspotSsid("Instant Hotspot abcde")
                        .addHotspotSecurityType(SECURITY_TYPE_PSK)
                        .build());

        MockitoSession session = mockitoSession().spyStatic(NonSdkApiWrapper.class).startMocking();
        try {
            doReturn(false).when(() ->
                    NonSdkApiWrapper.isNetworkProviderBatteryChargingStatusEnabled());
            assertThat(entry.isBatteryCharging()).isTrue();
        } finally {
            session.finishMocking();
        }
    }

    @Test
    public void testIsBatteryCharging_apiFlagOn_extraFalse() {
        final Bundle extras = new Bundle();
        extras.putBoolean(HotspotNetworkEntry.EXTRA_KEY_IS_BATTERY_CHARGING, false);
        final HotspotNetworkEntry entry = new HotspotNetworkEntry(
                mMockInjector, mMockContext, mTestHandler,
                mMockWifiManager, mMockSharedConnectivityManager,
                new HotspotNetwork.Builder()
                        .setDeviceId(1)
                        .setNetworkProviderInfo(new NetworkProviderInfo
                                .Builder("My Phone", "Pixel 7")
                                .setDeviceType(NetworkProviderInfo.DEVICE_TYPE_PHONE)
                                .setBatteryPercentage(100)
                                .setConnectionStrength(3)
                                .setExtras(extras)
                                .build())
                        .setHostNetworkType(HotspotNetwork.NETWORK_TYPE_CELLULAR)
                        .setNetworkName("Google Fi")
                        .setHotspotSsid("Instant Hotspot abcde")
                        .addHotspotSecurityType(SECURITY_TYPE_PSK)
                        .build());

        MockitoSession session = mockitoSession().spyStatic(NonSdkApiWrapper.class).startMocking();
        try {
            doReturn(true).when(() ->
                    NonSdkApiWrapper.isNetworkProviderBatteryChargingStatusEnabled());
            assertThat(entry.isBatteryCharging()).isFalse();
        } finally {
            session.finishMocking();
        }
    }

    @Test
    public void testIsBatteryCharging_apiFlagOn_apiFalse() {
        final HotspotNetworkEntry entry = new HotspotNetworkEntry(
                mMockInjector, mMockContext, mTestHandler,
                mMockWifiManager, mMockSharedConnectivityManager,
                new HotspotNetwork.Builder()
                        .setDeviceId(1)
                        .setNetworkProviderInfo(new NetworkProviderInfo
                                .Builder("My Phone", "Pixel 7")
                                .setDeviceType(NetworkProviderInfo.DEVICE_TYPE_PHONE)
                                .setBatteryPercentage(100)
                                .setConnectionStrength(3)
                                .setBatteryCharging(false)
                                .build())
                        .setHostNetworkType(HotspotNetwork.NETWORK_TYPE_CELLULAR)
                        .setNetworkName("Google Fi")
                        .setHotspotSsid("Instant Hotspot abcde")
                        .addHotspotSecurityType(SECURITY_TYPE_PSK)
                        .build());

        MockitoSession session = mockitoSession().spyStatic(NonSdkApiWrapper.class).startMocking();
        try {
            doReturn(true).when(() ->
                    NonSdkApiWrapper.isNetworkProviderBatteryChargingStatusEnabled());
            assertThat(entry.isBatteryCharging()).isFalse();
        } finally {
            session.finishMocking();
        }
    }

    @Test
    public void testIsBatteryCharging_apiFlagOn_noneSet() {
        final HotspotNetworkEntry entry = new HotspotNetworkEntry(
                mMockInjector, mMockContext, mTestHandler,
                mMockWifiManager, mMockSharedConnectivityManager,
                new HotspotNetwork.Builder()
                        .setDeviceId(1)
                        .setNetworkProviderInfo(new NetworkProviderInfo
                                .Builder("My Phone", "Pixel 7")
                                .setDeviceType(NetworkProviderInfo.DEVICE_TYPE_PHONE)
                                .setBatteryPercentage(100)
                                .setConnectionStrength(3)
                                .build())
                        .setHostNetworkType(HotspotNetwork.NETWORK_TYPE_CELLULAR)
                        .setNetworkName("Google Fi")
                        .setHotspotSsid("Instant Hotspot abcde")
                        .addHotspotSecurityType(SECURITY_TYPE_PSK)
                        .build());

        MockitoSession session = mockitoSession().spyStatic(NonSdkApiWrapper.class).startMocking();
        try {
            doReturn(true).when(() ->
                    NonSdkApiWrapper.isNetworkProviderBatteryChargingStatusEnabled());
            assertThat(entry.isBatteryCharging()).isFalse();
        } finally {
            session.finishMocking();
        }
    }

    @Test
    public void testGetLevel_statusNotConnected_returnsMaxValue() {
        final HotspotNetworkEntry entry = new HotspotNetworkEntry(
                mMockInjector, mMockContext, mTestHandler,
                mMockWifiManager, mMockSharedConnectivityManager, TEST_HOTSPOT_NETWORK_DATA);

        when(mMockWifiInfo.getSSID()).thenReturn("Instant Hotspot fghij");
        when(mMockWifiInfo.getCurrentSecurityType()).thenReturn(SECURITY_TYPE_SAE);
        entry.onNetworkCapabilitiesChanged(mMockNetwork, mMockNetworkCapabilities);

        assertThat(entry.getLevel()).isEqualTo(WIFI_LEVEL_MAX);
    }

    @Test
    public void testConnect_serviceCalled() {
        final HotspotNetworkEntry entry = new HotspotNetworkEntry(
                mMockInjector, mMockContext, mTestHandler,
                mMockWifiManager, mMockSharedConnectivityManager, TEST_HOTSPOT_NETWORK_DATA);

        entry.connect(null);
        verify(mMockSharedConnectivityManager).connectHotspotNetwork(TEST_HOTSPOT_NETWORK_DATA);
    }

    @Test
    public void testConnect_nullManager_failureCallback() {
        final HotspotNetworkEntry entry = new HotspotNetworkEntry(
                mMockInjector, mMockContext, mTestHandler,
                mMockWifiManager, /* sharedConnectivityManager= */ null, TEST_HOTSPOT_NETWORK_DATA);

        entry.setListener(mMockListener);
        entry.connect(mMockConnectCallback);
        mTestLooper.dispatchAll();
        verify(mMockConnectCallback)
                .onConnectResult(WifiEntry.ConnectCallback.CONNECT_STATUS_FAILURE_UNKNOWN);
    }

    @Test
    public void testConnect_onConnectionStatusChanged_failureCallback() {
        final HotspotNetworkEntry entry = new HotspotNetworkEntry(
                mMockInjector, mMockContext, mTestHandler,
                mMockWifiManager, mMockSharedConnectivityManager, TEST_HOTSPOT_NETWORK_DATA);

        entry.setListener(mMockListener);
        entry.connect(mMockConnectCallback);
        mTestLooper.dispatchAll();
        verify(mMockConnectCallback, never()).onConnectResult(anyInt());

        entry.onConnectionStatusChanged(
                HotspotNetworkConnectionStatus.CONNECTION_STATUS_UNKNOWN_ERROR);
        mTestLooper.dispatchAll();
        verify(mMockConnectCallback, times(1))
                .onConnectResult(WifiEntry.ConnectCallback.CONNECT_STATUS_FAILURE_UNKNOWN);

        entry.onConnectionStatusChanged(
                HotspotNetworkConnectionStatus.CONNECTION_STATUS_PROVISIONING_FAILED);
        mTestLooper.dispatchAll();
        verify(mMockConnectCallback, times(2))
                .onConnectResult(WifiEntry.ConnectCallback.CONNECT_STATUS_FAILURE_UNKNOWN);

        entry.onConnectionStatusChanged(
                HotspotNetworkConnectionStatus.CONNECTION_STATUS_TETHERING_TIMEOUT);
        mTestLooper.dispatchAll();
        verify(mMockConnectCallback, times(3))
                .onConnectResult(WifiEntry.ConnectCallback.CONNECT_STATUS_FAILURE_UNKNOWN);

        entry.onConnectionStatusChanged(
                HotspotNetworkConnectionStatus.CONNECTION_STATUS_TETHERING_UNSUPPORTED);
        mTestLooper.dispatchAll();
        verify(mMockConnectCallback, times(4))
                .onConnectResult(WifiEntry.ConnectCallback.CONNECT_STATUS_FAILURE_UNKNOWN);

        entry.onConnectionStatusChanged(
                HotspotNetworkConnectionStatus.CONNECTION_STATUS_NO_CELL_DATA);
        mTestLooper.dispatchAll();
        verify(mMockConnectCallback, times(5))
                .onConnectResult(WifiEntry.ConnectCallback.CONNECT_STATUS_FAILURE_UNKNOWN);

        entry.onConnectionStatusChanged(
                HotspotNetworkConnectionStatus.CONNECTION_STATUS_ENABLING_HOTSPOT_FAILED);
        mTestLooper.dispatchAll();
        verify(mMockConnectCallback, times(6))
                .onConnectResult(WifiEntry.ConnectCallback.CONNECT_STATUS_FAILURE_UNKNOWN);

        entry.onConnectionStatusChanged(
                HotspotNetworkConnectionStatus.CONNECTION_STATUS_ENABLING_HOTSPOT_TIMEOUT);
        mTestLooper.dispatchAll();
        verify(mMockConnectCallback, times(7))
                .onConnectResult(WifiEntry.ConnectCallback.CONNECT_STATUS_FAILURE_UNKNOWN);

        entry.onConnectionStatusChanged(
                HotspotNetworkConnectionStatus.CONNECTION_STATUS_CONNECT_TO_HOTSPOT_FAILED);
        mTestLooper.dispatchAll();
        verify(mMockConnectCallback, times(8))
                .onConnectResult(WifiEntry.ConnectCallback.CONNECT_STATUS_FAILURE_UNKNOWN);
    }

    @Test
    public void testDisconnect_serviceCalled() {
        final HotspotNetworkEntry entry = new HotspotNetworkEntry(
                mMockInjector, mMockContext, mTestHandler,
                mMockWifiManager, mMockSharedConnectivityManager, TEST_HOTSPOT_NETWORK_DATA);

        entry.disconnect(null);
        verify(mMockSharedConnectivityManager).disconnectHotspotNetwork(TEST_HOTSPOT_NETWORK_DATA);
    }

    @Test
    public void testDisconnect_nullManager_failureCallback() {
        final HotspotNetworkEntry entry = new HotspotNetworkEntry(
                mMockInjector, mMockContext, mTestHandler,
                mMockWifiManager, /* sharedConnectivityManager= */ null, TEST_HOTSPOT_NETWORK_DATA);

        entry.setListener(mMockListener);
        entry.disconnect(mMockDisconnectCallback);
        mTestLooper.dispatchAll();
        verify(mMockDisconnectCallback)
                .onDisconnectResult(WifiEntry.DisconnectCallback.DISCONNECT_STATUS_FAILURE_UNKNOWN);
    }

    @Test
    public void testOnConnectionStatusChanged_withoutConnect_updatesString() {
        final HotspotNetworkEntry entry = new HotspotNetworkEntry(
                mMockInjector, mMockContext, mTestHandler,
                mMockWifiManager, mMockSharedConnectivityManager, TEST_HOTSPOT_NETWORK_DATA);

        entry.setListener(mMockListener);
        entry.onConnectionStatusChanged(
                HotspotNetworkConnectionStatus.CONNECTION_STATUS_ENABLING_HOTSPOT);
        mTestLooper.dispatchAll();

        assertThat(entry.getSummary()).isEqualTo("Connecting…");

        entry.onConnectionStatusChanged(
                HotspotNetworkConnectionStatus.CONNECTION_STATUS_UNKNOWN_ERROR);
        mTestLooper.dispatchAll();

        assertThat(entry.getSummary()).isNotEqualTo("Connecting…");
    }

    @Test
    public void testOnConnectionStatusChanged_connectedStatus_updatesString() {
        final HotspotNetworkEntry entry = new HotspotNetworkEntry(
                mMockInjector, mMockContext, mTestHandler,
                mMockWifiManager, mMockSharedConnectivityManager, TEST_HOTSPOT_NETWORK_DATA);
        entry.setListener(mMockListener);
        entry.onConnectionStatusChanged(
                HotspotNetworkConnectionStatus.CONNECTION_STATUS_ENABLING_HOTSPOT);
        mTestLooper.dispatchAll();
        assertThat(entry.getSummary()).isEqualTo("Connecting…");

        entry.onConnectionStatusChanged(HotspotNetworkEntry.CONNECTION_STATUS_CONNECTED);
        mTestLooper.dispatchAll();

        assertThat(entry.getSummary()).isNotEqualTo("Connecting…");
    }

    @Test
    public void testOnConnectionStatusChanged_connectedStatus_callsCallback() {
        final HotspotNetworkEntry entry = new HotspotNetworkEntry(
                mMockInjector, mMockContext, mTestHandler,
                mMockWifiManager, mMockSharedConnectivityManager, TEST_HOTSPOT_NETWORK_DATA);
        entry.setListener(mMockListener);
        entry.connect(mMockConnectCallback);
        entry.onConnectionStatusChanged(
                HotspotNetworkConnectionStatus.CONNECTION_STATUS_ENABLING_HOTSPOT);
        mTestLooper.dispatchAll();
        verify(mMockConnectCallback, never()).onConnectResult(anyInt());

        entry.onConnectionStatusChanged(HotspotNetworkEntry.CONNECTION_STATUS_CONNECTED);
        mTestLooper.dispatchAll();

        verify(mMockConnectCallback)
                .onConnectResult(WifiEntry.ConnectCallback.CONNECT_STATUS_SUCCESS);
    }

    @Test
    public void testGetSummary_connectionStatusFailureGeneric_displaysErrorInSummary() {
        final HotspotNetworkEntry entry = new HotspotNetworkEntry(
                mMockInjector, mMockContext, mTestHandler,
                mMockWifiManager, mMockSharedConnectivityManager, TEST_HOTSPOT_NETWORK_DATA);
        entry.setListener(mMockListener);

        entry.onConnectionStatusChanged(
                HotspotNetworkConnectionStatus.CONNECTION_STATUS_UNKNOWN_ERROR);
        mTestLooper.dispatchAll();

        assertThat(entry.getSummary()).isEqualTo("Can't connect. Try connecting again.");
    }

    @Test
    public void testGetSummary_connectionStatusFailureSettings_phone_displaysErrorInSummary() {
        final HotspotNetworkEntry entry = new HotspotNetworkEntry(
                mMockInjector, mMockContext, mTestHandler,
                mMockWifiManager, mMockSharedConnectivityManager, TEST_HOTSPOT_NETWORK_DATA);
        entry.setListener(mMockListener);

        entry.onConnectionStatusChanged(
                HotspotNetworkConnectionStatus.CONNECTION_STATUS_CONNECT_TO_HOTSPOT_FAILED);
        mTestLooper.dispatchAll();

        assertThat(entry.getSummary()).isEqualTo(
                "Can't connect. Check phone settings and try again.");
    }

    @Test
    public void testGetSummary_connectionStatusFailureSettings_tablet_displaysErrorInSummary() {
        HotspotNetwork testNetwork = new HotspotNetwork.Builder()
                .setDeviceId(1)
                .setNetworkProviderInfo(new NetworkProviderInfo
                        .Builder("My Pixel", "Pixel 7")
                        .setDeviceType(NetworkProviderInfo.DEVICE_TYPE_TABLET)
                        .setBatteryPercentage(100)
                        .setConnectionStrength(3)
                        .build())
                .setHostNetworkType(HotspotNetwork.NETWORK_TYPE_CELLULAR)
                .setNetworkName("Google Fi")
                .setHotspotSsid("Instant Hotspot abcde")
                .addHotspotSecurityType(SECURITY_TYPE_PSK)
                .build();
        final HotspotNetworkEntry entry = new HotspotNetworkEntry(
                mMockInjector, mMockContext, mTestHandler,
                mMockWifiManager, mMockSharedConnectivityManager, testNetwork);
        entry.setListener(mMockListener);

        entry.onConnectionStatusChanged(
                HotspotNetworkConnectionStatus.CONNECTION_STATUS_CONNECT_TO_HOTSPOT_FAILED);
        mTestLooper.dispatchAll();

        assertThat(entry.getSummary()).isEqualTo(
                "Can't connect. Check tablet settings and try again.");
    }

    @Test
    public void testGetSummary_connectionStatusFailureSettings_computer_displaysErrorInSummary() {
        HotspotNetwork testNetwork = new HotspotNetwork.Builder()
                .setDeviceId(1)
                .setNetworkProviderInfo(new NetworkProviderInfo
                        .Builder("My Pixel", "Pixel 7")
                        .setDeviceType(NetworkProviderInfo.DEVICE_TYPE_LAPTOP)
                        .setBatteryPercentage(100)
                        .setConnectionStrength(3)
                        .build())
                .setHostNetworkType(HotspotNetwork.NETWORK_TYPE_CELLULAR)
                .setNetworkName("Google Fi")
                .setHotspotSsid("Instant Hotspot abcde")
                .addHotspotSecurityType(SECURITY_TYPE_PSK)
                .build();
        final HotspotNetworkEntry entry = new HotspotNetworkEntry(
                mMockInjector, mMockContext, mTestHandler,
                mMockWifiManager, mMockSharedConnectivityManager, testNetwork);
        entry.setListener(mMockListener);

        entry.onConnectionStatusChanged(
                HotspotNetworkConnectionStatus.CONNECTION_STATUS_CONNECT_TO_HOTSPOT_FAILED);
        mTestLooper.dispatchAll();

        assertThat(entry.getSummary()).isEqualTo(
                "Can't connect. Check computer settings and try again.");
    }

    @Test
    public void testGetSummary_connectionStatusFailureSettings_watch_displaysErrorInSummary() {
        HotspotNetwork testNetwork = new HotspotNetwork.Builder()
                .setDeviceId(1)
                .setNetworkProviderInfo(new NetworkProviderInfo
                        .Builder("My Pixel", "Pixel 7")
                        .setDeviceType(NetworkProviderInfo.DEVICE_TYPE_WATCH)
                        .setBatteryPercentage(100)
                        .setConnectionStrength(3)
                        .build())
                .setHostNetworkType(HotspotNetwork.NETWORK_TYPE_CELLULAR)
                .setNetworkName("Google Fi")
                .setHotspotSsid("Instant Hotspot abcde")
                .addHotspotSecurityType(SECURITY_TYPE_PSK)
                .build();
        final HotspotNetworkEntry entry = new HotspotNetworkEntry(
                mMockInjector, mMockContext, mTestHandler,
                mMockWifiManager, mMockSharedConnectivityManager, testNetwork);
        entry.setListener(mMockListener);

        entry.onConnectionStatusChanged(
                HotspotNetworkConnectionStatus.CONNECTION_STATUS_CONNECT_TO_HOTSPOT_FAILED);
        mTestLooper.dispatchAll();

        assertThat(entry.getSummary()).isEqualTo(
                "Can't connect. Check watch settings and try again.");
    }

    @Test
    public void testGetSummary_connectionStatusFailureSettings_vehicle_displaysErrorInSummary() {
        HotspotNetwork testNetwork = new HotspotNetwork.Builder()
                .setDeviceId(1)
                .setNetworkProviderInfo(new NetworkProviderInfo
                        .Builder("My Pixel", "Pixel 7")
                        .setDeviceType(NetworkProviderInfo.DEVICE_TYPE_AUTO)
                        .setBatteryPercentage(100)
                        .setConnectionStrength(3)
                        .build())
                .setHostNetworkType(HotspotNetwork.NETWORK_TYPE_CELLULAR)
                .setNetworkName("Google Fi")
                .setHotspotSsid("Instant Hotspot abcde")
                .addHotspotSecurityType(SECURITY_TYPE_PSK)
                .build();
        final HotspotNetworkEntry entry = new HotspotNetworkEntry(
                mMockInjector, mMockContext, mTestHandler,
                mMockWifiManager, mMockSharedConnectivityManager, testNetwork);
        entry.setListener(mMockListener);

        entry.onConnectionStatusChanged(
                HotspotNetworkConnectionStatus.CONNECTION_STATUS_CONNECT_TO_HOTSPOT_FAILED);
        mTestLooper.dispatchAll();

        assertThat(entry.getSummary()).isEqualTo(
                "Can't connect. Check vehicle settings and try again.");
    }

    @Test
    public void testGetSummary_connectionStatusFailureSettings_unknown_displaysErrorInSummary() {
        HotspotNetwork testNetwork = new HotspotNetwork.Builder()
                .setDeviceId(1)
                .setNetworkProviderInfo(new NetworkProviderInfo
                        .Builder("My Pixel", "Pixel 7")
                        .setDeviceType(NetworkProviderInfo.DEVICE_TYPE_UNKNOWN)
                        .setBatteryPercentage(100)
                        .setConnectionStrength(3)
                        .build())
                .setHostNetworkType(HotspotNetwork.NETWORK_TYPE_CELLULAR)
                .setNetworkName("Google Fi")
                .setHotspotSsid("Instant Hotspot abcde")
                .addHotspotSecurityType(SECURITY_TYPE_PSK)
                .build();
        final HotspotNetworkEntry entry = new HotspotNetworkEntry(
                mMockInjector, mMockContext, mTestHandler,
                mMockWifiManager, mMockSharedConnectivityManager, testNetwork);
        entry.setListener(mMockListener);

        entry.onConnectionStatusChanged(
                HotspotNetworkConnectionStatus.CONNECTION_STATUS_CONNECT_TO_HOTSPOT_FAILED);
        mTestLooper.dispatchAll();

        assertThat(entry.getSummary()).isEqualTo(
                "Can't connect. Check device settings and try again.");
    }

    @Test
    public void testGetSummary_connectionStatusFailureCarrierBlock_displaysErrorInSummary() {
        final HotspotNetworkEntry entry = new HotspotNetworkEntry(
                mMockInjector, mMockContext, mTestHandler,
                mMockWifiManager, mMockSharedConnectivityManager, TEST_HOTSPOT_NETWORK_DATA);
        entry.setListener(mMockListener);

        entry.onConnectionStatusChanged(
                HotspotNetworkConnectionStatus.CONNECTION_STATUS_TETHERING_UNSUPPORTED);
        mTestLooper.dispatchAll();

        assertThat(entry.getSummary()).isEqualTo("Google Fi doesn't allow this connection");
    }

    @Test
    public void testGetSummary_connectionStatusFailureCarrierIncomplete_displaysErrorInSummary() {
        final HotspotNetworkEntry entry = new HotspotNetworkEntry(
                mMockInjector, mMockContext, mTestHandler,
                mMockWifiManager, mMockSharedConnectivityManager, TEST_HOTSPOT_NETWORK_DATA);
        entry.setListener(mMockListener);

        entry.onConnectionStatusChanged(
                HotspotNetworkConnectionStatus.CONNECTION_STATUS_TETHERING_TIMEOUT);
        mTestLooper.dispatchAll();

        assertThat(entry.getSummary()).isEqualTo("Can't connect. Contact Google Fi for help.");
    }

    @Test
    public void testGetSummary_connectionStatusConnecting_resetsErrorString() {
        final HotspotNetworkEntry entry = new HotspotNetworkEntry(
                mMockInjector, mMockContext, mTestHandler,
                mMockWifiManager, mMockSharedConnectivityManager, TEST_HOTSPOT_NETWORK_DATA);
        entry.setListener(mMockListener);
        entry.connect(mMockConnectCallback);
        entry.onConnectionStatusChanged(
                HotspotNetworkConnectionStatus.CONNECTION_STATUS_UNKNOWN_ERROR);
        mTestLooper.dispatchAll();
        assertThat(entry.getSummary()).isEqualTo("Can't connect. Try connecting again.");

        entry.onConnectionStatusChanged(
                HotspotNetworkConnectionStatus.CONNECTION_STATUS_ENABLING_HOTSPOT);
        mTestLooper.dispatchAll();

        assertThat(entry.getSummary()).isNotEqualTo("Can't connect. Try connecting again.");
    }

    @Test
    public void testGetSummary_connectionStatusConnected_resetsErrorString() {
        final HotspotNetworkEntry entry = new HotspotNetworkEntry(
                mMockInjector, mMockContext, mTestHandler,
                mMockWifiManager, mMockSharedConnectivityManager, TEST_HOTSPOT_NETWORK_DATA);
        entry.setListener(mMockListener);
        entry.connect(mMockConnectCallback);
        entry.onConnectionStatusChanged(
                HotspotNetworkConnectionStatus.CONNECTION_STATUS_UNKNOWN_ERROR);
        mTestLooper.dispatchAll();
        assertThat(entry.getSummary()).isEqualTo("Can't connect. Try connecting again.");

        entry.onConnectionStatusChanged(HotspotNetworkEntry.CONNECTION_STATUS_CONNECTED);
        mTestLooper.dispatchAll();

        assertThat(entry.getSummary()).isNotEqualTo("Can't connect. Try connecting again.");
    }
}
