package de.kreiscraft.modules.jail;

import static org.assertj.core.api.Assertions.assertThat;

import de.kreiscraft.core.config.serializers.KreiscraftSerializers;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.ConfigurationOptions;

class JailConfigTest {

    private static final ConfigurationOptions OPTS =
            ConfigurationOptions.defaults()
                    .serializers(KreiscraftSerializers.builder().build());

    @Test
    void defaults_blockedCommands_whenMissing() throws Exception {
        CommentedConfigurationNode node = CommentedConfigurationNode.root(OPTS);

        JailConfig cfg = node.get(JailConfig.class);

        assertThat(cfg).isNotNull();
        assertThat(cfg.blockedCommands()).containsExactlyInAnyOrder("msg", "tell", "w", "pay", "r");
    }

    @Test
    void roundTrip_blockedCommands() throws Exception {
        CommentedConfigurationNode node = CommentedConfigurationNode.root(OPTS);
        node.node("blocked-commands").setList(String.class, List.of("pay", "msg"));

        JailConfig cfg = node.get(JailConfig.class);

        assertThat(cfg).isNotNull();
        assertThat(cfg.blockedCommands()).containsExactly("pay", "msg");
    }

    @Test
    void constructor_nullBlockedCommands_usesDefaults() {
        JailConfig cfg = new JailConfig(null);
        assertThat(cfg.blockedCommands()).containsExactlyInAnyOrder("msg", "tell", "w", "pay", "r");
    }

    @Test
    void constructor_emptyBlockedCommands_usesDefaults() {
        JailConfig cfg = new JailConfig(List.of());
        assertThat(cfg.blockedCommands()).containsExactlyInAnyOrder("msg", "tell", "w", "pay", "r");
    }
}
