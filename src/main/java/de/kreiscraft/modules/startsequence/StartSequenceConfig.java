package de.kreiscraft.modules.startsequence;

import java.time.Duration;
import org.spongepowered.configurate.objectmapping.ConfigSerializable;

/**
 * Configuration for the Start Sequence.
 *
 * <p>The border expansion is configured and documented in seconds but represented internally as a
 * {@link Duration}. It is converted to ticks only at the Bukkit {@code WorldBorder} boundary, and
 * the whole expansion is delegated to Minecraft as a single transition. No phase, timestamp, or
 * remaining-duration state is owned by the plugin.
 */
@ConfigSerializable
public record StartSequenceConfig(
        Duration countdown,
        Double initialBorderSize,
        Double targetBorderSize,
        Duration borderDuration) {

    public StartSequenceConfig {
        if (countdown == null) {
            countdown = Duration.ofSeconds(10);
        }
        if (initialBorderSize == null) {
            initialBorderSize = 50.0;
        }
        if (targetBorderSize == null) {
            targetBorderSize = 30_000_000.0;
        }
        if (borderDuration == null) {
            // Configured in seconds; converted to ticks only at the Bukkit WorldBorder boundary.
            borderDuration = Duration.ofSeconds(30_000);
        }
    }
}
