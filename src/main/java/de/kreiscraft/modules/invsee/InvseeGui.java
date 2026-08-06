package de.kreiscraft.modules.invsee;

import de.kreiscraft.platform.SlottedItem;
import java.util.List;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import xyz.xenondevs.invui.gui.Gui;
import xyz.xenondevs.invui.inventory.ReferencingInventory;
import xyz.xenondevs.invui.item.impl.SimpleItem;

/**
 * Factory for InvUI {@link Gui} instances used by the invsee and enderchest commands.
 *
 * <p>Slot mapping for the 5-row player-inventory view (GUI slots 0-44):
 *
 * <ul>
 *   <li>Rows 1–3 (GUI 0–26): main inventory (Bukkit slots 9–35)
 *   <li>Row 4 (GUI 27–31): armor boots→helmet (Bukkit 36–39), offhand (Bukkit 40);
 *       GUI slots 32–35 are left empty (filler).
 *   <li>Row 5 (GUI 36–44): hotbar (Bukkit slots 0–8)
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

    /** GUI size for the 4-row offline / editable-storage view. */
    static final int STORAGE_GUI_SIZE = 36;

    /** GUI size for the 3-row ender-chest view. */
    static final int ENDER_CHEST_GUI_SIZE = 27;

    // -----------------------------------------------------------------------
    // Read-only: online player — 5-row view
    // -----------------------------------------------------------------------

    /**
     * Builds a read-only 5-row GUI showing the full player inventory.
     *
     * @param allContents {@code PlayerInventory.getContents()} — length ≥ 41
     */
    public static Gui buildReadOnly(ItemStack[] allContents) {
        ItemStack[] guiItems = mapPlayerInvToGuiSlots(allContents);
        return buildGuiFromItems(guiItems, 9, 5);
    }

    // -----------------------------------------------------------------------
    // Editable: online player — 4-row view (storage only, no armor)
    // -----------------------------------------------------------------------

    /**
     * Builds an editable 4-row GUI backed by the player's live inventory (storage slots only —
     * hotbar + main, no armor/offhand). Changes made in the GUI write back to the live inventory.
     */
    public static Gui buildEditable(PlayerInventory inv) {
        // fromReversedPlayerStorageContents gives 36 slots in natural chest order
        // (main rows 1-3 first, then hotbar row 4)
        ReferencingInventory ref = ReferencingInventory.fromReversedPlayerStorageContents(inv);
        Gui gui = Gui.empty(9, 4);
        gui.fillRectangle(0, 0, 9, ref, false);
        return gui;
    }

    // -----------------------------------------------------------------------
    // Read-only: offline player — 4-row view
    // -----------------------------------------------------------------------

    /**
     * Builds a read-only 4-row GUI from offline NBT data.
     *
     * <p>Slot mapping:
     *
     * <ul>
     *   <li>Rows 1–3 (GUI 0–26): Bukkit main-inventory slots 9–35
     *   <li>Row 4 (GUI 27–35): hotbar (Bukkit slots 0–8)
     * </ul>
     */
    public static Gui buildOffline(List<SlottedItem> items) {
        ItemStack[] guiItems = new ItemStack[STORAGE_GUI_SIZE];
        for (SlottedItem si : items) {
            int bSlot = si.slot();
            if (bSlot >= 9 && bSlot <= 35) {
                guiItems[bSlot - 9] = si.stack(); // main → rows 1-3
            } else if (bSlot >= 0 && bSlot <= 8) {
                guiItems[27 + bSlot] = si.stack(); // hotbar → row 4
            }
            // armor/offhand slots (36-40) are skipped — not shown in offline view
        }
        return buildGuiFromItems(guiItems, 9, 4);
    }

    // -----------------------------------------------------------------------
    // Ender chest — 3-row views
    // -----------------------------------------------------------------------

    /** Builds a read-only 3-row GUI for the player's ender chest. */
    public static Gui buildEnderChestReadOnly(Inventory enderChest) {
        ItemStack[] contents = enderChest.getContents();
        return buildGuiFromItems(contents, 9, 3);
    }

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
    // Package-private helpers (exposed for unit tests)
    // -----------------------------------------------------------------------

    /**
     * Maps a Bukkit {@code PlayerInventory.getContents()} array (length ≥ 41) to a 45-element GUI
     * slot array.
     *
     * <p>See class-level Javadoc for the mapping table.
     */
    static ItemStack[] mapPlayerInvToGuiSlots(ItemStack[] contents) {
        ItemStack[] gui = new ItemStack[PLAYER_GUI_SIZE];
        // main inventory (Bukkit 9-35 → GUI 0-26)
        for (int i = 0; i < 27 && (i + 9) < contents.length; i++) {
            gui[i] = contents[i + 9];
        }
        // armor (Bukkit 36-39 → GUI 27-30)
        for (int i = 0; i < 4 && (36 + i) < contents.length; i++) {
            gui[27 + i] = contents[36 + i];
        }
        // offhand (Bukkit 40 → GUI 31)
        if (40 < contents.length) {
            gui[31] = contents[40];
        }
        // GUI 32-35: intentionally left null (filler)
        // hotbar (Bukkit 0-8 → GUI 36-44)
        for (int i = 0; i < 9 && i < contents.length; i++) {
            gui[36 + i] = contents[i];
        }
        return gui;
    }

    // -----------------------------------------------------------------------
    // Private builder helpers
    // -----------------------------------------------------------------------

    private static Gui buildGuiFromItems(ItemStack[] items, int width, int height) {
        Gui gui = Gui.empty(width, height);
        for (int i = 0; i < items.length && i < width * height; i++) {
            if (items[i] != null) {
                gui.setItem(i, new SimpleItem(items[i]));
            }
        }
        return gui;
    }
}
