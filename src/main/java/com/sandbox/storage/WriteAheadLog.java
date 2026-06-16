package com.sandbox.storage;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

public class WriteAheadLog {
    private final Path filePath;
    private final String nodeId;

    // The "Genesis Hash" - the starting point for the chain
    private String lastHash = "0000000000000000000000000000000000000000000000000000000000000000";

    public WriteAheadLog(String nodeId) {
        this.nodeId = nodeId;
        this.filePath = Path.of(nodeId + "_wal.dat");

        try {
            Files.deleteIfExists(filePath); // Clear old logs on startup
        } catch (IOException e) {
            throw new RuntimeException("Failed to clear old WAL", e);
        }
    }

    // 1. WRITE: Generate a cryptographic chain and append to file
    public void appendEntry(String entryData) {
        // Compute the new hash based on the PREVIOUS hash + the NEW data
        String currentHash = HashUtil.generateSHA256(lastHash + entryData);

        // Format: DATA|HASH
        String line = entryData + "|" + currentHash + "\n";
        byte[] data = line.getBytes();
        ByteBuffer buffer = ByteBuffer.wrap(data);

        try (FileChannel channel = FileChannel.open(filePath, StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
            channel.write(buffer);
            lastHash = currentHash; // Update the chain
        } catch (IOException e) {
            throw new RuntimeException("Critical Disk Failure on Node " + nodeId, e);
        }
    }

    // 2. READ: Read from disk and mathematically verify data integrity
    public List<String> readAndVerify() {
        if (!Files.exists(filePath)) return new ArrayList<>();

        List<String> validEntries = new ArrayList<>();
        String runningHash = "0000000000000000000000000000000000000000000000000000000000000000";

        try {
            List<String> lines = Files.readAllLines(filePath);

            for (String line : lines) {
                String[] parts = line.split("\\|");
                if (parts.length != 2) throw new IllegalStateException("Corrupted WAL format!");

                String data = parts[0];
                String savedHash = parts[1];

                // Verify the math!
                String computedHash = HashUtil.generateSHA256(runningHash + data);
                if (!computedHash.equals(savedHash)) {
                    throw new IllegalStateException("🛑 TAMPER DETECTED! WAL Corruption on Node " + nodeId);
                }

                validEntries.add(data);
                runningHash = computedHash; // Advance the chain
            }
            return validEntries;
        } catch (IOException e) {
            throw new RuntimeException("Failed to read WAL", e);
        }
    }
}