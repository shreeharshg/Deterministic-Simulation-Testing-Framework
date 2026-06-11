package com.sandbox.core;

import com.sandbox.network.Node;
import com.sandbox.network.VirtualNetwork;

public class Main {
    public static void main(String[] args) {
        System.out.println("=== INITIALIZING DETERMINISTIC SANDBOX ===");

        // 1. Initialize the Kernel with Seed 42
        SimulationContext context = new SimulationContext(42);
        EventLoop loop = new EventLoop(context);

        // 2. Initialize the Virtual Network
        VirtualNetwork network = new VirtualNetwork(context, loop);

        // 3. Create two dummy mock nodes to test the network
        Node nodeA = new Node() {
            @Override
            public String getId() { return "Node_A"; }

            @Override
            public void onMessageReceived(String from, String payload) {
                System.out.println("[Time: " + context.getVirtualTime() + "ms] 🟢 Node_A received: '" + payload + "' from " + from);
            }
        };

        Node nodeB = new Node() {
            @Override
            public String getId() { return "Node_B"; }

            @Override
            public void onMessageReceived(String from, String payload) {
                System.out.println("[Time: " + context.getVirtualTime() + "ms] 🟢 Node_B received: '" + payload + "' from " + from);
            }
        };

        // Register them to the network
        network.registerNode(nodeA);
        network.registerNode(nodeB);

        // 4. Create an Event to fire off 5 messages simultaneously at Virtual Time 0
        loop.schedule(new Event(0) {
            @Override
            public void execute() {
                System.out.println("\n[Time: " + context.getVirtualTime() + "ms] Node_A is sending 5 packets to Node_B...");
                for (int i = 1; i <= 5; i++) {
                    network.send("Node_A", "Node_B", "Packet #" + i);
                }
                System.out.println("-------------------------------------------------");
            }
        });

        // 5. Start the Engine!
        loop.run();
    }
}