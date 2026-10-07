package de.kreiscraft.shared.preferences;

import java.util.Optional;

/**
 * Canonical language values a player may persist in their preferences.
 *
 * <p>The persisted form is the lower-case {@link #code() code} ({@code de} or {@code en}), not the
 * enum constant name. Unrecognised codes resolve to {@link Optional#empty()} rather than throwing so
 * that malformed stored values can be treated as an absent preference.
 */
public enum PlayerLanguage {
    DE("de"), EN("en");

    private final String code;

    PlayerLanguage(String code) {
        this.code = code;
    }

    /** The canonical lower-case value written to YAML. */
    public String code() {
        return code;
    }

    /** Resolves a canonical code, case-insensitively; empty when {@code code} is null or unknown. */
    public static Optional<PlayerLanguage> fromCode(String code) {
        if (code == null) {
            return Optional.empty();
        }
        for (PlayerLanguage value : values()) {
            if (value.code.equalsIgnoreCase(code)) {
                return Optional.of(value);
            }
        }
        return Optional.empty();
    }
}
