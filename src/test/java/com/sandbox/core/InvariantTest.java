package com.sandbox.core;

import com.sandbox.ledger.*;
import com.sandbox.network.VirtualNetwork;
import com.sandbox.storage.VirtualDisk;
import org.junit.jupiter.api.RepeatedTest;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class InvariantTest {

    @RepeatedTest(100) // Runs the simulation 100 times with random noise
    void testTotalMoneyInvariant() {
        long randomSeed = (long) (Math.random() * 100000);
        SimulationContext context = new SimulationContext(randomSeed);
        EventLoop loop = new EventLoop(context);

        // Use a "Silent" exporter (No UI needed for testing)
        VirtualNetwork network = new VirtualNetwork(context, loop, json -> {});
        VirtualDisk disk = new VirtualDisk(context, loop);

        BankNode nodeA = new BankNode("NodeA", 500, context, loop, network, disk);
        BankNode nodeB = new BankNode("NodeB", 100, context, loop, network, disk);
        Coordinator coord = new Coordinator(context, network, json -> {});

        network.registerNode(nodeA);
        network.registerNode(nodeB);
        network.registerNode(coord);

        // Schedule transactions
        loop.schedule(new Event(0) {
            public void execute() {
                for(int i=0; i<10; i++)
                    coord.initiateTransfer(new Transaction("TX-"+i, "NodeA", "NodeB", 10));
            }
        });

        loop.run();

        // ASSERTION: This is where we mathematically prove the system integrity
        // If money is lost, the test fails, and the seed is captured by the JUnit report.
        assertEquals(600, nodeA.getBalance() + nodeB.getBalance(),
                "Invariant Failed on Seed: " + randomSeed);
    }
}