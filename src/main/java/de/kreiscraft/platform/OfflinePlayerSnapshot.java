package de.kreiscraft.platform;

import java.util.List;
import org.bukkit.inventory.ItemStack;

/**
 * A read-only snapshot of an offline player's persisted inventory data.
 *
 * <p>Holds the ordinary inventory (Bukkit slots 0–40, including armor and offhand) and the Ender
 * Chest ({@code EnderItems}, slots 0–26). The slot lists are defensively copied on construction so
 * that opening a snapshot never exposes a mutable view of the source data.
 *
 * @param inventory ordinary inventory slots that held items
 * @param enderItems Ender Chest slots that held items
 */
public record OfflinePlayerSnapshot(List<SlottedItem> inventory, List<SlottedItem> enderItems) {

    public OfflinePlayerSnapshot {
        inventory = List.copyOf(inventory);
        enderItems = List.copyOf(enderItems);
    }

    /** @return a snapshot with no inventory and no Ender Chest items */
    public static OfflinePlayerSnapshot empty() {
        return new OfflinePlayerSnapshot(List.of(), List.of());
    }

    /** @return {@code true} when neither the inventory nor the Ender Chest holds an item */
    public boolean isEmpty() {
        return inventory.isEmpty() && enderItems.isEmpty();
    }

    /**
     * Convenience for callers that only care about the ordinary inventory.
     *
     * @return a snapshot with the given inventory and no Ender Chest items
     */
    public static OfflinePlayerSnapshot ofInventory(List<SlottedItem> inventory) {
        return new OfflinePlayerSnapshot(inventory, List.of());
    }

    /**
     * Convenience for callers that only care about the Ender Chest.
     *
     * @return a snapshot with no inventory and the given Ender Chest items
     */
    public static OfflinePlayerSnapshot ofEnderItems(List<SlottedItem> enderItems) {
        return new OfflinePlayerSnapshot(List.of(), enderItems);
    }

    /** @return the item in the ordinary inventory {@code slot}, or {@code null} when absent */
    public ItemStack inventoryAt(int slot) {
        return stackAt(inventory, slot);
    }

    /** @return the item in Ender Chest {@code slot}, or {@code null} when absent */
    public ItemStack enderItemAt(int slot) {
        return stackAt(enderItems, slot);
    }

    private static ItemStack stackAt(List<SlottedItem> slots, int slot) {
        for (SlottedItem item : slots) {
            if (item.slot() == slot) {
                return item.stack();
            }
        }
        return null;
    }
}
