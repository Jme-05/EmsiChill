package me.jaime.emsichill.staff;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;

import org.junit.jupiter.api.Test;

class MuteDurationTest {
    @Test
    void acceptsSecondsMinutesHoursAndDays() {
        assertEquals(Duration.ofSeconds(30).toMillis(), MuteDuration.parse("30s").orElseThrow());
        assertEquals(Duration.ofMinutes(10).toMillis(), MuteDuration.parse("10m").orElseThrow());
        assertEquals(Duration.ofHours(2).toMillis(), MuteDuration.parse("2h").orElseThrow());
        assertEquals(Duration.ofDays(1).toMillis(), MuteDuration.parse("1d").orElseThrow());
    }

    @Test
    void treatsPlainNumbersAsSeconds() {
        assertEquals(Duration.ofSeconds(45).toMillis(), MuteDuration.parse("45").orElseThrow());
    }

    @Test
    void rejectsZeroMalformedAndExcessiveDurations() {
        assertTrue(MuteDuration.parse("0s").isEmpty());
        assertTrue(MuteDuration.parse("tomorrow").isEmpty());
        assertTrue(MuteDuration.parse("366d").isEmpty());
    }
}
