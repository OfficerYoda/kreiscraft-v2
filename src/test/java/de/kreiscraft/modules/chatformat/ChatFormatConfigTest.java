package de.kreiscraft.modules.chatformat;

import static org.assertj.core.api.Assertions.assertThat;

import org.bukkit.event.EventPriority;
import org.junit.jupiter.api.Test;
import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.ConfigurationNode;

class ChatFormatConfigTest {

    @Test
    void defaults_eventPriority_isLowest() {
        ChatFormatConfig cfg = new ChatFormatConfig();
        assertThat(cfg.eventPriority()).isEqualTo(EventPriority.LOWEST);
    }

    @Test
    void defaults_allowMiniMessageInBody_isFalse() {
        ChatFormatConfig cfg = new ChatFormatConfig();
        assertThat(cfg.allowMiniMessageInBody()).isFalse();
    }

    @Test
    void defaults_format_isExpectedString() {
        ChatFormatConfig cfg = new ChatFormatConfig();
        assertThat(cfg.format())
                .isEqualTo("<team-prefix><name-colored><team-suffix><gray>: <reset><body>");
    }

    @Test
    void roundTrip_parsesFromConfigurationNode() throws Exception {
        ConfigurationNode node = CommentedConfigurationNode.root();
        node.node("event-priority").set("HIGH");
        node.node("allow-mini-message-in-body").set(true);
        node.node("format").set("<name>: <body>");

        ChatFormatConfig cfg = node.get(ChatFormatConfig.class);

        assertThat(cfg).isNotNull();
        assertThat(cfg.eventPriority()).isEqualTo(EventPriority.HIGH);
        assertThat(cfg.allowMiniMessageInBody()).isTrue();
        assertThat(cfg.format()).isEqualTo("<name>: <body>");
    }

    @Test
    void emptyNode_parsesToDefaults() throws Exception {
        ConfigurationNode node = CommentedConfigurationNode.root();

        ChatFormatConfig cfg = node.get(ChatFormatConfig.class);

        assertThat(cfg).isNotNull();
        assertThat(cfg.eventPriority()).isEqualTo(EventPriority.LOWEST);
        assertThat(cfg.allowMiniMessageInBody()).isFalse();
        assertThat(cfg.format())
                .isEqualTo("<team-prefix><name-colored><team-suffix><gray>: <reset><body>");
    }

    @Test
    void compactConstructor_nullEventPriority_defaultsToLowest() {
        ChatFormatConfig cfg = new ChatFormatConfig(null, false, null);
        assertThat(cfg.eventPriority()).isEqualTo(EventPriority.LOWEST);
        assertThat(cfg.format())
                .isEqualTo("<team-prefix><name-colored><team-suffix><gray>: <reset><body>");
    }
}
