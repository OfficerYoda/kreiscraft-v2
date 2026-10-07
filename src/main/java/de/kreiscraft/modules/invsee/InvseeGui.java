package de.kreiscraft.modules.invsee;

import de.kreiscraft.platform.OfflinePlayerSnapshot;
import de.kreiscraft.platform.SlottedItem;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import xyz.xenondevs.invui.gui.Gui;
import xyz.xenondevs.invui.gui.SlotElement;
import xyz.xenondevs.invui.inventory.ReferencingInventory;
import xyz.xenondevs.invui.item.Item;

/**
 * Factory for InvUI {@link Gui} instances used by the invsee and enderchest commands.
 *
 * <p>Slot mapping for the 5-row editable player-inventory view (GUI slots 0-44):
 *
 * <ul>
 *   <li>Rows 1–3 (GUI 0–26): main inventory (Bukkit slots 9–35)
 *   <li>Row 4 (GUI 27–31): armor boots→helmet (Bukkit 36–39), offhand (Bukkit 40);
 *       GUI slots 32–35 are left empty (filler).
 *   <li>Row 5 (GUI 36–44): hotbar (Bukkit slots 0–8)
 * </ul>
 *
 * <p>Slot mapping for the read-only 5-row offline snapshot (GUI slots 0–44):
 *
 * <ul>
 *   <li>Row 1 (GUI 0–4): helmet, chestplate, leggings, boots, offhand (Bukkit 39, 38, 37, 36, 40);
 *       GUI slots 5–8 are immovable {@link Material#BARRIER} placeholders.
 *   <li>Rows 2–4 (GUI 9–35): main inventory (Bukkit slots 9–35) — GUI index equals the Bukkit slot.
 *   <li>Row 5 (GUI 36–44): hotbar (Bukkit slots 0–8).
 * </ul>
 */
public final class InvseeGui {

    private InvseeGui() {}

    // -----------------------------------------------------------------------
    // Slot-mapping constants (package-private for tests)
    // -----------------------------------------------------------------------

    /** Number of Bukkit player-inventory slots that are mapped (0–40). */
    static final int PLAYER_SLOTS = 41;

    /** GUI size for the full 5-row player-inventory view. */
    static final int PLAYER_GUI_SIZE = 45;

    /** GUI size for the read-only 5-row offline snapshot. */
    static final int OFFLINE_INVENTORY_GUI_SIZE = 45;

    /** GUI size for the 3-row ender-chest view. */
    static final int ENDER_CHEST_GUI_SIZE = 27;

    /** First GUI slot in the offline top row that has no inventory position. */
    static final int OFFLINE_FILLER_FIRST = 5;

    /** Last GUI slot in the offline top row that has no inventory position. */
    static final int OFFLINE_FILLER_LAST = 8;

    // -----------------------------------------------------------------------
    // Editable: online player — 5-row view (all 41 slots)
    // -----------------------------------------------------------------------

    /**
     * Builds an editable 5-row GUI backed by the player's live inventory (all 41 slots: hotbar,
     * main, armor, offhand). Changes made in the GUI write back to the live inventory immediately.
     *
     * <p>Layout follows the class-level editable slot mapping exactly — each GUI slot is bound to
     * the corresponding Bukkit player-inventory slot via {@link SlotElement.InventoryLink}.
     */
    public static Gui buildEditable(PlayerInventory inv) {
        // fromContents wraps PlayerInventory.getContents() (≥41 slots in Paper).
        // We bind exactly the 41 known player-inventory slots 0–40.
        ReferencingInventory ref = ReferencingInventory.fromContents(inv);
        Gui gui = Gui.empty(9, 5);
        // Main inventory (Bukkit 9-35 → GUI 0-26)
        for (int i = 0; i < 27; i++) {
            gui.setSlotElement(i, new SlotElement.InventoryLink(ref, i + 9));
        }
        // Armor (Bukkit 36-39 → GUI 27-30)
        for (int i = 0; i < 4; i++) {
            gui.setSlotElement(27 + i, new SlotElement.InventoryLink(ref, 36 + i));
        }
        // Offhand (Bukkit 40 → GUI 31)
        gui.setSlotElement(31, new SlotElement.InventoryLink(ref, 40));
        // GUI 32-35: filler (intentionally left empty)
        // Hotbar (Bukkit 0-8 → GUI 36-44)
        for (int i = 0; i < 9; i++) {
            gui.setSlotElement(36 + i, new SlotElement.InventoryLink(ref, i));
        }
        return gui;
    }

    // -----------------------------------------------------------------------
    // Read-only: offline snapshot — 5-row view
    // -----------------------------------------------------------------------

    /** Builds the read-only 5-row offline inventory view for {@code snapshot}. */
    public static Gui buildOfflineInventory(OfflinePlayerSnapshot snapshot) {
        return buildOfflineInventory(snapshot.inventory());
    }

    static Gui buildOfflineInventory(List<SlottedItem> items) {
        Map<Integer, ItemStack> bySlot = bySlot(items);
        ItemStack[] cells = new ItemStack[OFFLINE_INVENTORY_GUI_SIZE];
        // Top row: armor worn on the head first, then offhand (GUI 0-4).
        put(cells, bySlot, 39, 0); // helmet
        put(cells, bySlot, 38, 1); // chestplate
        put(cells, bySlot, 37, 2); // leggings
        put(cells, bySlot, 36, 3); // boots
        put(cells, bySlot, 40, 4); // offhand
        // No inventory position exists for GUI 5-8: render immovable barriers.
        for (int i = OFFLINE_FILLER_FIRST; i <= OFFLINE_FILLER_LAST; i++) {
            cells[i] = new ItemStack(Material.BARRIER);
        }
        // Main inventory: GUI index equals the Bukkit slot (9-35).
        for (int slot = 9; slot <= 35; slot++) {
            put(cells, bySlot, slot, slot);
        }
        // Hotbar: bottom row (Bukkit 0-8 → GUI 36-44).
        for (int slot = 0; slot <= 8; slot++) {
            put(cells, bySlot, slot, 36 + slot);
        }
        return buildGuiFromItems(cells, 9, 5);
    }

    // -----------------------------------------------------------------------
    // Read-only: offline snapshot — 3-row Ender Chest view
    // -----------------------------------------------------------------------

    /** Builds the read-only 3-row offline Ender Chest view for {@code snapshot}. */
    public static Gui buildOfflineEnderChest(OfflinePlayerSnapshot snapshot) {
        return buildOfflineEnderChest(snapshot.enderItems());
    }

    static Gui buildOfflineEnderChest(List<SlottedItem> items) {
        ItemStack[] cells = new ItemStack[ENDER_CHEST_GUI_SIZE];
        for (SlottedItem si : items) {
            if (si.slot() >= 0 && si.slot() < ENDER_CHEST_GUI_SIZE) {
                cells[si.slot()] = si.stack();
            }
        }
        return buildGuiFromItems(cells, 9, 3);
    }

    // -----------------------------------------------------------------------
    // Ender chest — editable 3-row view
    // -----------------------------------------------------------------------

    /**
     * Builds an editable 3-row GUI backed by the player's live ender-chest inventory. Changes in
     * the GUI write back to the live inventory.
     */
    public static Gui buildEnderChestEditable(Inventory enderChest) {
        ReferencingInventory ref = ReferencingInventory.fromStorageContents(enderChest);
        Gui gui = Gui.empty(9, 3);
        gui.fillRectangle(0, 0, 9, ref, false);
        return gui;
    }

    // -----------------------------------------------------------------------
    // Private builder helpers
    // -----------------------------------------------------------------------

    private static Map<Integer, ItemStack> bySlot(List<SlottedItem> items) {
        Map<Integer, ItemStack> bySlot = new LinkedHashMap<>();
        for (SlottedItem item : items) {
            bySlot.put(item.slot(), item.stack());
        }
        return bySlot;
    }

    private static void put(
            ItemStack[] cells, Map<Integer, ItemStack> bySlot, int bukkitSlot, int guiIndex) {
        ItemStack stack = bySlot.get(bukkitSlot);
        if (stack != null) {
            cells[guiIndex] = stack;
        }
    }

    private static Gui buildGuiFromItems(ItemStack[] items, int width, int height) {
        Gui gui = Gui.empty(width, height);
        for (int i = 0; i < items.length && i < width * height; i++) {
            if (items[i] != null) {
                gui.setItem(i, Item.simple(items[i]));
            }
        }
        return gui;
    }
}
