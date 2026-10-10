package de.kreiscraft.modules.wither;

import org.spongepowered.configurate.objectmapping.ConfigSerializable;

/**
 * Server Configuration for the Wither boss fight.
 *
 * <p>The three explosion groups (spawn, half-health, death) each carry power/fire/blockDamage.
 * An {@code ExplosionConfig} value type would reduce the parameter count from 15 to 9 but would
 * change the Configurate-serialized YAML layout from the flat keys that operators already know
 * (e.g. {@code spawn-explosion-power}) to a nested map structure.  That is a breaking config-file
 * migration for no behavioural gain, so the flat record is intentionally preserved.
 * Revisit only if the config format is versioned and a migration path is provided.
 */
@ConfigSerializable
public record WitherConfig(
        double maxHealth,
        double attackDamage,
        double armor,
        double halfHealthThreshold,
        int minionCount,
        double spawnExplosionPower,
        Boolean spawnExplosionFire,
        Boolean spawnExplosionBlockDamage,
        double halfHealthExplosionPower,
        Boolean halfHealthExplosionFire,
        Boolean halfHealthExplosionBlockDamage,
        double deathExplosionPower,
        Boolean deathExplosionFire,
        Boolean deathExplosionBlockDamage,
        int deathBossBarRadius) {

    public WitherConfig {
        if (maxHealth <= 0.0) {
            maxHealth = 600.0;
        }
        if (attackDamage <= 0.0) {
            attackDamage = 40.0;
        }
        if (armor <= 0.0) {
            armor = 8.0;
        }
        if (halfHealthThreshold <= 0.0) {
            halfHealthThreshold = 300.0;
        }
        if (minionCount <= 0) {
            minionCount = 4;
        }
        if (spawnExplosionPower <= 0.0) {
            spawnExplosionPower = 20.0;
        }
        if (spawnExplosionFire == null) {
            spawnExplosionFire = Boolean.TRUE;
        }
        if (spawnExplosionBlockDamage == null) {
            spawnExplosionBlockDamage = Boolean.TRUE;
        }
        if (halfHealthExplosionPower <= 0.0) {
            halfHealthExplosionPower = 4.0;
        }
        if (halfHealthExplosionFire == null) {
            halfHealthExplosionFire = Boolean.TRUE;
        }
        if (halfHealthExplosionBlockDamage == null) {
            halfHealthExplosionBlockDamage = Boolean.TRUE;
        }
        if (deathExplosionPower <= 0.0) {
            deathExplosionPower = 20.0;
        }
        if (deathExplosionFire == null) {
            deathExplosionFire = Boolean.TRUE;
        }
        if (deathExplosionBlockDamage == null) {
            deathExplosionBlockDamage = Boolean.TRUE;
        }
        if (deathBossBarRadius <= 0) {
            deathBossBarRadius = 50;
        }
    }
}
