package de.kreiscraft.modules.settingsgui;

import static org.assertj.core.api.Assertions.assertThat;

import de.kreiscraft.core.config.ConfigField;
import de.kreiscraft.core.config.ConfigFieldType;
import de.kreiscraft.core.config.ConfigLifecycle;
import de.kreiscraft.core.config.ConfigValidators;
import de.kreiscraft.core.config.ServerConfigSchema;
import java.util.Optional;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * Unit coverage for the typed value transforms behind the Server Configuration controls.
 *
 * <p>These are the pure functions the GUI item handlers call; exercising them directly keeps the
 * control semantics independent of InvUI rendering.
 */
class ServerConfigControlsTest {

    private ServerMock server;
    private Player sensor;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        sensor = server.addPlayer("Editor");
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private static ConfigField field(ConfigFieldType type) {
        return new ConfigField(
                java.util.List.of("modules", "test", "value"),
                type,
                ConfigValidators.alwaysValid(),
                true,
                ConfigLifecycle.HOT_RELOAD);
    }

    @Test
    void boolean_togglesOnClick() {
        ConfigField field = field(ConfigFieldType.BOOLEAN);
        assertThat(ServerConfigControls.next(field, true, sensor)).isEqualTo(false);
        assertThat(ServerConfigControls.next(field, false, sensor)).isEqualTo(true);
        assertThat(ServerConfigControls.next(field, null, sensor)).isEqualTo(true);
    }

    @Test
    void integer_stepsInBothDirections() {
        ConfigField field = field(ConfigFieldType.INTEGER);
        assertThat(ServerConfigControls.next(field, 4, sensor)).isEqualTo(5);
        assertThat(ServerConfigControls.previous(field, 4, sensor)).isEqualTo(3);
    }

    @Test
    void double_stepsByATenth() {
        ConfigField field = field(ConfigFieldType.DOUBLE);
        assertThat(ServerConfigControls.next(field, 0.4, sensor)).isEqualTo(0.5);
        assertThat(ServerConfigControls.previous(field, 0.4, sensor)).isEqualTo(0.3);
    }

    @Test
    void duration_cyclesBoundedOptions() {
        ConfigField field = field(ConfigFieldType.DURATION);
        assertThat(ServerConfigControls.next(field, "5s", sensor)).isEqualTo("10s");
        assertThat(ServerConfigControls.previous(field, "5s", sensor)).isEqualTo("3s");
        assertThat(ServerConfigControls.next(field, "unknown", sensor)).isEqualTo("1s");
    }

    @Test
    void enum_cyclesDeclarationOrder() {
        ConfigField display =
                ServerConfigSchema.editableFields().stream()
                        .filter(f -> f.path().toString().contains("default-display"))
                        .findFirst()
                        .orElseThrow();
        assertThat(display.options()).containsExactly("CHAT", "ACTIONBAR", "TITLE");
        assertThat(ServerConfigControls.next(display, "ACTIONBAR", sensor)).isEqualTo("TITLE");
        assertThat(ServerConfigControls.previous(display, "ACTIONBAR", sensor)).isEqualTo("CHAT");
    }

    @Test
    void duration_usesTheFieldsOwnOptionsWhenDeclared() {
        ConfigField border =
                ServerConfigSchema.editableFields().stream()
                        .filter(f -> f.path().toString().contains("border-duration"))
                        .findFirst()
                        .orElseThrow();

        assertThat(border.options()).containsExactly("600s", "3600s", "30000s");
        assertThat(ServerConfigControls.next(border, "600s", sensor)).isEqualTo("3600s");
    }

    @Test
    void particleEnum_cyclesTheCuratedOptions() {
        ConfigField particle =
                ServerConfigSchema.editableFields().stream()
                        .filter(f -> f.path().toString().contains("default-particle"))
                        .findFirst()
                        .orElseThrow();

        assertThat(particle.options()).startsWith("HAPPY_VILLAGER");
        assertThat(ServerConfigControls.next(particle, "HAPPY_VILLAGER", sensor)).isEqualTo("FLAME");
    }

    @Test
    void material_cyclesBoundedOptions() {
        ConfigField material = field(ConfigFieldType.MATERIAL);
        assertThat(ServerConfigControls.next(material, "STONE", sensor)).isEqualTo("DIRT");
        assertThat(ServerConfigControls.previous(material, "STONE", sensor))
                .isEqualTo("DIAMOND");
    }

    @Test
    void location_setsToTheEditorsCurrentPosition() {
        ConfigField location = field(ConfigFieldType.LOCATION);
        PlayerMock player = server.addPlayer("Builder");
        player.teleport(new Location(player.getWorld(), 12.0, 65.0, -4.0));

        Object value = ServerConfigControls.next(location, null, player);

        assertThat(value).isInstanceOf(Location.class);
        Location result = (Location) value;
        assertThat(result.getBlockX()).isEqualTo(12);
        assertThat(result.getBlockZ()).isEqualTo(-4);
    }

    @Test
    void textEntry_typesAreRoutedToChat() {
        assertThat(ServerConfigControls.isTextEntry(field(ConfigFieldType.STRING))).isTrue();
        assertThat(ServerConfigControls.isTextEntry(field(ConfigFieldType.STRING_LIST))).isFalse();
        assertThat(ServerConfigControls.isTextEntry(field(ConfigFieldType.BOOLEAN))).isFalse();
        assertThat(ServerConfigControls.isTextEntry(field(ConfigFieldType.ENUM))).isFalse();
    }

    @Test
    void cycle_wrapsAroundAndHandlesMissingValues() {
        Optional<String> first =
                Optional.ofNullable(ServerConfigControls.cycle(java.util.List.of("a", "b"), "b", 1));
        assertThat(first).contains("a");
        assertThat(ServerConfigControls.cycle(java.util.List.of(), "a", 1)).isNull();
        assertThat(ServerConfigControls.cycle(java.util.List.of("a", "b"), null, -1)).isEqualTo("b");
    }
}
