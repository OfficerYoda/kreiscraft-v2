package de.kreiscraft.modules.positions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.kreiscraft.core.message.ConfigurateMessageService;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.plugin.PluginMock;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;
import xyz.xenondevs.invui.Click;
import xyz.xenondevs.invui.gui.Gui;

class PositionsGuiTest {

    private ServerMock server;
    private PluginMock plugin;
    private PlayerMock player;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        server.addSimpleWorld("world");
        plugin = MockBukkit.createMockPlugin();
        player = server.addPlayer("Alice");
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    // -----------------------------------------------------------------------
    // materialFor()
    // -----------------------------------------------------------------------

    @Test
    void materialFor_overworld_returnsGrassBlock() {
        World world = mock(World.class);
        when(world.getEnvironment()).thenReturn(World.Environment.NORMAL);
        Location loc = new Location(world, 0, 64, 0);
        assertThat(PositionsGui.materialFor(loc)).isEqualTo(Material.GRASS_BLOCK);
    }

    @Test
    void materialFor_nether_returnsNetherrack() {
        World world = mock(World.class);
        when(world.getEnvironment()).thenReturn(World.Environment.NETHER);
        Location loc = new Location(world, 0, 64, 0);
        assertThat(PositionsGui.materialFor(loc)).isEqualTo(Material.NETHERRACK);
    }

    @Test
    void materialFor_end_returnsEndStone() {
        World world = mock(World.class);
        when(world.getEnvironment()).thenReturn(World.Environment.THE_END);
        Location loc = new Location(world, 0, 64, 0);
        assertThat(PositionsGui.materialFor(loc)).isEqualTo(Material.END_STONE);
    }

    @Test
    void materialFor_nullWorld_returnsGrassBlock() {
        Location loc = new Location(null, 0, 64, 0);
        assertThat(PositionsGui.materialFor(loc)).isEqualTo(Material.GRASS_BLOCK);
    }

    // -----------------------------------------------------------------------
    // build()
    // -----------------------------------------------------------------------

    @Test
    void build_emptyPositions_returnsGuiWithoutPositionItems() {
        PositionsConfig cfg = new PositionsConfig(6, true);
        Map<String, PositionEntry> positions = new LinkedHashMap<>();

        Gui gui = PositionsGui.build(positions, cfg, player, (p, e) -> {
        });

        assertThat(gui).isNotNull();
        assertThat(gui.getWidth()).isEqualTo(9);
        assertThat(gui.getHeight()).isEqualTo(6);
    }

    @Test
    void build_withPositions_setsItemsInInnerSlots() {
        PositionsConfig cfg = new PositionsConfig(6, true);
        Location loc = new Location(server.getWorld("world"), 0, 64, 0);
        Map<String, PositionEntry> positions = new LinkedHashMap<>();
        positions.put("home", new PositionEntry(loc));

        Gui gui = PositionsGui.build(positions, cfg, player, (p, e) -> {
        });

        assertThat(gui).isNotNull();
        // Slot 10 = row 1, col 1 — first inner slot
        assertThat(gui.getItem(10)).isNotNull();
    }

    @Test
    void build_with28Positions_fillsAllInnerSlots() {
        PositionsConfig cfg = new PositionsConfig(6, true);
        Location loc = new Location(server.getWorld("world"), 0, 64, 0);
        Map<String, PositionEntry> positions = new LinkedHashMap<>();
        for (int i = 0; i < 28; i++) {
            positions.put("pos" + i, new PositionEntry(loc));
        }

        Gui gui = PositionsGui.build(positions, cfg, player, (p, e) -> {
        });

        assertThat(gui).isNotNull();
        // First inner slot (row 1, col 1 = slot 10) should have an item
        assertThat(gui.getItem(10)).isNotNull();
        // Last inner slot (row 4, col 7 = slot 43) should have an item
        assertThat(gui.getItem(43)).isNotNull();
    }

    @Test
    void build_guiDimensions_matchConfig() {
        PositionsConfig cfg = new PositionsConfig(4, false);
        Map<String, PositionEntry> positions = new LinkedHashMap<>();

        Gui gui = PositionsGui.build(positions, cfg, player, (p, e) -> {
        });

        assertThat(gui.getWidth()).isEqualTo(9);
        assertThat(gui.getHeight()).isEqualTo(4);
    }

    @Test
    void click_withoutTeleportPermission_doesNotTeleport() {
        AtomicInteger teleports = new AtomicInteger();
        Gui gui = guiWithOnePosition(teleports);

        gui.handleClick(10, new Click(player, org.bukkit.event.inventory.ClickType.LEFT));

        assertThat(teleports).hasValue(0);
    }

    @Test
    void click_withTeleportPermission_teleportsWhenEnabled() {
        player.addAttachment(plugin, "kreiscraft.positions.teleport", true);
        AtomicInteger teleports = new AtomicInteger();
        Gui gui = guiWithOnePosition(teleports);

        gui.handleClick(10, new Click(player, org.bukkit.event.inventory.ClickType.LEFT));

        assertThat(teleports).hasValue(1);
    }

    @Test
    void teleportPermission_defaultsToOperators() throws Exception {
        var pluginConfig = YamlConfigurationLoader.builder()
                .url(getClass().getClassLoader().getResource("plugin.yml"))
                .build()
                .load();

        assertThat(pluginConfig.node("permissions", "kreiscraft.positions.teleport", "default")
                .getString())
                        .isEqualTo("op");
    }

    private Gui guiWithOnePosition(AtomicInteger teleports) {
        Location location = new Location(server.getWorld("world"), 10, 64, 10);
        return PositionsGui.build(
                Map.of("home", new PositionEntry(location)),
                new PositionsConfig(6, true),
                player,
                (clickedPlayer, entry) -> teleports.incrementAndGet());
    }

    @Test
    void build_rendersColoredProvenanceAndActionLore(@TempDir Path tmp) throws IOException {
        ConfigurateMessageService messages =
                new ConfigurateMessageService(tmp, getClass().getClassLoader(), "en", server);
        player.addAttachment(plugin, "kreiscraft.positions.teleport", true);
        PlayerMock viewer = server.addPlayer("Viewer");
        Location location = new Location(server.getWorld("world"), 1, 64, 2);
        PositionEntry entry = new PositionEntry(
                location, UUID.randomUUID(), "Alex", Instant.parse("2026-10-08T12:34:56Z"));

        Gui gui = PositionsGui.build(
                Map.of("home", entry), new PositionsConfig(6, true), messages, player, (p, e) -> {
                });

        var meta = gui.getItem(10).getItemProvider(viewer).get().getItemMeta();
        assertThat(plain(meta.displayName())).isEqualTo("home");
        assertThat(meta.displayName().color()).isEqualTo(NamedTextColor.GOLD);
        List<Component> lore = meta.lore();
        assertThat(lore).extracting(PositionsGuiTest::plain).satisfiesExactly(
                line -> assertThat(line).isEqualTo("Location: world 1 64 2"),
                line -> assertThat(line).isEqualTo("Saved by: Alex"),
                line -> assertThat(line).startsWith("Saved at: Oct 8, 2026, 2:34:56"),
                line -> assertThat(line).isEqualTo("Click to teleport"));
        assertThat(colorOf(lore.get(0), "world 1 64 2")).isEqualTo(NamedTextColor.AQUA);
        assertThat(colorOf(lore.get(1), "Alex")).isEqualTo(NamedTextColor.GOLD);
        assertThat(colorOf(lore.get(2), "Oct 8, 2026, 2:34:56")).isEqualTo(NamedTextColor.AQUA);
        assertThat(lore.get(3).color()).isEqualTo(NamedTextColor.GREEN);
    }

    @Test
    void build_rendersLegacyFallback(@TempDir Path tmp) throws IOException {
        ConfigurateMessageService messages =
                new ConfigurateMessageService(tmp, getClass().getClassLoader(), "en", server);
        PlayerMock viewer = server.addPlayer("Viewer");
        PositionEntry entry = new PositionEntry(new Location(server.getWorld("world"), 1, 64, 2));

        Gui gui = PositionsGui.build(
                Map.of("legacy", entry), new PositionsConfig(6, false), messages, player, (p, e) -> {
                });

        List<Component> lore = gui.getItem(10).getItemProvider(viewer).get().getItemMeta().lore();
        assertThat(lore).extracting(PositionsGuiTest::plain).containsExactly(
                "Location: world 1 64 2", "Saved by: Unknown (legacy)", "Saved at: Unknown (legacy)");
    }

    private static String plain(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    private static NamedTextColor colorOf(Component component, String text) {
        if (component instanceof net.kyori.adventure.text.TextComponent tc && tc.content().contains(text)) {
            return (NamedTextColor) component.color();
        }
        for (Component child : component.children()) {
            NamedTextColor found = colorOf(child, text);
            if (found != null) {
                return found;
            }
        }
        return null;
    }
}
