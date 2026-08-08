package de.kreiscraft.modules.wither;

import org.spongepowered.configurate.objectmapping.ConfigSerializable;

@ConfigSerializable
public record WitherConfig(double maxHealth, double attackDamage, double armor, double halfHealthThreshold,
        int minionCount, double halfHealthExplosionPower, int deathBossBarRadius, double deathExplosionPower) {

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
        if (halfHealthExplosionPower <= 0.0) {
            halfHealthExplosionPower = 4.0;
        }
        if (deathBossBarRadius <= 0) {
            deathBossBarRadius = 50;
        }
        if (deathExplosionPower <= 0.0) {
            deathExplosionPower = 20.0;
        }
    }
}
