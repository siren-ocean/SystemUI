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

import static android.net.wifi.WifiInfo.SECURITY_TYPE_PSK;

import static com.android.wifitrackerlib.StandardWifiEntry.ssidAndSecurityTypeToStandardWifiEntryKey;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.net.wifi.WifiManager;
import android.net.wifi.sharedconnectivity.app.KnownNetwork;
import android.net.wifi.sharedconnectivity.app.KnownNetworkConnectionStatus;
import android.net.wifi.sharedconnectivity.app.NetworkProviderInfo;
import android.net.wifi.sharedconnectivity.app.SharedConnectivityManager;
import android.os.Handler;
import android.os.test.TestLooper;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class KnownNetworkEntryTest {
    @Mock private WifiEntry.WifiEntryCallback mMockListener;
    @Mock private WifiEntry.ConnectCallback mMockConnectCallback;
    @Mock private WifiTrackerInjector mMockInjector;
    @Mock private Context mMockContext;
    @Mock private WifiManager mMockWifiManager;
    @Mock private SharedConnectivityManager mMockSharedConnectivityManager;

    private TestLooper mTestLooper;
    private Handler mTestHandler;

    private static final KnownNetwork TEST_KNOWN_NETWORK_DATA = new KnownNetwork.Builder()
            .setNetworkSource(KnownNetwork.NETWORK_SOURCE_NEARBY_SELF)
            .setSsid("ssid")
            .addSecurityType(SECURITY_TYPE_PSK)
            .setNetworkProviderInfo(new NetworkProviderInfo
                    .Builder("My Phone", "Pixel 7")
                    .setDeviceType(NetworkProviderInfo.DEVICE_TYPE_PHONE)
                    .setBatteryPercentage(100)
                    .setConnectionStrength(3)
                    .build())
            .build();

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        mTestLooper = new TestLooper();
        mTestHandler = new Handler(mTestLooper.getLooper());

        when(mMockInjector.getContext()).thenReturn(mMockContext);
        when(mMockContext.getString(eq(R.string.wifitrackerlib_known_network_summary), anyString()))
                .thenAnswer(invocation -> {
                    Object[] args = invocation.getArguments();
                    return "Available from " + args[1];
                });
    }

    @Test
    public void testGetSummary_usesKnownNetworkData() {
        final KnownNetworkEntry entry = new KnownNetworkEntry(
                mMockInjector, mTestHandler,
                ssidAndSecurityTypeToStandardWifiEntryKey("ssid", SECURITY_TYPE_PSK),
                mMockWifiManager, mMockSharedConnectivityManager, TEST_KNOWN_NETWORK_DATA);

        assertThat(entry.getSummary()).isEqualTo("Available from My Phone");
    }

    @Test
    public void testConnect_serviceCalled() {
        final KnownNetworkEntry entry = new KnownNetworkEntry(
                mMockInjector, mTestHandler,
                ssidAndSecurityTypeToStandardWifiEntryKey("ssid", SECURITY_TYPE_PSK),
                mMockWifiManager, mMockSharedConnectivityManager, TEST_KNOWN_NETWORK_DATA);

        entry.connect(null);
        verify(mMockSharedConnectivityManager).connectKnownNetwork(TEST_KNOWN_NETWORK_DATA);
    }

    @Test
    public void testConnect_nullManager_failureCallback() {
        final KnownNetworkEntry entry = new KnownNetworkEntry(
                mMockInjector, mTestHandler,
                ssidAndSecurityTypeToStandardWifiEntryKey("ssid", SECURITY_TYPE_PSK),
                mMockWifiManager, /* sharedConnectivityManager= */ null, TEST_KNOWN_NETWORK_DATA);

        entry.setListener(mMockListener);
        entry.connect(mMockConnectCallback);
        mTestLooper.dispatchAll();
        verify(mMockConnectCallback)
                .onConnectResult(WifiEntry.ConnectCallback.CONNECT_STATUS_FAILURE_UNKNOWN);
    }

    @Test
    public void testConnect_onConnectionStatusChanged_failureCallback() {
        final KnownNetworkEntry entry = new KnownNetworkEntry(
                mMockInjector, mTestHandler,
                ssidAndSecurityTypeToStandardWifiEntryKey("ssid", SECURITY_TYPE_PSK),
                mMockWifiManager, mMockSharedConnectivityManager, TEST_KNOWN_NETWORK_DATA);

        entry.setListener(mMockListener);
        entry.connect(mMockConnectCallback);
        mTestLooper.dispatchAll();
        verify(mMockConnectCallback, never()).onConnectResult(anyInt());

        entry.onConnectionStatusChanged(KnownNetworkConnectionStatus.CONNECTION_STATUS_SAVE_FAILED);
        mTestLooper.dispatchAll();
        verify(mMockConnectCallback)
                .onConnectResult(WifiEntry.ConnectCallback.CONNECT_STATUS_FAILURE_UNKNOWN);
    }
}
