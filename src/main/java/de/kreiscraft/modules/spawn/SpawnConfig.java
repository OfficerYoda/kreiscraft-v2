package de.kreiscraft.modules.spawn;

import org.spongepowered.configurate.objectmapping.ConfigSerializable;

/**
 * Spawn-module configuration.
 *
 * <p>{@code spawnRadius} is the single <b>Spawn Radius</b> shared by spawn access and glide
 * eligibility. {@code /spawn} uses it as the horizontal radius of the Spawn Access Zone while
 * glide initiation uses it as the three-dimensional radius of the Glide Zone.
 */
@ConfigSerializable
public record SpawnConfig(int spawnRadius, GlideConfig glide, GlideBoostConfig glideBoost) {

    /** Default Spawn Radius in blocks, used when {@code spawn-radius} is absent or non-positive. */
    public static final int DEFAULT_SPAWN_RADIUS = 50;

    /** Apply safe defaults for every nested config that was absent from YAML. */
    public SpawnConfig {
        if (spawnRadius <= 0) {
            spawnRadius = DEFAULT_SPAWN_RADIUS;
        }
        if (glide == null) {
            glide = new GlideConfig(true, 3);
        }
        if (glideBoost == null) {
            glideBoost = new GlideBoostConfig(false, 2.0);
        }
    }

    // ── nested records ──────────────────────────────────────────────────────

    /**
     * Configuration for the spawn-area elytra glide feature.
     *
     * <p>{@code multiplyValue} scales the player's facing direction when a glide is initiated. The
     * direction returned by {@link org.bukkit.Location#getDirection()} is a unit vector, so
     * multiplying it by {@code multiplyValue} launches the player at that many blocks per tick
     * along their look direction. Defaults to 3.
     *
     * <p>Note: {@code enabled} is a primitive {@code boolean}; when the {@code glide:} key is
     * absent from the config file, Configurate initialises it to {@code false} (the Java default
     * for {@code boolean}). In production the bundled {@code config.yml} always contains an
     * explicit {@code enabled: true} entry, so this is only relevant for edge-case testing.
     */
    @ConfigSerializable
    public record GlideConfig(boolean enabled, float multiplyValue) {

        public GlideConfig {
            // 0 / negative means "missing from config" — apply the safe default multiplier.
            if (multiplyValue <= 0) {
                multiplyValue = 2;
            }
        }
    }

    /**
     * Configuration for the persistent glide boost triggered by sneaking.
     *
     * <p>Players that START gliding inside the Glide Zone become boost-eligible for the
     * duration of that glide. While still gliding (even outside the zone), pressing sneak
     * applies one directional velocity boost.
     */
    @ConfigSerializable
    public record GlideBoostConfig(boolean enabled, double boostStrength) {

        public GlideBoostConfig {
            // 0.0 means "missing from config" — apply the safe default.
            if (boostStrength == 0.0) {
                boostStrength = 2.0;
            }
        }
    }
}
