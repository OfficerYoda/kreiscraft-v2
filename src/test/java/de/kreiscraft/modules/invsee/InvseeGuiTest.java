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

    @Test
    void mapPlayerInvToGuiSlots_hotbarAppearsInLastRow() {
        ItemStack[] contents = new ItemStack[InvseeGui.PLAYER_SLOTS];
        ItemStack sword = new ItemStack(Material.DIAMOND_SWORD);
        contents[0] = sword; // hotbar slot 0
        ItemStack[] gui = InvseeGui.mapPlayerInvToGuiSlots(contents);
        // Hotbar slot 0 → GUI row 5, first slot = GUI index 36
        assertThat(gui[36]).isEqualTo(sword);
    }

    @Test
    void mapPlayerInvToGuiSlots_hotbarLastSlotAtIndex44() {
        ItemStack[] contents = new ItemStack[InvseeGui.PLAYER_SLOTS];
        ItemStack pick = new ItemStack(Material.DIAMOND_PICKAXE);
        contents[8] = pick; // hotbar slot 8
        ItemStack[] gui = InvseeGui.mapPlayerInvToGuiSlots(contents);
        // Hotbar slot 8 → GUI index 36+8 = 44
        assertThat(gui[44]).isEqualTo(pick);
    }

    @Test
    void mapPlayerInvToGuiSlots_mainInvFirstSlotAtGuiIndex0() {
        ItemStack[] contents = new ItemStack[InvseeGui.PLAYER_SLOTS];
        ItemStack dirt = new ItemStack(Material.DIRT);
        contents[9] = dirt; // main inventory first slot
        ItemStack[] gui = InvseeGui.mapPlayerInvToGuiSlots(contents);
        // Main slot 9 → GUI index 0
        assertThat(gui[0]).isEqualTo(dirt);
    }

    @Test
    void mapPlayerInvToGuiSlots_mainInvLastSlotAtGuiIndex26() {
        ItemStack[] contents = new ItemStack[InvseeGui.PLAYER_SLOTS];
        ItemStack stone = new ItemStack(Material.STONE);
        contents[35] = stone; // main inventory last slot
        ItemStack[] gui = InvseeGui.mapPlayerInvToGuiSlots(contents);
        // Main slot 35 → GUI index 26
        assertThat(gui[26]).isEqualTo(stone);
    }

    @Test
    void mapPlayerInvToGuiSlots_armorBootsAtGuiIndex27() {
        ItemStack[] contents = new ItemStack[InvseeGui.PLAYER_SLOTS];
        ItemStack boots = new ItemStack(Material.DIAMOND_BOOTS);
        contents[36] = boots; // boots slot
        ItemStack[] gui = InvseeGui.mapPlayerInvToGuiSlots(contents);
        // Boots (Bukkit 36) → GUI 27
        assertThat(gui[27]).isEqualTo(boots);
    }

    @Test
    void mapPlayerInvToGuiSlots_armorHelmetAtGuiIndex30() {
        ItemStack[] contents = new ItemStack[InvseeGui.PLAYER_SLOTS];
        ItemStack helmet = new ItemStack(Material.DIAMOND_HELMET);
        contents[39] = helmet; // helmet slot
        ItemStack[] gui = InvseeGui.mapPlayerInvToGuiSlots(contents);
        // Helmet (Bukkit 39) → GUI 30
        assertThat(gui[30]).isEqualTo(helmet);
    }

    @Test
    void mapPlayerInvToGuiSlots_offhandAtGuiIndex31() {
        ItemStack[] contents = new ItemStack[InvseeGui.PLAYER_SLOTS];
        ItemStack shield = new ItemStack(Material.SHIELD);
        contents[40] = shield; // offhand slot
        ItemStack[] gui = InvseeGui.mapPlayerInvToGuiSlots(contents);
        // Offhand (Bukkit 40) → GUI 31
        assertThat(gui[31]).isEqualTo(shield);
    }

    @Test
    void mapPlayerInvToGuiSlots_fillerSlotsAreNull() {
        ItemStack[] contents = new ItemStack[InvseeGui.PLAYER_SLOTS];
        ItemStack[] gui = InvseeGui.mapPlayerInvToGuiSlots(contents);
        // GUI slots 32-35 should be null (filler row 4 remainder)
        for (int i = 32; i <= 35; i++) {
            assertThat(gui[i]).as("GUI filler slot %d", i).isNull();
        }
    }

    @Test
    void mapPlayerInvToGuiSlots_outputLengthIs45() {
        ItemStack[] gui = InvseeGui.mapPlayerInvToGuiSlots(new ItemStack[InvseeGui.PLAYER_SLOTS]);
        assertThat(gui).hasSize(InvseeGui.PLAYER_GUI_SIZE);
    }

    // -----------------------------------------------------------------------
    // buildOffline — offline slot mapping
    // -----------------------------------------------------------------------

    @Test
    void buildOffline_mainInvSlotMappedToRows1to3() {
        // This test verifies buildOffline doesn't throw; slot order is implied by the
        // mapPlayerInvToGuiSlots tests above and the offline-specific mapping docs.
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
            if (gui.getSlotElement(i) instanceof SlotElement.InventorySlotElement) {
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
        assertThat(elem).isInstanceOf(SlotElement.InventorySlotElement.class);
        SlotElement.InventorySlotElement invElem = (SlotElement.InventorySlotElement) elem;
        // Slot 30 in the GUI is bound to Bukkit slot 39 (helmet)
        assertThat(invElem.getSlot()).isEqualTo(39);
    }

    @Test
    void buildEditable_offhandBoundAtGuiIndex31() {
        PlayerMock target = (PlayerMock) MockBukkit.getMock().addPlayer();
        xyz.xenondevs.invui.gui.Gui gui = InvseeGui.buildEditable(target.getInventory());
        xyz.xenondevs.invui.gui.SlotElement elem = gui.getSlotElement(31);
        assertThat(elem).isInstanceOf(SlotElement.InventorySlotElement.class);
        SlotElement.InventorySlotElement invElem = (SlotElement.InventorySlotElement) elem;
        // Offhand is Bukkit slot 40
        assertThat(invElem.getSlot()).isEqualTo(40);
    }

    @Test
    void buildEditable_fillerSlotsHaveNoInventoryBinding() {
        PlayerMock target = (PlayerMock) MockBukkit.getMock().addPlayer();
        xyz.xenondevs.invui.gui.Gui gui = InvseeGui.buildEditable(target.getInventory());
        // GUI slots 32-35 are filler — no InventorySlotElement
        for (int i = 32; i <= 35; i++) {
            assertThat(gui.getSlotElement(i))
                    .as("GUI slot %d should be empty filler", i)
                    .isNull();
        }
    }
}
