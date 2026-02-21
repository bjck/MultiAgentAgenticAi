package com.bko.orchestration.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.Nullable;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
public final class ToolCallAudit {

    private static final int MAX_SNIPPET = 2000;

    private final AtomicLong count = new AtomicLong();
    private final List<ToolCallRecord> calls = Collections.synchronizedList(new ArrayList<>());
    private final String role;
    private final String taskId;

    public ToolCallAudit(@Nullable String role, @Nullable String taskId) {
        this.role = role;
        this.taskId = taskId;
    }

    void recordCall(@Nullable String name, @Nullable String input, @Nullable String output) {
        count.incrementAndGet();
        String safeName = StringUtils.hasText(name) ? name : "unknown";
        ToolCallRecord record = new ToolCallRecord(safeName, truncate(input), truncate(output));
        calls.add(record);
        log.info("Tool call: name={}, role={}, taskId={}, inputSnippet={}", safeName, role, taskId, truncate(input));
    }

    int count() {
        long value = count.get();
        return value > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) value;
    }

    int writeCount() {
        int total = 0;
        for (ToolCallRecord record : snapshot()) {
            String name = record.name();
            if (!StringUtils.hasText(name)) {
                continue;
            }
            String normalized = name.toLowerCase(Locale.ROOT);
            if ("write_file".equals(normalized)
                    || normalized.endsWith(".write_file")
                    || normalized.endsWith("/write_file")
                    || normalized.endsWith(":write_file")) {
                total++;
            }
        }
        return total;
    }

    List<ToolCallRecord> snapshot() {
        synchronized (calls) {
            return new ArrayList<>(calls);
        }
    }

    private String truncate(@Nullable String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String normalized = value.replace("\r", " ").replace("\n", " ").trim();
        if (normalized.length() <= MAX_SNIPPET) {
            return normalized;
        }
        return normalized.substring(0, MAX_SNIPPET) + "...";
    }
}
