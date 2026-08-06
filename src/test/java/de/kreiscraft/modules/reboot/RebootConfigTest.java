package de.kreiscraft.modules.reboot;

import static org.assertj.core.api.Assertions.assertThat;

import de.kreiscraft.core.config.serializers.KreiscraftSerializers;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.ConfigurationOptions;
import org.spongepowered.configurate.serialize.TypeSerializerCollection;

class RebootConfigTest {

    private static ConfigurationOptions opts() {
        TypeSerializerCollection all = KreiscraftSerializers.builder().build();
        return ConfigurationOptions.defaults().serializers(all);
    }

    @Test
    void compactConstructor_fillsNullsWithDefaults() {
        RebootConfig cfg = new RebootConfig(null, null, null, null);

        assertThat(cfg.defaultCountdown()).isEqualTo(Duration.ofSeconds(60));
        assertThat(cfg.kickMessage()).isEqualTo("Server restarting");
        assertThat(cfg.titleSeconds()).isEqualTo(List.of(500, 60, 30, 10));
        assertThat(cfg.chatSeconds()).isEqualTo(List.of(10, 9, 8, 7, 6, 5, 4, 3, 2, 1));
    }

    @Test
    void defaults_appliedForMissingFields() throws Exception {
        CommentedConfigurationNode node = CommentedConfigurationNode.root(opts());
        // Set one field to make the node non-virtual so Configurate builds the record
        node.node("kick-message").set("Server restarting");

        RebootConfig cfg = node.get(RebootConfig.class);

        assertThat(cfg).isNotNull();
        assertThat(cfg.defaultCountdown()).isEqualTo(Duration.ofSeconds(60));
        assertThat(cfg.kickMessage()).isEqualTo("Server restarting");
        assertThat(cfg.titleSeconds()).isEqualTo(List.of(500, 60, 30, 10));
        assertThat(cfg.chatSeconds()).isEqualTo(List.of(10, 9, 8, 7, 6, 5, 4, 3, 2, 1));
    }

    @Test
    void roundTrip_customValues() throws Exception {
        CommentedConfigurationNode node = CommentedConfigurationNode.root(opts());
        node.node("default-countdown").set("2m");
        node.node("kick-message").set("Goodbye!");
        node.node("title-seconds").setList(Integer.class, List.of(120, 60, 30));
        node.node("chat-seconds").setList(Integer.class, List.of(5, 4, 3, 2, 1));

        RebootConfig cfg = node.get(RebootConfig.class);

        assertThat(cfg).isNotNull();
        assertThat(cfg.defaultCountdown()).isEqualTo(Duration.ofMinutes(2));
        assertThat(cfg.kickMessage()).isEqualTo("Goodbye!");
        assertThat(cfg.titleSeconds()).isEqualTo(List.of(120, 60, 30));
        assertThat(cfg.chatSeconds()).isEqualTo(List.of(5, 4, 3, 2, 1));
    }

    @Test
    void kickMessage_parsedCorrectly() throws Exception {
        CommentedConfigurationNode node = CommentedConfigurationNode.root(opts());
        node.node("kick-message").set("Server is restarting for maintenance");
        RebootConfig cfg = node.get(RebootConfig.class);
        assertThat(cfg.kickMessage()).isEqualTo("Server is restarting for maintenance");
    }
}
