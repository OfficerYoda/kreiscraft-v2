package de.kreiscraft.modules.shatteredcrown;

import java.util.List;
import java.util.Optional;
import org.bukkit.NamespacedKey;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.objectmapping.ConfigSerializable;
import org.spongepowered.configurate.serialize.SerializationException;

/** Fixed encounter tuning, snapshotted at the start of an attempt. */
@ConfigSerializable
public record ShatteredCrownConfig(String worldKey, Double maxHealth, Integer defenderCount,
        Double arenaRadius,
        Double arrivalX, Double arrivalY, Double arrivalZ, Double arrivalRadius,
        Double arrivalHeight, Double anchorDistance, Integer groundSearchRadius, Double groundMaxY,
        String defenderType, Double defenderHealth, Double defenderDamage,
        Double bombardmentWarningSeconds, Double bombardmentCadenceSeconds,
        Double bombardmentDamage, Double bombardmentRadius,
        Double laneLength, Double laneWidth, Double laneWarningSeconds, Double laneLifetimeSeconds,
        Double laneCadenceSeconds, Double laneDamage, Double laneDamageCadenceSeconds,
        Double landingCadenceSeconds, Double landingWindowSeconds, Double landingApproachSeconds,
        Double landingRadius, Double preyPercentage, Double preyCadenceSeconds, Double preyCountdownSeconds,
        Double poolRadius, Double poolDamage, Double poolDamageCadenceSeconds, Double poolLifetimeSeconds,
        Double sweepRadius, Double sweepAngle, Double sweepWarningSeconds, Double sweepCadenceSeconds,
        Double sweepDamage, Double sweepKnockback, Double finalHealthFraction,
        Double finalCadenceFactor, Double finalPoolLifetimeFactor,
        Double explosionWindowSeconds, Double explosionWindowBudget, Boolean movementAttacks,
        Double openingGraceSeconds, Double divePassSeconds, Double diveClimbSeconds,
        Double attackGapMinSeconds, Double attackGapMaxSeconds,
        Integer openingArmorPercentage,
        Double smashCraterRadius, Integer smashCraterDepth, Double debrisLifetimeSeconds,
        Double smashShockwaveRadius, Double smashShockwaveSeconds, Double smashShockwaveDamagePercentage,
        Double smashShockwaveKnockback,
        Double bombardmentSpawnHeight, Double bombardmentDescentSeconds) {
    /** Validate full reloads and Crown edits, without blocking unrelated edits on a broken section. */
    public static Optional<String> validateCandidate(ConfigurationNode candidate) {
        Object[] path = candidate.path().array();
        if (path.length != 0 && (path.length < 2 || !path[0].equals("modules")
                || !path[1].equals("shattered-crown"))) {
            return Optional.empty();
        }
        while (candidate.parent() != null) {
            candidate = candidate.parent();
        }
        ConfigurationNode section = candidate.node("modules", "shattered-crown");
        if (section.virtual()) {
            return Optional.empty();
        }
        try {
            load(section);
            return Optional.empty();
        } catch (SerializationException | IllegalArgumentException error) {
            Throwable cause = error;
            while (cause.getCause() != null) {
                cause = cause.getCause();
            }
            return Optional.of("Invalid shattered-crown configuration: " + cause.getMessage());
        }
    }

    /** Validate the complete supplied section, including the enable flag outside the tuning record. */
    public static ShatteredCrownConfig load(org.spongepowered.configurate.ConfigurationNode node)
            throws org.spongepowered.configurate.serialize.SerializationException {
        if (node.virtual()) {
            // Missing sections use default tuning without publishing or enabling a profile in YAML.
            node = node.copy();
            node.node("enabled").set(false);
        }
        if (!node.node("enabled").virtual()) {
            var invalid = de.kreiscraft.core.config.ConfigValidators.booleanValue().validate(node.node("enabled"));
            if (invalid.isPresent()) {
                throw new org.spongepowered.configurate.serialize.SerializationException(
                        "shattered-crown.enabled " + invalid.get());
            }
        }
        if (!node.node("movement-attacks").virtual()) {
            var invalid = de.kreiscraft.core.config.ConfigValidators.booleanValue()
                    .validate(node.node("movement-attacks"));
            if (invalid.isPresent()) {
                throw new SerializationException("shattered-crown.movement-attacks " + invalid.get());
            }
        }
        return node.get(ShatteredCrownConfig.class);
    }

    public ShatteredCrownConfig {
        bombardmentSpawnHeight = bombardmentSpawnHeight == null ? 20.0 : bombardmentSpawnHeight;
        bombardmentDescentSeconds = bombardmentDescentSeconds == null ? 2.0 : bombardmentDescentSeconds;
        bounded("bombardment-spawn-height", bombardmentSpawnHeight, 16, 24);
        bounded("bombardment-descent-seconds", bombardmentDescentSeconds, 1.5, 2.5);
        openingArmorPercentage = openingArmorPercentage == null ? 35 : openingArmorPercentage;
        bounded("opening-armor-percentage", openingArmorPercentage, 0, 50);
        movementAttacks = movementAttacks == null ? true : movementAttacks;
        attackGapMinSeconds = attackGapMinSeconds == null ? 2.0 : attackGapMinSeconds;
        attackGapMaxSeconds = attackGapMaxSeconds == null ? 4.0 : attackGapMaxSeconds;
        bounded("attack-gap-min-seconds", attackGapMinSeconds, 2, 10);
        bounded("attack-gap-max-seconds", attackGapMaxSeconds, attackGapMinSeconds, 10);
        openingGraceSeconds = openingGraceSeconds == null ? 30.0 : openingGraceSeconds;
        bounded("opening-grace-seconds", openingGraceSeconds, 0, 120);
        worldKey = worldKey == null ? "minecraft:the_end" : worldKey;
        maxHealth = maxHealth == null ? 1000.0 : maxHealth;
        defenderCount = defenderCount == null ? 4 : defenderCount;
        arenaRadius = arenaRadius == null ? 192.0 : arenaRadius;
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
        bombardmentWarningSeconds = bombardmentWarningSeconds == null ? 3.0 : bombardmentWarningSeconds;
        bombardmentCadenceSeconds = bombardmentCadenceSeconds == null ? 8.0 : bombardmentCadenceSeconds;
        bombardmentDamage = bombardmentDamage == null ? 6.0 : bombardmentDamage;
        bombardmentRadius = bombardmentRadius == null ? 4.0 : bombardmentRadius;
        laneLength = laneLength == null ? 40.0 : laneLength;
        laneWidth = laneWidth == null ? 10.0 : laneWidth;
        laneWarningSeconds = laneWarningSeconds == null ? 2.0 : laneWarningSeconds;
        laneLifetimeSeconds = laneLifetimeSeconds == null ? 6.0 : laneLifetimeSeconds;
        laneCadenceSeconds = laneCadenceSeconds == null ? 10.0 : laneCadenceSeconds;
        laneDamage = laneDamage == null ? 8.0 : laneDamage;
        divePassSeconds = divePassSeconds == null ? 1.5 : divePassSeconds;
        diveClimbSeconds = diveClimbSeconds == null ? 1.0 : diveClimbSeconds;
        bounded("dive-pass-seconds", divePassSeconds, 1, 10);
        bounded("dive-climb-seconds", diveClimbSeconds, 0.5, 10);
        laneDamageCadenceSeconds = laneDamageCadenceSeconds == null ? 1.0 : laneDamageCadenceSeconds;
        landingCadenceSeconds = landingCadenceSeconds == null ? 30.0 : landingCadenceSeconds;
        landingWindowSeconds = landingWindowSeconds == null ? 6.0 : landingWindowSeconds;
        landingApproachSeconds = landingApproachSeconds == null ? 20.0 : landingApproachSeconds;
        landingRadius = landingRadius == null ? 12.0 : landingRadius;
        preyPercentage = preyPercentage == null ? 35.0 : preyPercentage;
        preyCadenceSeconds = preyCadenceSeconds == null ? 20.0 : preyCadenceSeconds;
        preyCountdownSeconds = preyCountdownSeconds == null ? 4.0 : preyCountdownSeconds;
        poolRadius = poolRadius == null ? 5.0 : poolRadius;
        poolDamage = poolDamage == null ? 3.0 : poolDamage;
        poolDamageCadenceSeconds = poolDamageCadenceSeconds == null ? 1.0 : poolDamageCadenceSeconds;
        poolLifetimeSeconds = poolLifetimeSeconds == null ? 6.0 : poolLifetimeSeconds;
        sweepRadius = sweepRadius == null ? 8.0 : sweepRadius;
        sweepAngle = sweepAngle == null ? 90.0 : sweepAngle;
        sweepWarningSeconds = sweepWarningSeconds == null ? 3.0 : sweepWarningSeconds;
        sweepCadenceSeconds = sweepCadenceSeconds == null ? 25.0 : sweepCadenceSeconds;
        sweepDamage = sweepDamage == null ? 12.0 : sweepDamage;
        smashCraterRadius = smashCraterRadius == null ? 5.0 : smashCraterRadius;
        smashCraterDepth = smashCraterDepth == null ? 1 : smashCraterDepth;
        debrisLifetimeSeconds = debrisLifetimeSeconds == null ? 4.0 : debrisLifetimeSeconds;
        bounded("smash-crater-radius", smashCraterRadius, 1, 8);
        bounded("smash-crater-depth", smashCraterDepth, 1, 3);
        bounded("debris-lifetime-seconds", debrisLifetimeSeconds, 0.5, 10);
        sweepKnockback = sweepKnockback == null ? 1.2 : sweepKnockback;
        smashShockwaveRadius = smashShockwaveRadius == null ? 40.0 : smashShockwaveRadius;
        smashShockwaveSeconds = smashShockwaveSeconds == null ? 3.0 : smashShockwaveSeconds;
        smashShockwaveDamagePercentage = smashShockwaveDamagePercentage == null ? 50.0 : smashShockwaveDamagePercentage;
        smashShockwaveKnockback = smashShockwaveKnockback == null ? sweepKnockback : smashShockwaveKnockback;
        bounded("smash-shockwave-radius", smashShockwaveRadius, 1, 64);
        bounded("smash-shockwave-seconds", smashShockwaveSeconds, 0.5, 10);
        bounded("smash-shockwave-damage-percentage", smashShockwaveDamagePercentage, 0, 100);
        bounded("smash-shockwave-knockback", smashShockwaveKnockback, 0, 2);
        finalHealthFraction = finalHealthFraction == null ? 0.3 : finalHealthFraction;
        finalCadenceFactor = finalCadenceFactor == null ? 0.7 : finalCadenceFactor;
        finalPoolLifetimeFactor = finalPoolLifetimeFactor == null ? 0.5 : finalPoolLifetimeFactor;
        bounded("final-health-fraction", finalHealthFraction, 0.1, 0.5);
        bounded("final-cadence-factor", finalCadenceFactor, 0.5, 0.9);
        bounded("final-pool-lifetime-factor", finalPoolLifetimeFactor, 0.25, 0.9);
        explosionWindowSeconds = explosionWindowSeconds == null ? 2.0 : explosionWindowSeconds;
        explosionWindowBudget = explosionWindowBudget == null ? 40.0 : explosionWindowBudget;
        bounded("explosion-window-seconds", explosionWindowSeconds, 0.25, 5);
        bounded("explosion-window-budget", explosionWindowBudget, 1, 200);
        bounded("sweep-radius", sweepRadius, 4, 16);
        bounded("sweep-angle", sweepAngle, 30, 120);
        bounded("sweep-warning-seconds", sweepWarningSeconds, 3, 15);
        bounded("sweep-cadence-seconds", sweepCadenceSeconds, 5, 120);
        bounded("sweep-damage", sweepDamage, 0, 100);
        bounded("sweep-knockback", sweepKnockback, 0, 2);
        if (sweepCadenceSeconds < sweepWarningSeconds + 0.5) {
            throw new IllegalArgumentException(
                    "shattered-crown sweeps require serialized warnings");
        }
        bounded("prey-percentage", preyPercentage, 1, 100);
        bounded("prey-cadence-seconds", preyCadenceSeconds, 5, 120);
        bounded("prey-countdown-seconds", preyCountdownSeconds, 2, 15);
        bounded("pool-radius", poolRadius, 1, 6);
        bounded("pool-damage", poolDamage, 0, 100);
        bounded("pool-damage-cadence-seconds", poolDamageCadenceSeconds, 0.5, 5);
        bounded("pool-lifetime-seconds", poolLifetimeSeconds, 1, 15);
        if (preyCadenceSeconds < preyCountdownSeconds + 0.5) {
            throw new IllegalArgumentException(
                    "shattered-crown prey requires serialized marks");
        }
        bounded("landing-cadence-seconds", landingCadenceSeconds, 10, 120);
        bounded("landing-window-seconds", landingWindowSeconds, 6, 6);
        bounded("landing-approach-seconds", landingApproachSeconds, 5, 45);
        bounded("landing-radius", landingRadius, 8, 24);
        bounded("lane-length", laneLength, 8, 40);
        bounded("lane-width", laneWidth, 2, 12);
        bounded("lane-warning-seconds", laneWarningSeconds, 1, 15);
        bounded("lane-lifetime-seconds", laneLifetimeSeconds, 1, 20);
        bounded("lane-cadence-seconds", laneCadenceSeconds, 2, 120);
        bounded("lane-damage", laneDamage, 0, 100);
        bounded("lane-damage-cadence-seconds", laneDamageCadenceSeconds, 0.5, 5);
        if (laneCadenceSeconds < laneWarningSeconds + 0.5) {
            throw new IllegalArgumentException(
                    "shattered-crown lanes require serialized warnings");
        }
        if (NamespacedKey.fromString(worldKey) == null) {
            throw new IllegalArgumentException("shattered-crown.world-key must be a namespaced world key");
        }
        bounded("max-health", maxHealth, 1, 1024);
        bounded("defender-count", defenderCount, 1, 32);
        bounded("arena-radius", arenaRadius, 16, 256);
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
        bounded("bombardment-warning-seconds", bombardmentWarningSeconds, 1, 15);
        bounded("bombardment-cadence-seconds", bombardmentCadenceSeconds, 2, 120);
        bounded("bombardment-damage", bombardmentDamage, 0, 100);
        bounded("bombardment-radius", bombardmentRadius, 1, 12);
        if (bombardmentCadenceSeconds < bombardmentWarningSeconds + 0.5) {
            throw new IllegalArgumentException(
                    "shattered-crown bombardments require serialized warnings");
        }
        if (anchorDistance + groundSearchRadius + 3 >= arenaRadius) {
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
