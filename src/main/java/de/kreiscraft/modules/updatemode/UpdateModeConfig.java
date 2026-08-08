package de.kreiscraft.modules.updatemode;

import java.util.List;
import org.spongepowered.configurate.objectmapping.ConfigSerializable;

@ConfigSerializable
public record UpdateModeConfig(String bypassPermission, List<String> bypassNames) {

    public UpdateModeConfig {
        if (bypassPermission == null)
            bypassPermission = "kreiscraft.updatemode.bypass";
        if (bypassNames == null)
            bypassNames = List.of();
    }
}
