package com.sandbox.core;

import com.sandbox.ledger.BankNode;
import com.sandbox.ledger.Coordinator;
import com.sandbox.ledger.Transaction;
import com.sandbox.network.VirtualNetwork;
import com.sandbox.storage.VirtualDisk;
import com.sandbox.telemetry.TelemetryExporter;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class ChaosFuzzer {

    private static ExecutorService activeExecutor = null;

    public static void stopFuzzing(TelemetryExporter telemetry) {
        if (activeExecutor != null && !activeExecutor.isTerminated()) {
            activeExecutor.shutdownNow();
            telemetry.export("{\"type\": \"FUZZER_LOG\", \"status\": \"ERROR\", \"msg\": \" USER ABORT SIGNAL RECEIVED. Halting Virtual Threads...\"}");
            telemetry.export("{\"type\": \"FUZZER_STOPPED\"}");
        }
    }

    public static void runMassFuzzing(int totalUniverses, int txPerUniverse, double dropRate, int minLat, int maxLat, TelemetryExporter telemetry) {
        telemetry.export("{\"type\": \"FUZZER_LOG\", \"status\": \"INFO\", \"msg\": \" INITIALIZING MASS FUZZER (Project Loom)...\"}");
        telemetry.export("{\"type\": \"FUZZER_LOG\", \"status\": \"INFO\", \"msg\": \"️ PARAMETERS: " + totalUniverses + " Universes | " + txPerUniverse + " TX/Universe | " + (dropRate*100) + "% Packet Drop\"}");

        AtomicInteger bugsFound = new AtomicInteger(0);
        AtomicInteger completedSims = new AtomicInteger(0);
        AtomicInteger totalTxProcessed = new AtomicInteger(0);

        long startTime = System.currentTimeMillis();
        activeExecutor = Executors.newVirtualThreadPerTaskExecutor();

        try {
            for (long seed = 1; seed <= totalUniverses; seed++) {
                final long currentSeed = seed;

                activeExecutor.submit(() -> {
                    if (Thread.currentThread().isInterrupted()) return;

                    SimulationContext context = new SimulationContext(currentSeed);
                    EventLoop loop = new EventLoop(context);
                    TelemetryExporter dummyExporter = json -> {};

                    VirtualNetwork network = new VirtualNetwork(context, loop, dummyExporter);
                    network.setChaosParameters(dropRate, minLat, maxLat);

                    VirtualDisk disk = new VirtualDisk(context, loop);
                    BankNode nodeA = new BankNode("Node_A", 500, context, loop, network, disk);
                    BankNode nodeB = new BankNode("Node_B", 100, context, loop, network, disk);
                    Coordinator coord = new Coordinator(context, network, dummyExporter);

                    network.registerNode(nodeA);
                    network.registerNode(nodeB);
                    network.registerNode(coord);

                    loop.schedule(new Event(0) {
                        @Override
                        public void execute() {
                            for (int i = 1; i <= txPerUniverse; i++) {
                                coord.initiateTransfer(new Transaction("TX-" + i, "Node_A", "Node_B", 10));
                                totalTxProcessed.incrementAndGet();
                            }
                        }
                    });

                    loop.run();

                    if (!Thread.currentThread().isInterrupted()) {
                        int finalTotal = nodeA.getBalance() + nodeB.getBalance();

                        // SUPERCHARGED BUG REPORTING
                        if (finalTotal != 600) {
                            bugsFound.incrementAndGet();
                            int missing = 600 - finalTotal;
                            String rootCause = missing > 0 ? "Phase 2 COMMIT dropped to Receiver." : "Invalid State detected.";

                            String bugReport = String.format(
                                    " INVARIANT BREACH ON SEED %d | Expected: $600 | Actual: $%d | Leaked: $%d | Root Cause: %s",
                                    currentSeed, finalTotal, missing, rootCause
                            );
                            telemetry.export("{\"type\": \"FUZZER_LOG\", \"status\": \"ERROR\", \"msg\": \"" + bugReport + "\"}");
                        }

                        // HIGH DENSITY PROGRESS METRICS
                        int done = completedSims.incrementAndGet();
                        if (done % (totalUniverses / 10) == 0 || done == totalUniverses) {
                            long elapsed = System.currentTimeMillis() - startTime;
                            long tps = (totalTxProcessed.get() * 1000L) / (elapsed + 1); // Calculate TPS
                            double progress = ((double)done / totalUniverses) * 100;

                            String progMsg = String.format("⚡ PROGRESS: %.1f%% | Active V-Threads: %d | Throughput: %,d TPS", progress, (totalUniverses - done), tps);
                            telemetry.export("{\"type\": \"FUZZER_LOG\", \"status\": \"INFO\", \"msg\": \"" + progMsg + "\"}");
                        }
                    }
                });
            }
        } finally {
            activeExecutor.shutdown();
            while (!activeExecutor.isTerminated()) {
                try { Thread.sleep(100); } catch (InterruptedException ignored) {}
            }
        }

        long endTime = System.currentTimeMillis();
        long finalTps = (totalTxProcessed.get() * 1000L) / ((endTime - startTime) + 1);

        telemetry.export("{\"type\": \"FUZZER_LOG\", \"status\": \"INFO\", \"msg\": \"==================================================\"}");
        telemetry.export("{\"type\": \"FUZZER_LOG\", \"status\": \"INFO\", \"msg\": \" MASS FUZZING DIAGNOSTIC REPORT\"}");
        telemetry.export("{\"type\": \"FUZZER_LOG\", \"status\": \"INFO\", \"msg\": \"️ Execution Time: " + (endTime - startTime) + "ms\"}");
        telemetry.export("{\"type\": \"FUZZER_LOG\", \"status\": \"INFO\", \"msg\": \" Total Transactions Processed: " + totalTxProcessed.get() + "\"}");
        telemetry.export("{\"type\": \"FUZZER_LOG\", \"status\": \"INFO\", \"msg\": \" Final Throughput: " + finalTps + " TPS\"}");
        telemetry.export("{\"type\": \"FUZZER_LOG\", \"status\": \"INFO\", \"msg\": \" 2PC Consensus Failures Found: " + bugsFound.get() + "\"}");
        telemetry.export("{\"type\": \"FUZZER_LOG\", \"status\": \"INFO\", \"msg\": \"==================================================\"}");
        telemetry.export("{\"type\": \"FUZZER_STOPPED\"}");
    }
}