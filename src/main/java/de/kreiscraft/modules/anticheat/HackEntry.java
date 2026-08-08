package de.kreiscraft.modules.anticheat;

import java.time.Instant;
import java.util.UUID;
import org.spongepowered.configurate.objectmapping.ConfigSerializable;

@ConfigSerializable
public record HackEntry(UUID playerId, String playerName, Kind kind, String detail, Instant when) {

    public enum Kind {
        CHEAT, XRAY
    }
}
