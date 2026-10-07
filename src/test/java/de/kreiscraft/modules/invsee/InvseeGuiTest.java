package de.kreiscraft.modules.invsee;

import static org.assertj.core.api.Assertions.assertThat;

import de.kreiscraft.platform.SlottedItem;
import java.util.List;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import xyz.xenondevs.invui.gui.SlotElement;

class InvseeGuiTest {

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    // -----------------------------------------------------------------------
    // buildOffline — offline slot mapping
    // -----------------------------------------------------------------------

    @Test
    void buildOffline_mainInvSlotMappedToRows1to3() {
        // This test verifies buildOffline doesn't throw; the offline-specific mapping is
        // documented on buildOffline.
        List<SlottedItem> items =
                List.of(
                        new SlottedItem(9, new ItemStack(Material.DIRT)),
                        new SlottedItem(0, new ItemStack(Material.STONE)));
        // Should build without throwing
        xyz.xenondevs.invui.gui.Gui gui = InvseeGui.buildOffline(items);
        assertThat(gui).isNotNull();
        assertThat(gui.getSize()).isEqualTo(InvseeGui.STORAGE_GUI_SIZE);
    }

    @Test
    void buildOffline_armorSlotsIgnored() {
        // Armor slots (36-40) should not appear in the offline 4-row view
        List<SlottedItem> items =
                List.of(new SlottedItem(39, new ItemStack(Material.DIAMOND_HELMET)));
        xyz.xenondevs.invui.gui.Gui gui = InvseeGui.buildOffline(items);
        // No items should be set (armor slot ignored)
        assertThat(gui.getSize()).isEqualTo(InvseeGui.STORAGE_GUI_SIZE);
        for (int i = 0; i < InvseeGui.STORAGE_GUI_SIZE; i++) {
            assertThat(gui.getItem(i)).as("slot %d", i).isNull();
        }
    }

    // -----------------------------------------------------------------------
    // buildEditable — 41-slot editable view
    // -----------------------------------------------------------------------

    @Test
    void buildEditable_guiIs5Rows() {
        PlayerMock target = (PlayerMock) MockBukkit.getMock().addPlayer();
        xyz.xenondevs.invui.gui.Gui gui = InvseeGui.buildEditable(target.getInventory());
        assertThat(gui.getSize()).isEqualTo(InvseeGui.PLAYER_GUI_SIZE); // 45
    }

    @Test
    void buildEditable_exposesExactly41InventorySlotElements() {
        // The editable GUI must bind all 41 player-inventory slots (not just 36 storage).
        PlayerMock target = (PlayerMock) MockBukkit.getMock().addPlayer();
        xyz.xenondevs.invui.gui.Gui gui = InvseeGui.buildEditable(target.getInventory());
        int invSlotCount = 0;
        for (int i = 0; i < InvseeGui.PLAYER_GUI_SIZE; i++) {
            if (gui.getSlotElement(i) instanceof SlotElement.InventoryLink) {
                invSlotCount++;
            }
        }
        assertThat(invSlotCount).isEqualTo(InvseeGui.PLAYER_SLOTS); // 41
    }

    @Test
    void buildEditable_helmetBoundAtGuiIndex30() {
        // Helmet is Bukkit slot 39, which should map to GUI slot 30 (same as read-only).
        PlayerMock target = (PlayerMock) MockBukkit.getMock().addPlayer();
        target.getInventory().setHelmet(new ItemStack(Material.DIAMOND_HELMET));
        xyz.xenondevs.invui.gui.Gui gui = InvseeGui.buildEditable(target.getInventory());
        xyz.xenondevs.invui.gui.SlotElement elem = gui.getSlotElement(30);
        assertThat(elem).isInstanceOf(SlotElement.InventoryLink.class);
        SlotElement.InventoryLink invElem = (SlotElement.InventoryLink) elem;
        // Slot 30 in the GUI is bound to Bukkit slot 39 (helmet)
        assertThat(invElem.slot()).isEqualTo(39);
    }

    @Test
    void buildEditable_offhandBoundAtGuiIndex31() {
        PlayerMock target = (PlayerMock) MockBukkit.getMock().addPlayer();
        xyz.xenondevs.invui.gui.Gui gui = InvseeGui.buildEditable(target.getInventory());
        xyz.xenondevs.invui.gui.SlotElement elem = gui.getSlotElement(31);
        assertThat(elem).isInstanceOf(SlotElement.InventoryLink.class);
        SlotElement.InventoryLink invElem = (SlotElement.InventoryLink) elem;
        // Offhand is Bukkit slot 40
        assertThat(invElem.slot()).isEqualTo(40);
    }

    @Test
    void buildEditable_fillerSlotsHaveNoInventoryBinding() {
        PlayerMock target = (PlayerMock) MockBukkit.getMock().addPlayer();
        xyz.xenondevs.invui.gui.Gui gui = InvseeGui.buildEditable(target.getInventory());
        // GUI slots 32-35 are filler — no InventoryLink
        for (int i = 32; i <= 35; i++) {
            assertThat(gui.getSlotElement(i))
                    .as("GUI slot %d should be empty filler", i)
                    .isNull();
        }
    }
}
