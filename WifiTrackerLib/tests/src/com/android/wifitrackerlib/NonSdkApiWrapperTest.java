/*
 * Copyright (C) 2021 The Android Open Source Project
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

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.net.NetworkCapabilities;
import android.net.vcn.VcnTransportInfo;
import android.net.wifi.WifiInfo;
import android.text.Annotation;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ClickableSpan;

import org.junit.Test;

public class NonSdkApiWrapperTest {
    @Test
    public void testLinkifyAnnotation_annotation_returnTextWithClickableSpan() {
        final String annotationId = "id";
        final CharSequence testText = "test text ";
        final CharSequence testLink = "link";
        final CharSequence expectedText = "test text link";
        final SpannableStringBuilder builder = new SpannableStringBuilder(testText);
        builder.append(testLink, new Annotation("key", annotationId),
                Spanned.SPAN_INCLUSIVE_INCLUSIVE);

        final CharSequence output = NonSdkApiWrapper.linkifyAnnotation(
                mock(Context.class), builder, annotationId, "url");

        final SpannableString outputSpannableString = new SpannableString(output);
        assertEquals(output.toString(), expectedText.toString());
        assertEquals(outputSpannableString.getSpans(0, outputSpannableString.length(),
                ClickableSpan.class).length, 1);
    }

    @Test
    public void testLinkifyAnnotation_annotationWithEmptyUriString_returnOriginalText() {
        final String annotationId = "url";
        final CharSequence testText = "test text ";
        final CharSequence testLink = "Learn More";
        final CharSequence expectedText = "test text Learn More";
        final SpannableStringBuilder builder = new SpannableStringBuilder(testText);
        builder.append(testLink, new Annotation("key", annotationId),
                Spanned.SPAN_INCLUSIVE_INCLUSIVE);

        final CharSequence output = NonSdkApiWrapper.linkifyAnnotation(
                mock(Context.class), builder, annotationId, "");

        final SpannableString outputSpannableString = new SpannableString(output);
        assertEquals(output.toString(), expectedText.toString());
        assertEquals(outputSpannableString.getSpans(0, outputSpannableString.length(),
                ClickableSpan.class).length, 0);
    }

    /**
     * Verifies the functionality of {@link NonSdkApiWrapper#getWifiInfoIfVcn(NetworkCapabilities)}
     */
    @Test
    public void testGetVcnWifiInfo() {
        NetworkCapabilities networkCapabilities  = mock(NetworkCapabilities.class);

        assertThat(NonSdkApiWrapper.getWifiInfoIfVcn(networkCapabilities)).isNull();

        VcnTransportInfo vcnTransportInfo = mock(VcnTransportInfo.class);
        when(networkCapabilities.getTransportInfo()).thenReturn(vcnTransportInfo);

        assertThat(NonSdkApiWrapper.getWifiInfoIfVcn(networkCapabilities)).isNull();

        WifiInfo wifiInfo = mock(WifiInfo.class);
        when(vcnTransportInfo.getWifiInfo()).thenReturn(wifiInfo);

        assertThat(NonSdkApiWrapper.getWifiInfoIfVcn(networkCapabilities)).isEqualTo(wifiInfo);
    }
}
