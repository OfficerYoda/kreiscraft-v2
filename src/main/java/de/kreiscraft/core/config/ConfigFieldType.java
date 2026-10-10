package de.kreiscraft.core.config;

/**
 * The data shape of an editable Server Configuration field.
 *
 * <p>The type drives which control the Server Configuration GUI presents for a field. It is
 * deliberately independent of the Java type used in a module's {@code @ConfigSerializable}
 * record: a {@code DURATION} field, for example, is backed by a {@link java.time.Duration} but is
 * edited as a duration string.
 */
public enum ConfigFieldType {
    /** True/false toggle. */
    BOOLEAN,
    /** Whole number. */
    INTEGER,
    /** Decimal number. */
    DOUBLE,
    /** Duration string such as {@code 5s} or {@code 20t}. */
    DURATION,
    /** One value from a fixed enum set. */
    ENUM,
    /** A Bukkit material name. */
    MATERIAL,
    /** A world position. */
    LOCATION,
    /** Free-form text. */
    STRING,
    /** A list of text values. */
    STRING_LIST,
    /** A list of integer values. */
    INT_LIST
}
