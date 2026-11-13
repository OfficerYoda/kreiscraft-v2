package de.kreiscraft.modules.invsee;

import de.kreiscraft.platform.OfflinePlayerSnapshot;
import de.kreiscraft.platform.SlottedItem;
import de.kreiscraft.shared.inventory.ItemContents;
import java.util.function.Consumer;
import java.util.function.BooleanSupplier;
import org.bukkit.entity.Player;
import xyz.xenondevs.invui.inventory.event.PlayerUpdateReason;
import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import xyz.xenondevs.invui.gui.Gui;
import xyz.xenondevs.invui.gui.SlotElement;
import xyz.xenondevs.invui.inventory.ReferencingInventory;
import xyz.xenondevs.invui.inventory.VirtualInventory;
import xyz.xenondevs.invui.item.Item;

/**
 * Factory for InvUI {@link Gui} instances used by the invsee and enderchest commands.
 *
 * <p>Slot mapping for both 5-row player-inventory views (GUI slots 0-44):
 *
 * <ul>
 *   <li>Row 1 (GUI 0–4): helmet, chestplate, leggings, boots, offhand (Bukkit 39, 38, 37, 36, 40);
 *       GUI slots 5–8 are immovable {@link Material#GRAY_STAINED_GLASS_PANE} placeholders.
 *   <li>Rows 2–4 (GUI 9–35): main inventory (Bukkit slots 9–35) — GUI index equals the Bukkit slot.
 *   <li>Row 5 (GUI 36–44): hotbar (Bukkit slots 0–8).
 * </ul>
 *
 * <p>Offline contents are held in a detached {@link VirtualInventory} indexed by Bukkit slot, so a
 * snapshot can be edited without touching the source until the module stages and commits it.
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

    /** GUI size for the editable 5-row offline snapshot. */
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
        // fromContents references PlayerInventory.getContents() (≥41 slots in Paper), so edits
        // written through the shared layout reach the live player inventory immediately.
        return buildPlayerInventoryGui(ReferencingInventory.fromContents(inv));
    }

    // -----------------------------------------------------------------------
    // Editable: offline snapshot — 5-row view
    // -----------------------------------------------------------------------

    /**
     * Seeds a detached, editable inventory from {@code snapshot}, indexed by Bukkit player-inventory
     * slot (0–40). Empty slots are {@code null}; reading the same instance after the GUI closes
     * yields the edits.
     */
    public static VirtualInventory offlineInventoryContents(OfflinePlayerSnapshot snapshot) {
        ItemStack[] items = new ItemStack[PLAYER_SLOTS];
        for (SlottedItem item : snapshot.inventory()) {
            if (item.slot() >= 0 && item.slot() < PLAYER_SLOTS) {
                items[item.slot()] = item.stack();
            }
        }
        return new VirtualInventory(items);
    }

    /**
     * Builds the editable 5-row offline inventory view bound to {@code contents}, which must hold
     * {@link #PLAYER_SLOTS} slots indexed by Bukkit slot.
     */
    public static Gui buildOfflineInventory(VirtualInventory contents) {
        return buildPlayerInventoryGui(contents);
    }

    private static Gui buildPlayerInventoryGui(xyz.xenondevs.invui.inventory.Inventory contents) {
        Gui gui = Gui.empty(9, 5);
        // Top row: armor worn on the head first, then offhand (GUI 0-4).
        gui.setSlotElement(0, new SlotElement.InventoryLink(contents, 39)); // helmet
        gui.setSlotElement(1, new SlotElement.InventoryLink(contents, 38)); // chestplate
        gui.setSlotElement(2, new SlotElement.InventoryLink(contents, 37)); // leggings
        gui.setSlotElement(3, new SlotElement.InventoryLink(contents, 36)); // boots
        gui.setSlotElement(4, new SlotElement.InventoryLink(contents, 40)); // offhand
        // No inventory position exists for GUI 5-8: render immovable, unnamed glass panes.
        ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = filler.getItemMeta();
        meta.displayName(Component.text(" "));
        filler.setItemMeta(meta);
        for (int i = OFFLINE_FILLER_FIRST; i <= OFFLINE_FILLER_LAST; i++) {
            gui.setItem(i, Item.simple(filler));
        }
        // Main inventory: GUI index equals the Bukkit slot (9-35).
        for (int slot = 9; slot <= 35; slot++) {
            gui.setSlotElement(slot, new SlotElement.InventoryLink(contents, slot));
        }
        // Hotbar: bottom row (Bukkit 0-8 → GUI 36-44).
        for (int slot = 0; slot <= 8; slot++) {
            gui.setSlotElement(36 + slot, new SlotElement.InventoryLink(contents, slot));
        }
        return gui;
    }

    /** Builds the editable 5-row offline inventory view for {@code snapshot}. */
    public static Gui buildOfflineInventory(OfflinePlayerSnapshot snapshot) {
        return buildOfflineInventory(offlineInventoryContents(snapshot));
    }

    // -----------------------------------------------------------------------
    // Editable: offline snapshot — 3-row Ender Chest view
    // -----------------------------------------------------------------------

    /**
     * Seeds a detached, editable inventory from {@code snapshot}'s {@code EnderItems}, indexed by
     * Ender Chest slot (0–26).
     */
    public static VirtualInventory offlineEnderChestContents(OfflinePlayerSnapshot snapshot) {
        ItemStack[] items = new ItemStack[ENDER_CHEST_GUI_SIZE];
        for (SlottedItem item : snapshot.enderItems()) {
            if (item.slot() >= 0 && item.slot() < ENDER_CHEST_GUI_SIZE) {
                items[item.slot()] = item.stack();
            }
        }
        return new VirtualInventory(items);
    }

    /**
     * Builds the editable 3-row offline Ender Chest view bound to {@code contents}, which must hold
     * {@link #ENDER_CHEST_GUI_SIZE} slots indexed by Ender Chest slot.
     */
    public static Gui buildOfflineEnderChest(VirtualInventory contents) {
        return buildOfflineEnderChest(contents, player -> {
        }, () -> false);
    }

    public static Gui buildOfflineEnderChest(VirtualInventory contents, Consumer<Player> rejected) {
        return buildOfflineEnderChest(contents, rejected, () -> false);
    }

    public static Gui buildOfflineEnderChest(VirtualInventory contents, Consumer<Player> rejected,
            BooleanSupplier eggModuleEnabled) {
        protectEnderChest(contents, rejected, eggModuleEnabled);
        Gui gui = Gui.empty(9, 3);
        for (int slot = 0; slot < ENDER_CHEST_GUI_SIZE; slot++) {
            gui.setSlotElement(slot, new SlotElement.InventoryLink(contents, slot));
        }
        return gui;
    }

    /** Builds the editable 3-row offline Ender Chest view for {@code snapshot}. */
    public static Gui buildOfflineEnderChest(OfflinePlayerSnapshot snapshot) {
        return buildOfflineEnderChest(offlineEnderChestContents(snapshot));
    }

    // -----------------------------------------------------------------------
    // Ender chest — editable 3-row live view
    // -----------------------------------------------------------------------

    /**
     * Builds an editable 3-row GUI backed by the player's live ender-chest inventory. Changes in
     * the GUI write back to the live inventory.
     */
    public static Gui buildEnderChestEditable(Inventory enderChest) {
        return buildEnderChestEditable(enderChest, player -> {
        }, () -> false);
    }

    public static Gui buildEnderChestEditable(Inventory enderChest, Consumer<Player> rejected) {
        return buildEnderChestEditable(enderChest, rejected, () -> false);
    }

    public static Gui buildEnderChestEditable(Inventory enderChest, Consumer<Player> rejected,
            BooleanSupplier eggModuleEnabled) {
        ReferencingInventory ref = ReferencingInventory.fromStorageContents(enderChest);
        protectEnderChest(ref, rejected, eggModuleEnabled);
        Gui gui = Gui.empty(9, 3);
        gui.fillRectangle(0, 0, 9, ref, false);
        return gui;
    }

    private static void protectEnderChest(xyz.xenondevs.invui.inventory.Inventory contents, Consumer<Player> rejected,
            BooleanSupplier eggModuleEnabled) {
        // InvUI validates the proposed destination before changing the source, including swaps, bundles and drags.
        contents.addPreUpdateHandler(event -> {
            if (eggModuleEnabled.getAsBoolean() && ItemContents.containsDragonEgg(event.getNewItem())
                    && (event.getPreviousItem() == null || !event.getPreviousItem().isSimilar(event.getNewItem())
                            || event.getNewItem().getAmount() > event.getPreviousItem().getAmount())) {
                event.setCancelled(true);
                if (event.getUpdateReason() instanceof PlayerUpdateReason reason) {
                    rejected.accept(reason.player());
                }
            }
        });
    }

    // -----------------------------------------------------------------------
    // Snapshot extraction
    // -----------------------------------------------------------------------

    /**
     * Converts an editable offline {@link VirtualInventory} back into slot items, omitting empty
     * slots and air stacks. Works for both the 41-slot inventory and the 27-slot Ender Chest.
     */
    public static List<SlottedItem> toSlottedItems(VirtualInventory contents) {
        ItemStack[] items = contents.getItems();
        List<SlottedItem> out = new ArrayList<>();
        for (int slot = 0; slot < items.length; slot++) {
            ItemStack stack = items[slot];
            if (stack != null && !stack.getType().isAir()) {
                out.add(new SlottedItem(slot, stack));
            }
        }
        return out;
    }
}
