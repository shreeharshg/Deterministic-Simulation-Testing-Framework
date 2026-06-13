package com.sandbox.network;

public record Message(String from, String to, byte[] payload) {} // CHANGED TO byte[]