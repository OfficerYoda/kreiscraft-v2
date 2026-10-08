package de.kreiscraft.modules.chatformat;

import org.bukkit.event.EventPriority;
import org.spongepowered.configurate.objectmapping.ConfigSerializable;

@ConfigSerializable
public record ChatFormatConfig(EventPriority eventPriority, boolean allowMiniMessageInBody, String format) {

    private static final String DEFAULT_FORMAT =
            "<kc-prefix> <player-prefix><name-colored><player-suffix><gray>: <reset><body>";

    /** Compact constructor: apply safe defaults for null fields. */
    public ChatFormatConfig {
        if (eventPriority == null) {
            eventPriority = EventPriority.LOWEST;
        }
        if (format == null) {
            format = DEFAULT_FORMAT;
        }
    }

    /** No-arg constructor used when the config section is fully absent. */
    public ChatFormatConfig() {
        this(EventPriority.LOWEST, false, DEFAULT_FORMAT);
    }
}
