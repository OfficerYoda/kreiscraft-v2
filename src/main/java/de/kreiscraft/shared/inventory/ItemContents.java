package de.kreiscraft.shared.inventory;

import java.util.function.Predicate;
import org.bukkit.Material;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.BundleMeta;

/** Traverses native item containers, writing copied metadata back only when editing. */
public final class ItemContents {
    private ItemContents() {}

    public static boolean containsDragonEgg(ItemStack item) {
        return any(item, child -> child.getType() == Material.DRAGON_EGG);
    }

    public static boolean any(ItemStack item, Predicate<ItemStack> predicate) {
        return visit(item, predicate, false);
    }

    /** The predicate may edit the first matching stack; ancestors are persisted into {@code item}. */
    public static boolean editFirst(ItemStack item, Predicate<ItemStack> edit) {
        return visit(item, edit, true);
    }

    private static boolean visit(ItemStack item, Predicate<ItemStack> predicate, boolean edit) {
        if (item == null || item.getType().isAir()) {
            return false;
        }
        if (predicate.test(item)) {
            return true;
        }
        // ponytail: native BlockStateMeta and BundleMeta only; add adapters when another API container exists.
        var meta = item.getItemMeta();
        if (meta instanceof BlockStateMeta blockMeta && blockMeta.getBlockState() instanceof InventoryHolder holder) {
            var inventory = holder.getInventory();
            for (int slot = 0; slot < inventory.getSize(); slot++) {
                ItemStack child = inventory.getItem(slot);
                if (visit(child, predicate, edit)) {
                    if (edit) {
                        inventory.setItem(slot, child);
                        blockMeta.setBlockState((org.bukkit.block.BlockState) holder);
                        item.setItemMeta(blockMeta);
                    }
                    return true;
                }
            }
        } else if (meta instanceof BundleMeta bundle) {
            var children = bundle.getItems();
            for (ItemStack child : children) {
                if (visit(child, predicate, edit)) {
                    if (edit) {
                        bundle.setItems(children);
                        item.setItemMeta(bundle);
                    }
                    return true;
                }
            }
        }
        return false;
    }
}
