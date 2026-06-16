package com.sandbox.telemetry;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.util.DaemonThreadFactory;
import com.sandbox.core.ChaosFuzzer;
import com.sandbox.core.Main;
import io.javalin.Javalin;
import io.javalin.websocket.WsContext;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class LmaxDisruptorExporter implements TelemetryExporter {
    private final Javalin app;
    private final Set<WsContext> connections = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final Disruptor<LogEvent> disruptor;
    private final RingBuffer<LogEvent> ringBuffer;
    private final ObjectMapper mapper = new ObjectMapper();

    public LmaxDisruptorExporter() {
        app = Javalin.create().start(7070);

        app.ws("/ws", ws -> {
            ws.onConnect(ctx -> {
                System.out.println("🌐 [LMAX] UI Connected!");
                connections.add(ctx);
            });
            ws.onClose(ctx -> connections.remove(ctx));

            ws.onMessage(ctx -> {
                try {
                    JsonNode message = mapper.readTree(ctx.message());
                    String action = message.get("action").asText();

                    if (action.equals("RUN_SINGLE")) {
                        long seed = message.get("seed").asLong();
                        double drop = message.has("dropRate") ? message.get("dropRate").asDouble() : 0.20;
                        int minLat = message.has("minLat") ? message.get("minLat").asInt() : 10;
                        int maxLat = message.has("maxLat") ? message.get("maxLat").asInt() : 100;
                        new Thread(() -> Main.runUIDemo(seed, drop, minLat, maxLat, this)).start();
                    }
                    else if (action.equals("RUN_FUZZER")) {
                        int universes = message.get("universes").asInt();
                        int tx = message.get("tx").asInt();
                        double drop = message.has("dropRate") ? message.get("dropRate").asDouble() : 0.20;
                        int minLat = message.has("minLat") ? message.get("minLat").asInt() : 10;
                        int maxLat = message.has("maxLat") ? message.get("maxLat").asInt() : 100;
                        new Thread(() -> ChaosFuzzer.runMassFuzzing(universes, tx, drop, minLat, maxLat, this)).start();
                    }
                    else if (action.equals("STOP_FUZZER")) {
                        ChaosFuzzer.stopFuzzing(this);
                    }
                } catch (Exception e) {
                    System.err.println("Failed to parse WS message.");
                }
            });
        });

        int bufferSize = 1024;
        disruptor = new Disruptor<>(
                LogEvent::new, bufferSize, DaemonThreadFactory.INSTANCE,
                com.lmax.disruptor.dsl.ProducerType.SINGLE,
                new com.lmax.disruptor.YieldingWaitStrategy()
        );

        disruptor.handleEventsWith((event, sequence, endOfBatch) -> {
            String json = event.getJsonPayload();
            try { Thread.sleep(200); } catch (InterruptedException e) {}
            for (WsContext ctx : connections) ctx.send(json);
        });

        ringBuffer = disruptor.start();
    }

    @Override
    public void export(String jsonEvent) {
        long sequence = ringBuffer.next();
        try {
            LogEvent event = ringBuffer.get(sequence);
            event.setJsonPayload(jsonEvent);
        } finally {
            ringBuffer.publish(sequence);
        }
    }
}