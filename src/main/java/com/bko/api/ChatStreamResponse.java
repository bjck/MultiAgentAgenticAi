package com.bko.api;

import java.time.Instant;

public record ChatStreamResponse(
        String runId,
        Instant createdAt
) {
}
