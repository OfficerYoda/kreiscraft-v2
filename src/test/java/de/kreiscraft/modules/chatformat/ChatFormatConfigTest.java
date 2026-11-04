package de.kreiscraft.modules.chatformat;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.ConfigurationNode;

class ChatFormatConfigTest {

    @Test
    void defaults_allowMiniMessageInBody_isFalse() {
        ChatFormatConfig cfg = new ChatFormatConfig();
        assertThat(cfg.allowMiniMessageInBody()).isFalse();
    }

    @Test
    void defaults_format_isExpectedString() {
        ChatFormatConfig cfg = new ChatFormatConfig();
        assertThat(cfg.format())
                .isEqualTo("<kc-prefix> <player-prefix><name-colored><player-suffix><gray> » <reset><body>");
    }

    @Test
    void roundTrip_parsesFromConfigurationNode() throws Exception {
        ConfigurationNode node = CommentedConfigurationNode.root();
        node.node("allow-mini-message-in-body").set(true);
        node.node("format").set("<name>: <body>");

        ChatFormatConfig cfg = node.get(ChatFormatConfig.class);

        assertThat(cfg).isNotNull();
        assertThat(cfg.allowMiniMessageInBody()).isTrue();
        assertThat(cfg.format()).isEqualTo("<name>: <body>");
    }

    @Test
    void emptyNode_parsesToDefaults() throws Exception {
        ConfigurationNode node = CommentedConfigurationNode.root();

        ChatFormatConfig cfg = node.get(ChatFormatConfig.class);

        assertThat(cfg).isNotNull();
        assertThat(cfg.allowMiniMessageInBody()).isFalse();
        assertThat(cfg.format())
                .isEqualTo("<kc-prefix> <player-prefix><name-colored><player-suffix><gray> » <reset><body>");
    }

    @Test
    void compactConstructor_nullFormat_defaultsFormat() {
        ChatFormatConfig cfg = new ChatFormatConfig(false, null);
        assertThat(cfg.format())
                .isEqualTo("<kc-prefix> <player-prefix><name-colored><player-suffix><gray> » <reset><body>");
    }
}
