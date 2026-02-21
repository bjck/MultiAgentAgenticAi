package com.bko.stream;

import org.springframework.web.socket.WebSocketSession;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

class StreamRun {
    private final String runId;
    private final AtomicLong sequence = new AtomicLong();
    private final List<StreamEvent> buffer = new ArrayList<>();
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    private volatile boolean completed;
    private volatile boolean cancelled;
    private volatile Instant lastUpdated = Instant.now();

    StreamRun(String runId) {
        this.runId = runId;
    }

    String runId() {
        return runId;
    }

    Map<String, WebSocketSession> sessions() {
        return sessions;
    }

    synchronized StreamEvent addEvent(String type, Object data, int maxBufferSize) {
        StreamEvent event = new StreamEvent(sequence.incrementAndGet(), Instant.now(), type, data);
        buffer.add(event);
        if (buffer.size() > maxBufferSize) {
            buffer.remove(0);
        }
        lastUpdated = Instant.now();
        return event;
    }

    synchronized List<StreamEvent> snapshotSince(long sinceId) {
        return buffer.stream()
                .filter(event -> event.id() > sinceId)
                .toList();
    }

    boolean completed() {
        return completed;
    }

    boolean cancelled() {
        return cancelled;
    }

    void markCompleted() {
        completed = true;
        lastUpdated = Instant.now();
    }

    void markCancelled() {
        cancelled = true;
        lastUpdated = Instant.now();
    }

    Instant lastUpdated() {
        return lastUpdated;
    }
}
