/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.google.android.setupcompat.logging.internal;

import android.content.Context;
import androidx.annotation.IntDef;
import androidx.annotation.StringDef;
import com.google.android.setupcompat.logging.MetricKey;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/** Constant values used by {@link com.google.android.setupcompat.logging.SetupMetricsLogger}. */
public interface SetupMetricsLoggingConstants {

  /** Enumeration of supported metric types logged to SetupWizard. */
  @Retention(RetentionPolicy.SOURCE)
  @IntDef({MetricType.CUSTOM_EVENT, MetricType.COUNTER_EVENT, MetricType.DURATION_EVENT})
  @interface MetricType {
    /**
     * MetricType constant used when logging {@link
     * com.google.android.setupcompat.logging.CustomEvent}.
     */
    int CUSTOM_EVENT = 1;
    /**
     * MetricType constant used when logging {@link com.google.android.setupcompat.logging.Timer}.
     */
    int DURATION_EVENT = 2;

    /**
     * MetricType constant used when logging counter value using {@link
     * com.google.android.setupcompat.logging.SetupMetricsLogger#logCounter(Context, MetricKey,
     * int)}.
     */
    int COUNTER_EVENT = 3;

    /** MetricType constant used for internal logging purposes. */
    int INTERNAL = 100;
  }

  /** Keys of the bundle used while logging data to SetupWizard. */
  @Retention(RetentionPolicy.SOURCE)
  @StringDef({
    MetricBundleKeys.METRIC_KEY,
    MetricBundleKeys.METRIC_KEY_BUNDLE,
    MetricBundleKeys.CUSTOM_EVENT,
    MetricBundleKeys.CUSTOM_EVENT_BUNDLE,
    MetricBundleKeys.TIME_MILLIS_LONG,
    MetricBundleKeys.COUNTER_INT
  })
  @interface MetricBundleKeys {
    /**
     * {@link MetricKey} of the data being logged. This will be set when {@code metricType} is
     * either {@link MetricType#COUNTER_EVENT} or {@link MetricType#DURATION_EVENT}.
     *
     * @deprecated Use {@link #METRIC_KEY_BUNDLE} instead.
     */
    @Deprecated String METRIC_KEY = "MetricKey";

    /**
     * This key will be used when {@code metricType} is {@link MetricType#CUSTOM_EVENT} with the
     * value being a parcelable of type {@link com.google.android.setupcompat.logging.CustomEvent}.
     *
     * @deprecated Use {@link #CUSTOM_EVENT_BUNDLE} instead.
     */
    @Deprecated String CUSTOM_EVENT = "CustomEvent";

    /**
     * This key will be set when {@code metricType} is {@link MetricType#DURATION_EVENT} with the
     * value of type {@code long} representing the {@code duration} in milliseconds for the given
     * {@link MetricKey}.
     */
    String TIME_MILLIS_LONG = "timeMillis";

    /**
     * This key will be set when {@code metricType} is {@link MetricType#COUNTER_EVENT} with the
     * value of type {@code int} representing the {@code counter} value logged for the given {@link
     * MetricKey}.
     */
    String COUNTER_INT = "counter";

    /**
     * {@link MetricKey} of the data being logged. This will be set when {@code metricType} is
     * either {@link MetricType#COUNTER_EVENT} or {@link MetricType#DURATION_EVENT}.
     */
    String METRIC_KEY_BUNDLE = "MetricKey_bundle";

    /**
     * This key will be used when {@code metricType} is {@link MetricType#CUSTOM_EVENT} with the
     * value being a Bundle which can be used to read {@link
     * com.google.android.setupcompat.logging.CustomEvent}.
     */
    String CUSTOM_EVENT_BUNDLE = "CustomEvent_bundle";
  }
}
