package com.sandbox.core;

import com.sandbox.ledger.BankNode;
import com.sandbox.ledger.Coordinator;
import com.sandbox.ledger.Transaction;
import com.sandbox.network.VirtualNetwork;
import com.sandbox.storage.VirtualDisk;
import com.sandbox.telemetry.DirectWebSocketExporter;
import com.sandbox.telemetry.LmaxDisruptorExporter;
import com.sandbox.telemetry.TelemetryExporter;

import java.util.concurrent.CountDownLatch;

public class Main {
    public static void main(String[] args) {
        System.out.println("=== INITIALIZING DETERMINISTIC SANDBOX ===");

        // 1. Create a Latch that forces the program to wait for 1 connection
        CountDownLatch waitForUI = new CountDownLatch(1);

        // 2. Start the API Server
        TelemetryExporter telemetry = new LmaxDisruptorExporter(waitForUI);
        System.out.println("🚀 WebSocket Server started on ws://localhost:7070/ws");

        // 3. Initialize the Matrix
        SimulationContext context = new SimulationContext(150);
        EventLoop loop = new EventLoop(context);

        VirtualNetwork network = new VirtualNetwork(context, loop, telemetry);
        VirtualDisk disk = new VirtualDisk(context, loop);

        BankNode nodeA = new BankNode("Node_A", 500, context, loop, network, disk);
        BankNode nodeB = new BankNode("Node_B", 100, context, loop, network, disk);
        Coordinator coordinator = new Coordinator(context, network);

        network.registerNode(nodeA);
        network.registerNode(nodeB);
        network.registerNode(coordinator);

        // 4. Schedule the Transaction
        loop.schedule(new Event(0) {
            @Override
            public void execute() {
                Transaction tx = new Transaction("TX-001", "Node_A", "Node_B", 50);
                coordinator.initiateTransfer(tx);
            }
        });

        // 5. Schedule the Invariant Checker
        loop.schedule(new Event(1000) {
            @Override
            public void execute() {
                System.out.println("\n=== END OF SIMULATION ===");
                System.out.println("Node A Balance: $" + nodeA.getBalance());
                System.out.println("Node B Balance: $" + nodeB.getBalance());
                int total = nodeA.getBalance() + nodeB.getBalance();
                System.out.println("Total Money in System: $" + total + " (Should always be $600)");
                if (total != 600) {
                    System.out.println("🚨 INVARIANT FAILED: MONEY WAS LOST OR CREATED!");
                } else {
                    System.out.println("✅ INVARIANT PASSED: System is mathematically sound.");
                }
            }
        });

        // ---------------------------------------------------------
        // CRITICAL FIX: THIS FORCES JAVA TO STOP AND WAIT FOR THE UI
        // ---------------------------------------------------------
        System.out.println("⏳ WAITING FOR NEXT.JS UI TO CONNECT...");
        try {
            waitForUI.await(); // The code freezes here until the UI connects
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        System.out.println("▶️ UI CONNECTED! STARTING SIMULATION NOW!");
        // ---------------------------------------------------------

        // 6. Start the Engine!
        loop.run();
    }
}