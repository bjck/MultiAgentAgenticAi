package com.bko.orchestration.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * MVP implementation that converts a few common human-friendly schedule phrases
 * into cron expressions. This can later be upgraded to use an LLM-backed interpreter.
 */
@Component
@Slf4j
public class SchedulePatternInterpreter {

    private static final String HOURLY_CRON = "0 0 * * * *";

    public String interpret(String raw) {
        if (raw == null || raw.isBlank()) {
            // Default: run every hour
            return HOURLY_CRON;
        }
        String lower = raw.trim().toLowerCase();
        if ("every hour".equals(lower) || "hourly".equals(lower)) {
            return HOURLY_CRON;
        }
        if ("every day".equals(lower) || "daily".equals(lower)) {
            return "0 0 0 * * *";
        }
        if ("every 5 minutes".equals(lower)) {
            return "0 */5 * * * *";
        }
        if ("every 15 minutes".equals(lower)) {
            return "0 */15 * * * *";
        }
        if ("every 30 minutes".equals(lower)) {
            return "0 */30 * * * *";
        }
        // If the user already provides a cron expression (with 6 or 7 space-separated fields), trust it.
        if (looksLikeCron(raw)) {
            return raw.trim();
        }
        log.warn("Unrecognized schedule pattern '{}', defaulting to hourly.", raw);
        return HOURLY_CRON;
    }

    public java.time.OffsetDateTime computeInitialNextRun(String expression, OffsetDateTime from) {
        try {
            CronExpression cron = CronExpression.parse(expression);
            var next = cron.next(from.toZonedDateTime());
            return next != null ? next.toOffsetDateTime() : from.plusHours(1);
        } catch (Exception ex) {
            log.warn("Failed to compute next run for cron '{}', defaulting to +1h.", expression, ex);
            return from.plusHours(1);
        }
    }

    private boolean looksLikeCron(String raw) {
        String[] parts = raw.trim().split("\\s+");
        return parts.length >= 5 && parts.length <= 7;
    }
}

