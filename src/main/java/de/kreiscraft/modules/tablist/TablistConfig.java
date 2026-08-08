package de.kreiscraft.modules.tablist;

import java.time.Duration;
import org.spongepowered.configurate.objectmapping.ConfigSerializable;
import org.spongepowered.configurate.objectmapping.meta.Setting;

@ConfigSerializable
public record TablistConfig(Duration updateInterval, @Setting("header") String headerTemplate,
        @Setting("footer") String footerTemplate) {

    public TablistConfig {
        if (updateInterval == null)
            updateInterval = Duration.ofMillis(20 * 50L); // 20 ticks = 1 s
        if (headerTemplate == null)
            headerTemplate = "<gradient:#5555ff:#55ffff>Kreiscraft</gradient>";
        if (footerTemplate == null)
            footerTemplate = "TPS: %tps% | Players: %server_players%";
    }
}
