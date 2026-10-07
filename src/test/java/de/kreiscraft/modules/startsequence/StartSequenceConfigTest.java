package de.kreiscraft.modules.startsequence;

import static org.assertj.core.api.Assertions.assertThat;

import de.kreiscraft.core.config.serializers.KreiscraftSerializers;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.ConfigurationOptions;
import org.spongepowered.configurate.serialize.TypeSerializerCollection;

class StartSequenceConfigTest {

    private static CommentedConfigurationNode rootNode() {
        TypeSerializerCollection serializers = KreiscraftSerializers.builder().build();
        return CommentedConfigurationNode.root(ConfigurationOptions.defaults().serializers(serializers));
    }

    @Test
    void defaults_whenNodeIsEmpty_areApplied() throws Exception {
        CommentedConfigurationNode node = rootNode();

        StartSequenceConfig cfg = node.get(StartSequenceConfig.class);

        assertThat(cfg).isNotNull();
        assertThat(cfg.countdown()).isEqualTo(Duration.ofSeconds(10));
        assertThat(cfg.initialBorderSize()).isEqualTo(50.0);
        assertThat(cfg.targetBorderSize()).isEqualTo(30_000_000.0);
        assertThat(cfg.borderDuration()).isEqualTo(Duration.ofSeconds(30_000));
    }

    @Test
    void borderDuration_whenConfiguredInSeconds_isANonTickDuration() throws Exception {
        CommentedConfigurationNode node = rootNode();
        node.node("border-duration").set("30000s");

        StartSequenceConfig cfg = node.get(StartSequenceConfig.class);

        assertThat(cfg).isNotNull();
        assertThat(cfg.borderDuration()).isEqualTo(Duration.ofSeconds(30_000));
        // The stored Duration is unit-agnostic; the tick conversion happens only at the boundary.
        assertThat(cfg.borderDuration().toSeconds()).isEqualTo(30_000);
    }

    @Test
    void borderDuration_whenTickNotation_isConvertedToDuration() throws Exception {
        CommentedConfigurationNode node = rootNode();
        node.node("border-duration").set("600000t");

        StartSequenceConfig cfg = node.get(StartSequenceConfig.class);

        assertThat(cfg).isNotNull();
        assertThat(cfg.borderDuration()).isEqualTo(Duration.ofSeconds(30_000));
    }

    @Test
    void fields_whenFullYaml_areDeserializedCorrectly() throws Exception {
        CommentedConfigurationNode node = rootNode();
        node.node("countdown").set("5s");
        node.node("initial-border-size").set(100.0);
        node.node("target-border-size").set(5_000_000.0);
        node.node("border-duration").set("1000s");

        StartSequenceConfig cfg = node.get(StartSequenceConfig.class);

        assertThat(cfg).isNotNull();
        assertThat(cfg.countdown()).isEqualTo(Duration.ofSeconds(5));
        assertThat(cfg.initialBorderSize()).isEqualTo(100.0);
        assertThat(cfg.targetBorderSize()).isEqualTo(5_000_000.0);
        assertThat(cfg.borderDuration()).isEqualTo(Duration.ofSeconds(1000));
    }

    @Test
    void roundTrip_preservesValues() throws Exception {
        CommentedConfigurationNode node = rootNode();
        StartSequenceConfig original = new StartSequenceConfig(
                Duration.ofSeconds(15), 75.0, 20_000_000.0, Duration.ofSeconds(400_000));
        node.set(StartSequenceConfig.class, original);

        StartSequenceConfig restored = node.get(StartSequenceConfig.class);

        assertThat(restored).isEqualTo(original);
    }
}
