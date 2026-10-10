package de.kreiscraft.modules.combat;

import de.kreiscraft.shared.preferences.CombatDisplay;
import java.time.Duration;
import java.util.List;
import org.spongepowered.configurate.objectmapping.ConfigSerializable;

@ConfigSerializable
public record CombatConfig(Duration tagDuration, Boolean disableElytra, List<String> excludedWorlds,
        CombatDisplay defaultDisplay) {

    /** Compact constructor — fills nulls with safe defaults so the module works with an empty config section. */
    public CombatConfig {
        if (tagDuration == null) {
            tagDuration = Duration.ofSeconds(5);
        }
        if (disableElytra == null) {
            disableElytra = Boolean.TRUE;
        }
        if (excludedWorlds == null || excludedWorlds.isEmpty()) {
            excludedWorlds = List.of("Lobby");
        }
        if (defaultDisplay == null) {
            defaultDisplay = CombatDisplay.ACTIONBAR;
        }
    }
}
