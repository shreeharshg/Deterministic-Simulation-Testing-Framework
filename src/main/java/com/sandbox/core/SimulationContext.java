package com.sandbox.core;

import java.util.Random;

public class SimulationContext {
    private long virtualTime;
    private final Random prng;
    private final long seed;

    public SimulationContext(long seed) {
        this.seed = seed;
        this.prng = new Random(seed);
        this.virtualTime = 0; // The universe starts at time 0
    }

    public long getVirtualTime() {
        return virtualTime;
    }

    // Time only moves forward. Bypasses the OS completely.
    public void advanceTime(long targetTime) {
        if (targetTime < this.virtualTime) {
            throw new IllegalArgumentException("Time violation: Cannot move backward from "
                    + virtualTime + " to " + targetTime);
        }
        this.virtualTime = targetTime;
    }

    // Chaos generators tied strictly to our Seed
    public double randomDouble() {
        return prng.nextDouble();
    }

    public int randomInt(int min, int max) {
        return prng.nextInt((max - min) + 1) + min;
    }

    public long getSeed() {
        return seed;
    }
}