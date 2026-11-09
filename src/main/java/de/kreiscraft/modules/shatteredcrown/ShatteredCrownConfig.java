package de.kreiscraft.modules.shatteredcrown;

import java.util.List;
import org.bukkit.NamespacedKey;
import org.spongepowered.configurate.objectmapping.ConfigSerializable;

/** Fixed encounter tuning, snapshotted at the start of an attempt. */
@ConfigSerializable
public record ShatteredCrownConfig(String worldKey, Double maxHealth, Integer defenderCount,
        Double arenaRadius, Double arenaMinY, Double arenaMaxY,
        Double arrivalX, Double arrivalY, Double arrivalZ, Double arrivalRadius,
        Double arrivalHeight, Double anchorDistance, Integer groundSearchRadius, Double groundMaxY,
        String defenderType, Double defenderHealth, Double defenderDamage, Double defenderLeash) {
    public ShatteredCrownConfig {
        worldKey = worldKey == null ? "minecraft:the_end" : worldKey;
        maxHealth = maxHealth == null ? 1000.0 : maxHealth;
        defenderCount = defenderCount == null ? 4 : defenderCount;
        arenaRadius = arenaRadius == null ? 80.0 : arenaRadius;
        arenaMinY = arenaMinY == null ? 40.0 : arenaMinY;
        arenaMaxY = arenaMaxY == null ? 160.0 : arenaMaxY;
        arrivalX = arrivalX == null ? 100.0 : arrivalX;
        arrivalY = arrivalY == null ? 50.0 : arrivalY;
        arrivalZ = arrivalZ == null ? 0.0 : arrivalZ;
        arrivalRadius = arrivalRadius == null ? 8.0 : arrivalRadius;
        arrivalHeight = arrivalHeight == null ? 12.0 : arrivalHeight;
        anchorDistance = anchorDistance == null ? 24.0 : anchorDistance;
        groundSearchRadius = groundSearchRadius == null ? 8 : groundSearchRadius;
        groundMaxY = groundMaxY == null ? 90.0 : groundMaxY;
        defenderType = defenderType == null ? "HUSK" : defenderType;
        defenderHealth = defenderHealth == null ? 40.0 : defenderHealth;
        defenderDamage = defenderDamage == null ? 6.0 : defenderDamage;
        defenderLeash = defenderLeash == null ? 16.0 : defenderLeash;
        if (NamespacedKey.fromString(worldKey) == null) {
            throw new IllegalArgumentException("shattered-crown.world-key must be a namespaced world key");
        }
        bounded("max-health", maxHealth, 1, 1024);
        bounded("defender-count", defenderCount, 1, 32);
        bounded("arena-radius", arenaRadius, 16, 256);
        bounded("arena-min-y", arenaMinY, -64, 320);
        bounded("arena-max-y", arenaMaxY, -64, 320);
        bounded("arrival-x", arrivalX, -512, 512);
        bounded("arrival-y", arrivalY, -64, 320);
        bounded("arrival-z", arrivalZ, -512, 512);
        bounded("arrival-radius", arrivalRadius, 3, 32);
        bounded("arrival-height", arrivalHeight, 4, 64);
        bounded("anchor-distance", anchorDistance, 8, 192);
        bounded("ground-search-radius", groundSearchRadius, 0, 16);
        bounded("ground-max-y", groundMaxY, -64, 320);
        bounded("defender-health", defenderHealth, 1, 1000);
        bounded("defender-damage", defenderDamage, 0, 100);
        bounded("defender-leash", defenderLeash, 4, 32);
        if (arenaMinY >= arenaMaxY || groundMaxY < arenaMinY || groundMaxY > arenaMaxY
                || anchorDistance + groundSearchRadius + 3 >= arenaRadius) {
            throw new IllegalArgumentException(
                    "shattered-crown geometry must leave ground objectives inside the arena");
        }
        if (!List.of("HUSK", "ZOMBIE").contains(defenderType)) {
            throw new IllegalArgumentException("shattered-crown.defender-type must be HUSK or ZOMBIE");
        }
    }

    private static void bounded(String key, double value, double min, double max) {
        if (!Double.isFinite(value) || value < min || value > max) {
            throw new IllegalArgumentException(
                    "shattered-crown." + key + " must be finite and between " + min + " and " + max);
        }
    }
}
