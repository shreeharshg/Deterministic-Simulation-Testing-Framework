package com.sandbox.telemetry;

import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.util.DaemonThreadFactory;
import io.javalin.Javalin;
import io.javalin.websocket.WsContext;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;

public class LmaxDisruptorExporter implements TelemetryExporter {
    private final Javalin app;
    private final Set<WsContext> connections = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final Disruptor<LogEvent> disruptor;
    private final RingBuffer<LogEvent> ringBuffer;

    public LmaxDisruptorExporter(CountDownLatch connectionLatch) {
        // 1. START WEBSOCKET API (Same as before)
        app = Javalin.create().start(7070);
        app.ws("/ws", ws -> {
            ws.onConnect(ctx -> {
                System.out.println("🌐 [LMAX-API] Frontend Dashboard Connected!");
                connections.add(ctx);
                connectionLatch.countDown(); // Start the simulation!
            });
            ws.onClose(ctx -> connections.remove(ctx));
        });

        // 2. INITIALIZE LMAX DISRUPTOR
        int bufferSize = 1024; // Must be a power of 2

        disruptor = new Disruptor<>(
                LogEvent::new,
                bufferSize,
                DaemonThreadFactory.INSTANCE
        );

        // 3. DEFINE THE BACKGROUND I/O THREAD
        // This runs independently of the main simulation thread!
        disruptor.handleEventsWith((event, sequence, endOfBatch) -> {
            String json = event.getJsonPayload();

            // We put the delay here, in the background thread.
            // The core simulation NEVER stops!
            try { Thread.sleep(300); } catch (InterruptedException e) {}

            for (WsContext ctx : connections) {
                ctx.send(json);
            }
        });

        ringBuffer = disruptor.start();
    }

    // 4. THE NON-BLOCKING PUBLISH METHOD
    // The main simulation thread calls this. It drops data and immediately leaves.
    @Override
    public void export(String jsonEvent) {
        long sequence = ringBuffer.next(); // Grab an empty cart
        try {
            LogEvent event = ringBuffer.get(sequence);
            event.setJsonPayload(jsonEvent); // Put data in cart
        } finally {
            ringBuffer.publish(sequence); // Push the cart
        }
    }
}