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

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.fail;
import static org.mockito.Mockito.when;

import android.net.wifi.ScanResult;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.Clock;
import java.util.Arrays;
import java.util.List;

public class ScanResultUpdaterTest {
    private static final String SSID = "ssid";
    private static final String BSSID_1 = "11:11:11:11:11:11";
    private static final String BSSID_2 = "22:22:22:22:22:22";
    private static final String BSSID_3 = "33:33:33:33:33:33";
    private static final long NOW_MILLIS = 123_456_789;

    @Mock private Clock mMockClock;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        when(mMockClock.millis()).thenReturn(NOW_MILLIS);
    }

    /**
     * Verify that scan results of the same BSSID are merged to latest one.
     */
    @Test
    public void testGetScanResults_mergeSameBssid() {
        ScanResult oldResult = buildScanResult(SSID, BSSID_1, 10);
        ScanResult newResult = buildScanResult(SSID, BSSID_1, 20);

        // Add initial scan result. List should have 1 scan.
        ScanResultUpdater sru = new ScanResultUpdater(mMockClock);
        sru.update(Arrays.asList(oldResult));
        assertThat(sru.getScanResults()).containsExactly(oldResult);

        // Add new scan result. Old scan result should be replaced.
        sru.update(Arrays.asList(newResult));
        assertThat(sru.getScanResults()).containsExactly(newResult);

        // Add old scan result back. New scan result should still remain.
        sru.update(Arrays.asList(oldResult));
        assertThat(sru.getScanResults()).containsExactly(newResult);
    }

    /**
     * Verify that scan results are filtered out by age.
     */
    @Test
    public void testGetScanResults_filtersOldScans() {
        long maxScanAge = 15_000;

        ScanResult oldResult = buildScanResult(SSID, BSSID_1, NOW_MILLIS - (maxScanAge + 1));
        ScanResult newResult = buildScanResult(SSID, BSSID_2, NOW_MILLIS);

        // Add a new scan result and an out-of-date scan result.
        ScanResultUpdater sru = new ScanResultUpdater(mMockClock);
        sru.update(Arrays.asList(newResult, oldResult));

        // New scan result should remain and out-of-date scan result should not be returned.
        assertThat(sru.getScanResults(maxScanAge)).containsExactly(newResult);
    }

    /**
     * Verify that an exception is thrown if the getScanResults max scan age is larger than the
     * constructor's max scan age.
     */
    @Test
    public void testGetScanResults_invalidMaxScanAgeMillis_throwsException() {
        ScanResultUpdater sru = new ScanResultUpdater(mMockClock, 15_000);
        try {
            sru.getScanResults(20_000);
            fail("Should have thrown exception for maxScanAgeMillis too large.");
        } catch (IllegalArgumentException ok) {
            // Expected
        }
    }

    /**
     * Verify that the constructor max scan age is obeyed when getting scan results.
     */
    @Test
    public void testConstructor_maxScanAge_filtersOldScans() {
        ScanResultUpdater sru = new ScanResultUpdater(mMockClock, 15_000);

        ScanResult scan1 = buildScanResult(SSID, BSSID_1, NOW_MILLIS - 10_000);
        ScanResult scan2 = buildScanResult(SSID, BSSID_2, NOW_MILLIS - 15_000);
        ScanResult scan3 = buildScanResult(SSID, BSSID_3, NOW_MILLIS - 20_000);

        sru.update(Arrays.asList(scan1, scan2, scan3));

        List<ScanResult> scanResults = sru.getScanResults();

        assertThat(scanResults).containsExactly(scan1, scan2);
    }

    /**
     * Verify that getScanResults returns results aged by the passed in max scan age even if there
     * is a max scan age set by the constructor.
     */
    @Test
    public void testGetScanResults_overridesConstructorMaxScanAge() {
        ScanResultUpdater sru = new ScanResultUpdater(mMockClock, 15_000);

        ScanResult scan1 = buildScanResult(SSID, BSSID_1, NOW_MILLIS - 10_000);
        ScanResult scan2 = buildScanResult(SSID, BSSID_2, NOW_MILLIS - 15_000);
        ScanResult scan3 = buildScanResult(SSID, BSSID_3, NOW_MILLIS - 20_000);

        sru.update(Arrays.asList(scan1, scan2, scan3));

        // Aged getScanResults should override the constructor max scan age.
        List<ScanResult> scanResults = sru.getScanResults(11_000);
        assertThat(scanResults).containsExactly(scan1);

        // Non-aged getScanResults should revert to the constructor max scan age.
        scanResults = sru.getScanResults();
        assertThat(scanResults).containsExactly(scan1, scan2);
    }
}
