package com.sandbox.telemetry;

import io.javalin.Javalin;
import io.javalin.websocket.WsContext;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;

public class DirectWebSocketExporter implements TelemetryExporter {
    private final Javalin app;
    private final Set<WsContext> connections = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final CountDownLatch connectionLatch; // <-- ADD THIS

    // Pass the latch into the constructor
    public DirectWebSocketExporter(CountDownLatch connectionLatch) {
        this.connectionLatch = connectionLatch;

        app = Javalin.create().start(7070);

        app.ws("/ws", ws -> {
            ws.onConnect(ctx -> {
                System.out.println("🌐 [API] Frontend Dashboard Connected!");
                connections.add(ctx);
                connectionLatch.countDown(); // <-- RELEASE THE HOUNDS!
            });

            ws.onClose(ctx -> {
                System.out.println("🌐 [API] Frontend Dashboard Disconnected.");
                connections.remove(ctx);
            });
        });
    }

    @Override
    public void export(String jsonEvent) {
        // We leave the 200ms delay here so the UI animation looks smooth
        try { Thread.sleep(2000); } catch (InterruptedException e) {}

        for (WsContext ctx : connections) {
            ctx.send(jsonEvent);
        }
    }
}