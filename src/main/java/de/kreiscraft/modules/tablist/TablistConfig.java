package de.kreiscraft.modules.tablist;

import java.time.Duration;
import org.spongepowered.configurate.objectmapping.ConfigSerializable;
import org.spongepowered.configurate.objectmapping.meta.Setting;

@ConfigSerializable
public record TablistConfig(Duration updateInterval, @Setting("header") String headerTemplate,
        @Setting("footer") String footerTemplate) {

    /** The Kreiscraft banner used at the top of the header and the bottom of the footer. */
    private static final String BANNER =
            "<gray><st>                <reset><gray>[<blue><bold>Kreiscraft<reset><gray>]<gray><st>                ";

    public TablistConfig {
        if (updateInterval == null) {
            updateInterval = Duration.ofMillis(20 * 50L); // 20 ticks = 1 s
        }
        if (headerTemplate == null) {
            headerTemplate = BANNER + "\n\n<reset><gray>Playtime: <blue>%playtime%\n";
        }
        if (footerTemplate == null) {
            footerTemplate = "\n<gray>Uhrzeit: <blue>%system_time%\n\n<gray>Ping: <blue>%ping%"
                    + "   <gray>Tps: <blue>%tps%\n\n" + BANNER;
        }
    }
}
