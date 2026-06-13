package com.sandbox.network;

import com.sandbox.core.EventLoop;
import com.sandbox.core.SimulationContext;
import com.sandbox.telemetry.TelemetryExporter;

import java.util.HashMap;
import java.util.Map;

public class VirtualNetwork {
    private final SimulationContext context;
    private final EventLoop eventLoop;
    private final TelemetryExporter telemetry; // <-- Added Exporter
    private final Map<String, Node> nodes;

    private final double DROP_RATE = 0.20;
    private final int MIN_LATENCY = 10;
    private final int MAX_LATENCY = 100;

    // Inject TelemetryExporter via constructor
    public VirtualNetwork(SimulationContext context, EventLoop eventLoop, TelemetryExporter telemetry) {
        this.context = context;
        this.eventLoop = eventLoop;
        this.telemetry = telemetry;
        this.nodes = new HashMap<>();
    }

    public void registerNode(Node node) {
        nodes.put(node.getId(), node);
    }

    public void send(String from, String to, byte[] payload) {
        Node destination = nodes.get(to);
        if (destination == null) return;

        // --- CHAOS: PACKET DROP ---
        if (context.randomDouble() < DROP_RATE) {
            System.out.println("[Time: " + context.getVirtualTime() + "ms] ❌ CHAOS: Packet dropped (" + from + " -> " + to + ")");

            // Broadcast the DROP event to the UI
            String json = String.format("{\"type\": \"PACKET_DROP\", \"from\": \"%s\", \"to\": \"%s\", \"time\": %d}",
                    from, to, context.getVirtualTime());
            telemetry.export(json);
            return;
        }

        // --- NORMAL DELIVERY ---
        int delay = context.randomInt(MIN_LATENCY, MAX_LATENCY);
        long deliveryTime = context.getVirtualTime() + delay;

        System.out.println("[Time: " + context.getVirtualTime() + "ms] 📨 Network routing (" + from + " -> " + to + ")");

        // Broadcast the SENT event to the UI
        String json = String.format("{\"type\": \"PACKET_SENT\", \"from\": \"%s\", \"to\": \"%s\", \"time\": %d}",
                from, to, context.getVirtualTime());
        telemetry.export(json);

        Message msg = new Message(from, to, payload);
        eventLoop.schedule(new DeliverMessageEvent(deliveryTime, destination, msg));
    }
}