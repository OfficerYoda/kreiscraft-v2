package de.kreiscraft.platform;

import org.bukkit.inventory.ItemStack;

/**
 * A ({@code slot}, {@code stack}) pair in an {@link OfflinePlayerSnapshot}.
 *
 * <p>{@code slot} is the Bukkit inventory slot (0–40 for the ordinary inventory including armor and
 * offhand, 0–26 for the Ender Chest).
 */
public record SlottedItem(int slot, ItemStack stack) {
}
