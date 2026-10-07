package de.kreiscraft.core.config;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Replaces the durable configuration file with a flushed temporary file.
 *
 * <p>The production implementation prefers an atomic move and falls back to a replace when the
 * filesystem cannot rename atomically. It exists as a seam so tests can exercise the failure path
 * of {@link ConfigService#edit(ConfigEdit)} without relying on filesystem permissions.
 */
@FunctionalInterface
interface ConfigFileReplacer {

    /** Replaces {@code target} with {@code source}. */
    void replace(Path source, Path target) throws IOException;

    /** Prefers {@code ATOMIC_MOVE}, falling back to a plain replace where unsupported. */
    ConfigFileReplacer ATOMIC_WHEN_SUPPORTED =
            (source, target) -> {
                try {
                    Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
                } catch (AtomicMoveNotSupportedException e) {
                    Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
                }
            };
}
