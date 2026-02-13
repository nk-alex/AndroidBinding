package com.example.stripeandroidsource;

import android.Manifest;
import android.app.Application;
import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresPermission;

import com.stripe.stripeterminal.*;
import com.stripe.stripeterminal.external.callable.Callback;
import com.stripe.stripeterminal.external.callable.Cancelable;
import com.stripe.stripeterminal.external.callable.ConnectionTokenCallback;
import com.stripe.stripeterminal.external.callable.ConnectionTokenProvider;
import com.stripe.stripeterminal.external.callable.OfflineListener;
import com.stripe.stripeterminal.external.callable.ReaderCallback;
import com.stripe.stripeterminal.external.callable.TapToPayReaderListener;
import com.stripe.stripeterminal.external.callable.TerminalListener;
import com.stripe.stripeterminal.external.models.ConnectionConfiguration;
import com.stripe.stripeterminal.external.models.ConnectionStatus;
import com.stripe.stripeterminal.external.models.DisconnectReason;
import com.stripe.stripeterminal.external.models.DiscoveryConfiguration;
import com.stripe.stripeterminal.external.models.OfflineStatus;
import com.stripe.stripeterminal.external.models.PaymentIntent;
import com.stripe.stripeterminal.external.models.PaymentStatus;
import com.stripe.stripeterminal.external.models.Reader;
import com.stripe.stripeterminal.external.models.TerminalException;
import com.stripe.stripeterminal.log.LogLevel;
import com.stripe.stripeterminal.taptopay.TapToPay;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Helper wrappers for Stripe Tap-to-Pay discovery + connect for consumption from .NET.
 * High-level design:
 * - startDiscoverReaders returns a JSON array of discovered readers where each object contains an "id"
 *   that is a stable key for the life of the discovery session. The library stores the SDK Reader
 *   object in a map keyed by that id.
 * - connectReaderById connects using the stored SDK Reader instance (no SDK types are required on the .NET side).
 */

public class TapToPayImplementation {
    // Keep a single discovery cancelable and a map of discovered readers for interop.
    private static Cancelable discoverCancelable;
    private static final Map<String, Reader> discoveredReaders = Collections.synchronizedMap(new HashMap<>());
    private static boolean initialized = false;

    /**
     * Initializes the Stripe Terminal SDK for Tap to Pay on Android.
     * <p>
     * This method must be called from the host application's {@link Application#onCreate()}
     * method. It ensures that the Stripe Terminal SDK is correctly initialized in the main
     * application process and avoids initialization when running in the Tap to Pay
     * auxiliary process.
     * </p>
     *
     * <p>
     * This method is safe to call multiple times; subsequent calls after the first
     * successful initialization will be ignored.
     * </p>
     *
     * <p><b>Important:</b> Failing to call this method may result in runtime errors
     * or undefined behavior when using Stripe Terminal Tap to Pay features.</p>
     *
     * @param application The {@link Application} instance of the host app.
     */
    public static void initialize(@NonNull Application application) throws TerminalException {
        if (initialized) return;

        // Skip initialization if running in the TTPA process
        if (TapToPay.isInTapToPayProcess()) return;

        TerminalApplicationDelegate.onCreate(application);

        Terminal.init(
                application.getApplicationContext(),
                LogLevel.VERBOSE,   // choose explicit level
                new ConnectionTokenProvider() {
                    @Override
                    public void fetchConnectionToken(@NonNull ConnectionTokenCallback connectionTokenCallback) {

                    }
                },
                new TerminalListener() {
                    @Override
                    public void onConnectionStatusChange(@NonNull ConnectionStatus status) {
                        TerminalListener.super.onConnectionStatusChange(status);
                    }

                    @Override
                    public void onPaymentStatusChange(@NonNull PaymentStatus status) {
                        TerminalListener.super.onPaymentStatusChange(status);
                    }
                },
                new OfflineListener() {
                    @Override
                    public void onOfflineStatusChange(@NonNull OfflineStatus offlineStatus) {

                    }

                    @Override
                    public void onPaymentIntentForwarded(@NonNull PaymentIntent paymentIntent, @Nullable TerminalException e) {

                    }

                    @Override
                    public void onForwardingFailure(@NonNull TerminalException e) {

                    }
                }
        );

        initialized = true;
    }

    public static boolean isInitialized() {
        return initialized;
    }

    /**
     * Start discovering readers and keep a mapping of generated reader ids to SDK Reader objects.
     * Each discovered reader object in the returned JSON will include:
     * - id: generated string the caller should use to call connectReaderById(...)
     * - description: reader.toString() fallback
     *
     * @param context application/activity context (not stored)
     * @param enableSimulated whether to enable simulated readers
     * @param callback callback to receive results
     */
    @RequiresPermission(allOf = {Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION})
    public static synchronized void startDiscoverReaders(@NonNull Context context, boolean enableSimulated, @NonNull DiscoveryCallbackBase callback) {

        if (discoverCancelable != null) {
            callback.onFailure("Discovery already in progress. Cancel the previous discovery first.");
            return;
        }

        final DiscoveryConfiguration.TapToPayDiscoveryConfiguration config =
                new DiscoveryConfiguration.TapToPayDiscoveryConfiguration(enableSimulated);

        try {
            discoverCancelable = Terminal.getInstance().discoverReaders(
                    config,
                    new com.stripe.stripeterminal.external.callable.DiscoveryListener() {
                        @Override
                        public void onUpdateDiscoveredReaders(@NonNull List<Reader> readers) {
                            try {
                                // Refresh the map for this discovery update.
                                // Note: we keep previously generated ids, so existing IDs remain valid for this process lifetime.
                                JSONArray arr = new JSONArray();
                                for (Reader r : readers) {
                                    String id = UUID.randomUUID().toString();
                                    // store the SDK Reader for later connect
                                    discoveredReaders.put(id, r);

                                    JSONObject obj = new JSONObject();
                                    obj.put("id", id);
                                    // Safe representation fallback
                                    obj.put("description", safeToString(r));
                                    // Optionally include other safe fields if present (guarded try/catch)
                                    // Example (uncomment if SDK exposes these methods):
                                    // try { obj.put("serialNumber", r.getSerialNumber() == null ? JSONObject.NULL : r.getSerialNumber()); } catch (Throwable ignored) {}
                                    arr.put(obj);
                                }
                                callback.onReadersDiscovered(arr.toString());
                            } catch (Exception ex) {
                                callback.onFailure("Failed to serialize readers: " + ex.toString());
                            }
                        }
                    },
                    new Callback() {
                        @Override
                        public void onSuccess() {
                            callback.onSuccess();
                        }

                        @Override
                        public void onFailure(@NonNull TerminalException e) {
                            callback.onFailure("Discover call failed: " + (e.toString()));
                        }
                    }
            );
        } catch (Exception ex) {
            discoverCancelable = null;

            StringWriter sw = new StringWriter();
            ex.printStackTrace(new PrintWriter(sw));
            String exceptionAsString = sw.toString();

            callback.onFailure("discoverReaders threw: " + exceptionAsString);
        }
    }

    /**
     * Cancel active discovery (no-op if none).
     */
    public static synchronized void cancelDiscovery(@NonNull DiscoveryCallbackBase callback) {
        if (discoverCancelable == null) {
            callback.onFailure("No active discovery to cancel.");
            return;
        }

        try {
            discoverCancelable.cancel(new Callback() {
                @Override
                public void onSuccess() {
                    synchronized (TapToPayImplementation.class) {
                        discoverCancelable = null;
                        discoveredReaders.clear();
                    }
                    callback.onSuccess();
                }

                @Override
                public void onFailure(@NonNull TerminalException e) {
                    callback.onFailure("Cancel failed: " + (e.toString()));
                }
            });
        } catch (Exception ex) {
            callback.onFailure("cancel threw: " + ex.toString());
        }
    }

    /**
     * Connect to a discovered reader by the id returned in the discovery JSON.
     *
     * @param readerId id provided in discovery JSON
     * @param locationId Stripe location id required by Tap-to-Pay
     * @param autoReconnect whether to auto-reconnect on unexpected disconnect
     * @param callback callback to receive connect success/failure and reader events
     */
    public static synchronized void connectReaderById(@NonNull String readerId, @NonNull String locationId, boolean autoReconnect, @NonNull ConnectCallbackBase callback) {
        Reader reader = discoveredReaders.get(readerId);
        if (reader == null) {
            callback.onFailure("Reader not found for id: " + readerId);
            return;
        }

        // Build TapToPayConnectionConfiguration.
        // The third argument is a TapToPayReaderListener — map its events into callback.onReaderEvent(...)
        final android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
        TapToPayReaderListener readerListener = new TapToPayReaderListener() {

            @Override
            public void onDisconnect(@NonNull DisconnectReason reason) {
                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            JSONObject ev = new JSONObject();
                            ev.put("type", "disconnect");
                            ev.put("readerId", readerId);
                            ev.put("reason", safeToString(reason));
                            // If reason has getters, add guarded fields here
                            callback.onReaderEvent(ev.toString());
                        } catch (Exception ex) {
                            callback.onReaderEvent("{\"type\":\"disconnect\",\"readerId\":\"" + readerId + "\"}");
                        }
                    }
                });
            }

            @Override
            public void onReaderReconnectSucceeded(@NonNull Reader reader) {
                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            JSONObject ev = new JSONObject();
                            ev.put("type", "reconnect_succeeded");
                            ev.put("readerId", readerId);
                            ev.put("reader", safeReaderJson(reader));
                            callback.onReaderEvent(ev.toString());
                        } catch (Exception ex) {
                            callback.onReaderEvent("{\"type\":\"reconnect_succeeded\",\"readerId\":\"" + readerId + "\"}");
                        }
                    }
                });
            }

            @Override
            public void onReaderReconnectFailed(@NonNull Reader reader) {
                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            JSONObject ev = new JSONObject();
                            ev.put("type", "reconnect_failed");
                            ev.put("readerId", readerId);
                            ev.put("reader", safeReaderJson(reader));
                            callback.onReaderEvent(ev.toString());
                        } catch (Exception ex) {
                            callback.onReaderEvent("{\"type\":\"reconnect_failed\",\"readerId\":\"" + readerId + "\"}");
                        }
                    }
                });
            }
        };

        ConnectionConfiguration.TapToPayConnectionConfiguration config = new ConnectionConfiguration.TapToPayConnectionConfiguration(locationId, autoReconnect, readerListener);

        try {
            Terminal.getInstance().connectReader(
                    reader,
                    config,
                    new ReaderCallback() {
                        @Override
                        public void onSuccess(@NonNull Reader connectedReader) {
                            // Optionally replace stored reader with connectedReader
                            // Generate a JSON representation for the connected reader
                            try {
                                JSONObject obj = new JSONObject();
                                obj.put("id", readerId);
                                obj.put("description", safeToString(connectedReader));
                                callback.onSuccess(obj.toString());
                            } catch (Exception ex) {
                                callback.onSuccess("{\"id\":\"" + readerId + "\",\"description\":\"" + safeToString(connectedReader) + "\"}");
                            }
                        }

                        @Override
                        public void onFailure(@NonNull TerminalException e) {
                            callback.onFailure("connectReader failed: " + (e.toString()));
                        }
                    }
            );
        } catch (Exception ex) {
            callback.onFailure("connectReader threw: " + ex.toString());
        }
    }

    /**
     * Simple safe toString helper that guards against nulls and exceptions.
     */
    private static String safeToString(@Nullable Object o) {
        if (o == null) return "null";
        try {
            return o.toString();
        } catch (Exception ex) {
            return o.getClass().getName();
        }
    }

    /* helper: safe reader JSON (only include getters you confirm exist; guard them) */
    private static JSONObject safeReaderJson(@Nullable Reader r) {
        JSONObject obj = new JSONObject();
        try {
            if (r == null) {
                obj.put("description", JSONObject.NULL);
                return obj;
            }
            obj.put("description", safeToString(r));
            // Example guarded getter usage (uncomment if the getter exists in your SDK)
            // try { obj.put("serialNumber", r.getSerialNumber() == null ? JSONObject.NULL : r.getSerialNumber()); } catch (Throwable ignored) {}
        } catch (Exception ex) {
            // ignore, return partial obj
        }
        return obj;
    }

    /**
     * Optional: provide a method to clear stored discovered readers (e.g., call on session end).
     */
    public static synchronized void clearDiscoveredReaders() {
        discoveredReaders.clear();
    }
}


