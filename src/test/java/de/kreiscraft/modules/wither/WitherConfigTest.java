package de.kreiscraft.modules.wither;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.BufferedReader;
import java.io.StringReader;
import org.junit.jupiter.api.Test;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

class WitherConfigTest {

    private static ConfigurationNode parseYaml(String yaml) throws Exception {
        return YamlConfigurationLoader.builder()
                .source(() -> new BufferedReader(new StringReader(yaml)))
                .build()
                .load();
    }

    @Test
    void config_withAllValues_parsesCorrectly() throws Exception {
        ConfigurationNode node = parseYaml(
                "max-health: 600.0\n"
                        + "attack-damage: 40.0\n"
                        + "armor: 8.0\n"
                        + "half-health-threshold: 300.0\n"
                        + "minion-count: 4\n"
                        + "spawn-explosion-power: 20.0\n"
                        + "spawn-explosion-fire: true\n"
                        + "spawn-explosion-block-damage: true\n"
                        + "half-health-explosion-power: 4.0\n"
                        + "half-health-explosion-fire: true\n"
                        + "half-health-explosion-block-damage: true\n"
                        + "death-explosion-power: 20.0\n"
                        + "death-explosion-fire: true\n"
                        + "death-explosion-block-damage: true\n"
                        + "death-boss-bar-radius: 50\n");
        WitherConfig config = node.get(WitherConfig.class);

        assertThat(config).isNotNull();
        assertThat(config.maxHealth()).isEqualTo(600.0);
        assertThat(config.attackDamage()).isEqualTo(40.0);
        assertThat(config.armor()).isEqualTo(8.0);
        assertThat(config.halfHealthThreshold()).isEqualTo(300.0);
        assertThat(config.minionCount()).isEqualTo(4);
        assertThat(config.spawnExplosionPower()).isEqualTo(20.0);
        assertThat(config.spawnExplosionFire()).isTrue();
        assertThat(config.spawnExplosionBlockDamage()).isTrue();
        assertThat(config.halfHealthExplosionPower()).isEqualTo(4.0);
        assertThat(config.halfHealthExplosionFire()).isTrue();
        assertThat(config.halfHealthExplosionBlockDamage()).isTrue();
        assertThat(config.deathExplosionPower()).isEqualTo(20.0);
        assertThat(config.deathExplosionFire()).isTrue();
        assertThat(config.deathExplosionBlockDamage()).isTrue();
        assertThat(config.deathBossBarRadius()).isEqualTo(50);
    }

    @Test
    void config_withEmptyNode_usesDestructiveDefaults() throws Exception {
        ConfigurationNode node = parseYaml("{}");
        WitherConfig config = node.get(WitherConfig.class);

        assertThat(config).isNotNull();
        assertThat(config.maxHealth()).isEqualTo(600.0);
        assertThat(config.attackDamage()).isEqualTo(40.0);
        assertThat(config.armor()).isEqualTo(8.0);
        assertThat(config.halfHealthThreshold()).isEqualTo(300.0);
        assertThat(config.minionCount()).isEqualTo(4);
        // Spawn and death are destructive (power 20); half-health is contained (power 4).
        assertThat(config.spawnExplosionPower()).isEqualTo(20.0);
        assertThat(config.spawnExplosionFire()).isTrue();
        assertThat(config.spawnExplosionBlockDamage()).isTrue();
        assertThat(config.halfHealthExplosionPower()).isEqualTo(4.0);
        assertThat(config.halfHealthExplosionFire()).isTrue();
        assertThat(config.halfHealthExplosionBlockDamage()).isTrue();
        assertThat(config.deathExplosionPower()).isEqualTo(20.0);
        assertThat(config.deathExplosionFire()).isTrue();
        assertThat(config.deathExplosionBlockDamage()).isTrue();
        assertThat(config.deathBossBarRadius()).isEqualTo(50);
    }

    @Test
    void config_spawnExplosionFields_independentlyHonored() throws Exception {
        ConfigurationNode node = parseYaml(
                "spawn-explosion-power: 7.0\n"
                        + "spawn-explosion-fire: false\n"
                        + "spawn-explosion-block-damage: false\n");
        WitherConfig config = node.get(WitherConfig.class);

        assertThat(config.spawnExplosionPower()).isEqualTo(7.0);
        assertThat(config.spawnExplosionFire()).isFalse();
        assertThat(config.spawnExplosionBlockDamage()).isFalse();
    }

    @Test
    void config_deathExplosionFireAndBlockDamage_independentlyHonored() throws Exception {
        ConfigurationNode node = parseYaml(
                "death-explosion-power: 30.0\n"
                        + "death-explosion-fire: false\n"
                        + "death-explosion-block-damage: false\n");
        WitherConfig config = node.get(WitherConfig.class);

        assertThat(config.deathExplosionPower()).isEqualTo(30.0);
        assertThat(config.deathExplosionFire()).isFalse();
        assertThat(config.deathExplosionBlockDamage()).isFalse();
    }

    @Test
    void config_halfHealthExplosionFields_independentlyHonored() throws Exception {
        ConfigurationNode node = parseYaml(
                "half-health-explosion-power: 6.0\n"
                        + "half-health-explosion-fire: false\n"
                        + "half-health-explosion-block-damage: true\n");
        WitherConfig config = node.get(WitherConfig.class);

        assertThat(config.halfHealthExplosionPower()).isEqualTo(6.0);
        assertThat(config.halfHealthExplosionFire()).isFalse();
        assertThat(config.halfHealthExplosionBlockDamage()).isTrue();
    }

    @Test
    void config_roundTrip_preservesValues() throws Exception {
        WitherConfig original = new WitherConfig(
                800.0, 50.0, 10.0, 400.0, 6,
                25.0, false, true,
                6.0, false, false,
                35.0, true, false,
                75);
        ConfigurationNode node = parseYaml("{}");
        node.set(WitherConfig.class, original);
        WitherConfig loaded = node.get(WitherConfig.class);

        assertThat(loaded).isNotNull();
        assertThat(loaded.maxHealth()).isEqualTo(800.0);
        assertThat(loaded.minionCount()).isEqualTo(6);
        assertThat(loaded.spawnExplosionPower()).isEqualTo(25.0);
        assertThat(loaded.spawnExplosionFire()).isFalse();
        assertThat(loaded.spawnExplosionBlockDamage()).isTrue();
        assertThat(loaded.halfHealthExplosionFire()).isFalse();
        assertThat(loaded.halfHealthExplosionBlockDamage()).isFalse();
        assertThat(loaded.deathExplosionPower()).isEqualTo(35.0);
        assertThat(loaded.deathExplosionFire()).isTrue();
        assertThat(loaded.deathExplosionBlockDamage()).isFalse();
    }
}
