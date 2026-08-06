package de.kreiscraft.modules.updatemode;

import static org.assertj.core.api.Assertions.assertThat;

import de.kreiscraft.core.config.serializers.KreiscraftSerializers;
import java.util.List;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.junit.jupiter.api.Test;
import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.ConfigurationOptions;
import org.spongepowered.configurate.serialize.SerializationException;

class UpdateModeConfigTest {

    private static CommentedConfigurationNode emptyNode() {
        return CommentedConfigurationNode.root(
                ConfigurationOptions.defaults()
                        .serializers(KreiscraftSerializers.builder().build()));
    }

    @Test
    void defaults_whenConfigEmpty() throws SerializationException {
        UpdateModeConfig cfg = emptyNode().get(UpdateModeConfig.class);
        assertThat(cfg).isNotNull();
        assertThat(cfg.bypassPermission()).isEqualTo("kreiscraft.updatemode.bypass");
        assertThat(cfg.bypassNames()).isEmpty();
        assertThat(cfg.kickMessage())
                .isEqualTo(MiniMessage.miniMessage().deserialize("<red>Server im Wartungsmodus</red>"));
    }

    @Test
    void parsesExplicitValues() throws SerializationException {
        CommentedConfigurationNode node = emptyNode();
        node.node("bypass-permission").set("kreiscraft.custom.bypass");
        node.node("bypass-names").setList(String.class, List.of("admin", "tester"));
        node.node("kick-message").set("<yellow>Maintenance in progress");

        UpdateModeConfig cfg = node.get(UpdateModeConfig.class);
        assertThat(cfg).isNotNull();
        assertThat(cfg.bypassPermission()).isEqualTo("kreiscraft.custom.bypass");
        assertThat(cfg.bypassNames()).containsExactly("admin", "tester");
        assertThat(cfg.kickMessage())
                .isEqualTo(MiniMessage.miniMessage().deserialize("<yellow>Maintenance in progress"));
    }

    @Test
    void roundTrip_preservesValues() throws SerializationException {
        CommentedConfigurationNode node = emptyNode();
        node.node("bypass-permission").set("kreiscraft.updatemode.bypass");
        node.node("bypass-names").setList(String.class, List.of("alice"));
        node.node("kick-message").set("<red>Server im Wartungsmodus</red>");

        UpdateModeConfig first = node.get(UpdateModeConfig.class);
        assertThat(first).isNotNull();
        assertThat(first.bypassNames()).containsExactly("alice");
    }
}
