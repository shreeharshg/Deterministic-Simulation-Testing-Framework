package com.sandbox.storage;

import com.sandbox.core.EventLoop;
import com.sandbox.core.SimulationContext;

public class VirtualDisk {
    private final SimulationContext context;
    private final EventLoop eventLoop;

    // Simulated hardware latency: It takes 2ms for our "SSD" to write data
    private final int DISK_LATENCY_MS = 2;

    public VirtualDisk(SimulationContext context, EventLoop eventLoop) {
        this.context = context;
        this.eventLoop = eventLoop;
    }

    // Nodes call this method. It is ASYNCHRONOUS.
    public void writeAsync(Runnable actualWriteLogic, Runnable onCompleteCallback) {
        long finishTime = context.getVirtualTime() + DISK_LATENCY_MS;

        // Schedule the write to finish 2 milliseconds in the future
        eventLoop.schedule(new DiskWriteEvent(finishTime, actualWriteLogic, onCompleteCallback));
    }
}