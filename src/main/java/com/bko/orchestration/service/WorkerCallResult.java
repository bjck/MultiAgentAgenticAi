package com.bko.orchestration.service;

public record WorkerCallResult(String output, ToolCallAudit audit) {
    public int toolCallCount() {
        return audit == null ? 0 : audit.count();
    }

    public int writeCallCount() {
        return audit == null ? 0 : audit.writeCount();
    }
}
