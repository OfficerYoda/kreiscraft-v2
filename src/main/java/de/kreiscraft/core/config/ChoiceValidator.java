package de.kreiscraft.core.config;

import java.util.List;

/**
 * A {@link ConfigValidator} that accepts one of a fixed set of values.
 *
 * <p>Exposing the allowed values lets the Server Configuration GUI cycle exactly the values the
 * validator accepts, so the control and the validation rule cannot drift apart. Enum, fixed-choice
 * and curated-duration fields all use this shape; see {@link ConfigValidators#oneOf(String...)} and
 * {@link ConfigValidators#enumValue(Class)}.
 */
public interface ChoiceValidator extends ConfigValidator {

    /**
     * The canonical allowed values, in the order the Server Configuration GUI should cycle them.
     * Never empty.
     */
    List<String> options();
}
