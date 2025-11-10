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

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.content.res.Resources;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.Looper;
import android.os.test.TestLooper;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class MergedCarrierEntryTest {
    @Mock private WifiTrackerInjector mMockInjector;
    @Mock private WifiEntry.ConnectCallback mMockConnectCallback;
    @Mock private WifiManager mMockWifiManager;
    @Mock private WifiInfo mMockWifiInfo;
    @Mock private Network mMockNetwork;
    @Mock private NetworkCapabilities mMockNetworkCapabilities;
    @Mock private Context mMockContext;
    @Mock private Resources mMockResources;

    private TestLooper mTestLooper;
    private Handler mTestHandler;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        when(mMockWifiInfo.getNetworkId()).thenReturn(WifiConfiguration.INVALID_NETWORK_ID);
        when(mMockWifiInfo.getRssi()).thenReturn(WifiInfo.INVALID_RSSI);
        when(mMockWifiInfo.isPrimary()).thenReturn(true);
        when(mMockNetworkCapabilities.getTransportInfo()).thenReturn(mMockWifiInfo);
        mTestLooper = new TestLooper();
        mTestHandler = new Handler(mTestLooper.getLooper());
        when(mMockInjector.getContext()).thenReturn(mMockContext);
        when(mMockContext.getMainLooper()).thenReturn(Looper.getMainLooper());
        when(mMockContext.getResources()).thenReturn(mMockResources);
        when(mMockContext.getString(R.string.wifitrackerlib_summary_separator)).thenReturn("/");
        when(mMockResources.getText(R.string.wifitrackerlib_wifi_wont_autoconnect_for_now))
                .thenReturn("Wi-Fi won't auto-connect for now");
    }

    @Test
    public void testGetConnectedState_wifiInfoMatches_returnsConnected() {
        final int subId = 1;
        final MergedCarrierEntry entry = new MergedCarrierEntry(mMockInjector, mTestHandler,
                mMockWifiManager, false, subId);
        when(mMockWifiInfo.isCarrierMerged()).thenReturn(true);
        when(mMockWifiInfo.getSubscriptionId()).thenReturn(subId);

        entry.onNetworkCapabilitiesChanged(mMockNetwork, mMockNetworkCapabilities);

        assertThat(entry.getConnectedState()).isEqualTo(WifiEntry.CONNECTED_STATE_CONNECTED);
    }

    @Test
    public void testConnect_disablesNonCarrierMergedWifi() {
        Looper.prepare();
        final int subId = 1;
        final MergedCarrierEntry entry = new MergedCarrierEntry(mMockInjector, mTestHandler,
                mMockWifiManager, false, subId);

        entry.connect(mMockConnectCallback);
        mTestLooper.dispatchAll();

        verify(mMockConnectCallback)
                .onConnectResult(WifiEntry.ConnectCallback.CONNECT_STATUS_SUCCESS);
        verify(mMockWifiManager).startRestrictingAutoJoinToSubscriptionId(subId);
    }

    @Test
    public void testDisconnect_enablesNonCarrierMergedWifiAndTriggersScan() {
        final int subId = 1;
        final MergedCarrierEntry entry = new MergedCarrierEntry(mMockInjector, mTestHandler,
                mMockWifiManager, false, subId);

        entry.disconnect(null);
        mTestLooper.dispatchAll();
        verify(mMockWifiManager).stopRestrictingAutoJoinToSubscriptionId();
        verify(mMockWifiManager).startScan();
    }

    @Test
    public void testCanConnect_cellIsDefaultRoute_returnsFalse() {
        final int subId = 1;
        final MergedCarrierEntry entry = new MergedCarrierEntry(mMockInjector, mTestHandler,
                mMockWifiManager, false, subId);
        entry.updateIsCellDefaultRoute(false);

        assertThat(entry.canConnect()).isTrue();

        entry.updateIsCellDefaultRoute(true);

        assertThat(entry.canConnect()).isFalse();
    }

    @Test
    public void testGetSsid_connected_returnsSanitizedWifiInfoSsid() {
        final int subId = 1;
        final MergedCarrierEntry entry = new MergedCarrierEntry(mMockInjector, mTestHandler,
                mMockWifiManager, false, subId);
        when(mMockWifiInfo.isCarrierMerged()).thenReturn(true);
        when(mMockWifiInfo.getSubscriptionId()).thenReturn(subId);
        final String ssid = "ssid";
        when(mMockWifiInfo.getSSID()).thenReturn("\"" + ssid + "\"");

        entry.onNetworkCapabilitiesChanged(mMockNetwork, mMockNetworkCapabilities);

        assertThat(entry.getSsid()).isEqualTo(ssid);
    }
}
