package com.sandbox.core;

import com.sandbox.ledger.BankNode;
import com.sandbox.ledger.Coordinator;
import com.sandbox.ledger.Transaction;
import com.sandbox.network.VirtualNetwork;
import com.sandbox.storage.VirtualDisk;
import com.sandbox.telemetry.LmaxDisruptorExporter;
import com.sandbox.telemetry.TelemetryExporter;

public class Main {
    public static void main(String[] args) {
        System.out.println("=== DETERMINISTIC SANDBOX SERVER ===");
        System.out.println("🚀 Booting API and LMAX Disruptor...");

        new LmaxDisruptorExporter();

        System.out.println("✅ Backend is LIVE. Waiting for UI commands on Port 7070...");
    }

    // Accepts dynamic Chaos params from the UI
    public static void runUIDemo(long seed, double dropRate, int minLat, int maxLat, TelemetryExporter telemetry) {
        telemetry.export("{\"type\": \"FUZZER_LOG\", \"status\": \"INFO\", \"msg\": \"▶️ Starting visual playback for Seed " + seed + "...\"}");

        SimulationContext context = new SimulationContext(seed);
        EventLoop loop = new EventLoop(context);

        VirtualNetwork network = new VirtualNetwork(context, loop, telemetry);
        network.setChaosParameters(dropRate, minLat, maxLat); // APPLIES SETTINGS

        VirtualDisk disk = new VirtualDisk(context, loop);

        BankNode nodeA = new BankNode("Node_A", 500, context, loop, network, disk);
        BankNode nodeB = new BankNode("Node_B", 100, context, loop, network, disk);
        Coordinator coordinator = new Coordinator(context, network, telemetry);

        network.registerNode(nodeA);
        network.registerNode(nodeB);
        network.registerNode(coordinator);

        loop.schedule(new Event(0) {
            @Override
            public void execute() {
                for (int i = 1; i <= 10; i++) {
                    coordinator.initiateTransfer(new Transaction("TX-" + i, "Node_A", "Node_B", 10));
                }
            }
        });

        // Metrics Ticker updates the UI Balances every 50ms
// Metrics Ticker
        for (long t = 0; t <= 1500; t += 50) {
            loop.schedule(new Event(t) {
                @Override
                public void execute() {
                    // WE ADDED lockedA and lockedB to the JSON output
                    telemetry.export(String.format(
                            "{\"type\": \"METRICS\", \"time\": %d, \"balanceA\": %d, \"lockedA\": %d, \"balanceB\": %d, \"lockedB\": %d}",
                            context.getVirtualTime(),
                            nodeA.getBalance(), nodeA.getLockedFunds(),
                            nodeB.getBalance(), nodeB.getLockedFunds()
                    ));
                }
            });
        }

        loop.run();
        telemetry.export("{\"type\": \"FUZZER_LOG\", \"status\": \"INFO\", \"msg\": \"✅ Visual Playback Complete.\"}");
    }
}