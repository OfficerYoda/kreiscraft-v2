package de.kreiscraft.modules.chatformat;

import static org.assertj.core.api.Assertions.assertThat;

import org.bukkit.event.EventPriority;
import org.junit.jupiter.api.Test;
import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.ConfigurationNode;

class ChatFormatConfigTest {

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

}
