package de.kreiscraft.modules.admin;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import org.junit.jupiter.api.Test;
import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.ConfigurationNode;

class AdminConfigTest {

    @Test
    void defaults_whenLinksAbsent() throws Exception {
        ConfigurationNode node = CommentedConfigurationNode.root();
        AdminConfig cfg = node.get(AdminConfig.class);
        if (cfg == null)
            cfg = new AdminConfig(null, null);
        assertThat(cfg.links()).isNotNull();
        assertThat(cfg.links().website()).isEqualTo(URI.create("https://kreiscraft.de"));
        assertThat(cfg.links().discord().toString()).contains("discord");
        assertThat(cfg.links().youtube().toString()).contains("youtube");
    }

    @Test
    void defaults_timezoneIsUtc() throws Exception {
        ConfigurationNode node = CommentedConfigurationNode.root();
        AdminConfig cfg = node.get(AdminConfig.class);
        if (cfg == null)
            cfg = new AdminConfig(null, null);
        assertThat(cfg.timezone()).isEqualTo("UTC");
    }

    @Test
    void parsesLinks_whenPresent() throws Exception {
        ConfigurationNode node = CommentedConfigurationNode.root();
        node.node("links", "website").set("https://example.com");
        node.node("links", "discord").set("https://discord.gg/test");
        node.node("links", "youtube").set("https://youtube.com/@test");
        AdminConfig cfg = node.get(AdminConfig.class);
        assertThat(cfg).isNotNull();
        assertThat(cfg.links().website()).isEqualTo(URI.create("https://example.com"));
        assertThat(cfg.links().discord()).isEqualTo(URI.create("https://discord.gg/test"));
        assertThat(cfg.links().youtube()).isEqualTo(URI.create("https://youtube.com/@test"));
    }

    @Test
    void parsesTimezone_whenPresent() throws Exception {
        ConfigurationNode node = CommentedConfigurationNode.root();
        node.node("timezone").set("Europe/Berlin");
        AdminConfig cfg = node.get(AdminConfig.class);
        assertThat(cfg).isNotNull();
        assertThat(cfg.timezone()).isEqualTo("Europe/Berlin");
    }
}
