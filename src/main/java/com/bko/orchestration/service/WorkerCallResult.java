package com.bko.orchestration.service;

import org.springframework.lang.Nullable;

public record WorkerCallResult(String output, ToolCallAudit audit,
                              @Nullable Integer inputTokens, @Nullable Integer outputTokens) {
    public WorkerCallResult(String output, ToolCallAudit audit) {
        this(output, audit, null, null);
    }

    public int toolCallCount() {
        return audit == null ? 0 : audit.count();
    }

    public int writeCallCount() {
        return audit == null ? 0 : audit.writeCount();
    }
}
