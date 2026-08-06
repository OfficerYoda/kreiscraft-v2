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
                        + "half-health-explosion-power: 4.0\n"
                        + "death-boss-bar-radius: 50\n"
                        + "death-explosion-power: 20.0\n");
        WitherConfig config = node.get(WitherConfig.class);

        assertThat(config).isNotNull();
        assertThat(config.maxHealth()).isEqualTo(600.0);
        assertThat(config.attackDamage()).isEqualTo(40.0);
        assertThat(config.armor()).isEqualTo(8.0);
        assertThat(config.halfHealthThreshold()).isEqualTo(300.0);
        assertThat(config.minionCount()).isEqualTo(4);
        assertThat(config.halfHealthExplosionPower()).isEqualTo(4.0);
        assertThat(config.deathBossBarRadius()).isEqualTo(50);
        assertThat(config.deathExplosionPower()).isEqualTo(20.0);
    }

    @Test
    void config_withEmptyNode_usesDefaults() throws Exception {
        ConfigurationNode node = parseYaml("{}");
        WitherConfig config = node.get(WitherConfig.class);

        assertThat(config).isNotNull();
        assertThat(config.maxHealth()).isEqualTo(600.0);
        assertThat(config.attackDamage()).isEqualTo(40.0);
        assertThat(config.armor()).isEqualTo(8.0);
        assertThat(config.halfHealthThreshold()).isEqualTo(300.0);
        assertThat(config.minionCount()).isEqualTo(4);
        assertThat(config.halfHealthExplosionPower()).isEqualTo(4.0);
        assertThat(config.deathBossBarRadius()).isEqualTo(50);
        assertThat(config.deathExplosionPower()).isEqualTo(20.0);
    }

    @Test
    void config_roundTrip_preservesValues() throws Exception {
        WitherConfig original =
                new WitherConfig(800.0, 50.0, 10.0, 400.0, 6, 6.0, 75, 25.0);
        ConfigurationNode node = parseYaml("{}");
        node.set(WitherConfig.class, original);
        WitherConfig loaded = node.get(WitherConfig.class);

        assertThat(loaded).isNotNull();
        assertThat(loaded.maxHealth()).isEqualTo(800.0);
        assertThat(loaded.minionCount()).isEqualTo(6);
        assertThat(loaded.deathExplosionPower()).isEqualTo(25.0);
    }
}
