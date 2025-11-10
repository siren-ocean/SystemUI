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

import static com.android.wifitrackerlib.NetworkDetailsTracker.createNetworkDetailsTracker;
import static com.android.wifitrackerlib.StandardWifiEntry.ScanResultKey;
import static com.android.wifitrackerlib.StandardWifiEntry.StandardWifiEntryKey;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.fail;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.content.res.Resources;
import android.net.ConnectivityManager;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.net.wifi.sharedconnectivity.app.HotspotNetwork;
import android.net.wifi.sharedconnectivity.app.NetworkProviderInfo;
import android.os.Handler;
import android.os.test.TestLooper;

import androidx.lifecycle.Lifecycle;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Collections;

public class NetworkDetailsTrackerTest {

    private static final long START_MILLIS = 123_456_789;

    private static final long MAX_SCAN_AGE_MILLIS = 15_000;
    private static final long SCAN_INTERVAL_MILLIS = 10_000;

    @Mock private WifiTrackerInjector mInjector;
    @Mock private Lifecycle mMockLifecycle;
    @Mock private Context mMockContext;
    @Mock private Resources mResources;
    @Mock private WifiManager mMockWifiManager;
    @Mock private ConnectivityManager mMockConnectivityManager;
    @Mock private Clock mMockClock;

    private TestLooper mTestLooper;

    private NetworkDetailsTracker createTestNetworkDetailsTracker(String key) {
        final Handler testHandler = new Handler(mTestLooper.getLooper());

        return createNetworkDetailsTracker(
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
                key);
    }

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        mTestLooper = new TestLooper();

        when(mMockWifiManager.getScanResults()).thenReturn(new ArrayList<>());
        when(mMockWifiManager.getWifiState()).thenReturn(WifiManager.WIFI_STATE_ENABLED);
        when(mMockClock.millis()).thenReturn(START_MILLIS);
        when(mMockContext.getResources()).thenReturn(mResources);
    }

    /**
     * Tests that an invalid WifiEntry key passed into the constructor throws an exception.
     */
    @Test
    public void testCreateNetworkDetailsTracker_invalidKey_throwsError() {
        try {
            createTestNetworkDetailsTracker("Invalid Key");
            fail("Invalid key should have thrown an error!");
        } catch (IllegalArgumentException e) {
            // Test succeeded
        }
    }

    /**
     * Tests that createNetworkDetailsTracker() returns a StandardNetworkDetailsTracker if a
     * StandardWifiEntry key is passed in.
     */
    @Test
    public void testCreateNetworkDetailsTracker_returnsStandardNetworkDetailsTracker()
            throws Exception {
        final NetworkDetailsTracker tracker = createTestNetworkDetailsTracker(
                new StandardWifiEntryKey(new ScanResultKey("ssid",
                        Collections.singletonList(WifiEntry.SECURITY_NONE))).toString());
        assertThat(tracker).isInstanceOf(StandardNetworkDetailsTracker.class);
    }

    /**
     * Tests that createNetworkDetailsTracker() returns a HotspotNetworkDetailsTracker if a
     * HotspotNetworkEntry key is passed in.
     */
    @Test
    public void testCreateNetworkDetailsTracker_returnsHotspotNetworkDetailsTracker()
            throws Exception {
        final NetworkDetailsTracker tracker = createTestNetworkDetailsTracker(
                new HotspotNetworkEntry.HotspotNetworkEntryKey(new HotspotNetwork.Builder()
                        .setDeviceId(1)
                        .setNetworkProviderInfo(new NetworkProviderInfo
                                .Builder("Phone", "Pixel")
                                .setDeviceType(NetworkProviderInfo.DEVICE_TYPE_PHONE)
                                .setBatteryPercentage(100)
                                .setConnectionStrength(3).build())
                        .setHostNetworkType(HotspotNetwork.NETWORK_TYPE_CELLULAR)
                        .setNetworkName("Google Fi")
                        .setHotspotSsid("SSID")
                        .setHotspotBssid("BSSID")
                        .addHotspotSecurityType(WifiInfo.SECURITY_TYPE_WEP)
                        .build()).toString());
        assertThat(tracker).isInstanceOf(HotspotNetworkDetailsTracker.class);
    }
}
