package de.kreiscraft.modules.anticheat;

import java.time.Duration;
import org.spongepowered.configurate.objectmapping.ConfigSerializable;

@ConfigSerializable
public record AntiCheatConfig(String channel, Boolean kickOnFabricForge, Duration handshakeGraceDuration) {

    public AntiCheatConfig {
        if (channel == null || channel.isBlank()) {
            channel = "kreiscraft:mod_checker";
        }
        if (kickOnFabricForge == null) {
            kickOnFabricForge = Boolean.TRUE;
        }
        if (handshakeGraceDuration == null) {
            handshakeGraceDuration = Duration.ofSeconds(5);
        }
    }
}
