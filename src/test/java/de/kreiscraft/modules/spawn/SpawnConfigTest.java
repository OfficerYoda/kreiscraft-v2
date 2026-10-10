package de.kreiscraft.modules.spawn;

import static org.assertj.core.api.Assertions.assertThat;

import de.kreiscraft.core.config.serializers.KreiscraftSerializers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.ConfigurationOptions;

class SpawnConfigTest {

    private ServerMock server;

    @BeforeEach
    void setUp() {
        // MockBukkit is needed so Bukkit.getWorld() works inside LocationSerializer
        server = MockBukkit.mock();
        server.addSimpleWorld("world");
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private ConfigurationOptions opts() {
        return ConfigurationOptions.defaults()
                .serializers(KreiscraftSerializers.builder().build());
    }

    @Test
    void fullConfig_parsesAllFields() throws Exception {
        CommentedConfigurationNode node = CommentedConfigurationNode.root(opts());
        node.node("spawn-radius").set(75);
        node.node("glide", "enabled").set(true);
        node.node("glide", "multiply-value").set(5);
        node.node("glide-boost", "enabled").set(true);
        node.node("glide-boost", "boost-strength").set(3.0);

        SpawnConfig cfg = node.get(SpawnConfig.class);

        assertThat(cfg).isNotNull();
        assertThat(cfg.spawnRadius()).isEqualTo(75);
        assertThat(cfg.glide().enabled()).isTrue();
        assertThat(cfg.glide().multiplyValue()).isEqualTo(5);
        assertThat(cfg.glideBoost().enabled()).isTrue();
        assertThat(cfg.glideBoost().boostStrength()).isEqualTo(3.0);
    }

    @Test
    void minimalConfig_appliesDefaults() throws Exception {
        CommentedConfigurationNode node = CommentedConfigurationNode.root(opts());
        // Leave all keys out — compact constructors supply safe numeric defaults.

        SpawnConfig cfg = node.get(SpawnConfig.class);

        assertThat(cfg).isNotNull();
        assertThat(cfg.spawnRadius()).isEqualTo(SpawnConfig.DEFAULT_SPAWN_RADIUS); // 0 → 50
        assertThat(cfg.glide()).isNotNull();
        // NOTE: boolean enabled is a Java primitive; when the key is absent Configurate
        // initialises it to false (the zero value). The production config.yml always
        // provides an explicit 'enabled: true', so we only assert the numeric defaults here.
        assertThat(cfg.glide().multiplyValue()).isEqualTo(3); // default: 0 → 3
        assertThat(cfg.glideBoost().enabled()).isFalse(); // boolean zero = false
        assertThat(cfg.glideBoost().boostStrength()).isEqualTo(2.0); // default: 0 → 2.0
    }

    @Test
    void spawnRadius_defaultsToFifty_whenAbsentOrNonPositive() {
        assertThat(new SpawnConfig(0, null, null).spawnRadius()).isEqualTo(50);
        assertThat(new SpawnConfig(-1, null, null).spawnRadius()).isEqualTo(50);
        assertThat(new SpawnConfig(120, null, null).spawnRadius()).isEqualTo(120);
    }
}
