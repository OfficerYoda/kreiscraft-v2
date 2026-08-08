package de.kreiscraft.modules.bot;

import org.bukkit.Location;
import org.spongepowered.configurate.objectmapping.ConfigSerializable;

@ConfigSerializable
public record BotRecord(String name, Location location, int time) {

    public BotRecord {
        if (name == null)
            name = "";
    }
}
