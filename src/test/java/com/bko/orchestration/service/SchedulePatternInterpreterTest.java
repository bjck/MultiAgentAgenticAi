package com.bko.orchestration.service;

import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class SchedulePatternInterpreterTest {

    private final SchedulePatternInterpreter interpreter = new SchedulePatternInterpreter();

    @Test
    void interpretDefaultsToHourlyWhenBlank() {
        assertEquals("0 0 * * * *", interpreter.interpret(null));
        assertEquals("0 0 * * * *", interpreter.interpret("   "));
    }

    @Test
    void interpretRecognizesCommonPhrases() {
        assertEquals("0 0 * * * *", interpreter.interpret("every hour"));
        assertEquals("0 0 * * * *", interpreter.interpret("HoUrLy"));
        assertEquals("0 0 0 * * *", interpreter.interpret("daily"));
        assertEquals("0 */5 * * * *", interpreter.interpret("every 5 minutes"));
        assertEquals("0 */15 * * * *", interpreter.interpret("every 15 minutes"));
        assertEquals("0 */30 * * * *", interpreter.interpret("every 30 minutes"));
    }

    @Test
    void interpretPassesThroughCronExpressions() {
        String cron = "0 0 12 * * *";
        assertEquals(cron, interpreter.interpret(cron));
    }

    @Test
    void computeInitialNextRunProducesFutureTime() {
        OffsetDateTime now = OffsetDateTime.of(2024, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC);
        OffsetDateTime next = interpreter.computeInitialNextRun("0 0 * * * *", now);
        assertNotNull(next);
        // Next run should be after 'now'
        org.junit.jupiter.api.Assertions.assertTrue(next.isAfter(now));
    }
}

