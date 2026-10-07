package de.kreiscraft.modules.jail;

import java.util.List;
import org.spongepowered.configurate.objectmapping.ConfigSerializable;

/**
 * Configuration for the jail module.
 *
 * <p>The jail {@code location} is operator-owned Server Configuration (per ADR-10), written by
 * {@code /setjail} and read live through {@link JailService#jailLocation()}; it is therefore not a
 * module-config record component. {@code blockedCommands} defaults to the legacy hard-coded list
 * when absent.
 */
@ConfigSerializable
public record JailConfig(List<String> blockedCommands) {

    public JailConfig {
        if (blockedCommands == null || blockedCommands.isEmpty()) {
            blockedCommands = List.of("msg", "tell", "w", "pay", "r");
        }
    }
}
