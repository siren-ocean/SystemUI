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
import static android.net.wifi.WifiInfo.SECURITY_TYPE_SAE;
import static android.net.wifi.WifiInfo.SECURITY_TYPE_WEP;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.mockitoSession;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.verify;
import static com.android.wifitrackerlib.TestUtils.BAD_RSSI;
import static com.android.wifitrackerlib.TestUtils.GOOD_LEVEL;
import static com.android.wifitrackerlib.TestUtils.GOOD_RSSI;
import static com.android.wifitrackerlib.TestUtils.buildScanResult;
import static com.android.wifitrackerlib.TestUtils.buildWifiConfiguration;
import static com.android.wifitrackerlib.WifiEntry.CONNECTED_STATE_CONNECTED;
import static com.android.wifitrackerlib.WifiEntry.CONNECTED_STATE_DISCONNECTED;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.net.ConnectivityDiagnosticsManager;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.net.TransportInfo;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiEnterpriseConfig;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiScanner;
import android.net.wifi.hotspot2.PasspointConfiguration;
import android.net.wifi.hotspot2.pps.Credential;
import android.net.wifi.hotspot2.pps.HomeSp;
import android.net.wifi.sharedconnectivity.app.HotspotNetwork;
import android.net.wifi.sharedconnectivity.app.HotspotNetworkConnectionStatus;
import android.net.wifi.sharedconnectivity.app.KnownNetwork;
import android.net.wifi.sharedconnectivity.app.KnownNetworkConnectionStatus;
import android.net.wifi.sharedconnectivity.app.NetworkProviderInfo;
import android.net.wifi.sharedconnectivity.app.SharedConnectivityClientCallback;
import android.net.wifi.sharedconnectivity.app.SharedConnectivityManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.test.TestLooper;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.util.ArrayMap;
import android.util.Pair;

import androidx.annotation.NonNull;
import androidx.lifecycle.Lifecycle;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.MockitoSession;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class WifiPickerTrackerTest {

    private static final long START_MILLIS = 123_456_789;

    private static final long MAX_SCAN_AGE_MILLIS = 15_000;
    private static final long SCAN_INTERVAL_MILLIS = 10_000;

    @Mock private WifiTrackerInjector mInjector;
    @Mock private Lifecycle mMockLifecycle;
    @Mock private Context mMockContext;
    @Mock private Resources mMockResources;
    @Mock private WifiManager mMockWifiManager;
    @Mock private WifiScanner mWifiScanner;
    @Mock private ConnectivityManager mMockConnectivityManager;
    @Mock private ConnectivityDiagnosticsManager mMockConnectivityDiagnosticsManager;
    @Mock private TelephonyManager mMockTelephonyManager;
    @Mock private SubscriptionManager mMockSubscriptionManager;
    @Mock private Clock mMockClock;
    @Mock private WifiPickerTracker.WifiPickerTrackerCallback mMockCallback;
    @Mock private WifiInfo mMockWifiInfo;
    @Mock private Network mMockNetwork;
    @Mock private NetworkCapabilities mMockNetworkCapabilities;
    @Mock private NetworkCapabilities mMockVcnNetworkCapabilities;
    @Mock private LinkProperties mMockLinkProperties;
    @Mock private SharedConnectivityManager mMockSharedConnectivityManager;

    private TestLooper mTestLooper;

    private final ArgumentCaptor<BroadcastReceiver> mBroadcastReceiverCaptor =
            ArgumentCaptor.forClass(BroadcastReceiver.class);
    private final ArgumentCaptor<ConnectivityManager.NetworkCallback>
            mNetworkCallbackCaptor =
                ArgumentCaptor.forClass(ConnectivityManager.NetworkCallback.class);
    private final ArgumentCaptor<ConnectivityManager.NetworkCallback>
            mDefaultNetworkCallbackCaptor =
                ArgumentCaptor.forClass(ConnectivityManager.NetworkCallback.class);
    private final ArgumentCaptor<SharedConnectivityClientCallback>
            mSharedConnectivityCallbackCaptor =
            ArgumentCaptor.forClass(SharedConnectivityClientCallback.class);

    private WifiPickerTracker createTestWifiPickerTracker() {
        final Handler testHandler = new Handler(mTestLooper.getLooper());

        return new WifiPickerTracker(
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
                mMockCallback);
    }

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        mTestLooper = new TestLooper();

        when(mMockWifiManager.getScanResults()).thenReturn(new ArrayList<>());
        when(mMockWifiManager.getCurrentNetwork()).thenReturn(mMockNetwork);
        when(mMockWifiManager.getWifiState()).thenReturn(WifiManager.WIFI_STATE_ENABLED);
        when(mMockWifiManager.isWpa3SaeSupported()).thenReturn(true);
        when(mMockWifiManager.isWpa3SuiteBSupported()).thenReturn(true);
        when(mMockWifiManager.isEnhancedOpenSupported()).thenReturn(true);
        when(mMockWifiManager.calculateSignalLevel(TestUtils.GOOD_RSSI))
                .thenReturn(TestUtils.GOOD_LEVEL);
        when(mMockWifiManager.calculateSignalLevel(TestUtils.OKAY_RSSI))
                .thenReturn(TestUtils.OKAY_LEVEL);
        when(mMockWifiManager.calculateSignalLevel(TestUtils.BAD_RSSI))
                .thenReturn(TestUtils.BAD_LEVEL);
        when(mMockClock.millis()).thenReturn(START_MILLIS);
        when(mMockWifiInfo.isPrimary()).thenReturn(true);
        when(mMockWifiInfo.getNetworkId()).thenReturn(WifiConfiguration.INVALID_NETWORK_ID);
        when(mMockWifiInfo.getRssi()).thenReturn(WifiInfo.INVALID_RSSI);
        when(mMockWifiInfo.makeCopy(anyLong())).thenReturn(mMockWifiInfo);
        when(mMockWifiManager.getConnectionInfo()).thenReturn(mMockWifiInfo);
        when(mMockNetworkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI))
                .thenReturn(true);
        when(mMockNetworkCapabilities.getTransportInfo()).thenReturn(mMockWifiInfo);
        when(mMockVcnNetworkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR))
                .thenReturn(true);
        // Use a placeholder TransportInfo since VcnTransportInfo is @hide.
        // NonSdkApiWrapper is mocked to get the WifiInfo from these capabilities.
        when(mMockVcnNetworkCapabilities.getTransportInfo()).thenReturn(
                new TransportInfo() {
                    @NonNull
                    @Override
                    public TransportInfo makeCopy(long redactions) {
                        return TransportInfo.super.makeCopy(redactions);
                    }
                });
        // A real NetworkCapabilities is needed here in order to create a copy (with location info)
        // using the NetworkCapabilities constructor in handleOnStart.
        NetworkCapabilities realNetCaps = new NetworkCapabilities.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .setTransportInfo(mock(WifiInfo.class))
                .build();
        when(mMockConnectivityManager.getNetworkCapabilities(mMockNetwork)).thenReturn(realNetCaps);
        when(mMockConnectivityManager.getLinkProperties(mMockNetwork))
                .thenReturn(mMockLinkProperties);
        when(mMockSharedConnectivityManager.unregisterCallback(any())).thenReturn(true);
        when(mInjector.getContext()).thenReturn(mMockContext);
        when(mMockContext.getResources()).thenReturn(mMockResources);
        when(mMockContext.getSystemService(ConnectivityManager.class))
                .thenReturn(mMockConnectivityManager);
        when(mMockContext.getSystemService(TelephonyManager.class))
                .thenReturn(mMockTelephonyManager);
        when(mMockContext.getSystemService(SubscriptionManager.class))
                .thenReturn(mMockSubscriptionManager);
        when(mMockContext.getSystemService(ConnectivityDiagnosticsManager.class))
                .thenReturn(mMockConnectivityDiagnosticsManager);
        when(mMockContext.getSystemService(WifiScanner.class)).thenReturn(mWifiScanner);
        when(mMockContext.getSystemService(SharedConnectivityManager.class))
                .thenReturn(mMockSharedConnectivityManager);
        when(mMockContext.getString(anyInt())).thenReturn("");
        when(mMockResources.getStringArray(R.array.wifitrackerlib_wifi_status)).thenReturn(
                new String[]{"", "Scanning", "Connecting", "Authenticating", "Obtaining IP address",
                        "Connected", "Suspended", "Disconnecting", "Unsuccessful", "Blocked",
                        "Temporarily avoiding poor connection"});
        when(mInjector.isSharedConnectivityFeatureEnabled()).thenReturn(true);
    }

    /**
     * Tests that receiving a wifi state change broadcast updates getWifiState().
     */
    @Test
    public void testWifiStateChangeBroadcast_updatesWifiState() {
        final WifiPickerTracker wifiPickerTracker = createTestWifiPickerTracker();
        wifiPickerTracker.onStart();
        mTestLooper.dispatchAll();
        verify(mMockContext).registerReceiver(mBroadcastReceiverCaptor.capture(),
                any(), any(), any());

        // Set the wifi state to disabled
        mBroadcastReceiverCaptor.getValue().onReceive(mMockContext,
                new Intent(WifiManager.WIFI_STATE_CHANGED_ACTION)
                        .putExtra(WifiManager.EXTRA_WIFI_STATE, WifiManager.WIFI_STATE_DISABLED));

        assertThat(wifiPickerTracker.getWifiState()).isEqualTo(WifiManager.WIFI_STATE_DISABLED);

        // Change the wifi state to enabled
        mBroadcastReceiverCaptor.getValue().onReceive(mMockContext,
                new Intent(WifiManager.WIFI_STATE_CHANGED_ACTION)
                        .putExtra(WifiManager.EXTRA_WIFI_STATE, WifiManager.WIFI_STATE_ENABLED));

        assertThat(wifiPickerTracker.getWifiState()).isEqualTo(WifiManager.WIFI_STATE_ENABLED);
    }

    /**
     * Tests that receiving a wifi state change broadcast notifies the listener.
     */
    @Test
    public void testWifiStateChangeBroadcast_notifiesListener() {
        final WifiPickerTracker wifiPickerTracker = createTestWifiPickerTracker();
        wifiPickerTracker.onStart();
        mTestLooper.dispatchAll();
        verify(mMockContext).registerReceiver(mBroadcastReceiverCaptor.capture(),
                any(), any(), any());

        mBroadcastReceiverCaptor.getValue().onReceive(mMockContext,
                new Intent(WifiManager.WIFI_STATE_CHANGED_ACTION));
        mTestLooper.dispatchAll();

        verify(mMockCallback, atLeastOnce()).onWifiStateChanged();
    }

    /**
     * Tests that a CONFIGURED_NETWORKS_CHANGED broadcast notifies the listener for
     * numSavedNetworksChanged.
     */
    @Test
    public void testConfiguredNetworksChanged_notifiesListener() {
        final WifiPickerTracker wifiPickerTracker = createTestWifiPickerTracker();
        wifiPickerTracker.onStart();
        mTestLooper.dispatchAll();
        verify(mMockContext).registerReceiver(mBroadcastReceiverCaptor.capture(),
                any(), any(), any());

        mBroadcastReceiverCaptor.getValue().onReceive(mMockContext,
                new Intent(WifiManager.CONFIGURED_NETWORKS_CHANGED_ACTION));
        mTestLooper.dispatchAll();

        verify(mMockCallback, atLeastOnce()).onNumSavedNetworksChanged();
    }

    /**
     * Tests that receiving a scan results available broadcast notifies the listener.
     */
    @Test
    public void testScanResultsAvailableAction_notifiesListener() {
        final WifiPickerTracker wifiPickerTracker = createTestWifiPickerTracker();
        wifiPickerTracker.onStart();
        mTestLooper.dispatchAll();
        verify(mMockContext).registerReceiver(mBroadcastReceiverCaptor.capture(),
                any(), any(), any());

        mBroadcastReceiverCaptor.getValue().onReceive(mMockContext,
                new Intent(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION));
        mTestLooper.dispatchAll();

        verify(mMockCallback, atLeastOnce())
                .onWifiEntriesChanged(WifiPickerTracker.WIFI_ENTRIES_CHANGED_REASON_SCAN_RESULTS);
    }

    /**
     * Tests that an empty list of WifiEntries is returned if no scans are available.
     */
    @Test
    public void testGetWifiEntries_noScans_emptyList() {
        final WifiPickerTracker wifiPickerTracker = createTestWifiPickerTracker();
        wifiPickerTracker.onStart();
        mTestLooper.dispatchAll();
        verify(mMockContext).registerReceiver(mBroadcastReceiverCaptor.capture(),
                any(), any(), any());

        when(mMockWifiManager.getScanResults()).thenReturn(new ArrayList<>());

        mBroadcastReceiverCaptor.getValue().onReceive(mMockContext,
                new Intent(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION));

        assertThat(wifiPickerTracker.getWifiEntries()).isEmpty();
    }


    /**
     * Tests that a StandardWifiEntry is returned by getWifiEntries() for each non-null, non-empty
     * SSID/Security pair in the tracked scan results.
     */
    @Test
    public void testGetWifiEntries_wifiNetworkEntries_createdForEachSsidAndSecurityPair() {
        final WifiPickerTracker wifiPickerTracker = createTestWifiPickerTracker();
        wifiPickerTracker.onStart();
        mTestLooper.dispatchAll();
        verify(mMockContext).registerReceiver(mBroadcastReceiverCaptor.capture(),
                any(), any(), any());

        final ScanResult openNetwork = buildScanResult("Open Network", "bssid0", START_MILLIS);
        final ScanResult openNetworkDup = buildScanResult("Open Network", "bssid1", START_MILLIS);
        final ScanResult secureNetwork = buildScanResult("Secure Network", "bssid2", START_MILLIS);
        secureNetwork.capabilities = "EAP";

        when(mMockWifiManager.getScanResults()).thenReturn(Arrays.asList(
                openNetwork,
                openNetworkDup,
                secureNetwork,
                // Ignore null and empty SSIDs
                buildScanResult(null, "bssidNull", START_MILLIS),
                buildScanResult("", "bssidEmpty", START_MILLIS)));

        mBroadcastReceiverCaptor.getValue().onReceive(mMockContext,
                new Intent(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION));
        List<String> seenTitles = new ArrayList<>();
        for (WifiEntry wifiEntry : wifiPickerTracker.getWifiEntries()) {
            seenTitles.add(wifiEntry.getTitle());
        }

        assertThat(seenTitles).containsExactly("Open Network", "Secure Network");
    }

    /**
     * Tests that old WifiEntries are timed out if their scans are older than the max scan age.
     */
    @Test
    public void testGetWifiEntries_wifiNetworkEntries_oldEntriesTimedOut() {
        final WifiPickerTracker wifiPickerTracker = createTestWifiPickerTracker();
        wifiPickerTracker.onStart();
        mTestLooper.dispatchAll();
        verify(mMockContext).registerReceiver(mBroadcastReceiverCaptor.capture(),
                any(), any(), any());

        // Initial entries
        when(mMockWifiManager.getScanResults()).thenReturn(Arrays.asList(
                buildScanResult("ssid0", "bssid0", START_MILLIS),
                buildScanResult("ssid1", "bssid1", START_MILLIS),
                buildScanResult("ssid2", "bssid2", START_MILLIS),
                buildScanResult("ssid3", "bssid3", START_MILLIS),
                buildScanResult("ssid4", "bssid4", START_MILLIS)));
        mBroadcastReceiverCaptor.getValue().onReceive(mMockContext,
                new Intent(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION));

        // Advance clock to max scan age. Entries should still be valid.
        when(mMockClock.millis()).thenReturn(START_MILLIS + MAX_SCAN_AGE_MILLIS);
        mBroadcastReceiverCaptor.getValue().onReceive(mMockContext,
                new Intent(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION));
        assertThat(wifiPickerTracker.getWifiEntries()).isNotEmpty();


        // Advance the clock to time out old entries
        when(mMockClock.millis()).thenReturn(START_MILLIS + MAX_SCAN_AGE_MILLIS + 1);
        mBroadcastReceiverCaptor.getValue().onReceive(mMockContext,
                new Intent(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION));

        // All entries timed out
        assertThat(wifiPickerTracker.getWifiEntries()).isEmpty();
    }

    /**
     * Tests that a failed scan will result in extending the max scan age by the scan interval.
     * This is to allow the WifiEntry list to stay stable and not clear out if a single scan fails.
     */
    @Test
    public void testGetWifiEntries_wifiNetworkEntries_useOldEntriesOnFailedScan() {
        final WifiPickerTracker wifiPickerTracker = createTestWifiPickerTracker();
        wifiPickerTracker.onStart();
        mTestLooper.dispatchAll();
        verify(mMockContext).registerReceiver(mBroadcastReceiverCaptor.capture(),
                any(), any(), any());

        // Initial entries
        when(mMockWifiManager.getScanResults()).thenReturn(Arrays.asList(
                buildScanResult("ssid0", "bssid0", START_MILLIS),
                buildScanResult("ssid1", "bssid1", START_MILLIS),
                buildScanResult("ssid2", "bssid2", START_MILLIS),
                buildScanResult("ssid3", "bssid3", START_MILLIS),
                buildScanResult("ssid4", "bssid4", START_MILLIS)));
        mBroadcastReceiverCaptor.getValue().onReceive(mMockContext,
                new Intent(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION));
        final List<WifiEntry> previousEntries = wifiPickerTracker.getWifiEntries();

        // Advance the clock to time out old entries and simulate failed scan
        when(mMockClock.millis())
                .thenReturn(START_MILLIS + MAX_SCAN_AGE_MILLIS + SCAN_INTERVAL_MILLIS);
        mBroadcastReceiverCaptor.getValue().onReceive(mMockContext,
                new Intent(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
                        .putExtra(WifiManager.EXTRA_RESULTS_UPDATED, false));

        // Failed scan should result in old WifiEntries still being shown
        assertThat(previousEntries).containsExactlyElementsIn(wifiPickerTracker.getWifiEntries());

        mBroadcastReceiverCaptor.getValue().onReceive(mMockContext,
                new Intent(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
                        .putExtra(WifiManager.EXTRA_RESULTS_UPDATED, true));

        // Successful scan should time out old entries.
        assertThat(wifiPickerTracker.getWifiEntries()).isEmpty();
    }

    @Test
    public void testGetWifiEntries_differentSsidSameBssid_returnsDifferentEntries() {
        final WifiPickerTracker wifiPickerTracker = createTestWifiPickerTracker();
        wifiPickerTracker.onStart();
        mTestLooper.dispatchAll();
        verify(mMockContext).registerReceiver(mBroadcastReceiverCaptor.capture(),
                any(), any(), any());

        when(mMockWifiManager.getScanResults()).thenReturn(Arrays.asList(
                // Identical BSSID for 4 different SSIDs should return 4 entries.
                buildScanResult("ssid0", "bssid0", START_MILLIS),
                buildScanResult("ssid1", "bssid0", START_MILLIS),
                buildScanResult("ssid2", "bssid0", START_MILLIS),
                buildScanResult("ssid3", "bssid0", START_MILLIS),
                // Another identical BSSID for 4 different SSIDs should return 4 more entries.
                buildScanResult("ssid4", "bssid1", START_MILLIS),
                buildScanResult("ssid5", "bssid1", START_MILLIS),
                buildScanResult("ssid6", "bssid1", START_MILLIS),
                buildScanResult("ssid7", "bssid1", START_MILLIS),
                // Same SSID as the last for 2 different BSSIDs should not increase entries.
                buildScanResult("ssid7", "bssid2", START_MILLIS),
                buildScanResult("ssid7", "bssid3", START_MILLIS)));


        mBroadcastReceiverCaptor.getValue().onReceive(mMockContext,
                new Intent(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION));

        assertThat(wifiPickerTracker.getWifiEntries()).hasSize(8);
    }

    /**
     * Tests that a CONFIGURED_NETWORKS_CHANGED broadcast updates the correct WifiEntry from
     * unsaved to saved.
     */
    @Test
    public void testGetWifiEntries_configuredNetworksChanged_unsavedToSaved() {
        final WifiPickerTracker wifiPickerTracker = createTestWifiPickerTracker();
        wifiPickerTracker.onStart();
        mTestLooper.dispatchAll();
        verify(mMockContext).registerReceiver(mBroadcastReceiverCaptor.capture(),
                any(), any(), any());

        when(mMockWifiManager.getScanResults()).thenReturn(Arrays.asList(
                buildScanResult("ssid", "bssid", START_MILLIS)));
        mBroadcastReceiverCaptor.getValue().onReceive(mMockContext,
                new Intent(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION));
        final WifiEntry entry = wifiPickerTracker.getWifiEntries().get(0);

        assertThat(entry.isSaved()).isFalse();

        final WifiConfiguration config = new WifiConfiguration();
        config.SSID = "\"ssid\"";
        when(mMockWifiManager.getPrivilegedConfiguredNetworks())
                .thenReturn(Collections.singletonList(config));
        mBroadcastReceiverCaptor.getValue().onReceive(mMockContext,
                new Intent(WifiManager.CONFIGURED_NETWORKS_CHANGED_ACTION));

        assertThat(entry.isSaved()).isTrue();
    }

    /**
     * Tests that a CONFIGURED_NETWORKS_CHANGED broadcast updates the correct WifiEntry from
     * saved to unsaved.
     */
    @Test
    public void testGetWifiEntries_configuredNetworksChanged_savedToUnsaved() {
        final WifiPickerTracker wifiPickerTracker = createTestWifiPickerTracker();
        final WifiConfiguration config = new WifiConfiguration();
        config.SSID = "\"ssid\"";
        config.networkId = 1;
        when(mMockWifiManager.getPrivilegedConfiguredNetworks())
                .thenReturn(Collections.singletonList(config));
        wifiPickerTracker.onStart();
        mTestLooper.dispatchAll();
        verify(mMockContext).registerReceiver(mBroadcastReceiverCaptor.capture(),
                any(), any(), any());

        when(mMockWifiManager.getScanResults()).thenReturn(Arrays.asList(
                buildScanResult("ssid", "bssid", START_MILLIS)));
        mBroadcastReceiverCaptor.getValue().onReceive(mMockContext,
                new Intent(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION));
        final WifiEntry entry = wifiPickerTracker.getWifiEntries().get(0);

        assertThat(entry.isSaved()).isTrue();

        when(mMockWifiManager.getPrivilegedConfiguredNetworks())
                .thenReturn(Collections.emptyList());
        mBroadcastReceiverCaptor.getValue().onReceive(mMockContext,
                new Intent(WifiManager.CONFIGURED_NETWORKS_CHANGED_ACTION));

        assertThat(entry.isSaved()).isFalse();
    }

    /**
     * Tests that a CONFIGURED_NETWORKS_CHANGED broadcast does not create WifiEntries based on
     * cached scan results if Wi-Fi is disabled.
     */
    @Test
    public void testGetWifiEntries_configuredNetworksChangedWifiDisabled_doesntUpdateEntries() {
        final WifiPickerTracker wifiPickerTracker = createTestWifiPickerTracker();
        wifiPickerTracker.onStart();
        mTestLooper.dispatchAll();
        verify(mMockContext).registerReceiver(mBroadcastReceiverCaptor.capture(),
                any(), any(), any());
        // Start off with Wi-Fi enabled
        when(mMockWifiManager.getScanResults()).thenReturn(Arrays.asList(
                buildScanResult("ssid", "bssid", START_MILLIS)));
        mBroadcastReceiverCaptor.getValue().onReceive(mMockContext,
                new Intent(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION));
        assertThat(wifiPickerTracker.getWifiEntries()).isNotEmpty();

        // Disable Wi-Fi and verify wifi entries is empty
        when(mMockWifiManager.getWifiState()).thenReturn(WifiManager.WIFI_STATE_DISABLED);
        mBroadcastReceiverCaptor.getValue().onReceive(mMockContext,
                new Intent(WifiManager.WIFI_STATE_CHANGED_ACTION).putExtra(
                        WifiManager.EXTRA_WIFI_STATE, WifiManager.WIFI_STATE_DISABLED));
        assertThat(wifiPickerTracker.getWifiEntries()).isEmpty();

        // Receive CONFIGURED_NETWORKS_CHANGED, verify wifi entries is still empty
        mBroadcastReceiverCaptor.getValue().onReceive(mMockContext,
                new Intent(WifiManager.CONFIGURED_NETWORKS_CHANGED_ACTION));
        assertThat(wifiPickerTracker.getWifiEntries()).isEmpty();
    }

    /**
     * Tests that getConnectedEntry() returns the connected WifiEntry if we start already connected
     * to a network.
     */
    @Test
    public void testGetConnectedEntry_alreadyConnectedOnStart_returnsConnectedEntry() {
        final WifiPickerTracker wifiPickerTracker = createTestWifiPickerTracker();
        final WifiConfiguration config = new WifiConfiguration();
        config.SSID = "\"ssid\"";
        config.networkId = 1;
        when(mMockWifiManager.getPrivilegedConfiguredNetworks())
                .thenReturn(Collections.singletonList(config));
        when(mMockWifiInfo.getNetworkId()).thenReturn(1);
        when(mMockWifiInfo.getRssi()).thenReturn(-50);

        wifiPickerTracker.onStart();
        mTestLooper.dispatchAll();

        assertThat(wifiPickerTracker.getConnectedWifiEntry()).isNotNull();
    }

    /**
     * Tests that connecting to a network will update getConnectedEntry() to return the connected
     * WifiEntry and remove that entry from getWifiEntries().
     */
    @Test
    public void testGetConnectedEntry_connectToNetwork_returnsConnectedEntry() {
        final WifiPickerTracker wifiPickerTracker = createTestWifiPickerTracker();
        final WifiConfiguration config = new WifiConfiguration();
        config.SSID = "\"ssid\"";
        config.networkId = 1;
        when(mMockWifiManager.getPrivilegedConfiguredNetworks())
                .thenReturn(Collections.singletonList(config));
        when(mMockWifiManager.getScanResults()).thenReturn(Arrays.asList(
                buildScanResult("ssid", "bssid", START_MILLIS)));
        wifiPickerTracker.onStart();
        mTestLooper.dispatchAll();
        verify(mMockConnectivityManager).registerNetworkCallback(
                any(), mNetworkCallbackCaptor.capture(), any());
        final WifiEntry entry = wifiPickerTracker.getWifiEntries().get(0);

        when(mMockWifiInfo.getNetworkId()).thenReturn(1);
        when(mMockWifiInfo.getRssi()).thenReturn(-50);
        mNetworkCallbackCaptor.getValue().onCapabilitiesChanged(
                mMockNetwork, mMockNetworkCapabilities);
        mTestLooper.dispatchAll();

        verify(mMockCallback, atLeastOnce())
                .onWifiEntriesChanged(WifiPickerTracker.WIFI_ENTRIES_CHANGED_REASON_GENERAL);
        assertThat(wifiPickerTracker.getWifiEntries()).isEmpty();
        assertThat(wifiPickerTracker.getConnectedWifiEntry()).isEqualTo(entry);
    }

    /**
     * Tests that an L2 connected network (i.e. from NETWORK_STATE_CHANGED) will correctly be
     * returned in getConnectedEntry() as the primary network.
     */
    @Test
    public void testGetConnectedEntry_networkL2Connected_returnsConnectedEntry() {
        final WifiPickerTracker wifiPickerTracker = createTestWifiPickerTracker();
        final WifiConfiguration config = new WifiConfiguration();
        config.SSID = "\"ssid\"";
        config.networkId = 1;
        when(mMockWifiManager.getPrivilegedConfiguredNetworks())
                .thenReturn(Collections.singletonList(config));
        when(mMockWifiManager.getScanResults()).thenReturn(Arrays.asList(
                buildScanResult("ssid", "bssid", START_MILLIS)));
        wifiPickerTracker.onStart();
        mTestLooper.dispatchAll();
        verify(mMockContext).registerReceiver(
                mBroadcastReceiverCaptor.capture(), any(), any(), any());
        verify(mMockConnectivityManager).registerNetworkCallback(
                any(), mNetworkCallbackCaptor.capture(), any());
        final WifiEntry entry = wifiPickerTracker.getWifiEntries().get(0);

        // Simulate an L2 connected network that's still authenticating.
        when(mMockWifiInfo.getNetworkId()).thenReturn(1);
        when(mMockWifiInfo.getRssi()).thenReturn(GOOD_RSSI);
        NetworkInfo mockNetworkInfo = mock(NetworkInfo.class);
        when(mockNetworkInfo.getDetailedState())
                .thenReturn(NetworkInfo.DetailedState.AUTHENTICATING);
        Intent networkStateChanged = new Intent(WifiManager.NETWORK_STATE_CHANGED_ACTION);
        networkStateChanged.putExtra(WifiManager.EXTRA_NETWORK_INFO, mockNetworkInfo);
        mBroadcastReceiverCaptor.getValue().onReceive(mMockContext, networkStateChanged);
        mTestLooper.dispatchAll();

        // Network should be returned in getConnectedWifiEntry() even though it's not L3 connected.
        verify(mMockCallback, atLeastOnce())
                .onWifiEntriesChanged(WifiPickerTracker.WIFI_ENTRIES_CHANGED_REASON_GENERAL);
        assertThat(wifiPickerTracker.getWifiEntries()).isEmpty();
        assertThat(wifiPickerTracker.getConnectedWifiEntry()).isEqualTo(entry);
        assertThat(entry.isPrimaryNetwork()).isTrue();
        assertThat(entry.getLevel()).isEqualTo(GOOD_LEVEL);
    }

    /**
     * Tests that an L2 connected network request (i.e. from NETWORK_STATE_CHANGED) will correctly
     * be returned in getConnectedEntry().
     */
    @Test
    public void testGetConnectedEntry_networkRequestL2Connected_returnsConnectedEntry() {
        final WifiPickerTracker wifiPickerTracker = createTestWifiPickerTracker();
        final WifiConfiguration config = new WifiConfiguration();
        config.SSID = "\"ssid\"";
        config.networkId = 1;
        config.fromWifiNetworkSpecifier = true;
        when(mMockWifiManager.getPrivilegedConfiguredNetworks())
                .thenReturn(Collections.singletonList(config));
        wifiPickerTracker.onStart();
        mTestLooper.dispatchAll();
        verify(mMockContext).registerReceiver(
                mBroadcastReceiverCaptor.capture(), any(), any(), any());
        verify(mMockConnectivityManager).registerNetworkCallback(
                any(), mNetworkCallbackCaptor.capture(), any());

        // Simulate an L2 connected network that's still authenticating.
        when(mMockWifiInfo.getNetworkId()).thenReturn(1);
        when(mMockWifiInfo.getRssi()).thenReturn(-50);
        NetworkInfo mockNetworkInfo = mock(NetworkInfo.class);
        when(mockNetworkInfo.getDetailedState())
                .thenReturn(NetworkInfo.DetailedState.AUTHENTICATING);
        Intent networkStateChanged = new Intent(WifiManager.NETWORK_STATE_CHANGED_ACTION);
        networkStateChanged.putExtra(WifiManager.EXTRA_NETWORK_INFO, mockNetworkInfo);
        mBroadcastReceiverCaptor.getValue().onReceive(mMockContext, networkStateChanged);
        mTestLooper.dispatchAll();

        // Network should be returned in getConnectedWifiEntry() even though it's not L3 connected.
        verify(mMockCallback, atLeastOnce())
                .onWifiEntriesChanged(WifiPickerTracker.WIFI_ENTRIES_CHANGED_REASON_GENERAL);
        assertThat(wifiPickerTracker.getConnectedWifiEntry()).isNotNull();
    }

    /**
     * Tests that connecting to a network will update getConnectedEntry() to return the connected
     * WifiEntry if the framework times out and gives us an empty list of configs.
     */
    @Test
    public void testGetConnectedEntry_connectToNetworkWithoutConfigs_returnsConnectedEntry() {
        // Simulate the framework timing out and giving us an empty list of configs.
        when(mMockWifiManager.getPrivilegedConfiguredNetworks())
                .thenReturn(Collections.emptyList());
        final WifiPickerTracker wifiPickerTracker = createTestWifiPickerTracker();
        wifiPickerTracker.onStart();
        mTestLooper.dispatchAll();
        verify(mMockConnectivityManager).registerNetworkCallback(
                any(), mNetworkCallbackCaptor.capture(), any());

        // Populate the correct list of configs.
        final WifiConfiguration config = new WifiConfiguration();
        config.SSID = "\"ssid\"";
        config.networkId = 1;
        when(mMockWifiManager.getPrivilegedConfiguredNetworks())
                .thenReturn(Collections.singletonList(config));
        when(mMockWifiInfo.getNetworkId()).thenReturn(1);
        when(mMockWifiInfo.getRssi()).thenReturn(-50);
        mNetworkCallbackCaptor.getValue().onCapabilitiesChanged(
                mMockNetwork, mMockNetworkCapabilities);
        mTestLooper.dispatchAll();

        verify(mMockCallback, atLeastOnce())
                .onWifiEntriesChanged(WifiPickerTracker.WIFI_ENTRIES_CHANGED_REASON_GENERAL);
        assertThat(wifiPickerTracker.getConnectedWifiEntry()).isNotNull();
        assertThat(wifiPickerTracker.getConnectedWifiEntry().getTitle()).isEqualTo("ssid");
    }

    /**
     * Tests that disconnecting from a network will update getConnectedEntry() to return null.
     */
    @Test
    public void testGetConnectedEntry_disconnectFromNetwork_returnsNull() {
        final WifiPickerTracker wifiPickerTracker = createTestWifiPickerTracker();
        final WifiConfiguration config = new WifiConfiguration();
        config.SSID = "\"ssid\"";
        config.networkId = 1;
        when(mMockWifiManager.getPrivilegedConfiguredNetworks())
                .thenReturn(Collections.singletonList(config));
        when(mMockWifiManager.getScanResults()).thenReturn(Arrays.asList(
                buildScanResult("ssid", "bssid", START_MILLIS)));
        when(mMockWifiInfo.getNetworkId()).thenReturn(1);
        when(mMockWifiInfo.getRssi()).thenReturn(-50);
        wifiPickerTracker.onStart();
        mTestLooper.dispatchAll();
        verify(mMockConnectivityManager).registerNetworkCallback(
                any(), mNetworkCallbackCaptor.capture(), any());

        mNetworkCallbackCaptor.getValue().onLost(mMockNetwork);
        mTestLooper.dispatchAll();

        verify(mMockCallback, atLeastOnce())
                .onWifiEntriesChanged(WifiPickerTracker.WIFI_ENTRIES_CHANGED_REASON_GENERAL);
        assertThat(wifiPickerTracker.getConnectedWifiEntry()).isNull();
    }

    /**
     * Tests that disconnecting from a network during the stopped state will result in the network
     * being disconnected once we've started again.
     */
    @Test
    public void testGetConnectedEntry_disconnectFromNetworkWhileStopped_returnsNull() {
        final WifiPickerTracker wifiPickerTracker = createTestWifiPickerTracker();
        final WifiConfiguration config = new WifiConfiguration();
        config.SSID = "\"ssid\"";
        config.networkId = 1;
        when(mMockWifiManager.getPrivilegedConfiguredNetworks())
                .thenReturn(Collections.singletonList(config));
        when(mMockWifiManager.getScanResults()).thenReturn(Arrays.asList(
                buildScanResult("ssid", "bssid", START_MILLIS)));
        when(mMockWifiInfo.getNetworkId()).thenReturn(1);
        when(mMockWifiInfo.getRssi()).thenReturn(-50);
        wifiPickerTracker.onStart();
        mTestLooper.dispatchAll();
        verify(mMockConnectivityManager).registerNetworkCallback(
                any(), mNetworkCallbackCaptor.capture(), any());

        // Simulate network disconnecting while in stopped state
        wifiPickerTracker.onStop();
        mTestLooper.dispatchAll();
        when(mMockWifiManager.getCurrentNetwork()).thenReturn(null);
        wifiPickerTracker.onStart();
        mTestLooper.dispatchAll();

        verify(mMockCallback, atLeastOnce())
                .onWifiEntriesChanged(WifiPickerTracker.WIFI_ENTRIES_CHANGED_REASON_GENERAL);
        assertThat(wifiPickerTracker.getConnectedWifiEntry()).isNull();
    }

    /**
     * Tests that captive portal will auto open if the activity stops and starts before we've
     * connected, such as if the user needs to input a password in a full screen dialog.
     */
    @Test
    public void testCaptivePortal_activityStopsAndStartsBeforeConnection_captivePortalAutoOpens() {
        final WifiPickerTracker wifiPickerTracker = createTestWifiPickerTracker();
        final WifiConfiguration config = new WifiConfiguration();
        config.SSID = "\"ssid\"";
        config.networkId = 1;
        when(mMockWifiManager.getPrivilegedConfiguredNetworks())
                .thenReturn(Collections.singletonList(config));
        when(mMockWifiManager.getScanResults()).thenReturn(Arrays.asList(
                buildScanResult("ssid", "bssid", START_MILLIS)));
        wifiPickerTracker.onStart();
        mTestLooper.dispatchAll();
        verify(mMockConnectivityManager).registerNetworkCallback(
                any(), mNetworkCallbackCaptor.capture(), any());
        final WifiEntry entry = wifiPickerTracker.getWifiEntries().get(0);

        // Simulate user connection
        entry.connect(null);
        // Activity is stopped and started
        wifiPickerTracker.onStop();
        wifiPickerTracker.onStart();
        mTestLooper.dispatchAll();

        // Verify captive portal auto-opens upon connection.
        when(mMockWifiInfo.getNetworkId()).thenReturn(1);
        when(mMockWifiInfo.getRssi()).thenReturn(-50);
        when(mMockNetworkCapabilities.hasCapability(
                NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL)).thenReturn(true);
        MockitoSession session = mockitoSession().spyStatic(NonSdkApiWrapper.class).startMocking();
        try {
            mNetworkCallbackCaptor.getValue().onCapabilitiesChanged(
                    mMockNetwork, mMockNetworkCapabilities);
            verify(() -> NonSdkApiWrapper.startCaptivePortalApp(any(), any()), times(1));
        } finally {
            session.finishMocking();
        }
    }

    /**
     * Tests that the wifi state changing to something other than WIFI_STATE_ENABLED will update
     * getConnectedEntry() to return null.
     */
    @Test
    public void testGetConnectedEntry_wifiStateDisabled_returnsNull() {
        final WifiPickerTracker wifiPickerTracker = createTestWifiPickerTracker();
        final WifiConfiguration config = new WifiConfiguration();
        config.SSID = "\"ssid\"";
        config.networkId = 1;
        when(mMockWifiManager.getPrivilegedConfiguredNetworks())
                .thenReturn(Collections.singletonList(config));
        when(mMockWifiManager.getScanResults()).thenReturn(Arrays.asList(
                buildScanResult("ssid", "bssid", START_MILLIS)));
        when(mMockWifiInfo.getNetworkId()).thenReturn(1);
        when(mMockWifiInfo.getRssi()).thenReturn(-50);
        wifiPickerTracker.onStart();
        mTestLooper.dispatchAll();
        verify(mMockContext).registerReceiver(mBroadcastReceiverCaptor.capture(),
                any(), any(), any());

        when(mMockWifiManager.getWifiState()).thenReturn(WifiManager.WIFI_STATE_DISABLED);
        mBroadcastReceiverCaptor.getValue().onReceive(mMockContext,
                new Intent(WifiManager.WIFI_STATE_CHANGED_ACTION));
        mTestLooper.dispatchAll();

        verify(mMockCallback, atLeastOnce())
                .onWifiEntriesChanged(WifiPickerTracker.WIFI_ENTRIES_CHANGED_REASON_GENERAL);
        assertThat(wifiPickerTracker.getConnectedWifiEntry()).isNull();
    }

    /**
     * Tests that a connected WifiEntry's isDefaultNetwork() will reflect updates from the default
     * network changing.
     */
    @Test
    public void testGetConnectedEntry_defaultNetworkChanges_isDefaultNetworkChanges() {
        final WifiPickerTracker wifiPickerTracker = createTestWifiPickerTracker();
        final WifiConfiguration config = new WifiConfiguration();
        config.SSID = "\"ssid\"";
        config.networkId = 1;
        when(mMockWifiManager.getPrivilegedConfiguredNetworks())
                .thenReturn(Collections.singletonList(config));
        when(mMockWifiManager.getScanResults()).thenReturn(Arrays.asList(
                buildScanResult("ssid", "bssid", START_MILLIS)));
        when(mMockWifiInfo.getNetworkId()).thenReturn(1);
        when(mMockWifiInfo.getRssi()).thenReturn(-50);
        wifiPickerTracker.onStart();
        mTestLooper.dispatchAll();
        verify(mMockConnectivityManager)
                .registerNetworkCallback(any(), mNetworkCallbackCaptor.capture(), any());
        verify(mMockConnectivityManager, atLeast(0)).registerSystemDefaultNetworkCallback(
                mDefaultNetworkCallbackCaptor.capture(), any());
        verify(mMockConnectivityManager, atLeast(0)).registerDefaultNetworkCallback(
                mDefaultNetworkCallbackCaptor.capture(), any());

        // No default
        assertThat(wifiPickerTracker.getConnectedWifiEntry().isDefaultNetwork()).isFalse();

        // Cell is default
        mDefaultNetworkCallbackCaptor.getValue().onCapabilitiesChanged(mock(Network.class),
                new NetworkCapabilities.Builder()
                        .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR).build());

        assertThat(wifiPickerTracker.getConnectedWifiEntry().isDefaultNetwork()).isFalse();

        // Other Wi-Fi network is default
        Network otherWifiNetwork = mock(Network.class);
        WifiInfo otherWifiInfo = mock(WifiInfo.class);
        when(otherWifiInfo.getNetworkId()).thenReturn(2);
        NetworkCapabilities otherNetworkCapabilities = mock(NetworkCapabilities.class);
        when(otherNetworkCapabilities.getTransportInfo()).thenReturn(otherWifiInfo);
        when(otherNetworkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI))
                .thenReturn(true);
        mDefaultNetworkCallbackCaptor.getValue().onCapabilitiesChanged(otherWifiNetwork,
                otherNetworkCapabilities);
        assertThat(wifiPickerTracker.getConnectedWifiEntry().isDefaultNetwork()).isFalse();

        // This Wi-Fi network is default
        mDefaultNetworkCallbackCaptor.getValue().onCapabilitiesChanged(mMockNetwork,
                mMockNetworkCapabilities);
        assertThat(wifiPickerTracker.getConnectedWifiEntry().isDefaultNetwork()).isTrue();

        // Lose the default network
        mDefaultNetworkCallbackCaptor.getValue().onLost(mock(Network.class));
        assertThat(wifiPickerTracker.getConnectedWifiEntry().isDefaultNetwork()).isFalse();

        // Disconnect
        mNetworkCallbackCaptor.getValue().onLost(mMockNetwork);
        assertThat(wifiPickerTracker.getWifiEntries().get(0).isDefaultNetwork()).isFalse();
    }

    /**
     * Tests that a connected WifiEntry will become the default network if the network underlies
     * the current default network.
     */
    @Test
    public void testGetConnectedEntry_defaultNetworkHasUnderlyingWifi_becomesDefaultNetwork() {
        final WifiPickerTracker wifiPickerTracker = createTestWifiPickerTracker();
        final WifiConfiguration config = new WifiConfiguration();
        config.SSID = "\"ssid\"";
        config.networkId = 1;
        when(mMockWifiManager.getCurrentNetwork()).thenReturn(null);
        when(mMockWifiManager.getConnectionInfo()).thenReturn(null);
        when(mMockWifiManager.getPrivilegedConfiguredNetworks())
                .thenReturn(Collections.singletonList(config));
        when(mMockWifiManager.getScanResults()).thenReturn(Arrays.asList(
                buildScanResult("ssid", "bssid", START_MILLIS)));
        when(mMockWifiInfo.getNetworkId()).thenReturn(1);
        when(mMockWifiInfo.getRssi()).thenReturn(-50);
        wifiPickerTracker.onStart();
        mTestLooper.dispatchAll();
        verify(mMockConnectivityManager)
                .registerNetworkCallback(any(), mNetworkCallbackCaptor.capture(), any());
        verify(mMockConnectivityManager, atLeast(0)).registerSystemDefaultNetworkCallback(
                mDefaultNetworkCallbackCaptor.capture(), any());
        verify(mMockConnectivityManager, atLeast(0)).registerDefaultNetworkCallback(
                mDefaultNetworkCallbackCaptor.capture(), any());

        Network vpnNetwork = mock(Network.class);
        NetworkCapabilities vpnCaps = new NetworkCapabilities.Builder()
                .addCapability(NetworkCapabilities.TRANSPORT_VPN)
                .setUnderlyingNetworks(List.of(mMockNetwork))
                .build();
        mDefaultNetworkCallbackCaptor.getValue().onCapabilitiesChanged(vpnNetwork, vpnCaps);
        mNetworkCallbackCaptor.getValue().onCapabilitiesChanged(
                mMockNetwork, mMockNetworkCapabilities);

        assertThat(wifiPickerTracker.getConnectedWifiEntry().isDefaultNetwork()).isTrue();

        // Losing the network and regaining it should not reset it being the default.
        mNetworkCallbackCaptor.getValue().onLost(mMockNetwork);
        mNetworkCallbackCaptor.getValue().onCapabilitiesChanged(
                mMockNetwork, mMockNetworkCapabilities);

        assertThat(wifiPickerTracker.getConnectedWifiEntry().isDefaultNetwork()).isTrue();
    }

    /**
     * Tests that a connected WifiEntry will return "Low quality" as the summary if Wifi is
     * validated but cell is the default route.
     */
    @Test
    public void testGetConnectedEntry_wifiValidatedCellDefault_isLowQuality() {
        final String summarySeparator = " / ";
        final String lowQuality = "Low quality";
        final String[] wifiStatusArray = new String[]{"", "Scanning", "Connecting",
                "Authenticating", "Obtaining IP address", "Connected"};
        when(mMockContext.getString(R.string.wifitrackerlib_summary_separator))
                .thenReturn(summarySeparator);
        when(mMockContext.getString(R.string.wifi_connected_low_quality)).thenReturn(lowQuality);
        when(mMockResources.getStringArray(R.array.wifitrackerlib_wifi_status))
                .thenReturn(wifiStatusArray);

        final WifiPickerTracker wifiPickerTracker = createTestWifiPickerTracker();
        final WifiConfiguration config = new WifiConfiguration();
        config.SSID = "\"ssid\"";
        config.networkId = 1;
        when(mMockWifiManager.getPrivilegedConfiguredNetworks())
                .thenReturn(Collections.singletonList(config));
        when(mMockWifiManager.getScanResults()).thenReturn(Arrays.asList(
                buildScanResult("ssid", "bssid", START_MILLIS)));
        when(mMockWifiInfo.getNetworkId()).thenReturn(1);
        when(mMockWifiInfo.getRssi()).thenReturn(-50);
        wifiPickerTracker.onStart();
        mTestLooper.dispatchAll();
        verify(mMockConnectivityManager)
                .registerNetworkCallback(any(), mNetworkCallbackCaptor.capture(), any());
        verify(mMockConnectivityManager, atLeast(0)).registerSystemDefaultNetworkCallback(
                mDefaultNetworkCallbackCaptor.capture(), any());
        verify(mMockConnectivityManager, atLeast(0)).registerDefaultNetworkCallback(
                mDefaultNetworkCallbackCaptor.capture(), any());
        // Set cellular to be the default network
        mDefaultNetworkCallbackCaptor.getValue().onCapabilitiesChanged(Mockito.mock(Network.class),
                new NetworkCapabilities.Builder()
                        .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR).build());

        // Trigger a validation callback for the non-primary Wifi network.
        MockitoSession session = mockitoSession().spyStatic(NonSdkApiWrapper.class).startMocking();
        try {
            WifiInfo nonPrimaryWifiInfo = Mockito.mock(WifiInfo.class);
            when(nonPrimaryWifiInfo.makeCopy(anyLong())).thenReturn(nonPrimaryWifiInfo);
            NetworkCapabilities nonPrimaryCap = new NetworkCapabilities.Builder()
                    .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                    .setTransportInfo(nonPrimaryWifiInfo)
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                    .build();
            mNetworkCallbackCaptor.getValue().onCapabilitiesChanged(
                    mock(Network.class), nonPrimaryCap);
        } finally {
            session.finishMocking();
        }

        // Non-primary Wifi network validation should be ignored.
        assertThat(wifiPickerTracker.getConnectedWifiEntry().getSummary()).isNotEqualTo(lowQuality);

        when(mMockNetworkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED))
                .thenReturn(true);
        mNetworkCallbackCaptor.getValue().onCapabilitiesChanged(
                mMockNetwork, mMockNetworkCapabilities);

        // Cell default + primary network validation should trigger low quality
        assertThat(wifiPickerTracker.getConnectedWifiEntry().getSummary()).isEqualTo(lowQuality);

        // Cell + VPN is default should not trigger low quality, since the VPN underlying network is
        // determined by the VPN app and not whether Wi-Fi is low quality or not.
        mDefaultNetworkCallbackCaptor.getValue().onCapabilitiesChanged(Mockito.mock(Network.class),
                new NetworkCapabilities.Builder()
                        .addTransportType(NetworkCapabilities.TRANSPORT_VPN)
                        .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR).build());
        assertThat(wifiPickerTracker.getConnectedWifiEntry().getSummary()).isNotEqualTo(lowQuality);

        // Set Cell to the default but then lose the default network. Low quality should disappear
        // since cell default was lost.
        mDefaultNetworkCallbackCaptor.getValue().onCapabilitiesChanged(Mockito.mock(Network.class),
                new NetworkCapabilities.Builder()
                        .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR).build());
        mDefaultNetworkCallbackCaptor.getValue().onLost(mock(Network.class));
        assertThat(wifiPickerTracker.getConnectedWifiEntry().isDefaultNetwork()).isFalse();
    }

    /**
     * Tests that a connected WifiEntry will show "Checking for internet access..." as the summary
     * if the network hasn't finished the first validation attempt yet.
     */
    @Test
    public void testGetConnectedEntry_wifiNotValidatedYet_showsCheckingForInternetAccess() {
        final String summarySeparator = " / ";
        final String checkingForInternetAccess = "Checking for internet access..";
        final String[] wifiStatusArray = new String[]{"", "Scanning", "Connecting",
                "Authenticating", "Obtaining IP address", "Connected"};
        when(mMockContext.getString(R.string.wifitrackerlib_summary_separator))
                .thenReturn(summarySeparator);
        when(mMockContext.getString(R.string.wifitrackerlib_checking_for_internet_access))
                .thenReturn(checkingForInternetAccess);
        when(mMockResources.getStringArray(R.array.wifitrackerlib_wifi_status))
                .thenReturn(wifiStatusArray);

        final WifiPickerTracker wifiPickerTracker = createTestWifiPickerTracker();
        final WifiConfiguration config = new WifiConfiguration();
        config.SSID = "\"ssid\"";
        config.networkId = 1;
        when(mMockWifiManager.getPrivilegedConfiguredNetworks())
                .thenReturn(Collections.singletonList(config));
        when(mMockWifiManager.getScanResults()).thenReturn(Arrays.asList(
                buildScanResult("ssid", "bssid", START_MILLIS)));
        when(mMockWifiInfo.getNetworkId()).thenReturn(1);
        when(mMockWifiInfo.getRssi()).thenReturn(-50);
        ArgumentCaptor<ConnectivityDiagnosticsManager.ConnectivityDiagnosticsCallback>
                diagnosticsCallbackCaptor = ArgumentCaptor.forClass(
                        ConnectivityDiagnosticsManager.ConnectivityDiagnosticsCallback.class);
        wifiPickerTracker.onStart();
        mTestLooper.dispatchAll();
        verify(mMockConnectivityManager)
                .registerNetworkCallback(any(), mNetworkCallbackCaptor.capture(), any());
        verify(mMockConnectivityDiagnosticsManager).registerConnectivityDiagnosticsCallback(
                any(), any(), diagnosticsCallbackCaptor.capture());
        verify(mMockConnectivityManager, atLeast(0)).registerSystemDefaultNetworkCallback(
                mDefaultNetworkCallbackCaptor.capture(), any());
        verify(mMockConnectivityManager, atLeast(0)).registerDefaultNetworkCallback(
                mDefaultNetworkCallbackCaptor.capture(), any());

        // Still checking for internet access.
        assertThat(wifiPickerTracker.getConnectedWifiEntry().getSummary())
                .isEqualTo(checkingForInternetAccess);

        // Trigger a validation callback and connectivity report for the primary Wifi network.
        when(mMockNetworkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED))
                .thenReturn(true);
        mNetworkCallbackCaptor.getValue().onCapabilitiesChanged(
                mMockNetwork, mMockNetworkCapabilities);
        ConnectivityDiagnosticsManager.ConnectivityReport report = mock(
                ConnectivityDiagnosticsManager.ConnectivityReport.class);
        when(report.getNetwork()).thenReturn(mMockNetwork);
        diagnosticsCallbackCaptor.getValue().onConnectivityReportAvailable(report);
        mDefaultNetworkCallbackCaptor.getValue().onCapabilitiesChanged(
                mMockNetwork, mMockNetworkCapabilities);

        // Now we're fully connected
        assertThat(wifiPickerTracker.getConnectedWifiEntry().getSummary()).isEqualTo("Connected");
    }

    /**
     * Tests that a connected WifiEntry that is expected to have no internet will display
     * "Connected to device. Can't provide internet" if the network is not validated.
     */
    @Test
    public void testGetConnectedEntry_wifiNotValidated_showsConnectedToDevice() {
        final String summarySeparator = " / ";
        final String connectedToDevice = "Connected to device. Can't provide internet";
        final String[] wifiStatusArray = new String[]{"", "Scanning", "Connecting",
                "Authenticating", "Obtaining IP address", "Connected"};
        when(mMockContext.getString(R.string.wifitrackerlib_summary_separator))
                .thenReturn(summarySeparator);
        when(mMockContext.getString(R.string.wifitrackerlib_wifi_connected_cannot_provide_internet))
                .thenReturn(connectedToDevice);
        when(mMockResources.getStringArray(R.array.wifitrackerlib_wifi_status))
                .thenReturn(wifiStatusArray);

        final WifiPickerTracker wifiPickerTracker = createTestWifiPickerTracker();
        final WifiConfiguration config = spy(new WifiConfiguration());
        config.SSID = "\"ssid\"";
        config.networkId = 1;
        when(config.isNoInternetAccessExpected()).thenReturn(true);
        when(mMockWifiManager.getPrivilegedConfiguredNetworks())
                .thenReturn(Collections.singletonList(config));
        when(mMockWifiManager.getScanResults()).thenReturn(Arrays.asList(
                buildScanResult("ssid", "bssid", START_MILLIS)));
        when(mMockWifiInfo.getNetworkId()).thenReturn(1);
        when(mMockWifiInfo.getRssi()).thenReturn(-50);
        ArgumentCaptor<ConnectivityDiagnosticsManager.ConnectivityDiagnosticsCallback>
                diagnosticsCallbackCaptor = ArgumentCaptor.forClass(
                ConnectivityDiagnosticsManager.ConnectivityDiagnosticsCallback.class);
        wifiPickerTracker.onStart();
        mTestLooper.dispatchAll();
        verify(mMockConnectivityManager)
                .registerNetworkCallback(any(), mNetworkCallbackCaptor.capture(), any());
        verify(mMockConnectivityDiagnosticsManager).registerConnectivityDiagnosticsCallback(
                any(), any(), diagnosticsCallbackCaptor.capture());

        assertThat(wifiPickerTracker.getConnectedWifiEntry().getSummary())
                .isEqualTo(connectedToDevice);

        // Trigger a no-validation callback and connectivity report for the primary Wifi network.
        mNetworkCallbackCaptor.getValue().onCapabilitiesChanged(
                mMockNetwork, mMockNetworkCapabilities);
        ConnectivityDiagnosticsManager.ConnectivityReport report = mock(
                ConnectivityDiagnosticsManager.ConnectivityReport.class);
        when(report.getNetwork()).thenReturn(mMockNetwork);
        diagnosticsCallbackCaptor.getValue().onConnectivityReportAvailable(report);

        assertThat(wifiPickerTracker.getConnectedWifiEntry().getSummary())
                .isEqualTo(connectedToDevice);
    }

    /**
     * Tests that getConnectedEntry() will return the correct primary network after an MBB sequence.
     */
    @Test
    public void testGetConnectedEntry_mbbFlow_matchesNewPrimary() {
        final WifiPickerTracker wifiPickerTracker = createTestWifiPickerTracker();
        final WifiConfiguration config = new WifiConfiguration();
        config.SSID = "\"ssid\"";
        config.networkId = 1;
        final WifiConfiguration mbbConfig = new WifiConfiguration();
        mbbConfig.SSID = "\"otherSsid\"";
        mbbConfig.networkId = 2;
        when(mMockWifiManager.getPrivilegedConfiguredNetworks())
                .thenReturn(Arrays.asList(config, mbbConfig));
        when(mMockWifiInfo.getNetworkId()).thenReturn(config.networkId);
        when(mMockWifiInfo.getRssi()).thenReturn(GOOD_RSSI);
        wifiPickerTracker.onStart();
        mTestLooper.dispatchAll();
        verify(mMockConnectivityManager).registerNetworkCallback(
                any(), mNetworkCallbackCaptor.capture(), any());

        // Start off connected
        WifiEntry originalConnectedEntry = wifiPickerTracker.getConnectedWifiEntry();
        assertThat(originalConnectedEntry).isNotNull();
        assertThat(originalConnectedEntry.getTitle()).isEqualTo("ssid");

        MockitoSession session = mockitoSession().spyStatic(NonSdkApiWrapper.class).startMocking();
        try {
            // MBB network connected but not primary yet.
            Network mbbNetwork = mock(Network.class);
            NetworkCapabilities mbbNetworkCapabilities = mock(NetworkCapabilities.class);
            WifiInfo mbbWifiInfo = mock(WifiInfo.class);
            when(mbbWifiInfo.getNetworkId()).thenReturn(mbbConfig.networkId);
            when(mbbWifiInfo.getRssi()).thenReturn(GOOD_RSSI);
            when(mbbNetworkCapabilities.getTransportInfo()).thenReturn(mbbWifiInfo);
            doReturn(false).when(() -> NonSdkApiWrapper.isPrimary(mbbWifiInfo));
            mNetworkCallbackCaptor.getValue()
                    .onCapabilitiesChanged(mbbNetwork, mbbNetworkCapabilities);
            // Original network should still be connected.
            assertThat(wifiPickerTracker.getConnectedWifiEntry()).isEqualTo(originalConnectedEntry);

            // Original network becomes non-primary and MBB network becomes primary.
            doReturn(false).when(() -> NonSdkApiWrapper.isPrimary(mMockWifiInfo));
            mNetworkCallbackCaptor.getValue()
                    .onCapabilitiesChanged(mMockNetwork, mMockNetworkCapabilities);
            doReturn(true).when(() -> NonSdkApiWrapper.isPrimary(mbbWifiInfo));
            mNetworkCallbackCaptor.getValue()
                    .onCapabilitiesChanged(mbbNetwork, mbbNetworkCapabilities);
            // MBB network should be connected now.
            WifiEntry newConnectedEntry = wifiPickerTracker.getConnectedWifiEntry();
            assertThat(newConnectedEntry).isNotNull();
            assertThat(newConnectedEntry.getTitle()).isEqualTo("otherSsid");
            assertThat(originalConnectedEntry.getConnectedState())
                    .isEqualTo(WifiEntry.CONNECTED_STATE_DISCONNECTED);

            // Original network is lost. MBB network should still be connected
            mNetworkCallbackCaptor.getValue().onLost(mMockNetwork);
            assertThat(wifiPickerTracker.getConnectedWifiEntry()).isEqualTo(newConnectedEntry);
        } finally {
            session.finishMocking();
        }
    }

    /**
     * Tests that a PasspointWifiEntry is returned when Passpoint scans are visible.
     */
    @Test
    public void testGetWifiEntries_passpointInRange_returnsPasspointWifiEntry() {
        final String passpointSsid = "passpointSsid";
        final String friendlyName = "friendlyName";
        final WifiPickerTracker wifiPickerTracker = createTestWifiPickerTracker();
        final PasspointConfiguration passpointConfig = new PasspointConfiguration();
        final HomeSp homeSp = new HomeSp();
        homeSp.setFqdn("fqdn");
        homeSp.setFriendlyName(friendlyName);
        passpointConfig.setHomeSp(homeSp);
        passpointConfig.setCredential(new Credential());
        when(mMockWifiManager.getPasspointConfigurations())
                .thenReturn(Collections.singletonList(passpointConfig));
        final ScanResult passpointScan =
                buildScanResult(passpointSsid, "bssid", START_MILLIS, GOOD_LEVEL);
        when(mMockWifiManager.getScanResults())
                .thenReturn(Collections.singletonList(passpointScan));
        wifiPickerTracker.onStart();
        mTestLooper.dispatchAll();
        verify(mMockContext).registerReceiver(mBroadcastReceiverCaptor.capture(),
                any(), any(), any());

        final WifiConfiguration wifiConfig = spy(new WifiConfiguration());
        when(wifiConfig.getKey()).thenReturn(passpointConfig.getUniqueId());
        final Map<Integer, List<ScanResult>> mapping = new ArrayMap<>();
        mapping.put(WifiManager.PASSPOINT_HOME_NETWORK, Collections.singletonList(passpointScan));
        List<Pair<WifiConfiguration, Map<Integer, List<ScanResult>>>> allMatchingWifiConfigs =
                Collections.singletonList(new Pair<>(wifiConfig, mapping));
        when(mMockWifiManager.getAllMatchingWifiConfigs(any())).thenReturn(allMatchingWifiConfigs);
        mBroadcastReceiverCaptor.getValue().onReceive(mMockContext,
                new Intent(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION));

        // Only the Passpoint entry should be present. The corresponding StandardWifiEntry with
        // matching SSID should be filtered out.
        assertThat(wifiPickerTracker.getWifiEntries().size()).isEqualTo(1);
        final WifiEntry passpointEntry = wifiPickerTracker.getWifiEntries().get(0);
        assertThat(passpointEntry.isSubscription()).isTrue();
        assertThat(passpointEntry.getTitle()).isEqualTo(friendlyName);
    }

    /**
     * Tests that the same PasspointWifiEntry from getWifiEntries() is returned when it becomes the
     * connected entry
     */
    @Test
    public void testGetWifiEntries_connectToPasspoint_returnsSamePasspointWifiEntry() {
        final WifiPickerTracker wifiPickerTracker = createTestWifiPickerTracker();
        final PasspointConfiguration passpointConfig = new PasspointConfiguration();
        final HomeSp homeSp = new HomeSp();
        homeSp.setFqdn("fqdn");
        homeSp.setFriendlyName("friendlyName");
        passpointConfig.setHomeSp(homeSp);
        passpointConfig.setCredential(new Credential());
        when(mMockWifiManager.getPasspointConfigurations())
                .thenReturn(Collections.singletonList(passpointConfig));
        final WifiConfiguration wifiConfig = spy(new WifiConfiguration());
        when(wifiConfig.getKey()).thenReturn(passpointConfig.getUniqueId());
        when(wifiConfig.isPasspoint()).thenReturn(true);
        wifiConfig.networkId = 1;
        final Map<Integer, List<ScanResult>> mapping = new ArrayMap<>();
        mapping.put(WifiManager.PASSPOINT_HOME_NETWORK, Collections.singletonList(
                buildScanResult("ssid", "bssid", START_MILLIS)));
        List<Pair<WifiConfiguration, Map<Integer, List<ScanResult>>>> allMatchingWifiConfigs =
                Collections.singletonList(new Pair<>(wifiConfig, mapping));
        when(mMockWifiManager.getAllMatchingWifiConfigs(any())).thenReturn(allMatchingWifiConfigs);
        when(mMockWifiManager.getPrivilegedConfiguredNetworks())
                .thenReturn(Collections.singletonList(wifiConfig));
        wifiPickerTracker.onStart();
        mTestLooper.dispatchAll();
        verify(mMockConnectivityManager).registerNetworkCallback(
                any(), mNetworkCallbackCaptor.capture(), any());
        assertThat(wifiPickerTracker.getWifiEntries()).isNotEmpty();
        final WifiEntry entry = wifiPickerTracker.getWifiEntries().get(0);

        when(mMockWifiInfo.isPasspointAp()).thenReturn(true);
        when(mMockWifiInfo.getPasspointFqdn()).thenReturn("fqdn");
        when(mMockWifiInfo.getNetworkId()).thenReturn(1);
        when(mMockWifiInfo.getRssi()).thenReturn(-50);
        mNetworkCallbackCaptor.getValue().onCapabilitiesChanged(
                mMockNetwork, mMockNetworkCapabilities);

        assertThat(wifiPickerTracker.getWifiEntries()).isEmpty();
        assertThat(wifiPickerTracker.getConnectedWifiEntry()).isEqualTo(entry);
    }

    /**
     * Tests that a PasspointWifiEntry will disappear from getWifiEntries() once it is out of range.
     */
    @Test
    public void testGetWifiEntries_passpointOutOfRange_returnsNull() {
        // Create conditions for one PasspointWifiEntry in getWifiEntries()
        final WifiPickerTracker wifiPickerTracker = createTestWifiPickerTracker();
        final PasspointConfiguration passpointConfig = new PasspointConfiguration();
        final HomeSp homeSp = new HomeSp();
        homeSp.setFqdn("fqdn");
        homeSp.setFriendlyName("friendlyName");
        passpointConfig.setHomeSp(homeSp);
        passpointConfig.setCredential(new Credential());
        when(mMockWifiManager.getPasspointConfigurations())
                .thenReturn(Collections.singletonList(passpointConfig));
        final WifiConfiguration wifiConfig = spy(new WifiConfiguration());
        when(wifiConfig.getKey()).thenReturn(passpointConfig.getUniqueId());
        final Map<Integer, List<ScanResult>> mapping = new ArrayMap<>();
        mapping.put(WifiManager.PASSPOINT_HOME_NETWORK, Collections.singletonList(
                buildScanResult("ssid", "bssid", START_MILLIS)));
        List<Pair<WifiConfiguration, Map<Integer, List<ScanResult>>>> allMatchingWifiConfigs =
                Collections.singletonList(new Pair<>(wifiConfig, mapping));
        when(mMockWifiManager.getAllMatchingWifiConfigs(any())).thenReturn(allMatchingWifiConfigs);
        wifiPickerTracker.onStart();
        mTestLooper.dispatchAll();
        verify(mMockContext).registerReceiver(mBroadcastReceiverCaptor.capture(),
                any(), any(), any());

        // Age out the scans and get out of range of Passpoint AP
        when(mMockClock.millis()).thenReturn(START_MILLIS + MAX_SCAN_AGE_MILLIS + 1);
        when(mMockWifiManager.getAllMatchingWifiConfigs(any())).thenReturn(new ArrayList<>());
        mBroadcastReceiverCaptor.getValue().onReceive(mMockContext,
                new Intent(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION));

        // getWifiEntries() should be empty now
        assertThat(wifiPickerTracker.getWifiEntries()).isEmpty();
    }

    /**
     * Verifies that the WifiEntries returned in WifiPickerTracker.getWifiEntries() are returned in
     * the order defined by the default WifiEntry.WIFI_PICKER_COMPARATOR.
     */
    @Test
    public void testGetWifiEntries_defaultSortingCriteria_returnsEntriesinCorrectOrder() {
        List<ScanResult> currentScans = new ArrayList<>();
        List<WifiConfiguration> currentConfiguredNetworks = new ArrayList<>();

        // Set up Passpoint entry
        final PasspointConfiguration passpointConfig = new PasspointConfiguration();
        final HomeSp homeSp = new HomeSp();
        homeSp.setFqdn("fqdn");
        homeSp.setFriendlyName("friendlyName");
        passpointConfig.setHomeSp(homeSp);
        passpointConfig.setCredential(new Credential());
        when(mMockWifiManager.getPasspointConfigurations())
                .thenReturn(Collections.singletonList(passpointConfig));
        final WifiConfiguration wifiConfig = spy(new WifiConfiguration());
        when(wifiConfig.getKey()).thenReturn(passpointConfig.getUniqueId());
        final Map<Integer, List<ScanResult>> mapping = new ArrayMap<>();
        mapping.put(WifiManager.PASSPOINT_HOME_NETWORK, Collections.singletonList(
                buildScanResult("ssid", "bssid", START_MILLIS)));
        List<Pair<WifiConfiguration, Map<Integer, List<ScanResult>>>> allMatchingWifiConfigs =
                Collections.singletonList(new Pair<>(wifiConfig, mapping));
        when(mMockWifiManager.getAllMatchingWifiConfigs(any())).thenReturn(allMatchingWifiConfigs);

        // Set up saved entry
        WifiConfiguration savedConfig = new WifiConfiguration();
        savedConfig.fromWifiNetworkSuggestion = false;
        savedConfig.SSID = "\"ssid\"";
        savedConfig.networkId = 1;
        currentConfiguredNetworks.add(savedConfig);
        currentScans.add(buildScanResult("ssid", "bssid", START_MILLIS));

        // Set up suggestion entry
        WifiConfiguration suggestionConfig = new WifiConfiguration(savedConfig);
        suggestionConfig.networkId = 2;
        suggestionConfig.creatorName = "creator1";
        suggestionConfig.carrierId = 1;
        suggestionConfig.subscriptionId = 1;
        suggestionConfig.fromWifiNetworkSuggestion = true;
        currentConfiguredNetworks.add(suggestionConfig);
        when(mMockWifiManager.getWifiConfigForMatchedNetworkSuggestionsSharedWithUser(any()))
                .thenReturn(Arrays.asList(suggestionConfig));

        // Set up high level entry
        currentScans.add(buildScanResult("high", "bssid", START_MILLIS, GOOD_RSSI));

        // Set up low level entry with high priority title
        String highPriorityTitle = "A";
        currentScans.add(buildScanResult(highPriorityTitle, "bssid", START_MILLIS, BAD_RSSI));

        // Set up low level entry with low priority title
        String lowPriorityTitle = "Z";
        currentScans.add(buildScanResult(lowPriorityTitle, "bssid", START_MILLIS, BAD_RSSI));

        // Set up EAP-SIM entry without SIM
        WifiConfiguration eapSimConfig = new WifiConfiguration();
        eapSimConfig.SSID = "\"eap-sim\"";
        eapSimConfig.networkId = 3;
        eapSimConfig.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_EAP);
        eapSimConfig.enterpriseConfig = mock(WifiEnterpriseConfig.class);
        when(eapSimConfig.enterpriseConfig.isAuthenticationSimBased()).thenReturn(true);
        currentConfiguredNetworks.add(eapSimConfig);
        ScanResult eapSimScan = buildScanResult("eap-sim", "bssid", START_MILLIS, GOOD_RSSI);
        eapSimScan.capabilities = "[EAP/SHA1]";
        currentScans.add(eapSimScan);

        // Set up WifiManager mocks
        when(mMockWifiManager.getPrivilegedConfiguredNetworks())
                .thenReturn(currentConfiguredNetworks);
        when(mMockWifiManager.getScanResults()).thenReturn(currentScans);

        final WifiPickerTracker wifiPickerTracker = createTestWifiPickerTracker();
        wifiPickerTracker.onStart();
        mTestLooper.dispatchAll();

        List<WifiEntry> entries = wifiPickerTracker.getWifiEntries();
        assertThat(entries.size()).isEqualTo(7);
        // Subscription
        assertThat(entries.get(0).isSubscription()).isTrue();
        // Saved
        assertThat(entries.get(1).isSaved()).isTrue();
        // Suggestion
        assertThat(entries.get(2).isSuggestion()).isTrue();
        // High level
        assertThat(entries.get(3).getLevel()).isEqualTo(GOOD_LEVEL);
        // High Title
        assertThat(entries.get(4).getTitle()).isEqualTo(highPriorityTitle);
        // Low Title
        assertThat(entries.get(5).getTitle()).isEqualTo(lowPriorityTitle);
        // Non-connectable
        assertThat(entries.get(6).canConnect()).isEqualTo(false);
    }

    /**
     * Tests that multiple wifi entries are returned for multiple suggestions for the same network.
     */
    @Test
    public void testGetWifiEntries_multipleSuggestions_returnsMultipleEntries() {
        WifiConfiguration savedConfig = new WifiConfiguration();
        savedConfig.fromWifiNetworkSuggestion = false;
        savedConfig.SSID = "\"ssid\"";
        savedConfig.networkId = 1;
        WifiConfiguration suggestionConfig1 = new WifiConfiguration(savedConfig);
        suggestionConfig1.networkId = 2;
        suggestionConfig1.creatorName = "creator1";
        suggestionConfig1.carrierId = 1;
        suggestionConfig1.subscriptionId = 1;
        suggestionConfig1.fromWifiNetworkSuggestion = true;
        WifiConfiguration suggestionConfig2 = new WifiConfiguration(savedConfig);
        suggestionConfig2.networkId = 3;
        suggestionConfig1.creatorName = "creator2";
        suggestionConfig1.carrierId = 2;
        suggestionConfig1.subscriptionId = 2;
        suggestionConfig2.fromWifiNetworkSuggestion = true;
        // Initial entries
        when(mMockWifiManager.getPrivilegedConfiguredNetworks()).thenReturn(
                Arrays.asList(suggestionConfig1, suggestionConfig2));
        when(mMockWifiManager.getScanResults()).thenReturn(Collections.singletonList(
                buildScanResult("ssid", "bssid", START_MILLIS)));
        when(mMockWifiManager.getWifiConfigForMatchedNetworkSuggestionsSharedWithUser(any()))
                .thenReturn(Arrays.asList(suggestionConfig1, suggestionConfig2));
        final WifiPickerTracker wifiPickerTracker = createTestWifiPickerTracker();
        wifiPickerTracker.onStart();
        mTestLooper.dispatchAll();
        verify(mMockContext).registerReceiver(mBroadcastReceiverCaptor.capture(),
                any(), any(), any());

        // 2 suggestion entries, no unsaved entry
        assertThat(wifiPickerTracker.getWifiEntries().size()).isEqualTo(2);
        for (WifiEntry entry : wifiPickerTracker.getWifiEntries()) {
            assertThat(entry.getTitle()).isEqualTo("ssid");
        }
        assertThat(wifiPickerTracker.getWifiEntries().stream()
                .filter(WifiEntry::isSuggestion)
                .count()).isEqualTo(2);

        // Add a saved entry
        when(mMockWifiManager.getPrivilegedConfiguredNetworks()).thenReturn(
                Arrays.asList(savedConfig, suggestionConfig1, suggestionConfig2));
        mBroadcastReceiverCaptor.getValue().onReceive(mMockContext,
                new Intent(WifiManager.CONFIGURED_NETWORKS_CHANGED_ACTION));

        // Saved entry should appear alongside suggestions
        assertThat(wifiPickerTracker.getWifiEntries().size()).isEqualTo(3);
        for (WifiEntry entry : wifiPickerTracker.getWifiEntries()) {
            assertThat(entry.getTitle()).isEqualTo("ssid");
        }
        assertThat(wifiPickerTracker.getWifiEntries().stream()
                .filter(WifiEntry::isSuggestion)
                .count())
                .isEqualTo(2);
        assertThat(wifiPickerTracker.getWifiEntries().stream()
                .filter(WifiEntry::isSaved)
                .count()).isEqualTo(1);
    }

    /**
     * Tests that a suggestion entry is returned after a user shared suggestion config is added.
     */
    @Test
    public void testGetWifiEntries_userSharedSuggestionConfigAdded_returnsSuggestion() {
        // Start out with non-saved network.
        when(mMockWifiManager.getScanResults()).thenReturn(Collections.singletonList(
                buildScanResult("ssid", "bssid", START_MILLIS)));
        final WifiPickerTracker wifiPickerTracker = createTestWifiPickerTracker();
        wifiPickerTracker.onStart();
        mTestLooper.dispatchAll();
        verify(mMockContext).registerReceiver(mBroadcastReceiverCaptor.capture(),
                any(), any(), any());
        assertThat(wifiPickerTracker.getWifiEntries().size()).isEqualTo(1);
        WifiEntry wifiEntry = wifiPickerTracker.getWifiEntries().get(0);
        assertThat(wifiEntry.getTitle()).isEqualTo("ssid");
        assertThat(wifiEntry.isSuggestion()).isFalse();

        // Update configs with suggestion config.
        WifiConfiguration suggestionConfig = new WifiConfiguration();
        suggestionConfig.SSID = "\"ssid\"";
        suggestionConfig.networkId = 2;
        suggestionConfig.creatorName = "creator1";
        suggestionConfig.carrierId = 1;
        suggestionConfig.subscriptionId = 1;
        suggestionConfig.fromWifiNetworkSuggestion = true;
        when(mMockWifiManager.getWifiConfigForMatchedNetworkSuggestionsSharedWithUser(any()))
                .thenReturn(Arrays.asList(suggestionConfig));
        when(mMockWifiManager.getPrivilegedConfiguredNetworks()).thenReturn(
                Arrays.asList(suggestionConfig));
        mBroadcastReceiverCaptor.getValue().onReceive(mMockContext,
                new Intent(WifiManager.CONFIGURED_NETWORKS_CHANGED_ACTION));

        // Non-saved network should turn into suggestion
        assertThat(wifiPickerTracker.getWifiEntries().size()).isEqualTo(1);
        wifiEntry = wifiPickerTracker.getWifiEntries().get(0);
        assertThat(wifiEntry.getTitle()).isEqualTo("ssid");
        assertThat(wifiEntry.isSuggestion()).isTrue();
    }

    /**
     * Tests that a suggestion entry is not returned after a non-shared suggestion config is added.
     */
    @Test
    public void testGetWifiEntries_nonSharedSuggestionConfigAdded_returnsNotSuggestion() {
        // Start out with non-saved network.
        when(mMockWifiManager.getScanResults()).thenReturn(Collections.singletonList(
                buildScanResult("ssid", "bssid", START_MILLIS)));
        final WifiPickerTracker wifiPickerTracker = createTestWifiPickerTracker();
        wifiPickerTracker.onStart();
        mTestLooper.dispatchAll();
        verify(mMockContext).registerReceiver(mBroadcastReceiverCaptor.capture(),
                any(), any(), any());
        assertThat(wifiPickerTracker.getWifiEntries().size()).isEqualTo(1);
        WifiEntry wifiEntry = wifiPickerTracker.getWifiEntries().get(0);
        assertThat(wifiEntry.getTitle()).isEqualTo("ssid");
        assertThat(wifiEntry.isSuggestion()).isFalse();

        // Update configs with non-shared suggestion config.
        WifiConfiguration suggestionConfig = new WifiConfiguration();
        suggestionConfig.SSID = "\"ssid\"";
        suggestionConfig.networkId = 2;
        suggestionConfig.creatorName = "creator1";
        suggestionConfig.carrierId = 1;
        suggestionConfig.subscriptionId = 1;
        suggestionConfig.fromWifiNetworkSuggestion = true;
        when(mMockWifiManager.getPrivilegedConfiguredNetworks()).thenReturn(
                Arrays.asList(suggestionConfig));
        mBroadcastReceiverCaptor.getValue().onReceive(mMockContext,
                new Intent(WifiManager.CONFIGURED_NETWORKS_CHANGED_ACTION));

        // Non-saved network should not turn into suggestion
        assertThat(wifiPickerTracker.getWifiEntries().size()).isEqualTo(1);
        wifiEntry = wifiPickerTracker.getWifiEntries().get(0);
        assertThat(wifiEntry.getTitle()).isEqualTo("ssid");
        assertThat(wifiEntry.isSuggestion()).isFalse();
    }

    /**
     * Tests that a suggestion entry created before scan results are available will be updated to
     * user shareable after scans become available.
     */
    @Test
    public void testGetWifiEntries_preConnectedSuggestion_becomesUserShareable() {
        WifiConfiguration suggestionConfig = new WifiConfiguration();
        suggestionConfig.SSID = "\"ssid\"";
        suggestionConfig.networkId = 1;
        suggestionConfig.creatorName = "creator";
        suggestionConfig.carrierId = 1;
        suggestionConfig.subscriptionId = 1;
        suggestionConfig.fromWifiNetworkSuggestion = true;
        // Initial entries
        when(mMockWifiManager.getPrivilegedConfiguredNetworks()).thenReturn(
                Arrays.asList(suggestionConfig));
        when(mMockWifiInfo.getNetworkId()).thenReturn(suggestionConfig.networkId);
        when(mMockWifiInfo.getRssi()).thenReturn(-50);
        final WifiPickerTracker wifiPickerTracker = createTestWifiPickerTracker();
        wifiPickerTracker.onStart();
        mTestLooper.dispatchAll();
        verify(mMockContext).registerReceiver(mBroadcastReceiverCaptor.capture(),
                any(), any(), any());
        verify(mMockConnectivityManager).registerNetworkCallback(
                any(), mNetworkCallbackCaptor.capture(), any());
        WifiEntry suggestionEntry = wifiPickerTracker.getConnectedWifiEntry();
        assertThat(suggestionEntry).isNotNull();

        // Update with user-shareable scan results for the suggestion
        when(mMockWifiManager.getScanResults()).thenReturn(Collections.singletonList(
                buildScanResult("ssid", "bssid", START_MILLIS)));
        when(mMockWifiManager.getWifiConfigForMatchedNetworkSuggestionsSharedWithUser(any()))
                .thenReturn(Arrays.asList(suggestionConfig));
        mBroadcastReceiverCaptor.getValue().onReceive(mMockContext,
                new Intent(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION));
        // Disconnect from network to verify its usershareability in the picker list
        verify(mMockConnectivityManager).registerNetworkCallback(
                any(), mNetworkCallbackCaptor.capture(), any());
        mNetworkCallbackCaptor.getValue().onLost(mMockNetwork);

        // Suggestion entry should be in picker list now
        suggestionEntry = wifiPickerTracker.getWifiEntries().get(0);
        assertThat(suggestionEntry.isSuggestion()).isTrue();
    }

    @Test
    public void testGetConnectedEntry_alreadyConnectedToPasspoint_returnsPasspointEntry() {
        final String fqdn = "fqdn";
        final String friendlyName = "friendlyName";
        final int networkId = 1;
        // Create a passpoint configuration to match with the current network
        final PasspointConfiguration passpointConfig = new PasspointConfiguration();
        final HomeSp homeSp = new HomeSp();
        homeSp.setFqdn(fqdn);
        homeSp.setFriendlyName(friendlyName);
        passpointConfig.setHomeSp(homeSp);
        passpointConfig.setCredential(new Credential());
        when(mMockWifiManager.getPasspointConfigurations())
                .thenReturn(Collections.singletonList(passpointConfig));
        // Create a wifi config to match the WifiInfo netId and unique id of the passpoint config
        final WifiConfiguration config = Mockito.mock(WifiConfiguration.class);
        config.SSID = "\"ssid\"";
        config.networkId = networkId;
        config.allowedKeyManagement = new BitSet();
        config.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.SUITE_B_192);
        config.subscriptionId = SubscriptionManager.INVALID_SUBSCRIPTION_ID;
        when(config.isPasspoint()).thenReturn(true);
        when(config.getKey()).thenReturn(passpointConfig.getUniqueId());
        when(mMockWifiManager.getPrivilegedConfiguredNetworks())
                .thenReturn(Collections.singletonList(config));
        when(mMockWifiInfo.isPasspointAp()).thenReturn(true);
        when(mMockWifiInfo.getNetworkId()).thenReturn(networkId);
        when(mMockWifiInfo.getPasspointFqdn()).thenReturn(fqdn);
        when(mMockWifiInfo.getRssi()).thenReturn(-50);
        final WifiPickerTracker wifiPickerTracker = createTestWifiPickerTracker();

        wifiPickerTracker.onStart();
        mTestLooper.dispatchAll();

        verify(mMockCallback, atLeastOnce())
                .onWifiEntriesChanged(WifiPickerTracker.WIFI_ENTRIES_CHANGED_REASON_GENERAL);
        assertThat(wifiPickerTracker.getConnectedWifiEntry().getTitle()).isEqualTo(friendlyName);
    }

    @Test
    public void testGetConnectedEntry_passpointWithoutScans_returnsPasspointEntry() {
        final String fqdn = "fqdn";
        final String friendlyName = "friendlyName";
        final int networkId = 1;
        // Create a passpoint configuration to match with the current network
        final PasspointConfiguration passpointConfig = new PasspointConfiguration();
        final HomeSp homeSp = new HomeSp();
        homeSp.setFqdn(fqdn);
        homeSp.setFriendlyName(friendlyName);
        passpointConfig.setHomeSp(homeSp);
        passpointConfig.setCredential(new Credential());
        when(mMockWifiManager.getPasspointConfigurations())
                .thenReturn(Collections.singletonList(passpointConfig));
        // Create a wifi config to match the WifiInfo netId and unique id of the passpoint config
        final WifiConfiguration config = Mockito.mock(WifiConfiguration.class);
        config.SSID = "\"ssid\"";
        config.networkId = networkId;
        config.allowedKeyManagement = new BitSet();
        config.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.SUITE_B_192);
        config.subscriptionId = SubscriptionManager.INVALID_SUBSCRIPTION_ID;
        when(config.isPasspoint()).thenReturn(true);
        when(config.getKey()).thenReturn(passpointConfig.getUniqueId());
        when(mMockWifiManager.getPrivilegedConfiguredNetworks())
                .thenReturn(Collections.singletonList(config));
        when(mMockWifiInfo.isPasspointAp()).thenReturn(true);
        when(mMockWifiInfo.getNetworkId()).thenReturn(networkId);
        when(mMockWifiInfo.getPasspointFqdn()).thenReturn(fqdn);
        when(mMockWifiInfo.getRssi()).thenReturn(-50);
        final WifiPickerTracker wifiPickerTracker = createTestWifiPickerTracker();
        wifiPickerTracker.onStart();
        mTestLooper.dispatchAll();
        verify(mMockContext).registerReceiver(mBroadcastReceiverCaptor.capture(),
                any(), any(), any());

        // Update with SCAN_RESULTS_AVAILABLE action while there are no scan results available yet.
        mBroadcastReceiverCaptor.getValue().onReceive(mMockContext,
                new Intent(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION));
        mTestLooper.dispatchAll();

        verify(mMockCallback, atLeastOnce())
                .onWifiEntriesChanged(WifiPickerTracker.WIFI_ENTRIES_CHANGED_REASON_GENERAL);
        assertThat(wifiPickerTracker.getConnectedWifiEntry()).isNotNull();
        assertThat(wifiPickerTracker.getConnectedWifiEntry().getTitle()).isEqualTo(friendlyName);
    }

    /**
     * Tests that getConnectedEntry will return the correct network request if there are multiple
     * active requests
     */
    @Test
    public void testGetConnectedEntry_multipleNetworkRequests_returnsConnectedRequest() {
        final WifiConfiguration requestConfig1 = new WifiConfiguration();
        requestConfig1.SSID = "\"ssid1\"";
        requestConfig1.networkId = 1;
        requestConfig1.fromWifiNetworkSpecifier = true;
        final WifiConfiguration requestConfig2 = new WifiConfiguration();
        requestConfig2.SSID = "\"ssid2\"";
        requestConfig2.networkId = 2;
        requestConfig2.fromWifiNetworkSpecifier = true;
        when(mMockWifiManager.getPrivilegedConfiguredNetworks()).thenReturn(
                Arrays.asList(requestConfig1, requestConfig2));
        when(mMockWifiInfo.getNetworkId()).thenReturn(1);
        when(mMockWifiInfo.getRssi()).thenReturn(-50);

        final WifiPickerTracker wifiPickerTracker = createTestWifiPickerTracker();
        wifiPickerTracker.onStart();
        mTestLooper.dispatchAll();
        verify(mMockConnectivityManager).registerNetworkCallback(
                any(), mNetworkCallbackCaptor.capture(), any());

        // WifiInfo has network id 1, so the connected entry should correspond to request 1
        assertThat(wifiPickerTracker.getConnectedWifiEntry().getSsid()).isEqualTo("ssid1");

        mNetworkCallbackCaptor.getValue().onLost(mMockNetwork);
        when(mMockWifiInfo.getNetworkId()).thenReturn(2);
        mNetworkCallbackCaptor.getValue().onCapabilitiesChanged(
                mMockNetwork, mMockNetworkCapabilities);

        // WifiInfo has network id 2, so the connected entry should correspond to request 2
        assertThat(wifiPickerTracker.getConnectedWifiEntry().getSsid()).isEqualTo("ssid2");

        mNetworkCallbackCaptor.getValue().onLost(mMockNetwork);
        when(mMockWifiInfo.getNetworkId()).thenReturn(-1);
        mNetworkCallbackCaptor.getValue().onCapabilitiesChanged(
                mMockNetwork, mMockNetworkCapabilities);

        // WifiInfo matches no request configs, so the connected entry should be null
        assertThat(wifiPickerTracker.getConnectedWifiEntry()).isNull();
    }

    /**
     * Tests that getActiveWifiEntries() returns the connected primary WifiEntry if we start already
     * connected to a network.
     */
    @Test
    public void testGetActiveWifiEntries_alreadyConnectedOnStart_returnsConnectedEntry() {
        final WifiPickerTracker wifiPickerTracker = createTestWifiPickerTracker();
        final WifiConfiguration config = new WifiConfiguration();
        config.SSID = "\"ssid\"";
        config.networkId = 1;
        when(mMockWifiManager.getPrivilegedConfiguredNetworks())
                .thenReturn(Collections.singletonList(config));
        when(mMockWifiInfo.getNetworkId()).thenReturn(1);
        when(mMockWifiInfo.getRssi()).thenReturn(-50);

        wifiPickerTracker.onStart();
        mTestLooper.dispatchAll();

        assertThat(wifiPickerTracker.getConnectedWifiEntry()).isNotNull();
        assertThat(wifiPickerTracker.getActiveWifiEntries()).isNotEmpty();
        assertThat(wifiPickerTracker.getActiveWifiEntries().get(0))
                .isEqualTo(wifiPickerTracker.getConnectedWifiEntry());
    }

    /**
     * Tests that getActiveWifiEntries() returns the connected primary WifiEntry first, and any
     * secondary OEM networks that are connected.
     */
    @Test
    public void testGetActiveWifiEntries_oemNetworksConnected_returnsPrimaryAndOemNetworks() {
        final WifiPickerTracker wifiPickerTracker = createTestWifiPickerTracker();
        final WifiConfiguration primaryConfig = new WifiConfiguration();
        primaryConfig.SSID = "\"primary\"";
        primaryConfig.networkId = 1;
        final WifiConfiguration oemConfig = new WifiConfiguration();
        oemConfig.SSID = "\"oem\"";
        oemConfig.networkId = 2;
        final WifiConfiguration oemPrivateConfig = new WifiConfiguration();
        oemPrivateConfig.SSID = "\"oemPrivate\"";
        oemPrivateConfig.networkId = 3;
        when(mMockWifiManager.getPrivilegedConfiguredNetworks())
                .thenReturn(Arrays.asList(primaryConfig, oemConfig, oemPrivateConfig));
        wifiPickerTracker.onStart();
        mTestLooper.dispatchAll();
        verify(mMockConnectivityManager).registerNetworkCallback(
                any(), mNetworkCallbackCaptor.capture(), any());

        MockitoSession session = mockitoSession().spyStatic(NonSdkApiWrapper.class).startMocking();
        try {
            // Connect to primary network
            when(mMockWifiInfo.getNetworkId()).thenReturn(primaryConfig.networkId);
            when(mMockWifiInfo.getRssi()).thenReturn(-50);
            doReturn(true).when(() -> NonSdkApiWrapper.isPrimary(mMockWifiInfo));
            mNetworkCallbackCaptor.getValue().onCapabilitiesChanged(
                    mMockNetwork, mMockNetworkCapabilities);

            // Connect to OEM network
            Network oemNetwork = mock(Network.class);
            NetworkCapabilities oemCapabilities = mock(NetworkCapabilities.class);
            WifiInfo oemWifiInfo = mock(WifiInfo.class);
            when(oemWifiInfo.getNetworkId()).thenReturn(oemConfig.networkId);
            when(oemWifiInfo.getRssi()).thenReturn(-50);
            doReturn(false).when(() -> NonSdkApiWrapper.isPrimary(oemWifiInfo));
            doReturn(true).when(() -> NonSdkApiWrapper.isOemCapabilities(oemCapabilities));
            when(oemCapabilities.getTransportInfo()).thenReturn(oemWifiInfo);
            mNetworkCallbackCaptor.getValue().onCapabilitiesChanged(
                    oemNetwork, oemCapabilities);

            WifiEntry primaryWifiEntry = wifiPickerTracker.getActiveWifiEntries().get(0);
            WifiEntry oemWifiEntry = wifiPickerTracker.getActiveWifiEntries().get(1);

            // Primary should go first, then the OEM network.
            assertThat(primaryWifiEntry.getTitle()).isEqualTo("primary");
            assertThat(oemWifiEntry.getTitle()).isEqualTo("oem");

            // Both entries should be connected
            assertThat(primaryWifiEntry.getConnectedState()).isEqualTo(CONNECTED_STATE_CONNECTED);
            assertThat(oemWifiEntry.getConnectedState()).isEqualTo(CONNECTED_STATE_CONNECTED);


            // Only the primary network should be primary
            assertThat(primaryWifiEntry.isPrimaryNetwork()).isTrue();
            assertThat(oemWifiEntry.isPrimaryNetwork()).isFalse();

            // The primary should be returned in getWifiEntries()
            assertThat(wifiPickerTracker.getConnectedWifiEntry()).isEqualTo(primaryWifiEntry);

            // Disconnect primary. Secondary OEM network should not be primary
            mNetworkCallbackCaptor.getValue().onLost(mMockNetwork);
            assertThat(wifiPickerTracker.getConnectedWifiEntry()).isNull();
            assertThat(primaryWifiEntry.getConnectedState())
                    .isEqualTo(CONNECTED_STATE_DISCONNECTED);
            assertThat(wifiPickerTracker.getActiveWifiEntries()).containsExactly(oemWifiEntry);
            assertThat(oemWifiEntry.getConnectedState()).isEqualTo(CONNECTED_STATE_CONNECTED);

            // OEM network becomes primary.
            doReturn(true).when(() -> NonSdkApiWrapper.isPrimary(oemWifiInfo));
            mNetworkCallbackCaptor.getValue().onCapabilitiesChanged(
                    oemNetwork, oemCapabilities);
            assertThat(wifiPickerTracker.getConnectedWifiEntry()).isEqualTo(oemWifiEntry);
            assertThat(wifiPickerTracker.getActiveWifiEntries()).containsExactly(oemWifiEntry);
            assertThat(oemWifiEntry.isPrimaryNetwork()).isTrue();

            // Disconnect the OEM network.
            mNetworkCallbackCaptor.getValue().onLost(oemNetwork);
            assertThat(oemWifiEntry.getConnectedState())
                    .isEqualTo(CONNECTED_STATE_DISCONNECTED);
            assertThat(wifiPickerTracker.getConnectedWifiEntry()).isNull();
            assertThat(wifiPickerTracker.getActiveWifiEntries()).isEmpty();
        } finally {
            session.finishMocking();
        }
    }

    /**
     * Tests that SCAN_RESULTS_AVAILABLE_ACTION calls WifiManager#getMatchingOsuProviders()
     */
    @Test
    public void testScanResultsAvailableAction_callsGetMatchingOsuProviders() {
        final WifiPickerTracker wifiPickerTracker = createTestWifiPickerTracker();
        wifiPickerTracker.onStart();
        mTestLooper.dispatchAll();
        verify(mMockContext).registerReceiver(mBroadcastReceiverCaptor.capture(),
                any(), any(), any());

        mBroadcastReceiverCaptor.getValue().onReceive(mMockContext,
                new Intent(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION));

        verify(mMockWifiManager, atLeastOnce()).getMatchingOsuProviders(any());
    }

    /**
     * Tests that a connected MergedCarrierEntry is returned if the current WifiInfo has a matching
     * subscription id.
     */
    @Test
    public void testGetMergedCarrierEntry_wifiInfoHasMatchingSubId_entryIsConnected() {
        final int subId = 1;
        when(mMockWifiInfo.isCarrierMerged()).thenReturn(true);
        when(mMockWifiInfo.getSubscriptionId()).thenReturn(subId);
        final WifiPickerTracker wifiPickerTracker = createTestWifiPickerTracker();
        wifiPickerTracker.onStart();
        mTestLooper.dispatchAll();
        verify(mMockContext).registerReceiver(mBroadcastReceiverCaptor.capture(),
                any(), any(), any());

        final Intent intent = new Intent(TelephonyManager.ACTION_DEFAULT_DATA_SUBSCRIPTION_CHANGED);
        intent.putExtra("subscription", subId);
        mBroadcastReceiverCaptor.getValue().onReceive(mMockContext, intent);

        assertThat(wifiPickerTracker.getMergedCarrierEntry().getConnectedState())
                .isEqualTo(CONNECTED_STATE_CONNECTED);
    }

    /**
     * Tests that getMergedCarrierEntry returns a new MergedCarrierEntry with the correct
     * subscription ID if the default subscription ID changes.
     */
    @Test
    public void testGetMergedCarrierEntry_subscriptionIdChanges_entryChanges() {
        final int subId1 = 1;
        final int subId2 = 2;
        final WifiPickerTracker wifiPickerTracker = createTestWifiPickerTracker();
        wifiPickerTracker.onStart();
        mTestLooper.dispatchAll();
        verify(mMockContext).registerReceiver(mBroadcastReceiverCaptor.capture(),
                any(), any(), any());
        final Intent intent1 =
                new Intent(TelephonyManager.ACTION_DEFAULT_DATA_SUBSCRIPTION_CHANGED);
        intent1.putExtra("subscription", subId1);
        mBroadcastReceiverCaptor.getValue().onReceive(mMockContext, intent1);

        final Intent intent2 =
                new Intent(TelephonyManager.ACTION_DEFAULT_DATA_SUBSCRIPTION_CHANGED);
        intent2.putExtra("subscription", subId2);
        mBroadcastReceiverCaptor.getValue().onReceive(mMockContext, intent2);

        assertThat(wifiPickerTracker.getMergedCarrierEntry().getSubscriptionId())
                .isEqualTo(subId2);
    }

    /**
     * Tests that getWifiEntries returns separate WifiEntries for each security family for scans
     * with the same SSID
     */
    @Test
    public void testGetWifiEntries_sameSsidDifferentSecurity_entriesMergedBySecurityFamily() {
        final ArrayList scanList = new ArrayList();
        final String ssid = "ssid";
        final String bssid = "bssid";
        int bssidNum = 0;
        for (String capabilities : Arrays.asList(
                "",
                "[OWE]",
                "[OWE_TRANSITION]",
                "[WEP]",
                "[PSK]",
                "[SAE]",
                "[PSK][SAE]",
                "[EAP/SHA1]",
                "[RSN-EAP/SHA1+EAP/SHA256][MFPC]",
                "[RSN-EAP/SHA256][MFPC][MFPR]",
                "[RSN-SUITE_B_192][MFPR]"
        )) {
            final ScanResult scan = buildScanResult(ssid, bssid + bssidNum++, START_MILLIS);
            scan.capabilities = capabilities;
            scanList.add(scan);
        }
        when(mMockWifiManager.getScanResults()).thenReturn(scanList);

        final WifiPickerTracker wifiPickerTracker = createTestWifiPickerTracker();
        wifiPickerTracker.onStart();
        mTestLooper.dispatchAll();
        verify(mMockContext).registerReceiver(mBroadcastReceiverCaptor.capture(),
                any(), any(), any());

        // Open/OWE, PSK/SAE, EAP/EAP-WPA3 should be merged to a single entry
        List<WifiEntry> wifiEntries = wifiPickerTracker.getWifiEntries();
        assertThat(wifiEntries.size()).isEqualTo(5);
        assertThat(wifiEntries.stream()
                .map(entry -> entry.getSecurityTypes())
                .collect(Collectors.toList()))
                .containsExactly(
                        Arrays.asList(WifiInfo.SECURITY_TYPE_OPEN, WifiInfo.SECURITY_TYPE_OWE),
                        Arrays.asList(WifiInfo.SECURITY_TYPE_WEP),
                        Arrays.asList(WifiInfo.SECURITY_TYPE_PSK, WifiInfo.SECURITY_TYPE_SAE),
                        Arrays.asList(WifiInfo.SECURITY_TYPE_EAP,
                                WifiInfo.SECURITY_TYPE_EAP_WPA3_ENTERPRISE),
                        Arrays.asList(WifiInfo.SECURITY_TYPE_EAP_WPA3_ENTERPRISE_192_BIT));

        // Use a PSK config, EAP config, and Open config, and see that the security types returned
        // for those grouped entries change to reflect the available configs.
        WifiConfiguration openConfig = buildWifiConfiguration(ssid);
        openConfig.setSecurityParams(WifiConfiguration.SECURITY_TYPE_OPEN);
        openConfig.networkId = 1;
        WifiConfiguration pskConfig = buildWifiConfiguration(ssid);
        pskConfig.setSecurityParams(WifiConfiguration.SECURITY_TYPE_PSK);
        pskConfig.networkId = 2;
        WifiConfiguration eapConfig = buildWifiConfiguration(ssid);
        eapConfig.setSecurityParams(WifiConfiguration.SECURITY_TYPE_EAP);
        eapConfig.networkId = 3;
        when(mMockWifiManager.getPrivilegedConfiguredNetworks()).thenReturn(
                Arrays.asList(openConfig, pskConfig, eapConfig));
        mBroadcastReceiverCaptor.getValue().onReceive(mMockContext,
                new Intent(WifiManager.CONFIGURED_NETWORKS_CHANGED_ACTION));
        mTestLooper.dispatchAll();

        // Open/OWE becomes Open, PSK/SAE becomes PSK, EAP/EAP-WPA3 does not change since EAP config
        // also works for EAP-WPA3.
        wifiEntries = wifiPickerTracker.getWifiEntries();
        assertThat(wifiEntries.size()).isEqualTo(5);
        assertThat(wifiEntries.stream()
                .map(entry -> entry.getSecurityTypes())
                .collect(Collectors.toList()))
                .containsExactly(
                        Arrays.asList(WifiInfo.SECURITY_TYPE_OPEN),
                        Arrays.asList(WifiInfo.SECURITY_TYPE_WEP),
                        Arrays.asList(WifiInfo.SECURITY_TYPE_PSK),
                        Arrays.asList(WifiInfo.SECURITY_TYPE_EAP,
                                WifiInfo.SECURITY_TYPE_EAP_WPA3_ENTERPRISE),
                        Arrays.asList(WifiInfo.SECURITY_TYPE_EAP_WPA3_ENTERPRISE_192_BIT));

        // Use SAE config, EAP-WPA3 config, and OWE config
        WifiConfiguration oweConfig = buildWifiConfiguration(ssid);
        oweConfig.setSecurityParams(WifiConfiguration.SECURITY_TYPE_OWE);
        oweConfig.networkId = 1;
        WifiConfiguration saeConfig = buildWifiConfiguration(ssid);
        saeConfig.setSecurityParams(WifiConfiguration.SECURITY_TYPE_SAE);
        saeConfig.networkId = 2;
        WifiConfiguration eapWpa3Config = buildWifiConfiguration(ssid);
        eapWpa3Config.setSecurityParams(WifiConfiguration.SECURITY_TYPE_EAP_WPA3_ENTERPRISE);
        eapWpa3Config.networkId = 3;
        when(mMockWifiManager.getPrivilegedConfiguredNetworks()).thenReturn(
                Arrays.asList(oweConfig, saeConfig, eapWpa3Config));
        mBroadcastReceiverCaptor.getValue().onReceive(mMockContext,
                new Intent(WifiManager.CONFIGURED_NETWORKS_CHANGED_ACTION));
        mTestLooper.dispatchAll();

        // Open/OWE becomes OWE, PSK/SAE becomes SAE, EAP/EAP-WPA3 becomes EAP-WPA3
        wifiEntries = wifiPickerTracker.getWifiEntries();
        assertThat(wifiEntries.size()).isEqualTo(5);
        assertThat(wifiEntries.stream()
                .map(entry -> entry.getSecurityTypes())
                .collect(Collectors.toList()))
                .containsExactly(
                        Arrays.asList(WifiInfo.SECURITY_TYPE_OWE),
                        Arrays.asList(WifiInfo.SECURITY_TYPE_WEP),
                        Arrays.asList(WifiInfo.SECURITY_TYPE_SAE),
                        Arrays.asList(WifiInfo.SECURITY_TYPE_EAP_WPA3_ENTERPRISE),
                        Arrays.asList(WifiInfo.SECURITY_TYPE_EAP_WPA3_ENTERPRISE_192_BIT));

        // Now use configs for all the security types in the family
        when(mMockWifiManager.getPrivilegedConfiguredNetworks()).thenReturn(
                Arrays.asList(openConfig, oweConfig, pskConfig, saeConfig, eapConfig,
                        eapWpa3Config));
        mBroadcastReceiverCaptor.getValue().onReceive(mMockContext,
                new Intent(WifiManager.CONFIGURED_NETWORKS_CHANGED_ACTION));
        mTestLooper.dispatchAll();

        // All of the security types in the family should be returned.
        wifiEntries = wifiPickerTracker.getWifiEntries();
        assertThat(wifiEntries.size()).isEqualTo(5);
        assertThat(wifiEntries.stream()
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
     * Tests that getNumSavedNetworks() returns the correct number of networks based on number of
     * unique network IDs even for split configs which may have the same network ID but different
     * security types.
     */
    @Test
    public void testGetNumSavedNetworks_splitConfigs_returnsNetworkIdCount() {
        WifiConfiguration openConfig = buildWifiConfiguration("ssid");
        openConfig.networkId = 1;
        // PSK + SAE split config with the same network ID
        WifiConfiguration pskConfig = buildWifiConfiguration("ssid");
        pskConfig.setSecurityParams(WifiInfo.SECURITY_TYPE_PSK);
        pskConfig.networkId = 2;
        WifiConfiguration saeConfig = buildWifiConfiguration("ssid");
        saeConfig.setSecurityParams(WifiInfo.SECURITY_TYPE_SAE);
        saeConfig.networkId = 2;
        when(mMockWifiManager.getPrivilegedConfiguredNetworks())
                .thenReturn(Arrays.asList(openConfig, pskConfig, saeConfig));
        final WifiPickerTracker wifiPickerTracker = createTestWifiPickerTracker();
        wifiPickerTracker.onStart();
        mTestLooper.dispatchAll();

        // 1 open config + 2 split configs with same network ID should be treated as 2 networks.
        assertThat(wifiPickerTracker.getNumSavedNetworks()).isEqualTo(2);
    }

    /**
     * Tests that the MergedCarrierEntry is the default network when it is connected and Wifi is
     * the default network.
     */
    @Test
    public void testGetMergedCarrierEntry_wifiIsDefault_entryIsDefaultNetwork() {
        final int subId = 1;
        when(mMockWifiInfo.isCarrierMerged()).thenReturn(true);
        when(mMockWifiInfo.getSubscriptionId()).thenReturn(subId);
        final WifiPickerTracker wifiPickerTracker = createTestWifiPickerTracker();
        wifiPickerTracker.onStart();
        mTestLooper.dispatchAll();
        verify(mMockContext).registerReceiver(mBroadcastReceiverCaptor.capture(),
                any(), any(), any());
        final Intent intent = new Intent(TelephonyManager.ACTION_DEFAULT_DATA_SUBSCRIPTION_CHANGED);
        intent.putExtra("subscription", subId);
        mBroadcastReceiverCaptor.getValue().onReceive(mMockContext, intent);
        verify(mMockConnectivityManager, atLeast(0)).registerSystemDefaultNetworkCallback(
                mDefaultNetworkCallbackCaptor.capture(), any());
        verify(mMockConnectivityManager, atLeast(0)).registerDefaultNetworkCallback(
                mDefaultNetworkCallbackCaptor.capture(), any());
        MergedCarrierEntry mergedCarrierEntry = wifiPickerTracker.getMergedCarrierEntry();
        assertThat(mergedCarrierEntry.getConnectedState())
                .isEqualTo(CONNECTED_STATE_CONNECTED);
        // Wifi isn't default yet, so isDefaultNetwork returns false
        assertThat(mergedCarrierEntry.isDefaultNetwork()).isFalse();

        mDefaultNetworkCallbackCaptor.getValue().onCapabilitiesChanged(
                mMockNetwork, mMockNetworkCapabilities);

        // Now Wifi is default, so isDefaultNetwork returns true
        assertThat(mergedCarrierEntry.isDefaultNetwork()).isTrue();
    }

    /**
     * Tests that the MergedCarrierEntry is the default network when it is connected and
     * VCN-over-Wifi is the default network.
     */
    @Test
    public void testGetMergedCarrierEntry_vcnWifiIsDefault_entryIsDefaultNetwork() {
        final int subId = 1;
        final WifiPickerTracker wifiPickerTracker = createTestWifiPickerTracker();
        wifiPickerTracker.onStart();
        mTestLooper.dispatchAll();
        verify(mMockContext).registerReceiver(mBroadcastReceiverCaptor.capture(),
                any(), any(), any());
        final Intent intent = new Intent(TelephonyManager.ACTION_DEFAULT_DATA_SUBSCRIPTION_CHANGED);
        intent.putExtra("subscription", subId);
        mBroadcastReceiverCaptor.getValue().onReceive(mMockContext, intent);
        verify(mMockConnectivityManager).registerNetworkCallback(
                any(), mNetworkCallbackCaptor.capture(), any());
        verify(mMockConnectivityManager, atLeast(0)).registerSystemDefaultNetworkCallback(
                mDefaultNetworkCallbackCaptor.capture(), any());
        verify(mMockConnectivityManager, atLeast(0)).registerDefaultNetworkCallback(
                mDefaultNetworkCallbackCaptor.capture(), any());

        MockitoSession session = mockitoSession().spyStatic(NonSdkApiWrapper.class).startMocking();
        try {
            // Connect to VCN-over-Wifi network
            when(mMockWifiInfo.isCarrierMerged()).thenReturn(true);
            when(mMockWifiInfo.getSubscriptionId()).thenReturn(subId);
            doReturn(mMockWifiInfo).when(() ->
                    NonSdkApiWrapper.getWifiInfoIfVcn(mMockVcnNetworkCapabilities));
            doReturn(true).when(() -> NonSdkApiWrapper.isPrimary(mMockWifiInfo));
            mNetworkCallbackCaptor.getValue().onCapabilitiesChanged(
                    mMockNetwork, mMockVcnNetworkCapabilities);
            MergedCarrierEntry mergedCarrierEntry = wifiPickerTracker.getMergedCarrierEntry();
            assertThat(mergedCarrierEntry.getConnectedState())
                    .isEqualTo(CONNECTED_STATE_CONNECTED);
            // Wifi isn't default yet, so isDefaultNetwork returns false
            assertThat(mergedCarrierEntry.isDefaultNetwork()).isFalse();
            mDefaultNetworkCallbackCaptor.getValue().onCapabilitiesChanged(mMockNetwork,
                    mMockVcnNetworkCapabilities);
            // Now VCN-over-Wifi is default, so isDefaultNetwork returns true
            assertThat(mergedCarrierEntry.isDefaultNetwork()).isTrue();
        } finally {
            session.finishMocking();
        }
    }

    /**
     * Tests that a MergedCarrierEntry is returned even if WifiPickerTracker hasn't been initialized
     * via handleOnStart() yet.
     */
    @Test
    public void testGetMergedCarrierEntry_trackerNotInitialized_entryIsNotNull() {
        final int subId = 1;
        MockitoSession session = mockitoSession().spyStatic(SubscriptionManager.class)
                .startMocking();
        try {
            doReturn(subId).when(SubscriptionManager::getDefaultDataSubscriptionId);
            final WifiPickerTracker wifiPickerTracker = createTestWifiPickerTracker();
            MergedCarrierEntry mergedCarrierEntry = wifiPickerTracker.getMergedCarrierEntry();
            assertThat(mergedCarrierEntry).isNotNull();
        } finally {
            session.finishMocking();
        }
    }

    /**
     * Verifies that the BroadcastReceiver and network callbacks are unregistered by the onStop()
     * worker thread runnable.
     */
    @Test
    public void testBroadcastReceiverAndNetworkCallbacks_onStopRunnable_unregistersCallbacks() {
        final WifiPickerTracker wifiPickerTracker = createTestWifiPickerTracker();
        wifiPickerTracker.onStart();
        mTestLooper.dispatchAll();
        verify(mMockContext).registerReceiver(mBroadcastReceiverCaptor.capture(),
                any(), any(), any());
        verify(mMockConnectivityManager, atLeast(0)).registerSystemDefaultNetworkCallback(
                mDefaultNetworkCallbackCaptor.capture(), any());
        verify(mMockConnectivityManager, atLeast(0)).registerDefaultNetworkCallback(
                mDefaultNetworkCallbackCaptor.capture(), any());

        wifiPickerTracker.onStop();
        mTestLooper.dispatchAll();
        verify(mMockContext).unregisterReceiver(mBroadcastReceiverCaptor.getValue());
        verify(mMockConnectivityManager).unregisterNetworkCallback(
                mDefaultNetworkCallbackCaptor.getValue());
        verify(mMockConnectivityManager).unregisterNetworkCallback(
                mDefaultNetworkCallbackCaptor.getValue());
    }

    /**
     * Verifies that the BroadcastReceiver and network callbacks are unregistered by onDestroyed().
     */
    @Test
    public void testBroadcastReceiverAndNetworkCallbacks_onDestroyed_unregistersCallbacks() {
        final WifiPickerTracker wifiPickerTracker = createTestWifiPickerTracker();
        wifiPickerTracker.onStart();
        mTestLooper.dispatchAll();
        verify(mMockContext).registerReceiver(mBroadcastReceiverCaptor.capture(),
                any(), any(), any());
        verify(mMockConnectivityManager, atLeast(0)).registerSystemDefaultNetworkCallback(
                mDefaultNetworkCallbackCaptor.capture(), any());
        verify(mMockConnectivityManager, atLeast(0)).registerDefaultNetworkCallback(
                mDefaultNetworkCallbackCaptor.capture(), any());

        wifiPickerTracker.onStop();
        wifiPickerTracker.onDestroy();
        verify(mMockContext).unregisterReceiver(mBroadcastReceiverCaptor.getValue());
        verify(mMockConnectivityManager).unregisterNetworkCallback(
                mDefaultNetworkCallbackCaptor.getValue());
        verify(mMockConnectivityManager).unregisterNetworkCallback(
                mDefaultNetworkCallbackCaptor.getValue());
    }

    /**
     * Tests that the BaseWifiTracker.Scanner continues scanning with WifiManager.startScan() after
     * the first WifiScanner result is received.
     */
    @Test
    public void testScanner_wifiScannerResultReceived_scannerContinues() {
        final WifiPickerTracker wifiPickerTracker = createTestWifiPickerTracker();
        wifiPickerTracker.onStart();
        mTestLooper.dispatchAll();
        verify(mMockContext).registerReceiver(mBroadcastReceiverCaptor.capture(),
                any(), any(), any());
        mBroadcastReceiverCaptor.getValue().onReceive(mMockContext,
                new Intent(WifiManager.WIFI_STATE_CHANGED_ACTION).putExtra(
                        WifiManager.EXTRA_WIFI_STATE, WifiManager.WIFI_STATE_ENABLED));

        ArgumentCaptor<WifiScanner.ScanListener> mScanListenerCaptor =
                ArgumentCaptor.forClass(WifiScanner.ScanListener.class);
        verify(mWifiScanner).startScan(any(), mScanListenerCaptor.capture());
        mTestLooper.moveTimeForward(SCAN_INTERVAL_MILLIS);
        mTestLooper.dispatchAll();
        verify(mMockWifiManager, never()).startScan();
        verify(mMockCallback).onScanRequested();

        mScanListenerCaptor.getValue().onResults(null);
        mTestLooper.dispatchAll();
        verify(mMockWifiManager).startScan();
        verify(mMockCallback, times(2)).onScanRequested();
    }

    /**
     * Tests that the BaseWifiTracker.Scanner continues scanning with WifiManager.startScan() after
     * the first WifiScanner scan fails.
     */
    @Test
    public void testScanner_wifiScannerFailed_scannerContinues() {
        final WifiPickerTracker wifiPickerTracker = createTestWifiPickerTracker();
        wifiPickerTracker.onStart();
        mTestLooper.dispatchAll();
        verify(mMockContext).registerReceiver(mBroadcastReceiverCaptor.capture(),
                any(), any(), any());
        mBroadcastReceiverCaptor.getValue().onReceive(mMockContext,
                new Intent(WifiManager.WIFI_STATE_CHANGED_ACTION).putExtra(
                        WifiManager.EXTRA_WIFI_STATE, WifiManager.WIFI_STATE_ENABLED));

        ArgumentCaptor<WifiScanner.ScanListener> mScanListenerCaptor =
                ArgumentCaptor.forClass(WifiScanner.ScanListener.class);
        verify(mWifiScanner).startScan(any(), mScanListenerCaptor.capture());
        mTestLooper.moveTimeForward(SCAN_INTERVAL_MILLIS);
        mTestLooper.dispatchAll();
        verify(mMockWifiManager, never()).startScan();

        mScanListenerCaptor.getValue().onFailure(0, "Reason");
        mTestLooper.dispatchAll();
        verify(mMockWifiManager).startScan();
    }

    /**
     * Tests that the BaseWifiTracker.Scanner does not scan if scanning was disabled.
     */
    @Test
    public void testScanner_scanningDisabled_scannerDoesNotStart() {
        final WifiPickerTracker wifiPickerTracker = createTestWifiPickerTracker();
        wifiPickerTracker.disableScanning();
        wifiPickerTracker.onStart();
        mTestLooper.dispatchAll();
        verify(mMockContext).registerReceiver(mBroadcastReceiverCaptor.capture(),
                any(), any(), any());
        mBroadcastReceiverCaptor.getValue().onReceive(mMockContext,
                new Intent(WifiManager.WIFI_STATE_CHANGED_ACTION).putExtra(
                        WifiManager.EXTRA_WIFI_STATE, WifiManager.WIFI_STATE_ENABLED));

        ArgumentCaptor<WifiScanner.ScanListener> mScanListenerCaptor =
                ArgumentCaptor.forClass(WifiScanner.ScanListener.class);
        verify(mWifiScanner, never()).startScan(any(), mScanListenerCaptor.capture());
        verify(mMockWifiManager, never()).startScan();
        verify(mInjector).disableVerboseLogging();
    }

    @Test
    public void testScanner_startAfterOnStop_doesNotStart() {
        final WifiPickerTracker wifiPickerTracker = createTestWifiPickerTracker();
        wifiPickerTracker.onStart();
        mTestLooper.dispatchAll();
        verify(mMockContext).registerReceiver(mBroadcastReceiverCaptor.capture(),
                any(), any(), any());

        // Stop and then get WIFI_STATE_ENABLED afterwards to trigger starting the scanner.
        wifiPickerTracker.onStop();
        mTestLooper.dispatchAll();
        mBroadcastReceiverCaptor.getValue().onReceive(mMockContext,
                new Intent(WifiManager.WIFI_STATE_CHANGED_ACTION)
                        .putExtra(WifiManager.EXTRA_WIFI_STATE, WifiManager.WIFI_STATE_ENABLED));

        // Scanner should not have started
        verify(mMockWifiManager, never()).startScan();

        // Start again and get WIFI_STATE_ENABLED
        wifiPickerTracker.onStart();
        mTestLooper.dispatchAll();
        mBroadcastReceiverCaptor.getValue().onReceive(mMockContext,
                new Intent(WifiManager.WIFI_STATE_CHANGED_ACTION)
                        .putExtra(WifiManager.EXTRA_WIFI_STATE, WifiManager.WIFI_STATE_ENABLED));

        // Scanner should start now
        verify(mMockWifiManager, never()).startScan();
    }

    @Test
    public void testSharedConnectivityManager_onStart_registersCallback() {
        final WifiPickerTracker wifiPickerTracker = createTestWifiPickerTracker();

        wifiPickerTracker.onStart();
        mTestLooper.dispatchAll();

        verify(mMockSharedConnectivityManager).registerCallback(any(),
                mSharedConnectivityCallbackCaptor.capture());
    }

    @Test
    public void testSharedConnectivityManager_onServiceConnected_gettersCalled() {
        final WifiPickerTracker wifiPickerTracker = createTestWifiPickerTracker();
        wifiPickerTracker.onStart();
        mTestLooper.dispatchAll();
        verify(mMockSharedConnectivityManager).registerCallback(any(),
                mSharedConnectivityCallbackCaptor.capture());

        mSharedConnectivityCallbackCaptor.getValue().onServiceConnected();
        mTestLooper.dispatchAll();

        verify(mMockSharedConnectivityManager).getKnownNetworks();
        verify(mMockSharedConnectivityManager).getHotspotNetworks();
        verify(mMockSharedConnectivityManager).getHotspotNetworkConnectionStatus();
    }

    @Test
    public void testSharedConnectivityManager_onServiceDisconnected_networksCleared() {
        final KnownNetwork testKnownNetwork = new KnownNetwork.Builder()
                .setNetworkSource(KnownNetwork.NETWORK_SOURCE_NEARBY_SELF)
                .setSsid("ssid")
                .addSecurityType(SECURITY_TYPE_PSK)
                .addSecurityType(SECURITY_TYPE_SAE)
                .setNetworkProviderInfo(new NetworkProviderInfo
                        .Builder("My Phone", "Pixel 7")
                        .setDeviceType(NetworkProviderInfo.DEVICE_TYPE_PHONE)
                        .setBatteryPercentage(100)
                        .setConnectionStrength(3)
                        .build())
                .build();
        when(mMockSharedConnectivityManager.getKnownNetworks()).thenReturn(
                Collections.singletonList(testKnownNetwork));
        when(mMockWifiManager.getScanResults()).thenReturn(
                Collections.singletonList(buildScanResult("ssid", "bssid", START_MILLIS,
                        "[PSK/SAE]")));
        final HotspotNetwork testHotspotNetwork = new HotspotNetwork.Builder()
                .setDeviceId(1)
                .setNetworkProviderInfo(new NetworkProviderInfo
                        .Builder("My Phone", "Pixel 7")
                        .setDeviceType(NetworkProviderInfo.DEVICE_TYPE_PHONE)
                        .setBatteryPercentage(100)
                        .setConnectionStrength(3)
                        .build())
                .setHostNetworkType(HotspotNetwork.NETWORK_TYPE_CELLULAR)
                .setNetworkName("Google Fi")
                .build();
        when(mMockSharedConnectivityManager.getHotspotNetworks()).thenReturn(
                Collections.singletonList(testHotspotNetwork));
        final WifiPickerTracker wifiPickerTracker = createTestWifiPickerTracker();
        wifiPickerTracker.onStart();
        mTestLooper.dispatchAll();
        verify(mMockSharedConnectivityManager).registerCallback(any(),
                mSharedConnectivityCallbackCaptor.capture());
        mSharedConnectivityCallbackCaptor.getValue().onServiceConnected();
        mTestLooper.dispatchAll();

        mSharedConnectivityCallbackCaptor.getValue().onServiceDisconnected();

        assertThat(wifiPickerTracker.getWifiEntries().stream().filter(
                entry -> entry instanceof KnownNetworkEntry).toList()).isEmpty();
        assertThat(wifiPickerTracker.getWifiEntries().stream().filter(
                entry -> entry instanceof HotspotNetworkEntry).toList()).isEmpty();
    }

    @Test
    public void testKnownNetworks_noMatchingScanResult_entryNotIncluded() {
        final KnownNetwork testKnownNetwork = new KnownNetwork.Builder()
                .setNetworkSource(KnownNetwork.NETWORK_SOURCE_NEARBY_SELF)
                .setSsid("ssid")
                .addSecurityType(SECURITY_TYPE_PSK)
                .addSecurityType(SECURITY_TYPE_SAE)
                .setNetworkProviderInfo(new NetworkProviderInfo
                        .Builder("My Phone", "Pixel 7")
                        .setDeviceType(NetworkProviderInfo.DEVICE_TYPE_PHONE)
                        .setBatteryPercentage(100)
                        .setConnectionStrength(3)
                        .build())
                .build();
        when(mMockSharedConnectivityManager.getKnownNetworks()).thenReturn(
                Collections.singletonList(testKnownNetwork));
        when(mMockWifiManager.getScanResults()).thenReturn(
                Collections.singletonList(buildScanResult("other_ssid", "bssid", START_MILLIS,
                        "[WEP]")));
        final WifiPickerTracker wifiPickerTracker = createTestWifiPickerTracker();
        wifiPickerTracker.onStart();
        mTestLooper.dispatchAll();
        verify(mMockSharedConnectivityManager).registerCallback(any(),
                mSharedConnectivityCallbackCaptor.capture());

        mSharedConnectivityCallbackCaptor.getValue().onServiceConnected();
        mTestLooper.dispatchAll();

        assertThat(wifiPickerTracker.getWifiEntries().stream().filter(
                entry -> entry instanceof KnownNetworkEntry).toList()).isEmpty();
    }

    @Test
    public void testKnownNetworks_matchingScanResult_entryIncluded() {
        final KnownNetwork testKnownNetwork = new KnownNetwork.Builder()
                .setNetworkSource(KnownNetwork.NETWORK_SOURCE_NEARBY_SELF)
                .setSsid("ssid")
                .addSecurityType(SECURITY_TYPE_PSK)
                .addSecurityType(SECURITY_TYPE_SAE)
                .setNetworkProviderInfo(new NetworkProviderInfo
                        .Builder("My Phone", "Pixel 7")
                        .setDeviceType(NetworkProviderInfo.DEVICE_TYPE_PHONE)
                        .setBatteryPercentage(100)
                        .setConnectionStrength(3)
                        .build())
                .build();
        when(mMockSharedConnectivityManager.getKnownNetworks()).thenReturn(
                Collections.singletonList(testKnownNetwork));
        when(mMockWifiManager.getScanResults()).thenReturn(
                Collections.singletonList(buildScanResult("ssid", "bssid", START_MILLIS,
                        "[PSK/SAE]")));
        final WifiPickerTracker wifiPickerTracker = createTestWifiPickerTracker();
        wifiPickerTracker.onStart();
        mTestLooper.dispatchAll();
        verify(mMockSharedConnectivityManager).registerCallback(any(),
                mSharedConnectivityCallbackCaptor.capture());

        mSharedConnectivityCallbackCaptor.getValue().onServiceConnected();
        mTestLooper.dispatchAll();

        assertThat(wifiPickerTracker.getWifiEntries().stream().filter(
                entry -> entry instanceof KnownNetworkEntry).toList()).hasSize(1);
    }

    @Test
    public void testKnownNetworks_matchingConnectedScanResult_entryNotIncluded() {
        final KnownNetwork testKnownNetwork = new KnownNetwork.Builder()
                .setNetworkSource(KnownNetwork.NETWORK_SOURCE_NEARBY_SELF)
                .setSsid("ssid")
                .addSecurityType(SECURITY_TYPE_PSK)
                .addSecurityType(SECURITY_TYPE_SAE)
                .setNetworkProviderInfo(new NetworkProviderInfo
                        .Builder("My Phone", "Pixel 7")
                        .setDeviceType(NetworkProviderInfo.DEVICE_TYPE_PHONE)
                        .setBatteryPercentage(100)
                        .setConnectionStrength(3)
                        .build())
                .build();
        when(mMockSharedConnectivityManager.getKnownNetworks()).thenReturn(
                Collections.singletonList(testKnownNetwork));
        when(mMockWifiManager.getScanResults()).thenReturn(
                Collections.singletonList(buildScanResult("ssid", "bssid", START_MILLIS,
                        "[PSK/SAE]")));
        final WifiConfiguration config = new WifiConfiguration();
        config.SSID = "\"ssid\"";
        config.networkId = 1;
        config.setSecurityParams(WifiConfiguration.SECURITY_TYPE_PSK);
        when(mMockWifiManager.getPrivilegedConfiguredNetworks())
                .thenReturn(Collections.singletonList(config));
        when(mMockWifiInfo.getNetworkId()).thenReturn(1);
        when(mMockWifiInfo.getRssi()).thenReturn(GOOD_RSSI);
        when(mMockWifiInfo.getCurrentSecurityType()).thenReturn(SECURITY_TYPE_PSK);
        final WifiPickerTracker wifiPickerTracker = createTestWifiPickerTracker();
        wifiPickerTracker.onStart();
        mTestLooper.dispatchAll();

        assertThat(wifiPickerTracker.getWifiEntries().stream().filter(
                entry -> entry instanceof KnownNetworkEntry).toList()).isEmpty();
        assertThat(wifiPickerTracker.getActiveWifiEntries().stream().filter(
                entry -> entry instanceof KnownNetworkEntry).toList()).isEmpty();
        assertThat(wifiPickerTracker.getActiveWifiEntries().stream().filter(
                entry -> entry instanceof StandardWifiEntry).toList()).hasSize(1);
    }

    @Test
    public void testKnownNetworks_newKnownNetworkMatchesSavedNetwork_knownNetworkNotIncluded() {
        final KnownNetwork testKnownNetwork = new KnownNetwork.Builder()
                .setNetworkSource(KnownNetwork.NETWORK_SOURCE_NEARBY_SELF)
                .setSsid("ssid")
                .addSecurityType(SECURITY_TYPE_PSK)
                .addSecurityType(SECURITY_TYPE_SAE)
                .setNetworkProviderInfo(new NetworkProviderInfo
                        .Builder("My Phone", "Pixel 7")
                        .setDeviceType(NetworkProviderInfo.DEVICE_TYPE_PHONE)
                        .setBatteryPercentage(100)
                        .setConnectionStrength(3)
                        .build())
                .build();
        when(mMockWifiManager.getScanResults()).thenReturn(
                Collections.singletonList(buildScanResult("ssid", "bssid", START_MILLIS,
                        "[PSK/SAE]")));
        final WifiConfiguration config = new WifiConfiguration();
        config.SSID = "\"ssid\"";
        config.networkId = 1;
        config.setSecurityParams(WifiConfiguration.SECURITY_TYPE_PSK);
        when(mMockWifiManager.getPrivilegedConfiguredNetworks())
                .thenReturn(Collections.singletonList(config));
        final WifiPickerTracker wifiPickerTracker = createTestWifiPickerTracker();
        wifiPickerTracker.onStart();
        mTestLooper.dispatchAll();
        wifiPickerTracker.handleKnownNetworksUpdated(Collections.singletonList(testKnownNetwork));

        assertThat(wifiPickerTracker.getWifiEntries().stream().filter(
                entry -> entry instanceof KnownNetworkEntry).toList()).isEmpty();
        assertThat(wifiPickerTracker.getWifiEntries().stream().filter(
                entry -> entry instanceof StandardWifiEntry).toList()).hasSize(1);
    }

    @Test
    public void testKnownNetworks_onKnownNetworkConnectionStatusChanged_matchingEntryCalled() {
        final KnownNetwork testKnownNetwork1 = new KnownNetwork.Builder()
                .setNetworkSource(KnownNetwork.NETWORK_SOURCE_NEARBY_SELF)
                .setSsid("ssid1")
                .addSecurityType(SECURITY_TYPE_PSK)
                .addSecurityType(SECURITY_TYPE_SAE)
                .setNetworkProviderInfo(new NetworkProviderInfo
                        .Builder("My Phone", "Pixel 7")
                        .setDeviceType(NetworkProviderInfo.DEVICE_TYPE_PHONE)
                        .setBatteryPercentage(100)
                        .setConnectionStrength(3)
                        .build())
                .build();
        final KnownNetwork testKnownNetwork2 = new KnownNetwork.Builder()
                .setNetworkSource(KnownNetwork.NETWORK_SOURCE_NEARBY_SELF)
                .setSsid("ssid2")
                .addSecurityType(SECURITY_TYPE_WEP)
                .setNetworkProviderInfo(new NetworkProviderInfo
                        .Builder("My Work Phone", "Pixel 6")
                        .setDeviceType(NetworkProviderInfo.DEVICE_TYPE_PHONE)
                        .setBatteryPercentage(90)
                        .setConnectionStrength(2)
                        .build())
                .build();
        final ScanResult testScanResult1 = buildScanResult("ssid1", "bssid1", START_MILLIS,
                "[PSK/SAE]");
        final ScanResult testScanResult2 = buildScanResult("ssid2", "bssid2", START_MILLIS,
                "[WEP]");
        when(mMockSharedConnectivityManager.getKnownNetworks()).thenReturn(
                List.of(testKnownNetwork1, testKnownNetwork2));
        when(mMockWifiManager.getScanResults()).thenReturn(
                List.of(testScanResult1, testScanResult2));
        final WifiPickerTracker wifiPickerTracker = createTestWifiPickerTracker();
        wifiPickerTracker.onStart();
        mTestLooper.dispatchAll();
        verify(mMockSharedConnectivityManager).registerCallback(any(),
                mSharedConnectivityCallbackCaptor.capture());
        mSharedConnectivityCallbackCaptor.getValue().onServiceConnected();
        mTestLooper.dispatchAll();
        assertThat(wifiPickerTracker.getWifiEntries()).hasSize(2);
        assertThat(wifiPickerTracker.getWifiEntries().get(0).getSsid()).isEqualTo("ssid1");
        final WifiEntry.ConnectCallback connectCallback1 = mock(WifiEntry.ConnectCallback.class);
        final WifiEntry.ConnectCallback connectCallback2 = mock(WifiEntry.ConnectCallback.class);
        wifiPickerTracker.getWifiEntries().get(0).connect(connectCallback1);
        wifiPickerTracker.getWifiEntries().get(1).connect(connectCallback2);

        mSharedConnectivityCallbackCaptor.getValue().onKnownNetworkConnectionStatusChanged(
                new KnownNetworkConnectionStatus.Builder()
                        .setStatus(KnownNetworkConnectionStatus.CONNECTION_STATUS_SAVE_FAILED)
                        .setKnownNetwork(testKnownNetwork1)
                        .build());
        mTestLooper.dispatchAll();

        verify(connectCallback1).onConnectResult(anyInt());
        verify(connectCallback2, never()).onConnectResult(anyInt());
    }

    @Test
    public void testKnownNetworks_entryRemoved() {
        final KnownNetwork testKnownNetwork = new KnownNetwork.Builder()
                .setNetworkSource(KnownNetwork.NETWORK_SOURCE_NEARBY_SELF)
                .setSsid("ssid")
                .addSecurityType(SECURITY_TYPE_PSK)
                .addSecurityType(SECURITY_TYPE_SAE)
                .setNetworkProviderInfo(new NetworkProviderInfo
                        .Builder("My Phone", "Pixel 7")
                        .setDeviceType(NetworkProviderInfo.DEVICE_TYPE_PHONE)
                        .setBatteryPercentage(100)
                        .setConnectionStrength(3)
                        .build())
                .build();
        when(mMockSharedConnectivityManager.getKnownNetworks()).thenReturn(
                Collections.singletonList(testKnownNetwork));
        when(mMockWifiManager.getScanResults()).thenReturn(
                Collections.singletonList(buildScanResult("ssid", "bssid", START_MILLIS,
                        "[PSK/SAE]")));
        final WifiPickerTracker wifiPickerTracker = createTestWifiPickerTracker();
        wifiPickerTracker.onStart();
        mTestLooper.dispatchAll();
        verify(mMockSharedConnectivityManager).registerCallback(any(),
                mSharedConnectivityCallbackCaptor.capture());
        mSharedConnectivityCallbackCaptor.getValue().onServiceConnected();
        mTestLooper.dispatchAll();
        assertThat(wifiPickerTracker.getWifiEntries().stream().filter(
                entry -> entry instanceof KnownNetworkEntry).toList()).hasSize(1);

        wifiPickerTracker.handleKnownNetworksUpdated(Collections.emptyList());

        assertThat(wifiPickerTracker.getWifiEntries().stream().filter(
                entry -> entry instanceof KnownNetworkEntry).toList()).isEmpty();
    }

    @Test
    public void testHotspotNetworks_noActiveHotspot_virtualEntryIncluded() {
        final HotspotNetwork testHotspotNetwork = new HotspotNetwork.Builder()
                .setDeviceId(1)
                .setNetworkProviderInfo(new NetworkProviderInfo
                        .Builder("My Phone", "Pixel 7")
                        .setDeviceType(NetworkProviderInfo.DEVICE_TYPE_PHONE)
                        .setBatteryPercentage(100)
                        .setConnectionStrength(3)
                        .build())
                .setHostNetworkType(HotspotNetwork.NETWORK_TYPE_CELLULAR)
                .setNetworkName("Google Fi")
                .build();
        when(mMockSharedConnectivityManager.getHotspotNetworks()).thenReturn(
                Collections.singletonList(testHotspotNetwork));
        final WifiPickerTracker wifiPickerTracker = createTestWifiPickerTracker();
        wifiPickerTracker.onStart();
        mTestLooper.dispatchAll();
        verify(mMockSharedConnectivityManager).registerCallback(any(),
                mSharedConnectivityCallbackCaptor.capture());

        mSharedConnectivityCallbackCaptor.getValue().onServiceConnected();
        mTestLooper.dispatchAll();

        assertThat(wifiPickerTracker.getWifiEntries().stream().filter(
                entry -> entry instanceof HotspotNetworkEntry).toList()).hasSize(1);
        WifiEntry hotspotNetworkEntry = wifiPickerTracker.getWifiEntries().stream().filter(
                entry -> entry instanceof HotspotNetworkEntry).findFirst().orElseThrow();
        assertThat(((HotspotNetworkEntry) hotspotNetworkEntry).getHotspotNetworkEntryKey()
                .isVirtualEntry()).isTrue();
    }

    @Test
    public void testHotspotNetworks_activeHotspot_nonVirtualEntryIncluded() {
        final HotspotNetwork testHotspotNetwork = new HotspotNetwork.Builder()
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
                .build();
        when(mMockSharedConnectivityManager.getHotspotNetworks()).thenReturn(
                Collections.singletonList(testHotspotNetwork));
        final WifiPickerTracker wifiPickerTracker = createTestWifiPickerTracker();
        wifiPickerTracker.onStart();
        mTestLooper.dispatchAll();
        verify(mMockSharedConnectivityManager).registerCallback(any(),
                mSharedConnectivityCallbackCaptor.capture());

        mSharedConnectivityCallbackCaptor.getValue().onServiceConnected();
        mTestLooper.dispatchAll();

        assertThat(wifiPickerTracker.getWifiEntries().stream().filter(
                entry -> entry instanceof HotspotNetworkEntry).toList()).hasSize(1);
        WifiEntry hotspotNetworkEntry = wifiPickerTracker.getWifiEntries().stream().filter(
                entry -> entry instanceof HotspotNetworkEntry).findFirst().orElseThrow();
        assertThat(((HotspotNetworkEntry) hotspotNetworkEntry).getHotspotNetworkEntryKey()
                .isVirtualEntry()).isFalse();
    }

    @Test
    public void testHotspotNetworks_duplicatesStandardEntry_standardEntryExcluded() {
        final HotspotNetwork testHotspotNetwork = new HotspotNetwork.Builder()
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
                .build();
        when(mMockSharedConnectivityManager.getHotspotNetworks()).thenReturn(
                Collections.singletonList(testHotspotNetwork));
        final WifiPickerTracker wifiPickerTracker = createTestWifiPickerTracker();
        wifiPickerTracker.onStart();
        mTestLooper.dispatchAll();
        verify(mMockContext).registerReceiver(mBroadcastReceiverCaptor.capture(),
                any(), any(), any());
        when(mMockWifiManager.getScanResults()).thenReturn(Collections.singletonList(
                buildScanResult("Instant Hotspot abcde", "0a:0b:0c:0d:0e:0f", START_MILLIS,
                        "[PSK/SAE]")));
        mBroadcastReceiverCaptor.getValue().onReceive(mMockContext,
                new Intent(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION));
        assertThat(wifiPickerTracker.getWifiEntries().stream().filter(
                entry -> entry instanceof StandardWifiEntry).toList()).hasSize(1);
        assertThat(wifiPickerTracker.getWifiEntries().stream().filter(
                entry -> entry instanceof HotspotNetworkEntry).toList()).isEmpty();

        verify(mMockSharedConnectivityManager).registerCallback(any(),
                mSharedConnectivityCallbackCaptor.capture());
        mSharedConnectivityCallbackCaptor.getValue().onServiceConnected();
        mTestLooper.dispatchAll();

        assertThat(wifiPickerTracker.getWifiEntries().stream().filter(
                entry -> entry instanceof StandardWifiEntry).toList()).isEmpty();
        assertThat(wifiPickerTracker.getWifiEntries().stream().filter(
                entry -> entry instanceof HotspotNetworkEntry).toList()).hasSize(1);
    }

    @Test
    public void testHotspotNetworks_duplicatesActiveStandardEntry_standardEntryExcluded() {
        final HotspotNetwork testHotspotNetwork = new HotspotNetwork.Builder()
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
                .build();
        when(mMockSharedConnectivityManager.getHotspotNetworks()).thenReturn(
                Collections.singletonList(testHotspotNetwork));
        final WifiConfiguration config = new WifiConfiguration();
        config.SSID = "\"Instant Hotspot abcde\"";
        config.networkId = 1;
        config.setSecurityParams(WifiConfiguration.SECURITY_TYPE_PSK);
        when(mMockWifiManager.getPrivilegedConfiguredNetworks())
                .thenReturn(Collections.singletonList(config));
        when(mMockWifiInfo.getNetworkId()).thenReturn(1);
        when(mMockWifiInfo.getRssi()).thenReturn(GOOD_RSSI);
        when(mMockWifiInfo.getSSID()).thenReturn("Instant Hotspot abcde");
        when(mMockWifiInfo.getCurrentSecurityType()).thenReturn(SECURITY_TYPE_PSK);
        when(mMockWifiManager.getScanResults()).thenReturn(Collections.singletonList(
                buildScanResult("Instant Hotspot abcde", "0a:0b:0c:0d:0e:0f", START_MILLIS,
                        "[PSK/SAE]")));
        final WifiPickerTracker wifiPickerTracker = createTestWifiPickerTracker();
        wifiPickerTracker.onStart();
        mTestLooper.dispatchAll();

        verify(mMockSharedConnectivityManager).registerCallback(any(),
                mSharedConnectivityCallbackCaptor.capture());
        mSharedConnectivityCallbackCaptor.getValue().onServiceConnected();
        mTestLooper.dispatchAll();

        assertThat(wifiPickerTracker.getActiveWifiEntries().stream().filter(
                entry -> entry instanceof StandardWifiEntry).toList()).isEmpty();
        assertThat(wifiPickerTracker.getActiveWifiEntries().stream().filter(
                entry -> entry instanceof HotspotNetworkEntry).toList()).hasSize(1);
    }

    @Test
    public void testHotspotNetworks_onHotspotNetworkConnectionStatusChanged_matchingEntryCalled() {
        final HotspotNetwork testHotspotNetwork1 = new HotspotNetwork.Builder()
                .setDeviceId(1)
                .setNetworkProviderInfo(new NetworkProviderInfo
                        .Builder("My Phone", "Pixel 7")
                        .setDeviceType(NetworkProviderInfo.DEVICE_TYPE_PHONE)
                        .setBatteryPercentage(100)
                        .setConnectionStrength(3)
                        .build())
                .setHostNetworkType(HotspotNetwork.NETWORK_TYPE_CELLULAR)
                .setNetworkName("Google Fi")
                .build();
        final HotspotNetwork testHotspotNetwork2 = new HotspotNetwork.Builder()
                .setDeviceId(2)
                .setNetworkProviderInfo(new NetworkProviderInfo
                        .Builder("My Work Phone", "Pixel 6")
                        .setDeviceType(NetworkProviderInfo.DEVICE_TYPE_PHONE)
                        .setBatteryPercentage(90)
                        .setConnectionStrength(2)
                        .build())
                .setHostNetworkType(HotspotNetwork.NETWORK_TYPE_CELLULAR)
                .setNetworkName("T-Mobile")
                .build();
        when(mMockSharedConnectivityManager.getHotspotNetworks()).thenReturn(
                List.of(testHotspotNetwork1, testHotspotNetwork2));
        final WifiPickerTracker wifiPickerTracker = createTestWifiPickerTracker();
        wifiPickerTracker.onStart();
        mTestLooper.dispatchAll();
        verify(mMockSharedConnectivityManager).registerCallback(any(),
                mSharedConnectivityCallbackCaptor.capture());
        mSharedConnectivityCallbackCaptor.getValue().onServiceConnected();
        mTestLooper.dispatchAll();

        assertThat(wifiPickerTracker.getWifiEntries()).hasSize(2);
        assertThat(((HotspotNetworkEntry) wifiPickerTracker.getWifiEntries().get(0))
                .getHotspotNetworkEntryKey().getDeviceId()).isEqualTo(1);
        final WifiEntry.ConnectCallback connectCallback1 = mock(WifiEntry.ConnectCallback.class);
        final WifiEntry.ConnectCallback connectCallback2 = mock(WifiEntry.ConnectCallback.class);
        wifiPickerTracker.getWifiEntries().get(0).connect(connectCallback1);
        wifiPickerTracker.getWifiEntries().get(1).connect(connectCallback2);

        mSharedConnectivityCallbackCaptor.getValue().onHotspotNetworkConnectionStatusChanged(
                new HotspotNetworkConnectionStatus.Builder()
                        .setStatus(HotspotNetworkConnectionStatus
                                .CONNECTION_STATUS_PROVISIONING_FAILED)
                        .setHotspotNetwork(testHotspotNetwork1)
                        .build());
        mTestLooper.dispatchAll();

        verify(connectCallback1).onConnectResult(anyInt());
        verify(connectCallback2, never()).onConnectResult(anyInt());
    }

    @Test
    public void testHotspotNetworks_multipleAvailableNetworks_sortedByUpstreamConnectionStrength() {
        final HotspotNetwork testHotspotNetwork1 = new HotspotNetwork.Builder()
                .setDeviceId(1)
                .setNetworkProviderInfo(new NetworkProviderInfo
                        .Builder("My Phone 1", "Pixel 5")
                        .setDeviceType(NetworkProviderInfo.DEVICE_TYPE_PHONE)
                        .setBatteryPercentage(100)
                        .setConnectionStrength(2)
                        .build())
                .setHostNetworkType(HotspotNetwork.NETWORK_TYPE_CELLULAR)
                .setNetworkName("Google Fi")
                .build();
        final HotspotNetwork testHotspotNetwork2 = new HotspotNetwork.Builder()
                .setDeviceId(2)
                .setNetworkProviderInfo(new NetworkProviderInfo
                        .Builder("My Phone 2", "Pixel 6")
                        .setDeviceType(NetworkProviderInfo.DEVICE_TYPE_PHONE)
                        .setBatteryPercentage(100)
                        .setConnectionStrength(4)
                        .build())
                .setHostNetworkType(HotspotNetwork.NETWORK_TYPE_CELLULAR)
                .setNetworkName("Google Fi")
                .build();
        final HotspotNetwork testHotspotNetwork3 = new HotspotNetwork.Builder()
                .setDeviceId(3)
                .setNetworkProviderInfo(new NetworkProviderInfo
                        .Builder("My Phone 3", "Pixel 7")
                        .setDeviceType(NetworkProviderInfo.DEVICE_TYPE_PHONE)
                        .setBatteryPercentage(100)
                        .setConnectionStrength(3)
                        .build())
                .setHostNetworkType(HotspotNetwork.NETWORK_TYPE_CELLULAR)
                .setNetworkName("Google Fi")
                .build();
        when(mMockSharedConnectivityManager.getHotspotNetworks()).thenReturn(
                List.of(testHotspotNetwork1, testHotspotNetwork2, testHotspotNetwork3));
        final WifiPickerTracker wifiPickerTracker = createTestWifiPickerTracker();
        wifiPickerTracker.onStart();
        mTestLooper.dispatchAll();
        verify(mMockSharedConnectivityManager).registerCallback(any(),
                mSharedConnectivityCallbackCaptor.capture());

        mSharedConnectivityCallbackCaptor.getValue().onServiceConnected();
        mTestLooper.dispatchAll();

        assertThat(wifiPickerTracker.getWifiEntries()).hasSize(3);
        assertThat(((HotspotNetworkEntry) wifiPickerTracker.getWifiEntries().get(0))
                .getHotspotNetworkEntryKey().getDeviceId()).isEqualTo(2);
        assertThat(((HotspotNetworkEntry) wifiPickerTracker.getWifiEntries().get(1))
                .getHotspotNetworkEntryKey().getDeviceId()).isEqualTo(3);
        assertThat(((HotspotNetworkEntry) wifiPickerTracker.getWifiEntries().get(2))
                .getHotspotNetworkEntryKey().getDeviceId()).isEqualTo(1);
    }

    @Test
    public void testHotspotNetworks_onHotspotNetworkConnectionStatusChanged_connectedExtra() {
        final HotspotNetwork testHotspotNetwork =
            new HotspotNetwork.Builder()
                .setDeviceId(1)
                .setNetworkProviderInfo(
                    new NetworkProviderInfo.Builder("My Phone", "Pixel 7")
                        .setDeviceType(NetworkProviderInfo.DEVICE_TYPE_PHONE)
                        .setBatteryPercentage(100)
                        .setConnectionStrength(3)
                        .build())
                .setHostNetworkType(HotspotNetwork.NETWORK_TYPE_CELLULAR)
                .setNetworkName("Google Fi")
                .build();
        when(mMockSharedConnectivityManager.getHotspotNetworks())
            .thenReturn(Collections.singletonList(testHotspotNetwork));
        final WifiPickerTracker wifiPickerTracker = createTestWifiPickerTracker();
        wifiPickerTracker.onStart();
        mTestLooper.dispatchAll();
        verify(mMockSharedConnectivityManager)
            .registerCallback(any(), mSharedConnectivityCallbackCaptor.capture());
        mSharedConnectivityCallbackCaptor.getValue().onServiceConnected();
        mTestLooper.dispatchAll();

        final WifiEntry.ConnectCallback connectCallback = mock(WifiEntry.ConnectCallback.class);
        wifiPickerTracker.getWifiEntries().get(0).connect(connectCallback);

        Bundle extras = new Bundle();
        extras.putBoolean("connection_status_connected", true);
        mSharedConnectivityCallbackCaptor
            .getValue()
            .onHotspotNetworkConnectionStatusChanged(
                new HotspotNetworkConnectionStatus.Builder()
                    .setStatus(HotspotNetworkConnectionStatus.CONNECTION_STATUS_UNKNOWN)
                    .setExtras(extras)
                    .setHotspotNetwork(testHotspotNetwork)
                    .build());
        mTestLooper.dispatchAll();

        verify(connectCallback).onConnectResult(WifiEntry.ConnectCallback.CONNECT_STATUS_SUCCESS);
    }
}
