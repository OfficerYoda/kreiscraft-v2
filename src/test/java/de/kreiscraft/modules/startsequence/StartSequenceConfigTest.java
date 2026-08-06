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
        assertThat(cfg.phase1Duration()).isEqualTo(Duration.ofSeconds(550_000));
        assertThat(cfg.phase2Duration()).isEqualTo(Duration.ofSeconds(300_000));
        assertThat(cfg.phase1To2Delay()).isEqualTo(Duration.ofMillis(7_500));
        assertThat(cfg.phase2ToInstantDelay()).isEqualTo(Duration.ofMillis(15_000));
    }

    @Test
    void fields_whenFullYaml_areDeserializedCorrectly() throws Exception {
        CommentedConfigurationNode node = rootNode();
        node.node("countdown").set("5s");
        node.node("initial-border-size").set(100.0);
        node.node("target-border-size").set(5_000_000.0);
        node.node("phase-1-duration").set("1000s");
        node.node("phase-2-duration").set("500s");
        node.node("phase-1-to-2-delay").set("3000ms");
        node.node("phase-2-to-instant-delay").set("6000ms");

        StartSequenceConfig cfg = node.get(StartSequenceConfig.class);

        assertThat(cfg).isNotNull();
        assertThat(cfg.countdown()).isEqualTo(Duration.ofSeconds(5));
        assertThat(cfg.initialBorderSize()).isEqualTo(100.0);
        assertThat(cfg.targetBorderSize()).isEqualTo(5_000_000.0);
        assertThat(cfg.phase1Duration()).isEqualTo(Duration.ofSeconds(1000));
        assertThat(cfg.phase2Duration()).isEqualTo(Duration.ofSeconds(500));
        assertThat(cfg.phase1To2Delay()).isEqualTo(Duration.ofMillis(3000));
        assertThat(cfg.phase2ToInstantDelay()).isEqualTo(Duration.ofMillis(6000));
    }

    @Test
    void roundTrip_preservesValues() throws Exception {
        CommentedConfigurationNode node = rootNode();
        StartSequenceConfig original =
                new StartSequenceConfig(
                        Duration.ofSeconds(15),
                        75.0,
                        20_000_000.0,
                        Duration.ofSeconds(400_000),
                        Duration.ofSeconds(200_000),
                        Duration.ofMillis(5_000),
                        Duration.ofMillis(10_000));
        node.set(StartSequenceConfig.class, original);

        StartSequenceConfig restored = node.get(StartSequenceConfig.class);

        assertThat(restored).isEqualTo(original);
    }
}
