package com.bko.stream;

import java.time.Instant;

public record StreamEvent(
        long id,
        Instant timestamp,
        String type,
        Object data
) {
}
