package de.kreiscraft.modules.tablist;

import static org.assertj.core.api.Assertions.assertThat;

import de.kreiscraft.core.config.serializers.KreiscraftSerializers;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.spongepowered.configurate.BasicConfigurationNode;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.ConfigurationOptions;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

class TablistConfigTest {

    private static final ConfigurationOptions OPTS =
            ConfigurationOptions.defaults().serializers(KreiscraftSerializers.builder().build());

    private static final String SHIPPED_BANNER =
            "<gray><st>                <reset><gray>[<gold><bold>Kreiscraft<reset><gray>]<gray><st>                ";

    private static final String SHIPPED_HEADER =
            SHIPPED_BANNER + "\n\n<reset><gray>Playtime: <gold>%playtime%\n";
    private static final String SHIPPED_FOOTER = "\n<gray>Uhrzeit: <gold>%system_time%\n\n"
            + "<gray>Ping: <gold>%ping%   <gray>Tps: <gold>%tps%\n\n" + SHIPPED_BANNER;

    private static YamlConfigurationLoader yamlLoader(String yaml) {
        return YamlConfigurationLoader.builder()
                .source(() -> new BufferedReader(new StringReader(yaml)))
                .defaultOptions(OPTS)
                .build();
    }

    @Test
    void defaults_whenNodeEmpty() throws Exception {
        BasicConfigurationNode node = BasicConfigurationNode.root(OPTS);
        TablistConfig cfg = node.get(TablistConfig.class);
        assertThat(cfg).isNotNull();
        assertThat(cfg.updateInterval()).isEqualTo(Duration.ofMillis(20 * 50L));
        assertThat(cfg.headerTemplate()).startsWith("<gray><st>").contains("%playtime%");
        assertThat(cfg.footerTemplate())
                .contains("%system_time%")
                .contains("%ping%")
                .contains("%tps%")
                .endsWith("<gray><st>                ");
    }

    @Test
    void defaults_restoreOriginalPresentation() throws Exception {
        BasicConfigurationNode node = BasicConfigurationNode.root(OPTS);
        TablistConfig cfg = node.get(TablistConfig.class);
        assertThat(cfg).isNotNull();
        // The shipped defaults use the legacy banner, playtime clock, ping and TPS tokens.
        assertThat(cfg.headerTemplate()).startsWith("<gray><st>").contains("%playtime%");
        assertThat(cfg.footerTemplate())
                .contains("%system_time%")
                .contains("%ping%")
                .contains("%tps%")
                .endsWith("<gray><st>                ");
    }

    @Test
    void shippedConfig_parsesGoldPresentation() throws Exception {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("config.yml")) {
            assertThat(in).as("config.yml is on the test classpath").isNotNull();
            ConfigurationNode root = YamlConfigurationLoader.builder()
                    .source(() -> new BufferedReader(new InputStreamReader(in)))
                    .defaultOptions(OPTS)
                    .build()
                    .load();
            TablistConfig cfg = root.node("modules", "tablist").get(TablistConfig.class);
            assertThat(cfg).isNotNull();
            assertThat(cfg.updateInterval()).isEqualTo(Duration.ofMillis(1000L));
            assertThat(cfg.headerTemplate()).isEqualTo(SHIPPED_HEADER);
            assertThat(cfg.footerTemplate()).isEqualTo(SHIPPED_FOOTER);
        }
    }

    @Test
    void yamlDoubleQuotedEscapes_areDecodedIntoNewlines() throws Exception {
        ConfigurationNode root = yamlLoader(
                "update-interval: 20t\n"
                        + "header: \"line1\\nline2\"\n"
                        + "footer: \"tail   \"\n")
                                .load();
        TablistConfig cfg = root.get(TablistConfig.class);
        assertThat(cfg).isNotNull();
        assertThat(cfg.headerTemplate()).isEqualTo("line1\nline2");
        assertThat(cfg.footerTemplate()).isEqualTo("tail   ");
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
    void explicitConstruction_preservesValues() {
        TablistConfig cfg =
                new TablistConfig(Duration.ofSeconds(2), "<red>header</red>", "<green>footer</green>");
        assertThat(cfg.updateInterval()).isEqualTo(Duration.ofSeconds(2));
        assertThat(cfg.headerTemplate()).isEqualTo("<red>header</red>");
        assertThat(cfg.footerTemplate()).isEqualTo("<green>footer</green>");
    }
}
