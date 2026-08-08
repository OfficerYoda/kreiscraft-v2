package de.kreiscraft.modules.pluginhider;

import java.util.List;
import org.spongepowered.configurate.objectmapping.ConfigSerializable;

@ConfigSerializable
public record PluginHiderConfig(List<String> defaultAllowedCommands) {

    public PluginHiderConfig {
        if (defaultAllowedCommands == null) {
            defaultAllowedCommands = List.of("help");
        }
    }
}
