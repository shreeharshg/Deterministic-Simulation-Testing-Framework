package com.sandbox.core;

public abstract class Event implements Comparable<Event> {
    private final long timestamp;
    private final long eventId;
    private static long idGenerator = 0; // Tie-breaker for events at the exact same microsecond

    public Event(long timestamp) {
        this.timestamp = timestamp;
        this.eventId = idGenerator++;
    }

    public long getTimestamp() {
        return timestamp;
    }

    // The actual logic of the event goes here
    public abstract void execute();

    @Override
    public int compareTo(Event other) {
        // 1. Sort by Timestamp (Lowest time executes first)
        int timeComparison = Long.compare(this.timestamp, other.timestamp);
        if (timeComparison != 0) {
            return timeComparison;
        }
        // 2. Tie-breaker: If two events happen at the EXACT same time,
        // the one created first executes first. This guarantees determinism.
        return Long.compare(this.eventId, other.eventId);
    }
}