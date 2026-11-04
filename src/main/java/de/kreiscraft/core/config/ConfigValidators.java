package de.kreiscraft.core.config;

import java.net.URI;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.spongepowered.configurate.serialize.SerializationException;

/**
 * Ready-made {@link ConfigValidator} factories for the field types the Server Configuration GUI
 * exposes.
 *
 * <p>Every factory returns a validator that only reads the detached candidate tree, so a rejected
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

    /** Applies numeric bounds and requires two sibling settings to remain in ascending order. */
    public static ConfigValidator orderedNumbers(
            ConfigValidator bounds, String lowerKey, double lowerDefault,
            String upperKey, double upperDefault, boolean strictlyIncreasing) {
        return value -> {
            Optional<String> invalid = bounds.validate(value);
            if (invalid.isPresent()) {
                return invalid;
            }
            if (value.parent() == null) {
                return Optional.of("must belong to a configuration section");
            }
            double lower = value.parent().node(lowerKey).getDouble(lowerDefault);
            double upper = value.parent().node(upperKey).getDouble(upperDefault);
            if (!Double.isFinite(lower) || !Double.isFinite(upper)
                    || (strictlyIncreasing ? lower >= upper : lower > upper)) {
                return Optional.of(lowerKey + " must be "
                        + (strictlyIncreasing ? "less than " : "at most ") + upperKey);
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
        List<String> names = Arrays.stream(type.getEnumConstants()).map(Enum::name).toList();
        return new ChoiceValidator() {
            @Override
            public List<String> options() {
                return names;
            }

            @Override
            public Optional<String> validate(
                    org.spongepowered.configurate.ConfigurationNode value) {
                String name = value.getString();
                if (name != null) {
                    for (String option : names) {
                        if (option.equalsIgnoreCase(name)) {
                            return Optional.empty();
                        }
                    }
                }
                return Optional.of("must be one of " + names);
            }
        };
    }

    /** Requires a string value matching one of {@code allowed}, ignoring case. */
    public static ConfigValidator oneOf(String... allowed) {
        List<String> values = List.of(allowed);
        return new ChoiceValidator() {
            @Override
            public List<String> options() {
                return values;
            }

            @Override
            public Optional<String> validate(
                    org.spongepowered.configurate.ConfigurationNode value) {
                String name = value.getString();
                if (name != null
                        && values.stream()
                                .anyMatch(option -> option.equalsIgnoreCase(name))) {
                    return Optional.empty();
                }
                return Optional.of("must be one of " + values);
            }
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

    /** Requires a string that parses as an absolute or relative {@link URI}. */
    public static ConfigValidator uri() {
        return value -> {
            String text = value.getString();
            if (text == null || text.isBlank()) {
                return Optional.of("must be a URL");
            }
            try {
                URI.create(text.trim());
                return Optional.empty();
            } catch (IllegalArgumentException e) {
                return Optional.of("must be a URL");
            }
        };
    }

    /** Requires a string that MiniMessage can parse without throwing. */
    public static ConfigValidator miniMessage() {
        return value -> {
            String text = value.getString();
            if (text == null) {
                return Optional.of("must not be empty");
            }
            try {
                MiniMessage.miniMessage().deserialize(text);
                return Optional.empty();
            } catch (RuntimeException e) {
                return Optional.of("must be valid MiniMessage");
            }
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

    /** Requires a list of strings which can be parsed to integers. */
    public static ConfigValidator intList() {
        return value -> {
            if (!value.isList()) {
                return Optional.of("must be a list of integers");
            }
            try {
                List<String> list = value.getList(String.class);
                if (list == null) {
                    return Optional.of("must be a list of integers");
                }
                if (list.contains(null)) {
                    return Optional.of("must not contain empty entries");
                }
                for (String item : list) {
                    try {
                        Integer.parseInt(item);
                    } catch (NumberFormatException e) {
                        return Optional.of("all entries must be valid integers");
                    }
                }
                return Optional.empty();
            } catch (SerializationException e) {
                return Optional.of("must be a list of integers");
            }
        };
    }
}
