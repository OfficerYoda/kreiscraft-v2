package de.kreiscraft.modules.jail;

import java.util.List;
import org.bukkit.Location;
import org.spongepowered.configurate.objectmapping.ConfigSerializable;

/**
 * Configuration for the jail module.
 *
 * <p>{@code location} is required — missing it from config will cause a deserialization error at
 * enable time. Batch C wiring always provides it. {@code blockedCommands} defaults to the legacy
 * hard-coded list when absent.
 */
@ConfigSerializable
public record JailConfig(Location location, List<String> blockedCommands) {

    public JailConfig {
        if (blockedCommands == null || blockedCommands.isEmpty()) {
            blockedCommands = List.of("msg", "tell", "w", "pay", "r");
        }
    }
}
