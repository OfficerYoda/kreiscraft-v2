package de.kreiscraft.core.util;

import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Durations {

    private static final Pattern PATTERN = Pattern.compile(
            "^\\s*(\\d+)\\s*(ms|t|s|m|h)\\s*$",
            Pattern.CASE_INSENSITIVE);

    private Durations() {}

    public static Duration parse(String input) {
        if (input == null) {
            throw new IllegalArgumentException("duration is null");
        }
        Matcher m = PATTERN.matcher(input);
        if (!m.matches()) {
            throw new IllegalArgumentException("unparseable duration: '" + input + "'");
        }
        long value = Long.parseLong(m.group(1));
        return switch (m.group(2).toLowerCase()) {
            case "ms" -> Duration.ofMillis(value);
            case "t" -> Duration.ofMillis(value * 50L);
            case "s" -> Duration.ofSeconds(value);
            case "m" -> Duration.ofMinutes(value);
            case "h" -> Duration.ofHours(value);
            default -> throw new IllegalStateException();
        };
    }

    public static String format(Duration duration) {
        long millis = duration.toMillis();
        if (millis % 3_600_000 == 0)
            return (millis / 3_600_000) + "h";
        if (millis % 60_000 == 0)
            return (millis / 60_000) + "m";
        if (millis % 1_000 == 0)
            return (millis / 1_000) + "s";
        return millis + "ms";
    }
}
