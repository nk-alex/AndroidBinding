package com.example.stripeandroidsource;

/**
 * Callback used to deliver connect results and runtime reader events.
 * onSuccess receives a JSON string describing the connected reader.
 * onReaderEvent receives JSON strings describing reader events (optional).
 */
public interface ConnectCallback {
    void onSuccess(String connectedReaderJson);
    void onFailure(String errorMessage);
    void onReaderEvent(String eventJson);
}
