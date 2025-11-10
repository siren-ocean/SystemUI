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

import static android.net.NetworkCapabilities.TRANSPORT_WIFI;
import static android.os.Build.VERSION_CODES;

import android.annotation.TargetApi;
import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityDiagnosticsManager;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiScanner;
import android.net.wifi.sharedconnectivity.app.HotspotNetwork;
import android.net.wifi.sharedconnectivity.app.HotspotNetworkConnectionStatus;
import android.net.wifi.sharedconnectivity.app.KnownNetwork;
import android.net.wifi.sharedconnectivity.app.KnownNetworkConnectionStatus;
import android.net.wifi.sharedconnectivity.app.SharedConnectivityClientCallback;
import android.net.wifi.sharedconnectivity.app.SharedConnectivityManager;
import android.net.wifi.sharedconnectivity.app.SharedConnectivitySettingsState;
import android.os.Handler;
import android.os.Looper;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.util.Log;

import androidx.annotation.AnyThread;
import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import androidx.core.os.BuildCompat;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleObserver;
import androidx.lifecycle.OnLifecycleEvent;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

/**
 * Base class for WifiTracker functionality.
 *
 * This class provides the basic functions of issuing scans, receiving Wi-Fi related broadcasts, and
 * keeping track of the Wi-Fi state.
 *
 * Subclasses are expected to provide their own API to clients and override the empty broadcast
 * handling methods here to populate the data returned by their API.
 *
 * This class runs on two threads:
 *
 * The main thread
 * - Processes lifecycle events (onStart, onStop)
 * - Runs listener callbacks
 *
 * The worker thread
 * - Drives the periodic scan requests
 * - Handles the system broadcasts to update the API return values
 * - Notifies the listener for updates to the API return values
 *
 * To keep synchronization simple, this means that the vast majority of work is done within the
 * worker thread. Synchronized blocks are only to be used for data returned by the API updated by
 * the worker thread and consumed by the main thread.
*/

public class BaseWifiTracker {
    private final String mTag;

    public boolean isVerboseLoggingEnabled() {
        return mInjector.isVerboseLoggingEnabled();
    }

    private int mWifiState = WifiManager.WIFI_STATE_DISABLED;

    private boolean mIsInitialized = false;
    private boolean mIsScanningDisabled = false;

    // Registered on the worker thread
    private final BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        @WorkerThread
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            if (isVerboseLoggingEnabled()) {
                Log.v(mTag, "Received broadcast: " + action);
            }

            if (WifiManager.WIFI_STATE_CHANGED_ACTION.equals(action)) {
                mWifiState = intent.getIntExtra(
                        WifiManager.EXTRA_WIFI_STATE, WifiManager.WIFI_STATE_DISABLED);
                mScanner.onWifiStateChanged(mWifiState == WifiManager.WIFI_STATE_ENABLED);
                notifyOnWifiStateChanged();
                handleWifiStateChangedAction();
            } else if (WifiManager.SCAN_RESULTS_AVAILABLE_ACTION.equals(action)) {
                handleScanResultsAvailableAction(intent);
            } else if (WifiManager.CONFIGURED_NETWORKS_CHANGED_ACTION.equals(action)) {
                handleConfiguredNetworksChangedAction(intent);
            } else if (WifiManager.NETWORK_STATE_CHANGED_ACTION.equals(action)) {
                handleNetworkStateChangedAction(intent);
            } else if (WifiManager.RSSI_CHANGED_ACTION.equals(action)) {
                handleRssiChangedAction(intent);
            } else if (TelephonyManager.ACTION_DEFAULT_DATA_SUBSCRIPTION_CHANGED.equals(action)) {
                handleDefaultSubscriptionChanged(intent.getIntExtra(
                        "subscription", SubscriptionManager.INVALID_SUBSCRIPTION_ID));
            }
        }
    };
    private final BaseWifiTracker.Scanner mScanner;
    private final BaseWifiTrackerCallback mListener;
    private final @NonNull LifecycleObserver mLifecycleObserver;

    protected final WifiTrackerInjector mInjector;
    protected final Context mContext;
    protected final @NonNull ActivityManager mActivityManager;
    protected final WifiManager mWifiManager;
    protected final ConnectivityManager mConnectivityManager;
    protected final ConnectivityDiagnosticsManager mConnectivityDiagnosticsManager;
    protected final Handler mMainHandler;
    protected final Handler mWorkerHandler;
    protected final long mMaxScanAgeMillis;
    protected final long mScanIntervalMillis;
    protected final ScanResultUpdater mScanResultUpdater;

    @Nullable protected SharedConnectivityManager mSharedConnectivityManager = null;

    // Network request for listening on changes to Wifi link properties and network capabilities
    // such as captive portal availability.
    private final NetworkRequest mNetworkRequest = new NetworkRequest.Builder()
            .clearCapabilities()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
            .addTransportType(TRANSPORT_WIFI)
            .build();

    private final ConnectivityManager.NetworkCallback mNetworkCallback =
            new ConnectivityManager.NetworkCallback(
                    ConnectivityManager.NetworkCallback.FLAG_INCLUDE_LOCATION_INFO) {
                @Override
                @WorkerThread
                public void onLinkPropertiesChanged(@NonNull Network network,
                        @NonNull LinkProperties lp) {
                    handleLinkPropertiesChanged(network, lp);
                }

                @Override
                @WorkerThread
                public void onCapabilitiesChanged(@NonNull Network network,
                        @NonNull NetworkCapabilities networkCapabilities) {
                    handleNetworkCapabilitiesChanged(network, networkCapabilities);
                }

                @Override
                @WorkerThread
                public void onLost(@NonNull Network network) {
                    handleNetworkLost(network);
                }
            };

    private final ConnectivityManager.NetworkCallback mDefaultNetworkCallback =
            new ConnectivityManager.NetworkCallback(
                    ConnectivityManager.NetworkCallback.FLAG_INCLUDE_LOCATION_INFO) {
                @Override
                @WorkerThread
                public void onCapabilitiesChanged(@NonNull Network network,
                        @NonNull NetworkCapabilities networkCapabilities) {
                    handleDefaultNetworkCapabilitiesChanged(network, networkCapabilities);
                }

                @WorkerThread
                public void onLost(@NonNull Network network) {
                    handleDefaultNetworkLost();
                }
            };

    private final ConnectivityDiagnosticsManager.ConnectivityDiagnosticsCallback
            mConnectivityDiagnosticsCallback =
            new ConnectivityDiagnosticsManager.ConnectivityDiagnosticsCallback() {
        @Override
        public void onConnectivityReportAvailable(
                @NonNull ConnectivityDiagnosticsManager.ConnectivityReport report) {
            handleConnectivityReportAvailable(report);
        }
    };

    private final Executor mConnectivityDiagnosticsExecutor = new Executor() {
        @Override
        public void execute(Runnable command) {
            mWorkerHandler.post(command);
        }
    };

    @TargetApi(VERSION_CODES.UPSIDE_DOWN_CAKE)
    private final Executor mSharedConnectivityExecutor = new Executor() {
        @Override
        public void execute(Runnable command) {
            mWorkerHandler.post(command);
        }
    };

    @TargetApi(VERSION_CODES.UPSIDE_DOWN_CAKE)
    @Nullable
    private SharedConnectivityClientCallback mSharedConnectivityCallback = null;

    @TargetApi(VERSION_CODES.UPSIDE_DOWN_CAKE)
    @NonNull
    private SharedConnectivityClientCallback createSharedConnectivityCallback() {
        return new SharedConnectivityClientCallback() {
            @Override
            public void onHotspotNetworksUpdated(@NonNull List<HotspotNetwork> networks) {
                handleHotspotNetworksUpdated(networks);
            }

            @Override
            public void onKnownNetworksUpdated(@NonNull List<KnownNetwork> networks) {
                handleKnownNetworksUpdated(networks);
            }

            @Override
            public void onSharedConnectivitySettingsChanged(
                    @NonNull SharedConnectivitySettingsState state) {
                handleSharedConnectivitySettingsChanged(state);
            }

            @Override
            public void onHotspotNetworkConnectionStatusChanged(
                    @NonNull HotspotNetworkConnectionStatus status) {
                handleHotspotNetworkConnectionStatusChanged(status);
            }

            @Override
            public void onKnownNetworkConnectionStatusChanged(
                    @NonNull KnownNetworkConnectionStatus status) {
                handleKnownNetworkConnectionStatusChanged(status);
            }

            @Override
            public void onServiceConnected() {
                handleServiceConnected();
            }

            @Override
            public void onServiceDisconnected() {
                handleServiceDisconnected();
            }

            @Override
            public void onRegisterCallbackFailed(Exception exception) {
                handleRegisterCallbackFailed(exception);
            }
        };
    }

    /**
     * Constructor for BaseWifiTracker.
     * @param injector Injector for commonly referenced objects.
     * @param lifecycle Lifecycle to register the internal LifecycleObserver with. Note that we
     *                  register the LifecycleObserver inside the constructor, which may cause an
     *                  NPE if the Lifecycle invokes onStart/onStop/onDestroyed within
     *                  {@link Lifecycle#addObserver}. To avoid this, pass {@code null} here and
     *                  register the LifecycleObserver from {@link #getLifecycleObserver()}
     *                  instead.
     * @param context Context for registering broadcast receiver and for resource strings.
     * @param wifiManager Provides all Wi-Fi info.
     * @param connectivityManager Provides network info.
     * @param mainHandler Handler for processing listener callbacks.
     * @param workerHandler Handler for processing all broadcasts and running the Scanner.
     * @param clock Clock used for evaluating the age of scans
     * @param maxScanAgeMillis Max age for tracked WifiEntries.
     * @param scanIntervalMillis Interval between initiating scans.
     */
    @SuppressWarnings("StaticAssignmentInConstructor")
    BaseWifiTracker(
            @NonNull WifiTrackerInjector injector,
            @Nullable Lifecycle lifecycle, @NonNull Context context,
            @NonNull WifiManager wifiManager,
            @NonNull ConnectivityManager connectivityManager,
            @NonNull Handler mainHandler,
            @NonNull Handler workerHandler,
            @NonNull Clock clock,
            long maxScanAgeMillis,
            long scanIntervalMillis,
            BaseWifiTrackerCallback listener,
            String tag) {
        mInjector = injector;
        mActivityManager = context.getSystemService(ActivityManager.class);
        mLifecycleObserver = new LifecycleObserver() {
            @OnLifecycleEvent(Lifecycle.Event.ON_START)
            @MainThread
            public void onStart() {
                BaseWifiTracker.this.onStart();
            }

            @OnLifecycleEvent(Lifecycle.Event.ON_STOP)
            @MainThread
            public void onStop() {
                BaseWifiTracker.this.onStop();
            }

            @OnLifecycleEvent(Lifecycle.Event.ON_DESTROY)
            @MainThread
            public void onDestroy() {
                BaseWifiTracker.this.onDestroy();
            }
        };
        mContext = context;
        mWifiManager = wifiManager;
        mConnectivityManager = connectivityManager;
        mConnectivityDiagnosticsManager =
                context.getSystemService(ConnectivityDiagnosticsManager.class);
        if (mInjector.isSharedConnectivityFeatureEnabled() && BuildCompat.isAtLeastU()) {
            mSharedConnectivityManager = context.getSystemService(SharedConnectivityManager.class);
            mSharedConnectivityCallback = createSharedConnectivityCallback();
        }
        mMainHandler = mainHandler;
        mWorkerHandler = workerHandler;
        mMaxScanAgeMillis = maxScanAgeMillis;
        mScanIntervalMillis = scanIntervalMillis;
        mListener = listener;
        mTag = tag;

        mScanResultUpdater = new ScanResultUpdater(clock,
                maxScanAgeMillis + scanIntervalMillis);
        mScanner = new BaseWifiTracker.Scanner(workerHandler.getLooper());

        if (lifecycle != null) { // Need to add after mScanner is initialized.
            lifecycle.addObserver(mLifecycleObserver);
        }
    }

    /**
     * Disable the scanning mechanism permanently.
     */
    public void disableScanning() {
        mIsScanningDisabled = true;
        // This method indicates SystemUI usage, which shouldn't output verbose logs since it's
        // always up.
        mInjector.disableVerboseLogging();
    }

    /**
     * Returns the LifecycleObserver to listen on the app's lifecycle state.
     */
    @AnyThread
    public LifecycleObserver getLifecycleObserver() {
        return mLifecycleObserver;
    }

    /**
     * Registers the broadcast receiver and network callbacks and starts the scanning mechanism.
     */
    @MainThread
    public void onStart() {
        if (isVerboseLoggingEnabled()) {
            Log.v(mTag, "onStart");
        }
        mScanner.onStart();
        mWorkerHandler.post(() -> {
            IntentFilter filter = new IntentFilter();
            filter.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
            filter.addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
            filter.addAction(WifiManager.CONFIGURED_NETWORKS_CHANGED_ACTION);
            filter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
            if (isVerboseLoggingEnabled()) {
                filter.addAction(WifiManager.RSSI_CHANGED_ACTION);
            }
            filter.addAction(TelephonyManager.ACTION_DEFAULT_DATA_SUBSCRIPTION_CHANGED);
            filter.setPriority(IntentFilter.SYSTEM_HIGH_PRIORITY);
            mContext.registerReceiver(mBroadcastReceiver, filter,
                    /* broadcastPermission */ null, mWorkerHandler);
            mConnectivityManager.registerNetworkCallback(mNetworkRequest, mNetworkCallback,
                    mWorkerHandler);
            mConnectivityManager.registerDefaultNetworkCallback(mDefaultNetworkCallback,
                    mWorkerHandler);
            mConnectivityDiagnosticsManager.registerConnectivityDiagnosticsCallback(mNetworkRequest,
                    mConnectivityDiagnosticsExecutor, mConnectivityDiagnosticsCallback);
            if (mSharedConnectivityManager != null && mSharedConnectivityCallback != null
                    && BuildCompat.isAtLeastU()) {
                mSharedConnectivityManager.registerCallback(mSharedConnectivityExecutor,
                        mSharedConnectivityCallback);
            }
            handleOnStart();
            mIsInitialized = true;
        });
    }

    /**
     * Unregisters the broadcast receiver, network callbacks, and pauses the scanning mechanism.
     */
    @MainThread
    public void onStop() {
        if (isVerboseLoggingEnabled()) {
            Log.v(mTag, "onStop");
        }
        mScanner.onStop();
        mWorkerHandler.post(() -> {
            try {
                mContext.unregisterReceiver(mBroadcastReceiver);
                mConnectivityManager.unregisterNetworkCallback(mNetworkCallback);
                mConnectivityManager.unregisterNetworkCallback(mDefaultNetworkCallback);
                mConnectivityDiagnosticsManager.unregisterConnectivityDiagnosticsCallback(
                        mConnectivityDiagnosticsCallback);
                if (mSharedConnectivityManager != null && mSharedConnectivityCallback != null
                        && BuildCompat.isAtLeastU()) {
                    boolean result =
                            mSharedConnectivityManager.unregisterCallback(
                                    mSharedConnectivityCallback);
                    if (!result) {
                        Log.e(mTag, "onStop: unregisterCallback failed");
                    }
                }
            } catch (IllegalArgumentException e) {
                // Already unregistered in onDestroyed().
            }
        });
    }

    /**
     * Unregisters the broadcast receiver network callbacks in case the Activity is destroyed before
     * the worker thread runnable posted in onStop() runs.
     */
    @MainThread
    public void onDestroy() {
        try {
            mContext.unregisterReceiver(mBroadcastReceiver);
            mConnectivityManager.unregisterNetworkCallback(mNetworkCallback);
            mConnectivityManager.unregisterNetworkCallback(mDefaultNetworkCallback);
            mConnectivityDiagnosticsManager.unregisterConnectivityDiagnosticsCallback(
                    mConnectivityDiagnosticsCallback);
            if (mSharedConnectivityManager != null && mSharedConnectivityCallback != null
                    && BuildCompat.isAtLeastU()) {
                boolean result =
                        mSharedConnectivityManager.unregisterCallback(
                                mSharedConnectivityCallback);
                if (!result) {
                    Log.e(mTag, "onDestroyed: unregisterCallback failed");
                }
            }
        } catch (IllegalArgumentException e) {
            // Already unregistered in onStop() worker thread runnable.
        }
    }

    /**
     * Returns true if this WifiTracker has already been initialized in the worker thread via
     * handleOnStart()
     */
    @AnyThread
    boolean isInitialized() {
        return mIsInitialized;
    }

    /**
     * Returns the state of Wi-Fi as one of the following values.
     *
     * <li>{@link WifiManager#WIFI_STATE_DISABLED}</li>
     * <li>{@link WifiManager#WIFI_STATE_ENABLED}</li>
     * <li>{@link WifiManager#WIFI_STATE_DISABLING}</li>
     * <li>{@link WifiManager#WIFI_STATE_ENABLING}</li>
     * <li>{@link WifiManager#WIFI_STATE_UNKNOWN}</li>
     */
    @AnyThread
    public int getWifiState() {
        return mWifiState;
    }

    /**
     * Method to run on the worker thread when onStart is invoked.
     * Data that can be updated immediately after onStart should be populated here.
     */
    @WorkerThread
    protected  void handleOnStart() {
        // Do nothing.
    }

    /**
     * Handle receiving the WifiManager.WIFI_STATE_CHANGED_ACTION broadcast
     */
    @WorkerThread
    protected void handleWifiStateChangedAction() {
        // Do nothing.
    }

    /**
     * Handle receiving the WifiManager.SCAN_RESULTS_AVAILABLE_ACTION broadcast
     */
    @WorkerThread
    protected void handleScanResultsAvailableAction(@NonNull Intent intent) {
        // Do nothing.
    }

    /**
     * Handle receiving the WifiManager.CONFIGURED_NETWORKS_CHANGED_ACTION broadcast
     */
    @WorkerThread
    protected void handleConfiguredNetworksChangedAction(@NonNull Intent intent) {
        // Do nothing.
    }

    /**
     * Handle receiving the WifiManager.NETWORK_STATE_CHANGED_ACTION broadcast
     */
    @WorkerThread
    protected void handleNetworkStateChangedAction(@NonNull Intent intent) {
        // Do nothing.
    }

    /**
     * Handle receiving the WifiManager.NETWORK_STATE_CHANGED_ACTION broadcast
     */
    @WorkerThread
    protected void handleRssiChangedAction(@NonNull Intent intent) {
        // Do nothing.
    }

    /**
     * Handle link property changes for the given network.
     */
    @WorkerThread
    protected void handleLinkPropertiesChanged(
            @NonNull Network network, @Nullable LinkProperties linkProperties) {
        // Do nothing.
    }

    /**
     * Handle network capability changes for the current connected Wifi network.
     */
    @WorkerThread
    protected void handleNetworkCapabilitiesChanged(
            @NonNull Network network, @NonNull NetworkCapabilities capabilities) {
        // Do nothing.
    }

    /**
     * Handle the loss of a network.
     */
    @WorkerThread
    protected void handleNetworkLost(@NonNull Network network) {
        // Do nothing.
    }

    /**
     * Handle receiving a connectivity report.
     */
    @WorkerThread
    protected void handleConnectivityReportAvailable(
            @NonNull ConnectivityDiagnosticsManager.ConnectivityReport connectivityReport) {
        // Do nothing.
    }

    /**
     * Handle default network capabilities changed.
     */
    @WorkerThread
    protected void handleDefaultNetworkCapabilitiesChanged(@NonNull Network network,
            @NonNull NetworkCapabilities networkCapabilities) {
        // Do nothing.
    }

    /**
     * Handle default network loss.
     */
    @WorkerThread
    protected void handleDefaultNetworkLost() {
        // Do nothing.
    }

    /**
     * Handle updates to the default data subscription id from SubscriptionManager.
     */
    @WorkerThread
    protected void handleDefaultSubscriptionChanged(int defaultSubId) {
        // Do nothing.
    }

    /**
     * Handle updates to the list of tether networks from SharedConnectivityManager.
     */
    @TargetApi(VERSION_CODES.UPSIDE_DOWN_CAKE)
    @WorkerThread
    protected void handleHotspotNetworksUpdated(List<HotspotNetwork> networks) {
        // Do nothing.
    }

    /**
     * Handle updates to the list of known networks from SharedConnectivityManager.
     */
    @TargetApi(VERSION_CODES.UPSIDE_DOWN_CAKE)
    @WorkerThread
    protected void handleKnownNetworksUpdated(List<KnownNetwork> networks) {
        // Do nothing.
    }

    /**
     * Handle changes to the shared connectivity settings from SharedConnectivityManager.
     */
    @TargetApi(VERSION_CODES.UPSIDE_DOWN_CAKE)
    @WorkerThread
    protected void handleSharedConnectivitySettingsChanged(
            @NonNull SharedConnectivitySettingsState state) {
        // Do nothing.
    }

    /**
     * Handle changes to the shared connectivity settings from SharedConnectivityManager.
     */
    @TargetApi(VERSION_CODES.UPSIDE_DOWN_CAKE)
    @WorkerThread
    protected void handleHotspotNetworkConnectionStatusChanged(
            @NonNull HotspotNetworkConnectionStatus status) {
        // Do nothing.
    }

    /**
     * Handle changes to the shared connectivity settings from SharedConnectivityManager.
     */
    @TargetApi(VERSION_CODES.UPSIDE_DOWN_CAKE)
    @WorkerThread
    protected void handleKnownNetworkConnectionStatusChanged(
            @NonNull KnownNetworkConnectionStatus status) {
        // Do nothing.
    }

    /**
     * Handle service connected callback from SharedConnectivityManager.
     */
    @TargetApi(VERSION_CODES.UPSIDE_DOWN_CAKE)
    @WorkerThread
    protected void handleServiceConnected() {
        // Do nothing.
    }

    /**
     * Handle service disconnected callback from SharedConnectivityManager.
     */
    @TargetApi(VERSION_CODES.UPSIDE_DOWN_CAKE)
    @WorkerThread
    protected void handleServiceDisconnected() {
        // Do nothing.
    }

    /**
     * Handle register callback failed callback from SharedConnectivityManager.
     */
    @TargetApi(VERSION_CODES.UPSIDE_DOWN_CAKE)
    @WorkerThread
    protected void handleRegisterCallbackFailed(Exception exception) {
        // Do nothing.
    }

    /**
     * Helper class to handle starting scans every SCAN_INTERVAL_MILLIS.
     *
     * Scanning is only done when the activity is in the Started state and Wi-Fi is enabled.
     */
    private class Scanner extends Handler {
        private boolean mIsStartedState = false;
        private boolean mIsWifiEnabled = false;
        private final WifiScanner.ScanListener mFirstScanListener = new WifiScanner.ScanListener() {
            @Override
            @MainThread
            public void onPeriodChanged(int periodInMs) {
                // No-op.
            }

            @Override
            @MainThread
            public void onResults(WifiScanner.ScanData[] results) {
                mWorkerHandler.post(() -> {
                    if (!shouldScan()) {
                        return;
                    }
                    if (isVerboseLoggingEnabled()) {
                        Log.v(mTag, "Received scan results from first scan request.");
                    }
                    List<ScanResult> scanResults = new ArrayList<>();
                    if (results != null) {
                        for (WifiScanner.ScanData scanData : results) {
                            scanResults.addAll(List.of(scanData.getResults()));
                        }
                    }
                    // Fake a SCAN_RESULTS_AVAILABLE_ACTION. The results should already be populated
                    // in mScanResultUpdater, which is the source of truth for the child classes.
                    mScanResultUpdater.update(scanResults);
                    handleScanResultsAvailableAction(
                            new Intent(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
                                    .putExtra(WifiManager.EXTRA_RESULTS_UPDATED, true));
                    // Now start scanning via WifiManager.startScan().
                    scanLoop();
                });
            }

            @Override
            @MainThread
            public void onFullResult(ScanResult fullScanResult) {
                // No-op.
            }

            @Override
            @MainThread
            public void onSuccess() {
                // No-op.
            }

            @Override
            @MainThread
            public void onFailure(int reason, String description) {
                mWorkerHandler.post(() -> {
                    if (!mIsWifiEnabled) {
                        return;
                    }
                    Log.e(mTag, "Failed to scan! Reason: " + reason + ", ");
                    // First scan failed, start scanning normally anyway.
                    scanLoop();
                });
            }
        };

        private Scanner(Looper looper) {
            super(looper);
        }

        /**
         * Called when the activity enters the Started state.
         * When this happens, evaluate if we need to start scanning.
         */
        @MainThread
        private void onStart() {
            mIsStartedState = true;
            mWorkerHandler.post(this::possiblyStartScanning);
        }

        /**
         * Called when the activity exits the Started state.
         * When this happens, stop scanning.
         */
        @MainThread
        private void onStop() {
            mIsStartedState = false;
            mWorkerHandler.post(this::stopScanning);
        }

        /**
         * Called whenever the Wi-Fi state changes. If the new state differs from the old state,
         * then re-evaluate whether we need to start or stop scanning.
         * @param enabled Whether Wi-Fi is enabled or not.
         */
        @WorkerThread
        private void onWifiStateChanged(boolean enabled) {
            boolean oldEnabled = mIsWifiEnabled;
            mIsWifiEnabled = enabled;
            if (mIsWifiEnabled != oldEnabled) {
                if (mIsWifiEnabled) {
                    possiblyStartScanning();
                } else {
                    stopScanning();
                }
            }
        }

        /**
         * Returns true if we should be scanning and false if not.
         * Scanning should only happen when Wi-Fi is enabled and the activity is started.
         */
        private boolean shouldScan() {
            return mIsWifiEnabled && mIsStartedState && !mIsScanningDisabled;
        }

        @WorkerThread
        private void possiblyStartScanning() {
            if (!shouldScan()) {
                return;
            }
            Log.i(mTag, "Scanning started");
            if (BuildCompat.isAtLeastU()) {
                // Start off with a fast scan of 2.4GHz, 5GHz, and 6GHz RNR using WifiScanner.
                // After this is done, fall back to WifiManager.startScan() to get the rest of
                // the bands and hidden networks.
                // TODO(b/274177966): Move to using WifiScanner exclusively once we have
                //                    permission to use ScanSettings.hiddenNetworks.
                WifiScanner.ScanSettings scanSettings = new WifiScanner.ScanSettings();
                scanSettings.band = WifiScanner.WIFI_BAND_BOTH;
                scanSettings.setRnrSetting(WifiScanner.WIFI_RNR_ENABLED);
                scanSettings.reportEvents = WifiScanner.REPORT_EVENT_FULL_SCAN_RESULT
                        | WifiScanner.REPORT_EVENT_AFTER_EACH_SCAN;
                WifiScanner wifiScanner = mContext.getSystemService(WifiScanner.class);
                if (wifiScanner != null) {
                    wifiScanner.stopScan(mFirstScanListener);
                    if (isVerboseLoggingEnabled()) {
                        Log.v(mTag, "Issuing scan request from WifiScanner");
                    }
                    wifiScanner.startScan(scanSettings, mFirstScanListener);
                    notifyOnScanRequested();
                    return;
                } else {
                    Log.e(mTag, "Failed to retrieve WifiScanner!");
                }
            }
            scanLoop();
        }

        @WorkerThread
        private void stopScanning() {
            Log.i(mTag, "Scanning stopped");
            removeCallbacksAndMessages(null);
        }

        @WorkerThread
        private void scanLoop() {
            if (!shouldScan()) {
                Log.e(mTag, "Scan loop called even though we shouldn't be scanning!"
                        + " mIsWifiEnabled=" + mIsWifiEnabled
                        + " mIsStartedState=" + mIsStartedState);
                return;
            }
            if (!isAppVisible()) {
                Log.e(mTag, "Scan loop called even though app isn't visible anymore!"
                        + " mIsWifiEnabled=" + mIsWifiEnabled
                        + " mIsStartedState=" + mIsStartedState);
                return;
            }
            if (isVerboseLoggingEnabled()) {
                Log.v(mTag, "Issuing scan request from WifiManager");
            }
            // Remove any pending scanLoops in case possiblyStartScanning was called more than once.
            removeCallbacksAndMessages(null);
            mWifiManager.startScan();
            notifyOnScanRequested();
            postDelayed(this::scanLoop, mScanIntervalMillis);
        }
    }

    private boolean isAppVisible() {
        ActivityManager.RunningAppProcessInfo processInfo =
                new ActivityManager.RunningAppProcessInfo();
        ActivityManager.getMyMemoryState(processInfo);
        return processInfo.importance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE;
    }

    /**
     * Posts onWifiStateChanged callback on the main thread.
     */
    @WorkerThread
    private void notifyOnWifiStateChanged() {
        if (mListener != null) {
            mMainHandler.post(mListener::onWifiStateChanged);
        }
    }

    /**
     * Posts onScanRequested callback on the main thread.
     */
    @WorkerThread
    private void notifyOnScanRequested() {
        if (mListener != null) {
            mMainHandler.post(mListener::onScanRequested);
        }
    }

    /**
     * Base callback handling Wi-Fi state changes
     *
     * Subclasses should extend this for their own needs.
     */
    protected interface BaseWifiTrackerCallback {
        /**
         * Called when the value for {@link #getWifiState() has changed.
         */
        @MainThread
        void onWifiStateChanged();

        @MainThread
        default void onScanRequested() {
            // Do nothing.
        }
    }
}
