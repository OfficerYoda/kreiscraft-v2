package de.kreiscraft.core.config;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.spongepowered.configurate.serialize.SerializationException;

/**
 * Ready-made {@link ConfigValidator} factories for the field types the Server Configuration GUI
 * exposes.
 *
 * <p>Every factory returns a validator that only reads the detached candidate value, so a rejected
 * edit never mutates live state. Error messages are operator-facing and intentionally terse; the
 * GUI wraps them in localized framing.
 */
public final class ConfigValidators {

    private ConfigValidators() {}

    /** Accepts any value, for fields whose correctness cannot be checked in isolation. */
    public static ConfigValidator alwaysValid() {
        return value -> Optional.empty();
    }

    /** Requires a literal boolean, rejecting coercible strings such as {@code "yes"}. */
    public static ConfigValidator booleanValue() {
        return value -> value.raw() instanceof Boolean
                ? Optional.empty()
                : Optional.of("must be a boolean");
    }

    /** Requires a whole number within an inclusive range. */
    public static ConfigValidator integerRange(long min, long max) {
        return value -> {
            if (!(value.raw() instanceof Number number)) {
                return Optional.of("must be a whole number");
            }
            long asLong = number.longValue();
            if (number.doubleValue() != (double) asLong) {
                return Optional.of("must be a whole number");
            }
            if (asLong < min || asLong > max) {
                return Optional.of("must be between " + min + " and " + max);
            }
            return Optional.empty();
        };
    }

    /** Requires a finite number within an inclusive range. */
    public static ConfigValidator decimalRange(double min, double max) {
        return value -> {
            if (!(value.raw() instanceof Number number)) {
                return Optional.of("must be a number");
            }
            double asDouble = number.doubleValue();
            if (!Double.isFinite(asDouble) || asDouble < min || asDouble > max) {
                return Optional.of("must be between " + min + " and " + max);
            }
            return Optional.empty();
        };
    }

    /** Requires a value the duration serializer can parse. */
    public static ConfigValidator duration() {
        return value -> {
            try {
                return value.get(Duration.class) != null
                        ? Optional.empty()
                        : Optional.of("must be a duration");
            } catch (SerializationException e) {
                return Optional.of("must be a duration");
            }
        };
    }

    /** Requires a name matching one of the constants of {@code type}, ignoring case. */
    public static <E extends Enum<E>> ConfigValidator enumValue(Class<E> type) {
        return value -> {
            String name = value.getString();
            if (name != null) {
                for (E constant : type.getEnumConstants()) {
                    if (constant.name().equalsIgnoreCase(name)) {
                        return Optional.empty();
                    }
                }
            }
            return Optional.of("must be one of " + Arrays.toString(type.getEnumConstants()));
        };
    }

    /** Requires a non-blank string. */
    public static ConfigValidator nonEmptyString() {
        return value -> {
            String text = value.getString();
            return text != null && !text.isBlank()
                    ? Optional.empty()
                    : Optional.of("must not be empty");
        };
    }

    /** Requires a list of non-null strings. */
    public static ConfigValidator stringList() {
        return value -> {
            if (!value.isList()) {
                return Optional.of("must be a list of strings");
            }
            try {
                List<String> list = value.getList(String.class);
                if (list == null) {
                    return Optional.of("must be a list of strings");
                }
                return list.contains(null)
                        ? Optional.of("must not contain empty entries")
                        : Optional.empty();
            } catch (SerializationException e) {
                return Optional.of("must be a list of strings");
            }
        };
    }
}
