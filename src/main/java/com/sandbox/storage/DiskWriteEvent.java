package com.sandbox.storage;

import com.sandbox.core.Event;

public class DiskWriteEvent extends Event {
    private final Runnable diskTask;
    private final Runnable onComplete;

    public DiskWriteEvent(long finishTime, Runnable diskTask, Runnable onComplete) {
        super(finishTime);
        this.diskTask = diskTask;
        this.onComplete = onComplete;
    }

    @Override
    public void execute() {
        // 1. Actually write to the real Mac hard drive
        diskTask.run();

        // 2. Notify the Node that the write is 100% safe on disk
        if (onComplete != null) {
            onComplete.run();
        }
    }
}