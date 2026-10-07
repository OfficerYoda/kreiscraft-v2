package de.kreiscraft.core.config;

import java.util.Optional;
import org.spongepowered.configurate.ConfigurationNode;

/**
 * Validates a candidate value for one editable Server Configuration field.
 *
 * <p>The candidate node is detached from the live configuration: it lives in a copy of the
 * configuration tree that is only kept if the validator accepts it. Returning a non-empty message
 * rejects the edit before either the live tree or the durable file is touched.
 */
@FunctionalInterface
public interface ConfigValidator {

    /**
     * Validates a candidate value.
     *
     * @param candidateValue the node holding the proposed value
     * @return an operator-facing reason when the value is rejected, or empty when it is valid
     */
    Optional<String> validate(ConfigurationNode candidateValue);
}
