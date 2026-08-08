package de.kreiscraft.platform;

import org.bukkit.inventory.ItemStack;

/** A ({@code slot}, {@code stack}) pair returned by {@link OfflineInventoryReader}. */
public record SlottedItem(int slot, ItemStack stack) {
}
