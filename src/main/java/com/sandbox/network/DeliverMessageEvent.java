package com.sandbox.network;

import com.sandbox.core.Event;

public class DeliverMessageEvent extends Event {
    private final Node destinationNode;
    private final Message message;

    public DeliverMessageEvent(long deliveryTime, Node destinationNode, Message message) {
        super(deliveryTime);
        this.destinationNode = destinationNode;
        this.message = message;
    }

    @Override
    public void execute() {
        // When the Virtual Time reaches 'deliveryTime', this method fires.
        // We hand the payload to the receiving node.
        destinationNode.onMessageReceived(message.from(), message.payload());
    }
}