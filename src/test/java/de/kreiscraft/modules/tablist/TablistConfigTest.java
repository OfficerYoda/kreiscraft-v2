package de.kreiscraft.modules.tablist;

import static org.assertj.core.api.Assertions.assertThat;

import de.kreiscraft.core.config.serializers.KreiscraftSerializers;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.spongepowered.configurate.BasicConfigurationNode;
import org.spongepowered.configurate.ConfigurationOptions;

class TablistConfigTest {

    private static final ConfigurationOptions OPTS =
            ConfigurationOptions.defaults().serializers(KreiscraftSerializers.builder().build());

    @Test
    void defaults_whenNodeEmpty() throws Exception {
        BasicConfigurationNode node = BasicConfigurationNode.root(OPTS);
        TablistConfig cfg = node.get(TablistConfig.class);
        assertThat(cfg).isNotNull();
        assertThat(cfg.updateInterval()).isEqualTo(Duration.ofMillis(20 * 50L));
        assertThat(cfg.headerTemplate()).isEqualTo("<gradient:#5555ff:#55ffff>Kreiscraft</gradient>");
        assertThat(cfg.footerTemplate()).isEqualTo("TPS: %tps% | Players: %server_players%");
    }

    @Test
    void roundTrip_fromYaml_usesShortYamlKeys() throws Exception {
        // YAML keys must stay "header" / "footer" per wiring block (@Setting maps them)
        BasicConfigurationNode node = BasicConfigurationNode.root(OPTS);
        node.node("update-interval").set("40t");
        node.node("header").set("<bold>My Server</bold>");
        node.node("footer").set("ping: %ping%");
        TablistConfig cfg = node.get(TablistConfig.class);
        assertThat(cfg).isNotNull();
        assertThat(cfg.updateInterval()).isEqualTo(Duration.ofMillis(40 * 50L));
        assertThat(cfg.headerTemplate()).isEqualTo("<bold>My Server</bold>");
        assertThat(cfg.footerTemplate()).isEqualTo("ping: %ping%");
    }

    @Test
    void roundTrip_wiringDefaults() throws Exception {
        BasicConfigurationNode node = BasicConfigurationNode.root(OPTS);
        node.node("update-interval").set("20t");
        node.node("header").set("<gradient:#5555ff:#55ffff>Kreiscraft</gradient>");
        node.node("footer").set("TPS: %tps% | Players: %server_players%");
        TablistConfig cfg = node.get(TablistConfig.class);
        assertThat(cfg).isNotNull();
        assertThat(cfg.updateInterval()).isEqualTo(Duration.ofMillis(1000L));
        assertThat(cfg.headerTemplate()).isEqualTo("<gradient:#5555ff:#55ffff>Kreiscraft</gradient>");
        assertThat(cfg.footerTemplate()).isEqualTo("TPS: %tps% | Players: %server_players%");
    }

    @Test
    void explicitConstruction_preservesValues() {
        TablistConfig cfg =
                new TablistConfig(Duration.ofSeconds(2), "<red>header</red>", "<green>footer</green>");
        assertThat(cfg.updateInterval()).isEqualTo(Duration.ofSeconds(2));
        assertThat(cfg.headerTemplate()).isEqualTo("<red>header</red>");
        assertThat(cfg.footerTemplate()).isEqualTo("<green>footer</green>");
    }
}
