package de.kreiscraft.core.config;

import java.io.IOException;

/**
 * Outcome of a {@link ConfigService#edit(ConfigEdit)} call.
 *
 * <p>Both {@link Rejected} and {@link Failed} leave the live configuration tree and the durable
 * file unchanged. They are distinguished so the GUI can report an invalid value differently from
 * an I/O problem.
 */
public sealed interface ConfigEditResult {

    /** The value was valid and the complete candidate was durably saved. */
    record Applied() implements ConfigEditResult {
    }

    /** The value was rejected by the field validator; nothing was changed. */
    record Rejected(String reason) implements ConfigEditResult {
    }

    /** The candidate could not be persisted; nothing was changed. */
    record Failed(IOException cause) implements ConfigEditResult {
    }
}
