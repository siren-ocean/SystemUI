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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.net.NetworkScoreManager;
import android.net.vcn.VcnTransportInfo;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.net.wifi.hotspot2.PasspointConfiguration;
import android.net.wifi.hotspot2.pps.Credential;
import android.net.wifi.hotspot2.pps.HomeSp;
import android.os.Handler;
import android.os.test.TestLooper;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.util.Pair;

import androidx.lifecycle.Lifecycle;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collections;
import java.util.HashMap;
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
    @Mock private ConnectivityManager mMockConnectivityManager;
    @Mock private NetworkScoreManager mMockNetworkScoreManager;
    @Mock private TelephonyManager mMockTelephonyManager;
    @Mock private Clock mMockClock;
    @Mock private WifiPickerTracker.WifiPickerTrackerCallback mMockCallback;
    @Mock private WifiInfo mMockWifiInfo;
    @Mock private NetworkInfo mMockNetworkInfo;
    @Mock private Network mMockNetwork;

    private TestLooper mTestLooper;

    private final ArgumentCaptor<BroadcastReceiver> mBroadcastReceiverCaptor =
            ArgumentCaptor.forClass(BroadcastReceiver.class);
    private final ArgumentCaptor<ConnectivityManager.NetworkCallback>
            mNetworkCallbackCaptor =
                ArgumentCaptor.forClass(ConnectivityManager.NetworkCallback.class);
    private final ArgumentCaptor<ConnectivityManager.NetworkCallback>
            mDefaultNetworkCallbackCaptor =
                ArgumentCaptor.forClass(ConnectivityManager.NetworkCallback.class);

    private WifiPickerTracker createTestWifiPickerTracker() {
        final Handler testHandler = new Handler(mTestLooper.getLooper());

        return new WifiPickerTracker(
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
        when(mMockWifiManager.getConnectionInfo()).thenReturn(mMockWifiInfo);
        when(mMockWifiManager.getWifiState()).thenReturn(WifiManager.WIFI_STATE_ENABLED);
        when(mMockWifiManager.isWpa3SaeSupported()).thenReturn(true);
        when(mMockWifiManager.isWpa3SuiteBSupported()).thenReturn(true);
        when(mMockWifiManager.isEnhancedOpenSupported()).thenReturn(true);
        when(mMockConnectivityManager.getNetworkInfo(any())).thenReturn(mMockNetworkInfo);
        when(mMockClock.millis()).thenReturn(START_MILLIS);
        when(mMockWifiInfo.getNetworkId()).thenReturn(WifiConfiguration.INVALID_NETWORK_ID);
        when(mMockWifiInfo.getRssi()).thenReturn(WifiInfo.INVALID_RSSI);
        when(mMockNetworkInfo.getDetailedState()).thenReturn(
                NetworkInfo.DetailedState.DISCONNECTED);
        when(mMockContext.getResources()).thenReturn(mMockResources);
        when(mMockContext.getSystemService(Context.NETWORK_SCORE_SERVICE))
                .thenReturn(mMockNetworkScoreManager);
        when(mMockContext.getSystemService(Context.TELEPHONY_SERVICE))
                .thenReturn(mMockTelephonyManager);
        when(mMockResources.getString(anyInt())).thenReturn("");
    }

    /**
     * Tests that receiving a wifi state change broadcast updates getWifiState().
     */
    @Test
    public void testWifiStateChangeBroadcast_updatesWifiState() {
        final WifiPickerTracker wifiPickerTracker = createTestWifiPickerTracker();
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
        final WifiPickerTracker wifiPickerTracker = createTestWifiPickerTracker();
        wifiPickerTracker.onStart();
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
        verify(mMockContext).registerReceiver(mBroadcastReceiverCaptor.capture(),
                any(), any(), any());

        mBroadcastReceiverCaptor.getValue().onReceive(mMockContext,
                new Intent(WifiManager.CONFIGURED_NETWORKS_CHANGED_ACTION));
        mTestLooper.dispatchAll();

        verify(mMockCallback, atLeastOnce()).onNumSavedNetworksChanged();
    }

    /**
     * Tests that the wifi state is set correctly after onStart, even if no broadcast was received.
     */
    @Test
    public void testOnStart_setsWifiState() {
        final WifiPickerTracker wifiPickerTracker = createTestWifiPickerTracker();

        // Set the wifi state to disabled
        when(mMockWifiManager.getWifiState()).thenReturn(WifiManager.WIFI_STATE_DISABLED);
        wifiPickerTracker.onStart();
        mTestLooper.dispatchAll();

        assertThat(wifiPickerTracker.getWifiState()).isEqualTo(WifiManager.WIFI_STATE_DISABLED);

        // Change the wifi state to enabled
        wifiPickerTracker.onStop();
        when(mMockWifiManager.getWifiState()).thenReturn(WifiManager.WIFI_STATE_ENABLED);
        wifiPickerTracker.onStart();
        mTestLooper.dispatchAll();

        assertThat(wifiPickerTracker.getWifiState()).isEqualTo(WifiManager.WIFI_STATE_ENABLED);
    }

    /**
     * Tests that receiving a scan results available broadcast notifies the listener.
     */
    @Test
    public void testScanResultsAvailableAction_notifiesListener() {
        final WifiPickerTracker wifiPickerTracker = createTestWifiPickerTracker();
        wifiPickerTracker.onStart();
        verify(mMockContext).registerReceiver(mBroadcastReceiverCaptor.capture(),
                any(), any(), any());

        mBroadcastReceiverCaptor.getValue().onReceive(mMockContext,
                new Intent(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION));
        mTestLooper.dispatchAll();

        verify(mMockCallback, atLeastOnce()).onWifiEntriesChanged();
    }

    /**
     * Tests that an empty list of WifiEntries is returned if no scans are available.
     */
    @Test
    public void testGetWifiEntries_noScans_emptyList() {
        final WifiPickerTracker wifiPickerTracker = createTestWifiPickerTracker();
        wifiPickerTracker.onStart();
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
        mTestLooper.dispatchAll();

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
        verify(mMockContext).registerReceiver(mBroadcastReceiverCaptor.capture(),
                any(), any(), any());
        mTestLooper.dispatchAll();

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
        when(mMockNetworkInfo.getDetailedState()).thenReturn(NetworkInfo.DetailedState.CONNECTED);

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
        verify(mMockContext).registerReceiver(mBroadcastReceiverCaptor.capture(),
                any(), any(), any());
        mTestLooper.dispatchAll();
        final WifiEntry entry = wifiPickerTracker.getWifiEntries().get(0);

        when(mMockWifiInfo.getNetworkId()).thenReturn(1);
        when(mMockWifiInfo.getRssi()).thenReturn(-50);
        when(mMockNetworkInfo.getDetailedState()).thenReturn(NetworkInfo.DetailedState.CONNECTED);
        mBroadcastReceiverCaptor.getValue().onReceive(mMockContext,
                new Intent(WifiManager.NETWORK_STATE_CHANGED_ACTION)
                        .putExtra(WifiManager.EXTRA_NETWORK_INFO, mMockNetworkInfo));

        verify(mMockCallback, atLeastOnce()).onWifiEntriesChanged();
        assertThat(wifiPickerTracker.getWifiEntries()).isEmpty();
        assertThat(wifiPickerTracker.getConnectedWifiEntry()).isEqualTo(entry);
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
        when(mMockNetworkInfo.getDetailedState()).thenReturn(NetworkInfo.DetailedState.CONNECTED);
        wifiPickerTracker.onStart();
        verify(mMockContext).registerReceiver(mBroadcastReceiverCaptor.capture(),
                any(), any(), any());
        mTestLooper.dispatchAll();

        when(mMockNetworkInfo.getDetailedState())
                .thenReturn(NetworkInfo.DetailedState.DISCONNECTED);
        mBroadcastReceiverCaptor.getValue().onReceive(mMockContext,
                new Intent(WifiManager.NETWORK_STATE_CHANGED_ACTION)
                        .putExtra(WifiManager.EXTRA_NETWORK_INFO, mMockNetworkInfo));

        verify(mMockCallback, atLeastOnce()).onWifiEntriesChanged();
        assertThat(wifiPickerTracker.getConnectedWifiEntry()).isNull();
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
        when(mMockNetworkInfo.getDetailedState()).thenReturn(NetworkInfo.DetailedState.CONNECTED);
        wifiPickerTracker.onStart();
        verify(mMockContext).registerReceiver(mBroadcastReceiverCaptor.capture(),
                any(), any(), any());
        mTestLooper.dispatchAll();

        when(mMockWifiManager.getWifiState()).thenReturn(WifiManager.WIFI_STATE_DISABLED);
        mBroadcastReceiverCaptor.getValue().onReceive(mMockContext,
                new Intent(WifiManager.WIFI_STATE_CHANGED_ACTION));

        verify(mMockCallback, atLeastOnce()).onWifiEntriesChanged();
        assertThat(wifiPickerTracker.getConnectedWifiEntry()).isNull();
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
        when(mMockResources.getString(R.string.wifitrackerlib_summary_separator))
                .thenReturn(summarySeparator);
        when(mMockResources.getString(R.string.wifi_connected_low_quality)).thenReturn(lowQuality);
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
        when(mMockNetworkInfo.getDetailedState()).thenReturn(NetworkInfo.DetailedState.CONNECTED);
        when(mMockConnectivityManager.getNetworkInfo(any())).thenReturn(mMockNetworkInfo);
        wifiPickerTracker.onStart();
        verify(mMockConnectivityManager)
                .registerNetworkCallback(any(), mNetworkCallbackCaptor.capture(), any());
        verify(mMockConnectivityManager)
                .registerDefaultNetworkCallback(mDefaultNetworkCallbackCaptor.capture(), any());
        mTestLooper.dispatchAll();

        // Set cellular to be the default network
        mDefaultNetworkCallbackCaptor.getValue().onCapabilitiesChanged(mMockNetwork,
                new NetworkCapabilities.Builder()
                        .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR).build());

        // Trigger a validation callback for the non-primary Wifi network.
        WifiInfo nonPrimaryWifiInfo = Mockito.mock(WifiInfo.class);
        when(nonPrimaryWifiInfo.isPrimary()).thenReturn(false);
        when(nonPrimaryWifiInfo.makeCopy(anyLong())).thenReturn(nonPrimaryWifiInfo);
        NetworkCapabilities nonPrimaryCap = new NetworkCapabilities.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .setTransportInfo(nonPrimaryWifiInfo)
                .addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                .build();
        mNetworkCallbackCaptor.getValue().onCapabilitiesChanged(mMockNetwork, nonPrimaryCap);

        // Non-primary Wifi network validation should be ignored.
        assertThat(wifiPickerTracker.getConnectedWifiEntry().getSummary()).isNotEqualTo(lowQuality);

        // Trigger a validation callback for the primary Wifi network.
        WifiInfo primaryWifiInfo = Mockito.mock(WifiInfo.class);
        when(primaryWifiInfo.isPrimary()).thenReturn(true);
        when(primaryWifiInfo.makeCopy(anyLong())).thenReturn(primaryWifiInfo);
        NetworkCapabilities primaryCap = new NetworkCapabilities.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .setTransportInfo(primaryWifiInfo)
                .addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                .build();
        mNetworkCallbackCaptor.getValue().onCapabilitiesChanged(mMockNetwork, primaryCap);

        // Cell default + primary network validation should trigger low quality
        assertThat(wifiPickerTracker.getConnectedWifiEntry().getSummary()).isEqualTo(lowQuality);
    }

    /**
     * Tests that a PasspointWifiEntry is returned when Passpoint scans are visible.
     */
    @Test
    public void testGetWifiEntries_passpointInRange_returnsPasspointWifiEntry() {
        final WifiPickerTracker wifiPickerTracker = createTestWifiPickerTracker();
        final PasspointConfiguration passpointConfig = new PasspointConfiguration();
        final HomeSp homeSp = new HomeSp();
        homeSp.setFqdn("fqdn");
        homeSp.setFriendlyName("friendlyName");
        passpointConfig.setHomeSp(homeSp);
        passpointConfig.setCredential(new Credential());
        when(mMockWifiManager.getPasspointConfigurations())
                .thenReturn(Collections.singletonList(passpointConfig));
        wifiPickerTracker.onStart();
        verify(mMockContext).registerReceiver(mBroadcastReceiverCaptor.capture(),
                any(), any(), any());
        mTestLooper.dispatchAll();

        final WifiConfiguration wifiConfig = spy(new WifiConfiguration());
        when(wifiConfig.getKey()).thenReturn(passpointConfig.getUniqueId());
        final Map<Integer, List<ScanResult>> mapping = new HashMap<>();
        mapping.put(WifiManager.PASSPOINT_HOME_NETWORK, Collections.singletonList(
                buildScanResult("ssid", "bssid", START_MILLIS)));
        List<Pair<WifiConfiguration, Map<Integer, List<ScanResult>>>> allMatchingWifiConfigs =
                Collections.singletonList(new Pair<>(wifiConfig, mapping));
        when(mMockWifiManager.getAllMatchingWifiConfigs(any())).thenReturn(allMatchingWifiConfigs);
        mBroadcastReceiverCaptor.getValue().onReceive(mMockContext,
                new Intent(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION));

        assertThat(wifiPickerTracker.getWifiEntries()).isNotEmpty();
        assertThat(wifiPickerTracker.getWifiEntries().get(0).getTitle()).isEqualTo("friendlyName");
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
        final Map<Integer, List<ScanResult>> mapping = new HashMap<>();
        mapping.put(WifiManager.PASSPOINT_HOME_NETWORK, Collections.singletonList(
                buildScanResult("ssid", "bssid", START_MILLIS)));
        List<Pair<WifiConfiguration, Map<Integer, List<ScanResult>>>> allMatchingWifiConfigs =
                Collections.singletonList(new Pair<>(wifiConfig, mapping));
        when(mMockWifiManager.getAllMatchingWifiConfigs(any())).thenReturn(allMatchingWifiConfigs);
        when(mMockWifiManager.getPrivilegedConfiguredNetworks())
                .thenReturn(Collections.singletonList(wifiConfig));
        wifiPickerTracker.onStart();
        verify(mMockContext).registerReceiver(mBroadcastReceiverCaptor.capture(),
                any(), any(), any());
        mTestLooper.dispatchAll();
        assertThat(wifiPickerTracker.getWifiEntries()).isNotEmpty();
        final WifiEntry entry = wifiPickerTracker.getWifiEntries().get(0);

        when(mMockWifiInfo.isPasspointAp()).thenReturn(true);
        when(mMockWifiInfo.getPasspointFqdn()).thenReturn("fqdn");
        when(mMockWifiInfo.getNetworkId()).thenReturn(1);
        when(mMockWifiInfo.getRssi()).thenReturn(-50);
        when(mMockNetworkInfo.getDetailedState()).thenReturn(NetworkInfo.DetailedState.CONNECTED);
        mBroadcastReceiverCaptor.getValue().onReceive(mMockContext,
                new Intent(WifiManager.NETWORK_STATE_CHANGED_ACTION)
                        .putExtra(WifiManager.EXTRA_NETWORK_INFO, mMockNetworkInfo));

        assertThat(wifiPickerTracker.getWifiEntries()).isEmpty();
        assertThat(wifiPickerTracker.getConnectedWifiEntry() == entry).isTrue();

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
        final Map<Integer, List<ScanResult>> mapping = new HashMap<>();
        mapping.put(WifiManager.PASSPOINT_HOME_NETWORK, Collections.singletonList(
                buildScanResult("ssid", "bssid", START_MILLIS)));
        List<Pair<WifiConfiguration, Map<Integer, List<ScanResult>>>> allMatchingWifiConfigs =
                Collections.singletonList(new Pair<>(wifiConfig, mapping));
        when(mMockWifiManager.getAllMatchingWifiConfigs(any())).thenReturn(allMatchingWifiConfigs);
        wifiPickerTracker.onStart();
        verify(mMockContext).registerReceiver(mBroadcastReceiverCaptor.capture(),
                any(), any(), any());
        mTestLooper.dispatchAll();

        // Age out the scans and get out of range of Passpoint AP
        when(mMockClock.millis()).thenReturn(START_MILLIS + MAX_SCAN_AGE_MILLIS + 1);
        when(mMockWifiManager.getAllMatchingWifiConfigs(any())).thenReturn(new ArrayList<>());
        mBroadcastReceiverCaptor.getValue().onReceive(mMockContext,
                new Intent(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION));

        // getWifiEntries() should be empty now
        assertThat(wifiPickerTracker.getWifiEntries()).isEmpty();
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
        verify(mMockContext).registerReceiver(mBroadcastReceiverCaptor.capture(),
                any(), any(), any());
        mTestLooper.dispatchAll();

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
        when(mMockNetworkInfo.getDetailedState()).thenReturn(NetworkInfo.DetailedState.CONNECTED);
        final WifiPickerTracker wifiPickerTracker = createTestWifiPickerTracker();
        wifiPickerTracker.onStart();
        verify(mMockContext).registerReceiver(mBroadcastReceiverCaptor.capture(),
                any(), any(), any());
        mTestLooper.dispatchAll();
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
        mBroadcastReceiverCaptor.getValue().onReceive(mMockContext,
                new Intent(WifiManager.NETWORK_STATE_CHANGED_ACTION));
        mTestLooper.dispatchAll();

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
        when(mMockNetworkInfo.getDetailedState()).thenReturn(NetworkInfo.DetailedState.CONNECTED);
        final WifiPickerTracker wifiPickerTracker = createTestWifiPickerTracker();

        wifiPickerTracker.onStart();
        verify(mMockContext).registerReceiver(mBroadcastReceiverCaptor.capture(),
                any(), any(), any());
        mTestLooper.dispatchAll();

        verify(mMockCallback, atLeastOnce()).onWifiEntriesChanged();
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
        when(mMockNetworkInfo.getDetailedState()).thenReturn(NetworkInfo.DetailedState.CONNECTED);
        final WifiPickerTracker wifiPickerTracker = createTestWifiPickerTracker();
        wifiPickerTracker.onStart();
        verify(mMockContext).registerReceiver(mBroadcastReceiverCaptor.capture(),
                any(), any(), any());
        mTestLooper.dispatchAll();

        // Update with SCAN_RESULTS_AVAILABLE action while there are no scan results available yet.
        mBroadcastReceiverCaptor.getValue().onReceive(mMockContext,
                new Intent(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION));
        mTestLooper.dispatchAll();

        verify(mMockCallback, atLeastOnce()).onWifiEntriesChanged();
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
        when(mMockNetworkInfo.getDetailedState()).thenReturn(NetworkInfo.DetailedState.CONNECTED);

        final WifiPickerTracker wifiPickerTracker = createTestWifiPickerTracker();
        wifiPickerTracker.onStart();
        verify(mMockContext).registerReceiver(mBroadcastReceiverCaptor.capture(),
                any(), any(), any());
        mTestLooper.dispatchAll();

        // WifiInfo has network id 1, so the connected entry should correspond to request 1
        assertThat(wifiPickerTracker.getConnectedWifiEntry().getSsid()).isEqualTo("ssid1");

        when(mMockWifiInfo.getNetworkId()).thenReturn(2);
        mBroadcastReceiverCaptor.getValue().onReceive(mMockContext,
                new Intent(WifiManager.NETWORK_STATE_CHANGED_ACTION)
                        .putExtra(WifiManager.EXTRA_NETWORK_INFO, mMockNetworkInfo));

        // WifiInfo has network id 2, so the connected entry should correspond to request 2
        assertThat(wifiPickerTracker.getConnectedWifiEntry().getSsid()).isEqualTo("ssid2");

        when(mMockWifiInfo.getNetworkId()).thenReturn(-1);
        mBroadcastReceiverCaptor.getValue().onReceive(mMockContext,
                new Intent(WifiManager.NETWORK_STATE_CHANGED_ACTION)
                        .putExtra(WifiManager.EXTRA_NETWORK_INFO, mMockNetworkInfo));

        // WifiInfo matches no request configs, so the connected entry should be null
        assertThat(wifiPickerTracker.getConnectedWifiEntry()).isNull();
    }

    /**
     * Tests that SCAN_RESULTS_AVAILABLE_ACTION calls WifiManager#getMatchingOsuProviders()
     */
    @Test
    public void testScanResultsAvailableAction_callsGetMatchingOsuProviders() {
        final WifiPickerTracker wifiPickerTracker = createTestWifiPickerTracker();
        wifiPickerTracker.onStart();
        verify(mMockContext).registerReceiver(mBroadcastReceiverCaptor.capture(),
                any(), any(), any());
        mTestLooper.dispatchAll();


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
        when(mMockNetworkInfo.getDetailedState()).thenReturn(NetworkInfo.DetailedState.CONNECTED);
        final WifiPickerTracker wifiPickerTracker = createTestWifiPickerTracker();
        wifiPickerTracker.onStart();
        mTestLooper.dispatchAll();
        verify(mMockContext).registerReceiver(mBroadcastReceiverCaptor.capture(),
                any(), any(), any());

        final Intent intent = new Intent(TelephonyManager.ACTION_DEFAULT_DATA_SUBSCRIPTION_CHANGED);
        intent.putExtra("subscription", subId);
        mBroadcastReceiverCaptor.getValue().onReceive(mMockContext, intent);

        assertThat(wifiPickerTracker.getMergedCarrierEntry().getConnectedState())
                .isEqualTo(WifiEntry.CONNECTED_STATE_CONNECTED);
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
        when(mMockNetworkInfo.getDetailedState()).thenReturn(NetworkInfo.DetailedState.CONNECTED);
        final WifiPickerTracker wifiPickerTracker = createTestWifiPickerTracker();
        wifiPickerTracker.onStart();
        mTestLooper.dispatchAll();
        verify(mMockContext).registerReceiver(mBroadcastReceiverCaptor.capture(),
                any(), any(), any());
        final Intent intent = new Intent(TelephonyManager.ACTION_DEFAULT_DATA_SUBSCRIPTION_CHANGED);
        intent.putExtra("subscription", subId);
        mBroadcastReceiverCaptor.getValue().onReceive(mMockContext, intent);
        verify(mMockConnectivityManager)
                .registerDefaultNetworkCallback(mDefaultNetworkCallbackCaptor.capture(), any());
        MergedCarrierEntry mergedCarrierEntry = wifiPickerTracker.getMergedCarrierEntry();
        assertThat(mergedCarrierEntry.getConnectedState())
                .isEqualTo(WifiEntry.CONNECTED_STATE_CONNECTED);
        // Wifi isn't default yet, so isDefaultNetwork returns false
        assertThat(mergedCarrierEntry.isDefaultNetwork()).isFalse();

        mDefaultNetworkCallbackCaptor.getValue().onCapabilitiesChanged(mMockNetwork,
                new NetworkCapabilities.Builder()
                        .addTransportType(NetworkCapabilities.TRANSPORT_WIFI).build());

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
        when(mMockWifiInfo.isCarrierMerged()).thenReturn(true);
        when(mMockWifiInfo.getSubscriptionId()).thenReturn(subId);
        when(mMockNetworkInfo.getDetailedState()).thenReturn(NetworkInfo.DetailedState.CONNECTED);
        final WifiPickerTracker wifiPickerTracker = createTestWifiPickerTracker();
        wifiPickerTracker.onStart();
        mTestLooper.dispatchAll();
        verify(mMockContext).registerReceiver(mBroadcastReceiverCaptor.capture(),
                any(), any(), any());
        final Intent intent = new Intent(TelephonyManager.ACTION_DEFAULT_DATA_SUBSCRIPTION_CHANGED);
        intent.putExtra("subscription", subId);
        mBroadcastReceiverCaptor.getValue().onReceive(mMockContext, intent);
        verify(mMockConnectivityManager)
                .registerDefaultNetworkCallback(mDefaultNetworkCallbackCaptor.capture(), any());
        MergedCarrierEntry mergedCarrierEntry = wifiPickerTracker.getMergedCarrierEntry();
        assertThat(mergedCarrierEntry.getConnectedState())
                .isEqualTo(WifiEntry.CONNECTED_STATE_CONNECTED);
        // Wifi isn't default yet, so isDefaultNetwork returns false
        assertThat(mergedCarrierEntry.isDefaultNetwork()).isFalse();

        mDefaultNetworkCallbackCaptor.getValue().onCapabilitiesChanged(mMockNetwork,
                new NetworkCapabilities.Builder()
                        .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
                        .setTransportInfo(new VcnTransportInfo(new WifiInfo.Builder().build()))
                        .build());

        // Now VCN-over-Wifi is default, so isDefaultNetwork returns true
        assertThat(mergedCarrierEntry.isDefaultNetwork()).isTrue();
    }

    /**
     * Tests that roaming from one network to another will update the new network as the default
     * network if the default route did not change away from Wifi during the roam. This happens if
     * the new network was switched to via MBB.
     */
    @Test
    public void testGetConnectedEntry_roamedButDefaultRouteDidNotChange_entryIsDefaultNetwork() {
        final WifiPickerTracker wifiPickerTracker = createTestWifiPickerTracker();
        final WifiConfiguration config1 = new WifiConfiguration();
        config1.SSID = "\"ssid1\"";
        config1.networkId = 1;
        final WifiConfiguration config2 = new WifiConfiguration();
        config2.SSID = "\"ssid2\"";
        config2.networkId = 2;
        when(mMockWifiManager.getPrivilegedConfiguredNetworks())
                .thenReturn(Arrays.asList(config1, config2));
        when(mMockWifiInfo.getNetworkId()).thenReturn(1);
        when(mMockWifiInfo.getRssi()).thenReturn(-50);
        when(mMockNetworkInfo.getDetailedState()).thenReturn(NetworkInfo.DetailedState.CONNECTED);
        wifiPickerTracker.onStart();
        mTestLooper.dispatchAll();
        verify(mMockContext).registerReceiver(mBroadcastReceiverCaptor.capture(),
                any(), any(), any());
        verify(mMockConnectivityManager)
                .registerDefaultNetworkCallback(mDefaultNetworkCallbackCaptor.capture(), any());
        // Set the default route to wifi
        mDefaultNetworkCallbackCaptor.getValue().onCapabilitiesChanged(mMockNetwork,
                new NetworkCapabilities.Builder()
                        .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                        .build());
        WifiEntry connectedEntry = wifiPickerTracker.getConnectedWifiEntry();
        assertThat(connectedEntry.getWifiConfiguration()).isEqualTo(config1);
        assertThat(connectedEntry.isDefaultNetwork()).isTrue();

        // Connect to new network but don't change the default route
        when(mMockWifiInfo.getNetworkId()).thenReturn(2);
        mBroadcastReceiverCaptor.getValue().onReceive(mMockContext,
                new Intent(WifiManager.NETWORK_STATE_CHANGED_ACTION)
                        .putExtra(WifiManager.EXTRA_NETWORK_INFO, mMockNetworkInfo));

        // Verify that the newly connected network is still marked as the default network
        connectedEntry = wifiPickerTracker.getConnectedWifiEntry();
        assertThat(connectedEntry.getWifiConfiguration()).isEqualTo(config2);
        assertThat(wifiPickerTracker.getConnectedWifiEntry().isDefaultNetwork()).isTrue();
    }
}
