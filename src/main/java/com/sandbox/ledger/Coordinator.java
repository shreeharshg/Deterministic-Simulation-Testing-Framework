package com.sandbox.ledger;

import com.google.protobuf.InvalidProtocolBufferException;
import com.sandbox.core.SimulationContext;
import com.sandbox.ledger.proto.LedgerMessages.NetworkEnvelope;
import com.sandbox.network.Node;
import com.sandbox.network.VirtualNetwork;
import com.sandbox.telemetry.TelemetryExporter;

import java.util.HashMap;
import java.util.Map;

public class Coordinator implements Node {
    private final String id = "Coordinator";
    private final SimulationContext context;
    private final VirtualNetwork network;
    private final TelemetryExporter telemetry; // <-- NEW

    private final Map<String, Integer> preparedVotes = new HashMap<>();
    private final Map<String, Transaction> pendingTxs = new HashMap<>();

    // NEW CONSTRUCTOR
    public Coordinator(SimulationContext context, VirtualNetwork network, TelemetryExporter telemetry) {
        this.context = context;
        this.network = network;
        this.telemetry = telemetry;
    }

    @Override
    public String getId() { return id; }

    public void initiateTransfer(Transaction tx) {
        pendingTxs.put(tx.transactionId(), tx);
        preparedVotes.put(tx.transactionId(), 0);

        NetworkEnvelope prepareSenderMsg = NetworkEnvelope.newBuilder()
                .setCommand("PREPARE_SENDER").setTransactionId(tx.transactionId())
                .setAmount(tx.amount()).setCoordinatorId(id).build();

        NetworkEnvelope prepareReceiverMsg = NetworkEnvelope.newBuilder()
                .setCommand("PREPARE_RECEIVER").setTransactionId(tx.transactionId())
                .setCoordinatorId(id).build();

        network.send(id, tx.fromNode(), prepareSenderMsg.toByteArray());
        network.send(id, tx.toNode(), prepareReceiverMsg.toByteArray());
    }

    @Override
    public void onMessageReceived(String from, byte[] payload) {
        try {
            NetworkEnvelope msg = NetworkEnvelope.parseFrom(payload);
            String command = msg.getCommand();
            String txId = msg.getTransactionId();

            if (command.equals("ABORT")) {
                rollback(txId);
                return;
            }

            if (command.equals("PREPARED")) {
                int votes = preparedVotes.get(txId) + 1;
                preparedVotes.put(txId, votes);
                if (votes == 2) commit(txId);
            }
        } catch (InvalidProtocolBufferException e) {
            System.err.println("Failed to parse incoming message.");
        }
    }

    private void commit(String txId) {
        Transaction tx = pendingTxs.get(txId);

        // --- SEND SUCCESS LOG TO UI ---
        telemetry.export("{\"type\": \"TX_SUCCESS\", \"txId\": \"" + txId + "\"}");

        NetworkEnvelope commitSenderMsg = NetworkEnvelope.newBuilder()
                .setCommand("COMMIT_SENDER").setTransactionId(txId).build();
        NetworkEnvelope commitReceiverMsg = NetworkEnvelope.newBuilder()
                .setCommand("COMMIT_RECEIVER").setTransactionId(txId).setAmount(tx.amount()).build();

        network.send(id, tx.fromNode(), commitSenderMsg.toByteArray());
        network.send(id, tx.toNode(), commitReceiverMsg.toByteArray());
    }

    private void rollback(String txId) {
        Transaction tx = pendingTxs.get(txId);

        // --- SEND FAILED LOG TO UI ---
        telemetry.export("{\"type\": \"TX_FAILED\", \"txId\": \"" + txId + "\"}");

        NetworkEnvelope rollbackMsg = NetworkEnvelope.newBuilder()
                .setCommand("ROLLBACK").setTransactionId(txId).build();

        network.send(id, tx.fromNode(), rollbackMsg.toByteArray());
        network.send(id, tx.toNode(), rollbackMsg.toByteArray());
    }
}