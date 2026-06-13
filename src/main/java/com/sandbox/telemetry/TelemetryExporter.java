package com.sandbox.telemetry;

public interface TelemetryExporter {
    // Takes a JSON string and sends it out of the simulation
    void export(String jsonEvent);
}