package de.kreiscraft.modules.combat;

import static org.assertj.core.api.Assertions.assertThat;

import de.kreiscraft.core.config.serializers.KreiscraftSerializers;
import de.kreiscraft.modules.settingsgui.CombatDisplay;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.ConfigurationOptions;
import org.spongepowered.configurate.serialize.TypeSerializerCollection;

class CombatConfigTest {

    private static ConfigurationOptions opts() {
        TypeSerializerCollection all = KreiscraftSerializers.builder().build();
        return ConfigurationOptions.defaults().serializers(all);
    }

    @Test
    void compactConstructor_fillsNullsWithDefaults() {
        CombatConfig cfg = new CombatConfig(null, null, null, null);

        assertThat(cfg.tagDuration()).isEqualTo(Duration.ofSeconds(10));
        assertThat(cfg.disableElytra()).isTrue();
        assertThat(cfg.excludedWorlds()).isEqualTo(List.of("Lobby"));
        assertThat(cfg.defaultDisplay()).isEqualTo(CombatDisplay.ACTIONBAR);
    }

    @Test
    void defaults_appliedForMissingFields() throws Exception {
        CommentedConfigurationNode node = CommentedConfigurationNode.root(opts());
        // Set one field to make the node non-virtual so Configurate builds the record
        node.node("tag-duration").set("10s");

        CombatConfig cfg = node.get(CombatConfig.class);

        assertThat(cfg).isNotNull();
        assertThat(cfg.tagDuration()).isEqualTo(Duration.ofSeconds(10));
        assertThat(cfg.excludedWorlds()).isEqualTo(List.of("Lobby"));
        assertThat(cfg.defaultDisplay()).isEqualTo(CombatDisplay.ACTIONBAR);
    }

    @Test
    void roundTrip_customTagDuration() throws Exception {
        CommentedConfigurationNode node = CommentedConfigurationNode.root(opts());
        node.node("tag-duration").set("30s");
        node.node("disable-elytra").set(false);
        node.node("excluded-worlds").setList(String.class, List.of("Lobby", "Spawn"));
        node.node("default-display").set("TITLE");

        CombatConfig cfg = node.get(CombatConfig.class);

        assertThat(cfg).isNotNull();
        assertThat(cfg.tagDuration()).isEqualTo(Duration.ofSeconds(30));
        assertThat(cfg.disableElytra()).isFalse();
        assertThat(cfg.excludedWorlds()).containsExactly("Lobby", "Spawn");
        assertThat(cfg.defaultDisplay()).isEqualTo(CombatDisplay.TITLE);
    }

    @Test
    void roundTrip_displayChat() throws Exception {
        CommentedConfigurationNode node = CommentedConfigurationNode.root(opts());
        node.node("tag-duration").set("5s");
        node.node("default-display").set("CHAT");

        CombatConfig cfg = node.get(CombatConfig.class);

        assertThat(cfg).isNotNull();
        assertThat(cfg.defaultDisplay()).isEqualTo(CombatDisplay.CHAT);
    }
}
