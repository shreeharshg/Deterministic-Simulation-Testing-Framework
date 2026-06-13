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

    public WriteAheadLog(String nodeId) {
        // Creates a file like "Node_A_wal.dat" in your project folder
        this.filePath = Path.of(nodeId + "_wal.dat");

        // Clear the log on startup so our tests are perfectly reproducible
        try {
            Files.deleteIfExists(filePath);
        } catch (IOException e) {
            throw new RuntimeException("Failed to clear old WAL", e);
        }
    }

    // 1. WRITE: Appends a string to the end of the file incredibly fast
    public void appendEntry(String entry) {
        String line = entry + "\n";
        byte[] data = line.getBytes();
        ByteBuffer buffer = ByteBuffer.wrap(data);

        try (FileChannel channel = FileChannel.open(filePath, StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
            channel.write(buffer);
        } catch (IOException e) {
            throw new RuntimeException("Critical Disk Failure!", e);
        }
    }

    // 2. READ: Used only when a node crashes and needs to recover its memory
    public List<String> readAllEntries() {
        if (!Files.exists(filePath)) return new ArrayList<>();

        try {
            return Files.readAllLines(filePath);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read WAL during recovery", e);
        }
    }
}