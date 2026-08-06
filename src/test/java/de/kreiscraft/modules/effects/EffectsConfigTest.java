package de.kreiscraft.modules.effects;

import static org.assertj.core.api.Assertions.assertThat;

import org.bukkit.Particle;
import org.junit.jupiter.api.Test;
import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.ConfigurationNode;

class EffectsConfigTest {

    @Test
    void defaults_areAppliedWhenKeysAbsent() {
        EffectsConfig cfg = new EffectsConfig(null, 0, 0, 0, 0, false);
        assertThat(cfg.defaultParticle()).isEqualTo(Particle.HAPPY_VILLAGER);
    }

    @Test
    void roundTrip_parsesFromConfigurationNode() throws Exception {
        ConfigurationNode node = CommentedConfigurationNode.root();
        node.node("default-particle").set("FLAME");
        node.node("count").set(10);
        node.node("offset-x").set(0.5);
        node.node("offset-y").set(0.5);
        node.node("offset-z").set(0.5);
        node.node("force").set(false);

        EffectsConfig cfg = node.get(EffectsConfig.class);

        assertThat(cfg).isNotNull();
        assertThat(cfg.defaultParticle()).isEqualTo(Particle.FLAME);
        assertThat(cfg.count()).isEqualTo(10);
        assertThat(cfg.offsetX()).isEqualTo(0.5);
        assertThat(cfg.force()).isFalse();
    }

    @Test
    void roundTrip_parsesDefaultWiringValues() throws Exception {
        ConfigurationNode node = CommentedConfigurationNode.root();
        node.node("default-particle").set("HAPPY_VILLAGER");
        node.node("count").set(25);
        node.node("offset-x").set(0.9);
        node.node("offset-y").set(0.9);
        node.node("offset-z").set(0.9);
        node.node("force").set(true);

        EffectsConfig cfg = node.get(EffectsConfig.class);

        assertThat(cfg).isNotNull();
        assertThat(cfg.defaultParticle()).isEqualTo(Particle.HAPPY_VILLAGER);
        assertThat(cfg.count()).isEqualTo(25);
        assertThat(cfg.offsetX()).isEqualTo(0.9);
        assertThat(cfg.force()).isTrue();
    }
}
