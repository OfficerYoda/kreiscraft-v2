package de.kreiscraft.core.config;

import java.util.Objects;

/**
 * A requested change to one Server Configuration field.
 *
 * <p>The edit is a proposal: {@link ConfigService#edit(ConfigEdit)} validates the value against a
 * detached candidate tree and only keeps the result if the value is valid and durably saved. A
 * rejected or failed edit leaves both the live configuration and the file on disk untouched.
 *
 * @param field the field metadata, including the path and validator
 * @param value the proposed value in the field's Java representation
 */
public record ConfigEdit(ConfigField field, Object value) {

    public ConfigEdit {
        Objects.requireNonNull(field, "field");
    }
}
