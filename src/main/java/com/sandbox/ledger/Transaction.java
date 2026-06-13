package com.sandbox.ledger;

public record Transaction(String transactionId, String fromNode, String toNode, int amount) {}