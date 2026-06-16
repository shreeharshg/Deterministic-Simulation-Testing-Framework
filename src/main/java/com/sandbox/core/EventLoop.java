package com.sandbox.core;

import java.util.PriorityQueue;

public class EventLoop {
    private final SimulationContext context;
    private final PriorityQueue<Event> queue;

    public EventLoop(SimulationContext context) {
        this.context = context;
        this.queue = new PriorityQueue<>();
    }

    public void schedule(Event event) {
        queue.add(event);
    }

    // The heart of the Matrix
    public void run() {
        System.out.println("--- Starting Simulation Loop ---");

        while (!queue.isEmpty() && !Thread.currentThread().isInterrupted()) {
            Event event = queue.poll(); // Get the event with the lowest timestamp

            // Fast-forward time to the exact moment this event occurs
            context.advanceTime(event.getTimestamp());

            // Execute it
            event.execute();
        }

        System.out.println("--- Simulation Complete. Final Time: " + context.getVirtualTime() + "ms ---");
    }
}