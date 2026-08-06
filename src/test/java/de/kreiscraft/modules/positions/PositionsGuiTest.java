package de.kreiscraft.modules.positions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.LinkedHashMap;
import java.util.Map;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import xyz.xenondevs.invui.gui.Gui;

class PositionsGuiTest {

    private ServerMock server;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        server.addSimpleWorld("world");
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

        Gui gui = PositionsGui.build(positions, cfg, (p, e) -> {
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

        Gui gui = PositionsGui.build(positions, cfg, (p, e) -> {
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

        Gui gui = PositionsGui.build(positions, cfg, (p, e) -> {
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

        Gui gui = PositionsGui.build(positions, cfg, (p, e) -> {
        });

        assertThat(gui.getWidth()).isEqualTo(9);
        assertThat(gui.getHeight()).isEqualTo(4);
    }
}
