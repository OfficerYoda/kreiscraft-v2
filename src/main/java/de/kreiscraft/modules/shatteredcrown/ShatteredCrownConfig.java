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
        String defenderType, Double defenderHealth, Double defenderDamage, Double defenderLeash,
        Double bombardmentWarningSeconds, Double bombardmentCadenceSeconds,
        Double bombardmentDamage, Double bombardmentRadius,
        Double laneLength, Double laneWidth, Double laneWarningSeconds, Double laneLifetimeSeconds,
        Double laneCadenceSeconds, Double laneDamage, Double laneDamageCadenceSeconds,
        Double landingCadenceSeconds, Double landingWindowSeconds, Double landingApproachSeconds,
        Double landingRadius) {
    /** Validate the complete supplied section, including the enable flag outside the tuning record. */
    public static ShatteredCrownConfig load(org.spongepowered.configurate.ConfigurationNode node)
            throws org.spongepowered.configurate.serialize.SerializationException {
        if (!node.node("enabled").virtual()) {
            var invalid = de.kreiscraft.core.config.ConfigValidators.booleanValue().validate(node.node("enabled"));
            if (invalid.isPresent()) {
                throw new org.spongepowered.configurate.serialize.SerializationException(
                        "shattered-crown.enabled " + invalid.get());
            }
        }
        return node.get(ShatteredCrownConfig.class);
    }

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
        bombardmentWarningSeconds = bombardmentWarningSeconds == null ? 3.0 : bombardmentWarningSeconds;
        bombardmentCadenceSeconds = bombardmentCadenceSeconds == null ? 8.0 : bombardmentCadenceSeconds;
        bombardmentDamage = bombardmentDamage == null ? 6.0 : bombardmentDamage;
        bombardmentRadius = bombardmentRadius == null ? 4.0 : bombardmentRadius;
        laneLength = laneLength == null ? 24.0 : laneLength;
        laneWidth = laneWidth == null ? 6.0 : laneWidth;
        laneWarningSeconds = laneWarningSeconds == null ? 3.0 : laneWarningSeconds;
        laneLifetimeSeconds = laneLifetimeSeconds == null ? 6.0 : laneLifetimeSeconds;
        laneCadenceSeconds = laneCadenceSeconds == null ? 10.0 : laneCadenceSeconds;
        laneDamage = laneDamage == null ? 4.0 : laneDamage;
        laneDamageCadenceSeconds = laneDamageCadenceSeconds == null ? 1.0 : laneDamageCadenceSeconds;
        landingCadenceSeconds = landingCadenceSeconds == null ? 30.0 : landingCadenceSeconds;
        landingWindowSeconds = landingWindowSeconds == null ? 12.0 : landingWindowSeconds;
        landingApproachSeconds = landingApproachSeconds == null ? 20.0 : landingApproachSeconds;
        landingRadius = landingRadius == null ? 12.0 : landingRadius;
        bounded("landing-cadence-seconds", landingCadenceSeconds, 10, 120);
        bounded("landing-window-seconds", landingWindowSeconds, 8, 30);
        bounded("landing-approach-seconds", landingApproachSeconds, 5, 45);
        bounded("landing-radius", landingRadius, 8, 24);
        bounded("lane-length", laneLength, 8, 40);
        bounded("lane-width", laneWidth, 2, 12);
        bounded("lane-warning-seconds", laneWarningSeconds, 1, 15);
        bounded("lane-lifetime-seconds", laneLifetimeSeconds, 1, 20);
        bounded("lane-cadence-seconds", laneCadenceSeconds, 2, 120);
        bounded("lane-damage", laneDamage, 0, 100);
        bounded("lane-damage-cadence-seconds", laneDamageCadenceSeconds, 0.5, 5);
        if (laneCadenceSeconds < laneWarningSeconds + 0.5 || laneWidth / 2 + 2 > laneWarningSeconds * 3) {
            throw new IllegalArgumentException(
                    "shattered-crown lanes require time to walk clear and serialized warnings");
        }
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
        bounded("bombardment-warning-seconds", bombardmentWarningSeconds, 1, 15);
        bounded("bombardment-cadence-seconds", bombardmentCadenceSeconds, 2, 120);
        bounded("bombardment-damage", bombardmentDamage, 0, 100);
        bounded("bombardment-radius", bombardmentRadius, 1, 12);
        if (bombardmentCadenceSeconds < bombardmentWarningSeconds + 0.5
                || bombardmentRadius + 2 > bombardmentWarningSeconds * 3) {
            throw new IllegalArgumentException(
                    "shattered-crown bombardments must leave warning time to walk clear and cannot overlap");
        }
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
