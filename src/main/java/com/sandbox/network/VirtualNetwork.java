package com.sandbox.network;

import com.sandbox.core.EventLoop;
import com.sandbox.core.SimulationContext;

import java.util.HashMap;
import java.util.Map;

public class VirtualNetwork {
    private final SimulationContext context;
    private final EventLoop eventLoop;
    private final Map<String, Node> nodes;

    // --- CHAOS PARAMETERS ---
    private final double DROP_RATE = 0.20; // 20% chance a packet is permanently lost
    private final int MIN_LATENCY = 10;    // Minimum travel time: 10ms
    private final int MAX_LATENCY = 100;   // Maximum travel time: 100ms

    public VirtualNetwork(SimulationContext context, EventLoop eventLoop) {
        this.context = context;
        this.eventLoop = eventLoop;
        this.nodes = new HashMap<>();
    }

    public void registerNode(Node node) {
        nodes.put(node.getId(), node);
        System.out.println("Network: Registered node [" + node.getId() + "]");
    }

    public void send(String from, String to, String payload) {
        Node destination = nodes.get(to);
        if (destination == null) {
            System.out.println("Network: Node [" + to + "] not found. Message dropped.");
            return;
        }

        // --- INJECTING CHAOS ---

        // 1. Packet Drop Check
        if (context.randomDouble() < DROP_RATE) {
            System.out.println("[Time: " + context.getVirtualTime() + "ms] ❌ CHAOS: Network dropped packet from " + from + " to " + to);
            return; // Exit immediately. The message is gone forever.
        }

        // 2. Latency Calculation
        int delay = context.randomInt(MIN_LATENCY, MAX_LATENCY);
        long deliveryTime = context.getVirtualTime() + delay;

        System.out.println("[Time: " + context.getVirtualTime() + "ms] 📨 Network routing packet from " + from + " to " + to + " (Arriving at " + deliveryTime + "ms)");

        // 3. Schedule the Delivery in the Event Loop
        Message msg = new Message(from, to, payload);
        eventLoop.schedule(new DeliverMessageEvent(deliveryTime, destination, msg));
    }
}