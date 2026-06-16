package com.sandbox.telemetry;

// HFT CONCEPT: Cache Line Padding to prevent False Sharing.
// A standard CPU cache line is 64 bytes.
// By adding 7 long variables (7 * 8 bytes = 56 bytes), plus the object header,
// we guarantee that NO TWO LogEvents share the same CPU cache line.
// This allows the RingBuffer to operate at maximum hardware speed without L1 cache invalidation.
public class LogEvent {
    private long p1, p2, p3, p4, p5, p6, p7; // Cache line padding (DO NOT REMOVE)

    private String jsonPayload;

    public void setJsonPayload(String jsonPayload) {
        this.jsonPayload = jsonPayload;
    }

    public String getJsonPayload() {
        return jsonPayload;
    }

    // Prevent the JVM from optimizing away our padding variables
    public long preventOptimization() { return p1 + p2 + p3 + p4 + p5 + p6 + p7; }
}