package com.sandbox.network;

public interface Node {
    String getId();
    void onMessageReceived(String from, byte[] payload); // CHANGED TO byte[]
}