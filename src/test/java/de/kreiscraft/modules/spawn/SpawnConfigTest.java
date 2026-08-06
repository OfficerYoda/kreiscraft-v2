package de.kreiscraft.modules.spawn;

import static org.assertj.core.api.Assertions.assertThat;

import de.kreiscraft.core.config.serializers.KreiscraftSerializers;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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
        node.node("teleport-range").set(50);
        node.node("glide", "enabled").set(true);
        node.node("glide", "multiply-value").set(5);
        node.node("glide", "spawn-radius").set(20);
        node.node("glide", "launch-location", "world").set("world");
        node.node("glide", "launch-location", "x").set(10.0);
        node.node("glide", "launch-location", "y").set(64.0);
        node.node("glide", "launch-location", "z").set(-5.0);
        node.node("glide", "launch-location", "yaw").set(0.0);
        node.node("glide", "launch-location", "pitch").set(0.0);
        node.node("swap-hand-boost", "enabled").set(true);
        node.node("swap-hand-boost", "vector-multiplier").set(2.5);
        node.node("spawn-launch", "enabled").set(false);
        node.node("spawn-launch", "trigger", "world").set("world");
        node.node("spawn-launch", "trigger", "x").set(0.0);
        node.node("spawn-launch", "trigger", "y").set(64.0);
        node.node("spawn-launch", "trigger", "z").set(0.0);
        node.node("spawn-launch", "trigger", "yaw").set(0.0);
        node.node("spawn-launch", "trigger", "pitch").set(0.0);
        node.node("spawn-launch", "velocity", "x").set(0.0);
        node.node("spawn-launch", "velocity", "y").set(10.0);
        node.node("spawn-launch", "velocity", "z").set(0.0);

        SpawnConfig cfg = node.get(SpawnConfig.class);

        assertThat(cfg).isNotNull();
        assertThat(cfg.teleportRange()).isEqualTo(50);
        assertThat(cfg.glide().enabled()).isTrue();
        assertThat(cfg.glide().multiplyValue()).isEqualTo(5);
        assertThat(cfg.glide().spawnRadius()).isEqualTo(20);
        assertThat(cfg.glide().launchLocation()).isNotNull();
        assertThat(cfg.glide().launchLocation().getWorld().getName()).isEqualTo("world");
        assertThat(cfg.swapHandBoost().enabled()).isTrue();
        assertThat(cfg.swapHandBoost().vectorMultiplier()).isEqualTo(2.5);
        assertThat(cfg.spawnLaunch().enabled()).isFalse();
        assertThat(cfg.spawnLaunch().velocity().getY()).isEqualTo(10.0);
    }

    @Test
    void minimalConfig_appliesDefaults() throws Exception {
        CommentedConfigurationNode node = CommentedConfigurationNode.root(opts());
        // Leave all keys out — compact constructors supply safe numeric defaults.

        SpawnConfig cfg = node.get(SpawnConfig.class);

        assertThat(cfg).isNotNull();
        assertThat(cfg.teleportRange()).isEqualTo(100); // default: 0 → 100
        assertThat(cfg.glide()).isNotNull();
        // NOTE: boolean enabled is a Java primitive; when the key is absent Configurate
        // initialises it to false (the zero value). The production config.yml always
        // provides an explicit 'enabled: true', so we only assert the numeric defaults here.
        assertThat(cfg.glide().multiplyValue()).isEqualTo(3); // default: 0 → 3
        assertThat(cfg.glide().spawnRadius()).isEqualTo(30); // default: 0 → 30
        assertThat(cfg.swapHandBoost().enabled()).isFalse(); // boolean zero = false
        assertThat(cfg.swapHandBoost().vectorMultiplier()).isEqualTo(3.0); // default: 0 → 3.0
        assertThat(cfg.spawnLaunch().enabled()).isFalse(); // boolean zero = false
        // velocity all-zero → compact constructor applies (0, 5, 0)
        assertThat(cfg.spawnLaunch().velocity().getY()).isEqualTo(5.0);
    }

    @Test
    void locationSpec_missingKey_isNotSet() throws Exception {
        CommentedConfigurationNode node = CommentedConfigurationNode.root(opts());
        node.node("glide", "enabled").set(true);
        // launch-location omitted

        SpawnConfig cfg = node.get(SpawnConfig.class);

        assertThat(cfg.glide().launchLocation()).isNull();
    }
}
