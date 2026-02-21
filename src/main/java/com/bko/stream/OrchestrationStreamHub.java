package com.bko.stream;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class OrchestrationStreamHub {
    private static final Logger log = LoggerFactory.getLogger(OrchestrationStreamHub.class);
    private static final int MAX_BUFFER_SIZE = 500;
    private static final long CLEANUP_TTL_MS = 30 * 60 * 1000L;

    private final ObjectMapper objectMapper;
    private final Map<String, StreamRun> runs = new ConcurrentHashMap<>();

    public OrchestrationStreamHub(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String createRun() {
        cleanupExpiredRuns();
        String runId = UUID.randomUUID().toString();
        runs.put(runId, new StreamRun(runId));
        return runId;
    }

    public void registerSession(String runId, WebSocketSession session, long sinceId) throws IOException {
        StreamRun run = runs.get(runId);
        if (run == null) {
            session.close();
            return;
        }
        cleanupExpiredRuns();
        run.sessions().put(session.getId(), session);
        session.getAttributes().put("runId", runId);
        for (StreamEvent event : run.snapshotSince(sinceId)) {
            send(session, event);
        }
    }

    public void removeSession(WebSocketSession session) {
        Object runIdObj = session.getAttributes().get("runId");
        if (runIdObj == null) {
            return;
        }
        String runId = runIdObj.toString();
        StreamRun run = runs.get(runId);
        if (run == null) {
            return;
        }
        run.sessions().remove(session.getId());
        pruneIfComplete(run);
    }

    public void emit(String runId, String type, Object data) {
        StreamRun run = runs.get(runId);
        if (run == null) {
            return;
        }
        if (run.cancelled() && !isCancelTerminal(type)) {
            return;
        }
        StreamEvent event = run.addEvent(type, data, MAX_BUFFER_SIZE);
        run.sessions().values().forEach(session -> send(session, event));
        if ("run-complete".equals(type) || "error".equals(type)) {
            run.markCompleted();
            pruneIfComplete(run);
        }
    }

    public boolean cancelRun(String runId) {
        StreamRun run = runs.get(runId);
        if (run == null) {
            return false;
        }
        if (run.cancelled()) {
            return true;
        }
        run.markCancelled();
        StreamEvent event = run.addEvent("run-cancel", Map.of(), MAX_BUFFER_SIZE);
        run.sessions().values().forEach(session -> send(session, event));
        return true;
    }

    public boolean isCancelled(String runId) {
        StreamRun run = runs.get(runId);
        return run != null && run.cancelled();
    }

    private boolean isCancelTerminal(String type) {
        return "run-cancel".equals(type) || "run-complete".equals(type) || "error".equals(type);
    }

    private void send(WebSocketSession session, StreamEvent event) {
        if (!session.isOpen()) {
            return;
        }
        try {
            String payload = objectMapper.writeValueAsString(event);
            synchronized (session) {
                session.sendMessage(new TextMessage(payload));
            }
        } catch (IOException ex) {
            log.debug("Failed to send stream event: {}", ex.getMessage());
        }
    }

    private void pruneIfComplete(StreamRun run) {
        if (!run.completed() || !run.sessions().isEmpty()) {
            return;
        }
        long cutoff = System.currentTimeMillis() - CLEANUP_TTL_MS;
        if (run.lastUpdated().toEpochMilli() < cutoff) {
            runs.remove(run.runId());
        }
    }

    private void cleanupExpiredRuns() {
        long cutoff = System.currentTimeMillis() - CLEANUP_TTL_MS;
        runs.values().removeIf(run -> run.completed()
                && run.sessions().isEmpty()
                && run.lastUpdated().toEpochMilli() < cutoff);
    }
}
