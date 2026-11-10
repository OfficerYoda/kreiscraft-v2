package de.kreiscraft.modules.status;

import org.spongepowered.configurate.objectmapping.ConfigSerializable;

@ConfigSerializable
public record StatusConfig(String joinFormat, String leaveFormat) {

    public StatusConfig {
        if (joinFormat == null) {
            joinFormat = "<kc-prefix><gray> » <player-prefix><name-colored><player-suffix>";
        }
        if (leaveFormat == null) {
            leaveFormat = "<kc-prefix><gray> « <player-prefix><name-colored><player-suffix>";
        }
    }

    public StatusConfig() {
        this(null, null);
    }
}
