package de.kreiscraft.modules.invsee;

import static org.assertj.core.api.Assertions.assertThat;

import de.kreiscraft.platform.OfflinePlayerSnapshot;
import de.kreiscraft.platform.SlottedItem;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import xyz.xenondevs.invui.gui.SlotElement;
import xyz.xenondevs.invui.inventory.VirtualInventory;

class InvseeGuiTest {

    private PlayerMock player;

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
        player = (PlayerMock) MockBukkit.getMock().addPlayer();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    // -----------------------------------------------------------------------
    // buildOfflineInventory — five-row editable snapshot layout
    // -----------------------------------------------------------------------

    @Test
    void buildOfflineInventory_isFiveRows() {
        xyz.xenondevs.invui.gui.Gui gui =
                InvseeGui.buildOfflineInventory(OfflinePlayerSnapshot.empty());

        assertThat(gui.getSize()).isEqualTo(InvseeGui.OFFLINE_INVENTORY_GUI_SIZE);
        assertThat(gui.getSize()).isEqualTo(45);
    }

    @Test
    void buildOfflineInventory_placesArmorAndOffhandInTopRow() {
        OfflinePlayerSnapshot snapshot =
                OfflinePlayerSnapshot.ofInventory(
                        List.of(
                                new SlottedItem(39, new ItemStack(Material.DIAMOND_HELMET)),
                                new SlottedItem(38, new ItemStack(Material.DIAMOND_CHESTPLATE)),
                                new SlottedItem(37, new ItemStack(Material.DIAMOND_LEGGINGS)),
                                new SlottedItem(36, new ItemStack(Material.DIAMOND_BOOTS)),
                                new SlottedItem(40, new ItemStack(Material.SHIELD))));

        xyz.xenondevs.invui.gui.Gui gui = InvseeGui.buildOfflineInventory(snapshot);

        assertThat(stackAt(gui, 0).getType()).isEqualTo(Material.DIAMOND_HELMET);
        assertThat(stackAt(gui, 1).getType()).isEqualTo(Material.DIAMOND_CHESTPLATE);
        assertThat(stackAt(gui, 2).getType()).isEqualTo(Material.DIAMOND_LEGGINGS);
        assertThat(stackAt(gui, 3).getType()).isEqualTo(Material.DIAMOND_BOOTS);
        assertThat(stackAt(gui, 4).getType()).isEqualTo(Material.SHIELD);
    }

    @Test
    void buildOfflineInventory_placesMainInventoryAfterTopRow() {
        OfflinePlayerSnapshot snapshot =
                OfflinePlayerSnapshot.ofInventory(
                        List.of(
                                new SlottedItem(9, new ItemStack(Material.STONE)),
                                new SlottedItem(35, new ItemStack(Material.DIRT))));

        xyz.xenondevs.invui.gui.Gui gui = InvseeGui.buildOfflineInventory(snapshot);

        assertThat(stackAt(gui, 9).getType()).isEqualTo(Material.STONE);
        assertThat(stackAt(gui, 35).getType()).isEqualTo(Material.DIRT);
    }

    @Test
    void buildOfflineInventory_placesHotbarInBottomRow() {
        OfflinePlayerSnapshot snapshot =
                OfflinePlayerSnapshot.ofInventory(
                        List.of(
                                new SlottedItem(0, new ItemStack(Material.STONE)),
                                new SlottedItem(8, new ItemStack(Material.DIRT))));

        xyz.xenondevs.invui.gui.Gui gui = InvseeGui.buildOfflineInventory(snapshot);

        assertThat(stackAt(gui, 36).getType()).isEqualTo(Material.STONE);
        assertThat(stackAt(gui, 44).getType()).isEqualTo(Material.DIRT);
    }

    @Test
    void buildOfflineInventory_fillerSlotsAreImmovableUnnamedGrayPanes() {
        xyz.xenondevs.invui.gui.Gui gui =
                InvseeGui.buildOfflineInventory(OfflinePlayerSnapshot.empty());

        for (int i = InvseeGui.OFFLINE_FILLER_FIRST; i <= InvseeGui.OFFLINE_FILLER_LAST; i++) {
            assertThat(stackAt(gui, i)).as("GUI slot %d should hold a gray pane", i).isNotNull();
            assertThat(stackAt(gui, i).getType()).as("GUI slot %d", i).isEqualTo(Material.GRAY_STAINED_GLASS_PANE);
            assertThat(plain(stackAt(gui, i).getItemMeta().displayName())).isEqualTo(" ");
            assertThat(gui.getSlotElement(i)).isNotInstanceOf(SlotElement.InventoryLink.class);
        }
    }

    @Test
    void buildOfflineInventory_fillerPanesCannotBeMovedByClicking() {
        xyz.xenondevs.invui.gui.Gui gui =
                InvseeGui.buildOfflineInventory(OfflinePlayerSnapshot.empty());

        gui.handleClick(
                InvseeGui.OFFLINE_FILLER_FIRST,
                new xyz.xenondevs.invui.Click(player, ClickType.LEFT));

        assertThat(stackAt(gui, InvseeGui.OFFLINE_FILLER_FIRST).getType())
                .isEqualTo(Material.GRAY_STAINED_GLASS_PANE);
    }

    @Test
    void buildOfflineInventory_emptyValidSlotsStayEmpty() {
        xyz.xenondevs.invui.gui.Gui gui =
                InvseeGui.buildOfflineInventory(OfflinePlayerSnapshot.empty());

        assertThat(stackAt(gui, 0)).as("empty helmet slot").isNull();
        assertThat(stackAt(gui, 9)).as("empty main slot").isNull();
        assertThat(stackAt(gui, 44)).as("empty hotbar slot").isNull();
    }

    @Test
    void buildOfflineInventory_rendersMetadataRichItem() {
        ItemStack sword = new ItemStack(Material.DIAMOND_SWORD);
        ItemMeta meta = sword.getItemMeta();
        meta.displayName(Component.text("Excalibur"));
        sword.setItemMeta(meta);
        OfflinePlayerSnapshot snapshot =
                OfflinePlayerSnapshot.ofInventory(List.of(new SlottedItem(0, sword)));

        xyz.xenondevs.invui.gui.Gui gui = InvseeGui.buildOfflineInventory(snapshot);

        ItemStack rendered = stackAt(gui, 36);
        assertThat(rendered.getType()).isEqualTo(Material.DIAMOND_SWORD);
        assertThat(plain(rendered.getItemMeta().displayName())).isEqualTo("Excalibur");
    }

    @Test
    void buildOfflineInventory_ignoresOutOfRangeSlots() {
        OfflinePlayerSnapshot snapshot =
                OfflinePlayerSnapshot.ofInventory(
                        List.of(new SlottedItem(99, new ItemStack(Material.STONE))));

        xyz.xenondevs.invui.gui.Gui gui = InvseeGui.buildOfflineInventory(snapshot);

        // Out-of-range slots have no GUI position; nothing else changes.
        assertThat(gui.getSize()).isEqualTo(45);
        for (int i = 0; i <= InvseeGui.OFFLINE_FILLER_LAST; i++) {
            if (i >= InvseeGui.OFFLINE_FILLER_FIRST) {
                assertThat(stackAt(gui, i).getType()).isEqualTo(Material.GRAY_STAINED_GLASS_PANE);
            } else {
                assertThat(stackAt(gui, i)).isNull();
            }
        }
    }

    // -----------------------------------------------------------------------
    // Editable offline inventory round-trips
    // -----------------------------------------------------------------------

    @Test
    void offlineInventoryContents_roundTripsEditsThroughToSlottedItems() {
        VirtualInventory contents =
                InvseeGui.offlineInventoryContents(
                        OfflinePlayerSnapshot.ofInventory(
                                List.of(new SlottedItem(39, new ItemStack(Material.IRON_HELMET)))));

        contents.setItem(null, 0, new ItemStack(Material.DIAMOND, 5));
        contents.setItem(null, 40, new ItemStack(Material.SHIELD));

        assertThat(InvseeGui.toSlottedItems(contents))
                .containsExactlyInAnyOrder(
                        new SlottedItem(0, new ItemStack(Material.DIAMOND, 5)),
                        new SlottedItem(39, new ItemStack(Material.IRON_HELMET)),
                        new SlottedItem(40, new ItemStack(Material.SHIELD)));
    }

    @Test
    void buildOfflineInventory_bindsEveryNonFillerSlotToEditableInventory() {
        VirtualInventory contents = InvseeGui.offlineInventoryContents(OfflinePlayerSnapshot.empty());
        xyz.xenondevs.invui.gui.Gui gui = InvseeGui.buildOfflineInventory(contents);

        for (int i = 0; i < InvseeGui.OFFLINE_INVENTORY_GUI_SIZE; i++) {
            boolean filler =
                    i >= InvseeGui.OFFLINE_FILLER_FIRST && i <= InvseeGui.OFFLINE_FILLER_LAST;
            if (filler) {
                assertThat(gui.getSlotElement(i)).isNotInstanceOf(SlotElement.InventoryLink.class);
            } else {
                assertThat(gui.getSlotElement(i)).isInstanceOf(SlotElement.InventoryLink.class);
            }
        }
    }

    // -----------------------------------------------------------------------
    // buildOfflineEnderChest — three-row editable snapshot
    // -----------------------------------------------------------------------

    @Test
    void buildOfflineEnderChest_isThreeRowsAndMapsSlots() {
        OfflinePlayerSnapshot snapshot =
                OfflinePlayerSnapshot.ofEnderItems(
                        List.of(
                                new SlottedItem(0, new ItemStack(Material.SHULKER_BOX)),
                                new SlottedItem(26, new ItemStack(Material.ENDER_PEARL)),
                                new SlottedItem(99, new ItemStack(Material.STONE))));

        xyz.xenondevs.invui.gui.Gui gui = InvseeGui.buildOfflineEnderChest(snapshot);

        assertThat(gui.getSize()).isEqualTo(InvseeGui.ENDER_CHEST_GUI_SIZE);
        assertThat(stackAt(gui, 0).getType()).isEqualTo(Material.SHULKER_BOX);
        assertThat(stackAt(gui, 26).getType()).isEqualTo(Material.ENDER_PEARL);
        assertThat(stackAt(gui, 1)).isNull();
    }

    @Test
    void offlineEnderChestContents_roundTripsEditsThroughToSlottedItems() {
        VirtualInventory contents =
                InvseeGui.offlineEnderChestContents(
                        OfflinePlayerSnapshot.ofEnderItems(
                                List.of(new SlottedItem(2, new ItemStack(Material.ENDER_PEARL)))));

        contents.setItem(null, 7, new ItemStack(Material.DIAMOND, 3));

        assertThat(InvseeGui.toSlottedItems(contents))
                .containsExactlyInAnyOrder(
                        new SlottedItem(2, new ItemStack(Material.ENDER_PEARL)),
                        new SlottedItem(7, new ItemStack(Material.DIAMOND, 3)));
    }

    // -----------------------------------------------------------------------
    // buildEditable — 41-slot editable view
    // -----------------------------------------------------------------------

    @Test
    void buildEditable_guiIs5Rows() {
        xyz.xenondevs.invui.gui.Gui gui = InvseeGui.buildEditable(player.getInventory());
        assertThat(gui.getSize()).isEqualTo(InvseeGui.PLAYER_GUI_SIZE); // 45
    }

    @Test
    void buildEditable_exposesExactly41InventorySlotElements() {
        // The editable GUI must bind all 41 player-inventory slots (not just 36 storage).
        xyz.xenondevs.invui.gui.Gui gui = InvseeGui.buildEditable(player.getInventory());
        int invSlotCount = 0;
        for (int i = 0; i < InvseeGui.PLAYER_GUI_SIZE; i++) {
            if (gui.getSlotElement(i) instanceof SlotElement.InventoryLink) {
                invSlotCount++;
            }
        }
        assertThat(invSlotCount).isEqualTo(InvseeGui.PLAYER_SLOTS); // 41
    }

    @Test
    void liveAndOfflineInventoriesUseTheSameBukkitSlotLayout() {
        VirtualInventory contents = InvseeGui.offlineInventoryContents(OfflinePlayerSnapshot.empty());
        xyz.xenondevs.invui.gui.Gui live = InvseeGui.buildEditable(player.getInventory());
        xyz.xenondevs.invui.gui.Gui offline = InvseeGui.buildOfflineInventory(contents);
        Integer[] expectedBukkitSlots = {
                39, 38, 37, 36, 40, null, null, null, null,
                9, 10, 11, 12, 13, 14, 15, 16, 17,
                18, 19, 20, 21, 22, 23, 24, 25, 26,
                27, 28, 29, 30, 31, 32, 33, 34, 35,
                0, 1, 2, 3, 4, 5, 6, 7, 8
        };

        for (int guiSlot = 0; guiSlot < expectedBukkitSlots.length; guiSlot++) {
            Integer expectedBukkitSlot = expectedBukkitSlots[guiSlot];
            if (expectedBukkitSlot == null) {
                assertThat(stackAt(live, guiSlot).getType()).isEqualTo(Material.GRAY_STAINED_GLASS_PANE);
                assertThat(stackAt(offline, guiSlot).getType()).isEqualTo(Material.GRAY_STAINED_GLASS_PANE);
                assertThat(plain(stackAt(live, guiSlot).getItemMeta().displayName())).isEqualTo(" ");
                assertThat(plain(stackAt(offline, guiSlot).getItemMeta().displayName())).isEqualTo(" ");
            } else {
                assertThat(((SlotElement.InventoryLink) live.getSlotElement(guiSlot)).slot())
                        .as("live GUI slot %d", guiSlot)
                        .isEqualTo(expectedBukkitSlot);
                assertThat(((SlotElement.InventoryLink) offline.getSlotElement(guiSlot)).slot())
                        .as("offline GUI slot %d", guiSlot)
                        .isEqualTo(expectedBukkitSlot);
            }
        }
    }

    @Test
    void buildEditable_writesChangesToLiveInventoryImmediately() {
        xyz.xenondevs.invui.gui.Gui gui = InvseeGui.buildEditable(player.getInventory());
        SlotElement.InventoryLink hotbarSlot = (SlotElement.InventoryLink) gui.getSlotElement(36);

        hotbarSlot.inventory().setItem(null, hotbarSlot.slot(), new ItemStack(Material.DIAMOND));

        assertThat(player.getInventory().getItem(0)).isEqualTo(new ItemStack(Material.DIAMOND));
    }

    private ItemStack stackAt(xyz.xenondevs.invui.gui.Gui gui, int slot) {
        SlotElement element = gui.getSlotElement(slot);
        return element == null ? null : element.getItemStack(player);
    }

    private static String plain(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }
}
