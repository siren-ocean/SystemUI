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

import static com.android.wifitrackerlib.StandardWifiEntry.ssidAndSecurityToStandardWifiEntryKey;
import static com.android.wifitrackerlib.StandardWifiEntry.wifiConfigToStandardWifiEntryKey;
import static com.android.wifitrackerlib.TestUtils.buildScanResult;
import static com.android.wifitrackerlib.Utils.getAutoConnectDescription;
import static com.android.wifitrackerlib.Utils.getBestScanResultByLevel;
import static com.android.wifitrackerlib.Utils.getCarrierNameForSubId;
import static com.android.wifitrackerlib.Utils.getImsiProtectionDescription;
import static com.android.wifitrackerlib.Utils.getMeteredDescription;
import static com.android.wifitrackerlib.Utils.getNetworkSelectionDescription;
import static com.android.wifitrackerlib.Utils.getSubIdForConfig;
import static com.android.wifitrackerlib.Utils.isImsiPrivacyProtectionProvided;
import static com.android.wifitrackerlib.Utils.isSimPresent;
import static com.android.wifitrackerlib.Utils.linkifyAnnotation;
import static com.android.wifitrackerlib.Utils.mapScanResultsToKey;
import static com.android.wifitrackerlib.WifiEntry.SECURITY_NONE;
import static com.android.wifitrackerlib.WifiEntry.SECURITY_PSK;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.*;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.content.res.Resources;
import android.net.NetworkInfo;
import android.net.NetworkScoreManager;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiConfiguration.NetworkSelectionStatus;
import android.net.wifi.WifiEnterpriseConfig;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiNetworkScoreCache;
import android.os.Handler;
import android.os.PersistableBundle;
import android.os.test.TestLooper;
import android.telephony.CarrierConfigManager;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.text.Annotation;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ClickableSpan;

import com.android.wifitrackerlib.shadow.ShadowSystem;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Config(shadows = {ShadowSystem.class})
public class UtilsTest {
    private static final String LABEL_AUTO_CONNECTION_DISABLED = "Auto-Connection disabled";
    private static final String LABEL_METERED = "Metered";
    private static final String LABEL_UNMETERED = "Unmetered";

    private static final String SYSTEM_UID_APP_NAME = "systemUidAppName";
    private static final String APP_LABEL = "appLabel";
    private static final String SETTINGS_APP_NAME = "com.android.settings";
    private static final int TEST_CARRIER_ID = 1191;
    private static final int TEST_SUB_ID = 1111;

    private static final String TEST_CARRIER_NAME = "carrierName";

    @Mock private Context mMockContext;
    @Mock private Resources mMockResources;
    @Mock private NetworkScoreManager mMockNetworkScoreManager;
    @Mock private WifiNetworkScoreCache mMockScoreCache;
    @Mock private SubscriptionManager mSubscriptionManager;
    @Mock private TelephonyManager mTelephonyManager;
    @Mock private CarrierConfigManager mCarrierConfigManager;
    @Mock private TelephonyManager mSpecifiedTm;

    private Handler mTestHandler;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        TestLooper testLooper = new TestLooper();
        mTestHandler = new Handler(testLooper.getLooper());
        when(mMockContext.getResources()).thenReturn(mMockResources);
        when(mMockContext.getSystemService(Context.NETWORK_SCORE_SERVICE))
                .thenReturn(mMockNetworkScoreManager);
        when(mMockContext.getSystemService(Context.CARRIER_CONFIG_SERVICE))
                .thenReturn(mCarrierConfigManager);
        when(mMockContext.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE))
                .thenReturn(mSubscriptionManager);
        when(mMockContext.getSystemService(Context.TELEPHONY_SERVICE))
                .thenReturn(mTelephonyManager);
        when(mTelephonyManager.createForSubscriptionId(TEST_CARRIER_ID)).thenReturn(mSpecifiedTm);
    }

    @Test
    public void testGetBestScanResult_emptyList_returnsNull() {
        assertThat(getBestScanResultByLevel(new ArrayList<>())).isNull();
    }

    @Test
    public void testGetBestScanResult_returnsBestRssiScan() {
        final ScanResult bestResult = buildScanResult("ssid", "bssid", 0, -50);
        final ScanResult okayResult = buildScanResult("ssid", "bssid", 0, -60);
        final ScanResult badResult = buildScanResult("ssid", "bssid", 0, -70);

        assertThat(getBestScanResultByLevel(Arrays.asList(bestResult, okayResult, badResult)))
                .isEqualTo(bestResult);
    }

    @Test
    public void testGetBestScanResult_singleScan_returnsScan() {
        final ScanResult scan = buildScanResult("ssid", "bssid", 0, -50);

        assertThat(getBestScanResultByLevel(Arrays.asList(scan))).isEqualTo(scan);
    }

    @Test
    public void testMapScanResultsToKey_filtersUnsupportedCapabilities() {
        final ScanResult wpa3SaeScan = new ScanResult();
        final ScanResult wpa3SuiteBScan = new ScanResult();
        final ScanResult oweScan = new ScanResult();
        wpa3SaeScan.SSID = "wpa3Sae";
        wpa3SaeScan.capabilities = "[SAE]";
        wpa3SuiteBScan.SSID = "wpa3SuiteB";
        wpa3SuiteBScan.capabilities = "[EAP_SUITE_B_192]";
        oweScan.SSID = "owe";
        oweScan.capabilities = "[OWE]";

        final Map<String, List<ScanResult>> scanResultsByKey = mapScanResultsToKey(
                Arrays.asList(wpa3SaeScan, wpa3SuiteBScan, oweScan),
                false /* chooseSingleSecurity */,
                null /* wifiConfigsByKey */,
                false /* isWpa3SaeSupported */,
                false /* isWpa3SuiteBSupported */,
                false /* isEnhancedOpenSupported */);

        assertThat(scanResultsByKey).isEmpty();
    }

    @Test
    public void testMapScanResultsToKey_convertsTransitionModeScansToSupportedSecurity() {
        final ScanResult wpa3TransitionScan = new ScanResult();
        final ScanResult oweTransitionScan = new ScanResult();
        wpa3TransitionScan.SSID = "wpa3Transition";
        wpa3TransitionScan.capabilities = "[PSK+SAE]";
        oweTransitionScan.SSID = "owe";
        oweTransitionScan.capabilities = "[OWE_TRANSITION]";

        final Map<String, List<ScanResult>> scanResultsByKey = mapScanResultsToKey(
                Arrays.asList(wpa3TransitionScan, oweTransitionScan),
                false /* chooseSingleSecurity */,
                null /* wifiConfigsByKey */,
                false /* isWpa3SaeSupported */,
                false /* isWpa3SuiteBSupported */,
                false /* isEnhancedOpenSupported */);

        assertThat(scanResultsByKey.keySet()).containsExactly(
                ssidAndSecurityToStandardWifiEntryKey(wpa3TransitionScan.SSID, SECURITY_PSK),
                ssidAndSecurityToStandardWifiEntryKey(oweTransitionScan.SSID, SECURITY_NONE));
    }

    @Test
    public void testGetAutoConnectDescription_autoJoinEnabled_returnEmptyString() {
        final WifiConfiguration config = new WifiConfiguration();
        config.SSID = "\"ssid\"";
        config.allowAutojoin = true;
        final StandardWifiEntry entry = getStandardWifiEntry(config);
        when(mMockResources.getString(R.string.auto_connect_disable))
                .thenReturn(LABEL_AUTO_CONNECTION_DISABLED);

        final String autoConnectDescription = getAutoConnectDescription(mMockContext, entry);

        assertThat(autoConnectDescription).isEqualTo("");
    }

    @Test
    public void testGetAutoConnectDescription_autoJoinDisabled_returnDisable() {
        final WifiConfiguration config = new WifiConfiguration();
        config.SSID = "\"ssid\"";
        config.allowAutojoin = false;
        final StandardWifiEntry entry = getStandardWifiEntry(config);
        when(mMockResources.getString(R.string.auto_connect_disable))
                .thenReturn(LABEL_AUTO_CONNECTION_DISABLED);

        final String autoConnectDescription = getAutoConnectDescription(mMockContext, entry);

        assertThat(autoConnectDescription).isEqualTo(LABEL_AUTO_CONNECTION_DISABLED);
    }

    @Test
    public void testGetMeteredDescription_noOverrideNoHint_returnEmptyString() {
        final WifiConfiguration config = new WifiConfiguration();
        config.SSID = "\"ssid\"";
        config.meteredOverride = WifiConfiguration.METERED_OVERRIDE_NONE;
        config.meteredHint = false;
        final StandardWifiEntry entry = getStandardWifiEntry(config);

        final String meteredDescription = getMeteredDescription(mMockContext, entry);

        assertThat(meteredDescription).isEqualTo("");
    }

    @Test
    public void testGetMeteredDescription_overrideMetered_returnMetered() {
        final WifiConfiguration config = new WifiConfiguration();
        config.SSID = "\"ssid\"";
        config.meteredOverride = WifiConfiguration.METERED_OVERRIDE_METERED;
        final StandardWifiEntry entry = getStandardWifiEntry(config);
        when(mMockResources.getString(R.string.wifi_metered_label)).thenReturn(LABEL_METERED);

        final String meteredDescription = getMeteredDescription(mMockContext, entry);

        assertThat(meteredDescription).isEqualTo(LABEL_METERED);
    }

    @Ignore // TODO(b/70983952): Remove ignore tag when StandardWifiEntry#isMetered() is ready.
    @Test
    public void testGetMeteredDescription__meteredHintTrueAndOverrideNone_returnMetered() {
        final WifiConfiguration config = new WifiConfiguration();
        config.SSID = "\"ssid\"";
        config.meteredHint = true;
        config.meteredOverride = WifiConfiguration.METERED_OVERRIDE_NONE;
        final StandardWifiEntry entry = getStandardWifiEntry(config);
        when(mMockResources.getString(R.string.wifi_metered_label)).thenReturn(LABEL_METERED);

        final String meteredDescription = getMeteredDescription(mMockContext, entry);

        assertThat(meteredDescription).isEqualTo(LABEL_METERED);
    }

    @Test
    public void testGetMeteredDescription__meteredHintTrueAndOverrideMetered_returnMetered() {
        final WifiConfiguration config = new WifiConfiguration();
        config.SSID = "\"ssid\"";
        config.meteredHint = true;
        config.meteredOverride = WifiConfiguration.METERED_OVERRIDE_METERED;
        final StandardWifiEntry entry = getStandardWifiEntry(config);
        when(mMockResources.getString(R.string.wifi_metered_label)).thenReturn(LABEL_METERED);

        final String meteredDescription = getMeteredDescription(mMockContext, entry);

        assertThat(meteredDescription).isEqualTo(LABEL_METERED);
    }

    @Test
    public void testGetMeteredDescription__meteredHintTrueAndOverrideNotMetered_returnUnmetered() {
        final WifiConfiguration config = new WifiConfiguration();
        config.SSID = "\"ssid\"";
        config.meteredHint = true;
        config.meteredOverride = WifiConfiguration.METERED_OVERRIDE_NOT_METERED;
        final StandardWifiEntry entry = getStandardWifiEntry(config);
        when(mMockResources.getString(R.string.wifi_unmetered_label)).thenReturn(LABEL_UNMETERED);

        final String meteredDescription = getMeteredDescription(mMockContext, entry);

        assertThat(meteredDescription).isEqualTo(LABEL_UNMETERED);
    }

    @Test
    public void testCheckSimPresentWithNoSubscription() {
        when(mSubscriptionManager.getActiveSubscriptionInfoList()).thenReturn(new ArrayList<>());
        assertFalse(isSimPresent(mMockContext, TEST_CARRIER_ID));
    }

    @Test
    public void testCheckSimPresentWithNoMatchingSubscription() {
        List<SubscriptionInfo> subscriptionInfoList = new ArrayList<>();
        SubscriptionInfo subscriptionInfo = mock(SubscriptionInfo.class);
        when(subscriptionInfo.getCarrierId()).thenReturn(TEST_CARRIER_ID + 1);
        subscriptionInfoList.add(subscriptionInfo);
        when(mSubscriptionManager.getActiveSubscriptionInfoList()).thenReturn(subscriptionInfoList);
        assertFalse(isSimPresent(mMockContext, TEST_CARRIER_ID));
    }

    @Test
    public void testCheckSimPresentWithMatchingSubscription() {
        List<SubscriptionInfo> subscriptionInfoList = new ArrayList<>();
        SubscriptionInfo subscriptionInfo = mock(SubscriptionInfo.class);
        when(subscriptionInfo.getCarrierId()).thenReturn(TEST_CARRIER_ID);
        subscriptionInfoList.add(subscriptionInfo);
        when(mSubscriptionManager.getActiveSubscriptionInfoList()).thenReturn(subscriptionInfoList);
        assertTrue(isSimPresent(mMockContext, TEST_CARRIER_ID));
    }

    @Test
    public void testGetCarrierName() {
        when(mSpecifiedTm.getSimCarrierIdName()).thenReturn(TEST_CARRIER_NAME);
        assertEquals(TEST_CARRIER_NAME, getCarrierNameForSubId(mMockContext, TEST_CARRIER_ID));
    }

    @Test
    public void testGetCarrierNameWithInvalidSubId() {
        when(mSpecifiedTm.getSimCarrierIdName()).thenReturn(TEST_CARRIER_NAME);
        assertNull(getCarrierNameForSubId(mMockContext,
                SubscriptionManager.INVALID_SUBSCRIPTION_ID));
    }

    @Test
    public void testCheckRequireImsiPrivacyProtectionWithNoCarrierConfig() {
        assertFalse(isImsiPrivacyProtectionProvided(mMockContext, TEST_SUB_ID));
    }

    @Test
    public void testCheckRequireImsiPrivacyProtectionWithCarrierConfigKeyAvailable() {
        PersistableBundle bundle = new PersistableBundle();
        bundle.putInt(CarrierConfigManager.IMSI_KEY_AVAILABILITY_INT,
                TelephonyManager.KEY_TYPE_WLAN);
        when(mCarrierConfigManager.getConfigForSubId(TEST_SUB_ID)).thenReturn(bundle);
        assertTrue(isImsiPrivacyProtectionProvided(mMockContext, TEST_SUB_ID));
    }

    @Test
    public void testGetSubIdForWifiConfigurationWithNoSubscription() {
        WifiConfiguration config = new WifiConfiguration();
        config.carrierId = TEST_CARRIER_ID;
        assertEquals(SubscriptionManager.INVALID_SUBSCRIPTION_ID,
                getSubIdForConfig(mMockContext, config));
    }

    @Test
    public void testGetSubIdForWifiConfigurationWithMatchingSubscription() {
        WifiConfiguration config = new WifiConfiguration();
        config.carrierId = TEST_CARRIER_ID;
        List<SubscriptionInfo> subscriptionInfoList = new ArrayList<>();
        SubscriptionInfo subscriptionInfo = mock(SubscriptionInfo.class);
        when(subscriptionInfo.getCarrierId()).thenReturn(TEST_CARRIER_ID);
        when(subscriptionInfo.getSubscriptionId()).thenReturn(TEST_SUB_ID);
        subscriptionInfoList.add(subscriptionInfo);
        when(mSubscriptionManager.getActiveSubscriptionInfoList()).thenReturn(subscriptionInfoList);
        assertEquals(TEST_SUB_ID, getSubIdForConfig(mMockContext, config));
    }

    @Test
    public void testGetSubIdForWifiConfigurationWithoutCarrierId() {
        WifiConfiguration config = new WifiConfiguration();
        assertEquals(SubscriptionManager.INVALID_SUBSCRIPTION_ID,
                getSubIdForConfig(mMockContext, config));
    }

    @Test
    public void testGetImsiProtectionDescription_isSimCredentialFalse_returnEmptyString() {
        final WifiConfiguration wificonfig = new WifiConfiguration();

        assertEquals(getImsiProtectionDescription(mMockContext, wificonfig).toString(), "");
    }

    @Test
    public void testGetImsiProtectionDescription_noValidSubId_returnEmptyString() {
        final WifiConfiguration mockWifiConfig = mock(WifiConfiguration.class);
        final WifiEnterpriseConfig mockWifiEnterpriseConfig = mock(WifiEnterpriseConfig.class);
        when(mockWifiEnterpriseConfig.isAuthenticationSimBased()).thenReturn(true);
        mockWifiConfig.enterpriseConfig = mockWifiEnterpriseConfig;

        assertEquals(getImsiProtectionDescription(mMockContext, mockWifiConfig).toString(), "");
    }

    @Test
    public void testLinkifyAnnotation_noAnnotation_returnOriginalText() {
        final CharSequence testText = "test text";

        final CharSequence output = linkifyAnnotation(mMockContext, testText, "id", "url");

        final SpannableString outputSpannableString = new SpannableString(output);
        assertEquals(output.toString(), testText);
        assertEquals(outputSpannableString.getSpans(0, outputSpannableString.length(),
                ClickableSpan.class).length, 0);
    }

    @Test
    public void testLinkifyAnnotation_annotation_returnTextWithClickableSpan() {
        final String annotationId = "id";
        final CharSequence testText = "test text ";
        final CharSequence testLink = "link";
        final CharSequence expectedText = "test text link";
        final SpannableStringBuilder builder = new SpannableStringBuilder(testText);
        builder.append(testLink, new Annotation("key", annotationId),
                Spanned.SPAN_INCLUSIVE_INCLUSIVE);

        final CharSequence output = linkifyAnnotation(mMockContext, builder, annotationId, "url");

        final SpannableString outputSpannableString = new SpannableString(output);
        assertEquals(output.toString(), expectedText.toString());
        assertEquals(outputSpannableString.getSpans(0, outputSpannableString.length(),
                ClickableSpan.class).length, 1);
    }

    @Test
    public void testGetNetworkSelectionDescription_disabledWrongPassword_showsWrongPasswordLabel() {
        String expected = " (NETWORK_SELECTION_TEMPORARY_DISABLED 1:02:03) "
                + "NETWORK_SELECTION_DISABLED_BY_WRONG_PASSWORD=2";
        WifiConfiguration wifiConfig = spy(new WifiConfiguration());
        NetworkSelectionStatus.Builder statusBuilder = new NetworkSelectionStatus.Builder();
        NetworkSelectionStatus networkSelectionStatus = spy(statusBuilder.setNetworkSelectionStatus(
                NetworkSelectionStatus.NETWORK_SELECTION_TEMPORARY_DISABLED)
                .setNetworkSelectionDisableReason(NetworkSelectionStatus.DISABLED_BY_WRONG_PASSWORD)
                .build());
        doReturn(2).when(networkSelectionStatus).getDisableReasonCounter(
                NetworkSelectionStatus.DISABLED_BY_WRONG_PASSWORD);
        long now = System.currentTimeMillis();
        // Network selection disable time is 1:02:03 ago.
        doReturn(now - (60 * 60 + 2 * 60 + 3) * 1000).when(networkSelectionStatus).getDisableTime();
        when(wifiConfig.getNetworkSelectionStatus()).thenReturn(networkSelectionStatus);

        assertThat(getNetworkSelectionDescription(wifiConfig)).isEqualTo(expected);
    }

    private StandardWifiEntry getStandardWifiEntry(WifiConfiguration config) {
        final WifiManager mockWifiManager = mock(WifiManager.class);
        final StandardWifiEntry entry = new StandardWifiEntry(mMockContext, mTestHandler,
                wifiConfigToStandardWifiEntryKey(config), config,
                mockWifiManager, mMockScoreCache, false /* forSavedNetworksPage */);
        final WifiInfo mockWifiInfo = mock(WifiInfo.class);
        final NetworkInfo mockNetworkInfo = mock(NetworkInfo.class);

        entry.updateConnectionInfo(mockWifiInfo, mockNetworkInfo);
        return entry;
    }
}
