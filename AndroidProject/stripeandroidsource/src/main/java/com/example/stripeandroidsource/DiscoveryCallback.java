package com.example.stripeandroidsource;

/**
 * Callback used to deliver discovery results to the consumer (C# will see a generated interface).
 */
public interface DiscoveryCallback {
    void onReadersDiscovered(String readersJson);
    void onSuccess();
    void onFailure(String errorMessage);
}
