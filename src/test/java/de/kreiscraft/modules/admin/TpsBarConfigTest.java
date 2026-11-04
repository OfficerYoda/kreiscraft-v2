package de.kreiscraft.modules.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.spongepowered.configurate.CommentedConfigurationNode;

class TpsBarConfigTest {

    @Test
    void defaults_workWhenAnExistingAdminConfigHasNoTpsBarSection() throws Exception {
        var node = CommentedConfigurationNode.root();
        node.node("links", "website").set("https://example.com");
        AdminConfig config = node.get(AdminConfig.class);

        assertThat(config.tpsBar()).isEqualTo(new TpsBarConfig(40.0, 50.0, 19.0, 15.0, 50, 120, 0.0, 50.0));
    }

    @Test
    void partialConfiguration_keepsDefaultsForMissingFields() throws Exception {
        var node = CommentedConfigurationNode.root();
        node.node("tps-bar", "mspt-green-max").set(35.0);
        node.node("tps-bar", "ping-yellow-max").set(150);
        AdminConfig config = node.get(AdminConfig.class);

        assertThat(config.tpsBar()).isEqualTo(new TpsBarConfig(35.0, 50.0, 19.0, 15.0, 50, 150, 0.0, 50.0));
    }

    @ParameterizedTest
    @CsvSource({
            "51, 50, 19, 15, 50, 120, 0, 50",
            "40, 50, 14, 15, 50, 120, 0, 50",
            "40, 50, 19, 15, 121, 120, 0, 50",
            "40, 50, 19, 15, 50, 120, 50, 50",
            "40, 50, 19, 15, 50, 120, 60, 50",
            "40, 50, 19, 15, 50, 120, 0, 0",
            "-1, 50, 19, 15, 50, 120, 0, 50",
            "40, 50, 21, 15, 50, 120, 0, 50"
    })
    void invalidConfiguration_rejectsCrossedThresholdsAndFillEndpoints(
            double msptGreen, double msptYellow, double tpsGreen, double tpsYellow,
            int pingGreen, int pingYellow, double empty, double full) {
        assertThatThrownBy(() -> new TpsBarConfig(msptGreen, msptYellow, tpsGreen, tpsYellow,
                pingGreen, pingYellow, empty, full)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void invalidConfiguration_rejectsNonFiniteValues() {
        assertThatThrownBy(() -> new TpsBarConfig(Double.NaN, null, null, null, null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TpsBarConfig(null, null, null, null, null, null, null, Double.POSITIVE_INFINITY))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
