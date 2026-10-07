package de.kreiscraft.modules.effects;

import static org.assertj.core.api.Assertions.assertThat;

import de.kreiscraft.core.config.serializers.KreiscraftSerializers;
import java.time.Duration;
import org.bukkit.Particle;
import org.junit.jupiter.api.Test;
import org.spongepowered.configurate.BasicConfigurationNode;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.ConfigurationOptions;

class EffectsConfigTest {

    private static final ConfigurationOptions OPTS =
            ConfigurationOptions.defaults().serializers(KreiscraftSerializers.builder().build());

    @Test
    void defaults_areAppliedWhenKeysAbsent() {
        EffectsConfig cfg = new EffectsConfig(null, null, 0, 0, 0, 0, false);
        assertThat(cfg.defaultParticle()).isEqualTo(Particle.HAPPY_VILLAGER);
    }

    @Test
    void schedule_defaultsToFiveTicks() {
        EffectsConfig cfg = new EffectsConfig(null, null, 0, 0, 0, 0, false);
        assertThat(cfg.schedule()).isEqualTo(Duration.ofMillis(250));
        assertThat(cfg.schedule()).isEqualTo(Duration.ofMillis(5 * 50L));
    }

    @Test
    void roundTrip_parsesFromConfigurationNode() throws Exception {
        ConfigurationNode node = BasicConfigurationNode.root(OPTS);
        node.node("default-particle").set("FLAME");
        node.node("schedule").set("10t");
        node.node("count").set(10);
        node.node("offset-x").set(0.5);
        node.node("offset-y").set(0.5);
        node.node("offset-z").set(0.5);
        node.node("force").set(false);

        EffectsConfig cfg = node.get(EffectsConfig.class);

        assertThat(cfg).isNotNull();
        assertThat(cfg.defaultParticle()).isEqualTo(Particle.FLAME);
        assertThat(cfg.schedule()).isEqualTo(Duration.ofMillis(500));
        assertThat(cfg.count()).isEqualTo(10);
        assertThat(cfg.offsetX()).isEqualTo(0.5);
        assertThat(cfg.force()).isFalse();
    }

    @Test
    void roundTrip_parsesDefaultWiringValues() throws Exception {
        ConfigurationNode node = BasicConfigurationNode.root(OPTS);
        node.node("default-particle").set("HAPPY_VILLAGER");
        node.node("schedule").set("5t");
        node.node("count").set(25);
        node.node("offset-x").set(0.9);
        node.node("offset-y").set(0.9);
        node.node("offset-z").set(0.9);
        node.node("force").set(true);

        EffectsConfig cfg = node.get(EffectsConfig.class);

        assertThat(cfg).isNotNull();
        assertThat(cfg.defaultParticle()).isEqualTo(Particle.HAPPY_VILLAGER);
        assertThat(cfg.schedule()).isEqualTo(EffectsConfig.DEFAULT_SCHEDULE);
        assertThat(cfg.count()).isEqualTo(25);
        assertThat(cfg.offsetX()).isEqualTo(0.9);
        assertThat(cfg.force()).isTrue();
    }
}
