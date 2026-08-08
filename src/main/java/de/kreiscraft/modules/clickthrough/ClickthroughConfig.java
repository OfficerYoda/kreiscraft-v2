package de.kreiscraft.modules.clickthrough;

import org.spongepowered.configurate.objectmapping.ConfigSerializable;

@ConfigSerializable
public record ClickthroughConfig(int targetBlockMaxDistance) {

    public ClickthroughConfig {
        if (targetBlockMaxDistance <= 0) {
            targetBlockMaxDistance = 6;
        }
    }
}
