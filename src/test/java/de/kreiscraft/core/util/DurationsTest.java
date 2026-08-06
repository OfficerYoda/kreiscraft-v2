package de.kreiscraft.core.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class DurationsTest {

    @Test
    void parse_ticks() {
        assertThat(Durations.parse("20t")).isEqualTo(Duration.ofMillis(1000));
        assertThat(Durations.parse("1t")).isEqualTo(Duration.ofMillis(50));
    }

    @Test
    void parse_milliseconds() {
        assertThat(Durations.parse("500ms")).isEqualTo(Duration.ofMillis(500));
    }

    @Test
    void parse_seconds() {
        assertThat(Durations.parse("30s")).isEqualTo(Duration.ofSeconds(30));
    }

    @Test
    void parse_minutes() {
        assertThat(Durations.parse("5m")).isEqualTo(Duration.ofMinutes(5));
    }

    @Test
    void parse_hours() {
        assertThat(Durations.parse("2h")).isEqualTo(Duration.ofHours(2));
    }

    @Test
    void parse_days() {
        assertThat(Durations.parse("7d")).isEqualTo(Duration.ofDays(7));
        assertThat(Durations.parse("30d")).isEqualTo(Duration.ofDays(30));
    }

    @Test
    void parse_isCaseInsensitiveAndTrimsWhitespace() {
        assertThat(Durations.parse("  5M ")).isEqualTo(Duration.ofMinutes(5));
    }

    @Test
    void parse_rejectsBadInput() {
        assertThatThrownBy(() -> Durations.parse("banana"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Durations.parse("5x"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Durations.parse(""))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void format_roundTrip() {
        assertThat(Durations.format(Duration.ofSeconds(30))).isEqualTo("30s");
        assertThat(Durations.format(Duration.ofMinutes(5))).isEqualTo("5m");
        assertThat(Durations.format(Duration.ofMillis(500))).isEqualTo("500ms");
        assertThat(Durations.format(Duration.ofDays(7))).isEqualTo("7d");
    }
}
