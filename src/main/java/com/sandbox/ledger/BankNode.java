package com.sandbox.ledger;

import com.google.protobuf.InvalidProtocolBufferException;
import com.sandbox.core.Event;
import com.sandbox.core.EventLoop;
import com.sandbox.core.SimulationContext;
import com.sandbox.ledger.proto.LedgerMessages.NetworkEnvelope; // IMPORT PROTOBUF
import com.sandbox.network.Node;
import com.sandbox.network.VirtualNetwork;
import com.sandbox.storage.VirtualDisk;
import com.sandbox.storage.WriteAheadLog;

public class BankNode implements Node {
    private final String nodeId;
    private final SimulationContext context;
    private final EventLoop eventLoop;
    private final VirtualNetwork network;
    private final VirtualDisk disk;
    private final WriteAheadLog wal;

    private int balance;
    private int lockedFunds = 0;

    public BankNode(String nodeId, int initialBalance, SimulationContext context, EventLoop loop, VirtualNetwork network, VirtualDisk disk) {
        this.nodeId = nodeId;
        this.balance = initialBalance;
        this.context = context;
        this.eventLoop = loop;
        this.network = network;
        this.disk = disk;
        this.wal = new WriteAheadLog(nodeId);
    }

    @Override
    public String getId() { return nodeId; }
    public int getBalance() { return balance; }

    public int getLockedFunds() { return lockedFunds; }
    @Override
    public void onMessageReceived(String from, byte[] payload) {
        try {
            System.out.println("[Time: " + context.getVirtualTime() + "ms] 📦 " + nodeId + " received raw bytes: " + java.util.Arrays.toString(payload));
            System.out.println("   └─ Deserializing Protobuf to Object...");
            // DESERIALIZE
            NetworkEnvelope msg = NetworkEnvelope.parseFrom(payload);

            switch (msg.getCommand()) {
                case "PREPARE_SENDER":
                    handlePrepareSender(msg.getTransactionId(), msg.getAmount(), msg.getCoordinatorId());
                    break;
                case "PREPARE_RECEIVER":
                    handlePrepareReceiver(msg.getTransactionId(), msg.getCoordinatorId());
                    break;
                case "COMMIT_SENDER":
                    handleCommitSender(msg.getTransactionId());
                    break;
                case "COMMIT_RECEIVER":
                    handleCommitReceiver(msg.getTransactionId(), msg.getAmount());
                    break;
                case "ROLLBACK":
                    handleRollback(msg.getTransactionId());
                    break;
            }
        } catch (InvalidProtocolBufferException e) {
            System.err.println("Node " + nodeId + " received corrupted bytes!");
        }
    }

    private void handlePrepareSender(String txId, int amount, String coordinatorId) {
        if (balance >= amount) {
            balance -= amount;
            lockedFunds += amount;

            disk.writeAsync(
                    () -> wal.appendEntry("LOCK:" + txId + ":" + amount),
                    () -> reply(coordinatorId, "PREPARED", txId)
            );

            // Timeout Logic (Unchanged)
            long timeoutTime = context.getVirtualTime() + 500;
            eventLoop.schedule(new Event(timeoutTime) {
                @Override
                public void execute() {
                    if (lockedFunds >= amount) {
                        System.out.println("[" + context.getVirtualTime() + "ms] ⏰ TIMEOUT on " + nodeId + ".");
                        handleRollback(txId);
                    }
                }
            });
        } else {
            reply(coordinatorId, "ABORT", txId);
        }
    }

    private void handlePrepareReceiver(String txId, String coordinatorId) {
        disk.writeAsync(
                () -> wal.appendEntry("PREPARE_RECEIVE:" + txId),
                () -> reply(coordinatorId, "PREPARED", txId)
        );
    }

    private void handleCommitSender(String txId) {
        lockedFunds = 0;
        disk.writeAsync(() -> wal.appendEntry("COMMIT_SEND:" + txId), () -> {});
    }

    private void handleCommitReceiver(String txId, int amount) {
        balance += amount;
        disk.writeAsync(() -> wal.appendEntry("COMMIT_RECEIVE:" + txId + ":" + amount), () -> {});
    }

    private void handleRollback(String txId) {
        if (lockedFunds > 0) {
            balance += lockedFunds;
            lockedFunds = 0;
            disk.writeAsync(() -> wal.appendEntry("ROLLBACK:" + txId), () -> {});
        }
    }

    // HELPER: Builds the Protobuf response
    private void reply(String toNode, String command, String txId) {
        NetworkEnvelope msg = NetworkEnvelope.newBuilder()
                .setCommand(command)
                .setTransactionId(txId)
                .build();
        network.send(nodeId, toNode, msg.toByteArray());
    }
}