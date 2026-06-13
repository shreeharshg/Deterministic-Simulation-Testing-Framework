package com.sandbox.ledger;

import com.google.protobuf.InvalidProtocolBufferException;
import com.sandbox.core.SimulationContext;
import com.sandbox.ledger.proto.LedgerMessages.NetworkEnvelope; // IMPORT PROTOBUF
import com.sandbox.network.Node;
import com.sandbox.network.VirtualNetwork;

import java.util.HashMap;
import java.util.Map;

public class Coordinator implements Node {
    private final String id = "Coordinator";
    private final SimulationContext context;
    private final VirtualNetwork network;

    private final Map<String, Integer> preparedVotes = new HashMap<>();
    private final Map<String, Transaction> pendingTxs = new HashMap<>();

    public Coordinator(SimulationContext context, VirtualNetwork network) {
        this.context = context;
        this.network = network;
    }

    @Override
    public String getId() {
        return id;
    }

    public void initiateTransfer(Transaction tx) {
        System.out.println("\n[" + context.getVirtualTime() + "ms] 🚀 Coordinator starting transfer of $" + tx.amount() + " from " + tx.fromNode() + " to " + tx.toNode());

        pendingTxs.put(tx.transactionId(), tx);
        preparedVotes.put(tx.transactionId(), 0);

        // CREATE STRICT PROTOBUF MESSAGES
        NetworkEnvelope prepareSenderMsg = NetworkEnvelope.newBuilder()
                .setCommand("PREPARE_SENDER")
                .setTransactionId(tx.transactionId())
                .setAmount(tx.amount())
                .setCoordinatorId(id)
                .build();

        NetworkEnvelope prepareReceiverMsg = NetworkEnvelope.newBuilder()
                .setCommand("PREPARE_RECEIVER")
                .setTransactionId(tx.transactionId())
                .setCoordinatorId(id)
                .build();

        // Convert the objects to byte[] arrays and send over the network
        network.send(id, tx.fromNode(), prepareSenderMsg.toByteArray());
        network.send(id, tx.toNode(), prepareReceiverMsg.toByteArray());
    }

    @Override
    public void onMessageReceived(String from, byte[] payload) {
        try {
            System.out.println("[Time: " + context.getVirtualTime() + "ms] 📦 Coordinator received raw bytes: " + java.util.Arrays.toString(payload));
            System.out.println("   └─ Deserializing Protobuf to Object...");
            // DESERIALIZE the raw bytes back into a Protobuf object
            NetworkEnvelope msg = NetworkEnvelope.parseFrom(payload);
            String command = msg.getCommand();
            String txId = msg.getTransactionId();

            if (command.equals("ABORT")) {
                System.out.println("[" + context.getVirtualTime() + "ms] 🛑 Coordinator aborting Tx: " + txId);
                rollback(txId);
                return;
            }

            if (command.equals("PREPARED")) {
                int votes = preparedVotes.get(txId) + 1;
                preparedVotes.put(txId, votes);

                if (votes == 2) commit(txId);
            }
        } catch (InvalidProtocolBufferException e) {
            System.err.println("Failed to parse incoming message: " + e.getMessage());
        }
    }

    private void commit(String txId) {
        Transaction tx = pendingTxs.get(txId);
        System.out.println("[" + context.getVirtualTime() + "ms] 🤝 Coordinator sending COMMIT...");

        NetworkEnvelope commitSenderMsg = NetworkEnvelope.newBuilder()
                .setCommand("COMMIT_SENDER").setTransactionId(txId).build();

        NetworkEnvelope commitReceiverMsg = NetworkEnvelope.newBuilder()
                .setCommand("COMMIT_RECEIVER").setTransactionId(txId).setAmount(tx.amount()).build();

        network.send(id, tx.fromNode(), commitSenderMsg.toByteArray());
        network.send(id, tx.toNode(), commitReceiverMsg.toByteArray());
    }

    private void rollback(String txId) {
        Transaction tx = pendingTxs.get(txId);
        NetworkEnvelope rollbackMsg = NetworkEnvelope.newBuilder()
                .setCommand("ROLLBACK").setTransactionId(txId).build();

        network.send(id, tx.fromNode(), rollbackMsg.toByteArray());
        network.send(id, tx.toNode(), rollbackMsg.toByteArray());
    }
}