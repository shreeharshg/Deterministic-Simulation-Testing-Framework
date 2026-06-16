package com.sandbox.network;

import com.sandbox.core.EventLoop;
import com.sandbox.core.SimulationContext;
import com.sandbox.telemetry.TelemetryExporter;

import java.util.HashMap;
import java.util.Map;

public class VirtualNetwork {
    private final SimulationContext context;
    private final EventLoop eventLoop;
    private final TelemetryExporter telemetry;
    private final Map<String, Node> nodes;

    // Default Chaos Parameters (Can be overridden by UI)
    private double dropRate = 0.20;
    private int minLatency = 10;
    private int maxLatency = 100;

    public VirtualNetwork(SimulationContext context, EventLoop eventLoop, TelemetryExporter telemetry) {
        this.context = context;
        this.eventLoop = eventLoop;
        this.telemetry = telemetry;
        this.nodes = new HashMap<>();
    }

    public void setChaosParameters(double dropRate, int minLatency, int maxLatency) {
        this.dropRate = dropRate;
        this.minLatency = minLatency;
        this.maxLatency = maxLatency;
    }

    public void registerNode(Node node) {
        nodes.put(node.getId(), node);
    }

    public void send(String from, String to, byte[] payload) {
        Node destination = nodes.get(to);
        if (destination == null) return;

        // CHAOS: Packet Drop
        if (context.randomDouble() < dropRate) {
            String json = String.format("{\"type\": \"PACKET_DROP\", \"from\": \"%s\", \"to\": \"%s\", \"time\": %d}",
                    from, to, context.getVirtualTime());
            telemetry.export(json);
            return;
        }

        // NORMAL DELIVERY
        int delay = context.randomInt(minLatency, maxLatency);
        long deliveryTime = context.getVirtualTime() + delay;

        String json = String.format("{\"type\": \"PACKET_SENT\", \"from\": \"%s\", \"to\": \"%s\", \"time\": %d}",
                from, to, context.getVirtualTime());
        telemetry.export(json);

        Message msg = new Message(from, to, payload);
        eventLoop.schedule(new DeliverMessageEvent(deliveryTime, destination, msg));
    }
}