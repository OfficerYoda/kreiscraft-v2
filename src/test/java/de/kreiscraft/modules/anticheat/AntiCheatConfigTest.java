package de.kreiscraft.modules.anticheat;

import static org.assertj.core.api.Assertions.assertThat;

import de.kreiscraft.core.config.YamlConfigService;
import de.kreiscraft.core.config.serializers.KreiscraftSerializers;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.spongepowered.configurate.BasicConfigurationNode;
import org.spongepowered.configurate.ConfigurationOptions;

class AntiCheatConfigTest {

    private static final ConfigurationOptions OPTS =
            ConfigurationOptions.defaults().serializers(KreiscraftSerializers.builder().build());

    @Test
    void defaults_whenNodeEmpty() throws Exception {
        BasicConfigurationNode node = BasicConfigurationNode.root(OPTS);
        AntiCheatConfig cfg = node.get(AntiCheatConfig.class);
        assertThat(cfg.channel()).isEqualTo("kreiscraft:mod_checker");
        assertThat(cfg.kickOnFabricForge()).isFalse();
        assertThat(cfg.handshakeGraceDuration()).isEqualTo(Duration.ofSeconds(5));
    }

    @Test
    void explicitOptIn_fromYaml() throws Exception {
        BasicConfigurationNode node = BasicConfigurationNode.root(OPTS);
        node.node("kick-on-fabric-forge").set(true);
        AntiCheatConfig cfg = node.get(AntiCheatConfig.class);
        assertThat(cfg.kickOnFabricForge()).isTrue();
    }

    @Test
    void roundTrip_fromYaml() throws Exception {
        BasicConfigurationNode node = BasicConfigurationNode.root(OPTS);
        node.node("channel").set("kreiscraft:my_channel");
        node.node("kick-on-fabric-forge").set(false);
        node.node("handshake-grace-duration").set("10s");
        AntiCheatConfig cfg = node.get(AntiCheatConfig.class);
        assertThat(cfg.channel()).isEqualTo("kreiscraft:my_channel");
        assertThat(cfg.kickOnFabricForge()).isFalse();
        assertThat(cfg.handshakeGraceDuration()).isEqualTo(Duration.ofSeconds(10));
    }

    @Test
    void hackEntry_kindEnum_values() {
        assertThat(HackEntry.Kind.values()).containsExactly(HackEntry.Kind.CHEAT, HackEntry.Kind.XRAY);
    }

    @Test
    void shippedConfig_loaderKickIsOptInOff(@TempDir Path tmp) throws Exception {
        YamlConfigService cfg = new YamlConfigService(tmp, getClass().getClassLoader());
        var node = cfg.module("anti-cheat").node("kick-on-fabric-forge");
        assertThat(node.isNull()).as("kick-on-fabric-forge must be present in shipped config.yml").isFalse();
        assertThat(node.getBoolean()).as("shipped default must not enforce loader kicking").isFalse();
    }
}
