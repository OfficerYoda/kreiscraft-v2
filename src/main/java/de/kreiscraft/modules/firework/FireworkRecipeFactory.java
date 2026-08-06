package de.kreiscraft.modules.firework;

import java.util.List;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Server;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.inventory.meta.components.CustomModelDataComponent;

public final class FireworkRecipeFactory {

    private FireworkRecipeFactory() {}

    public static void register(Server server, FireworkConfig config) {
        NamespacedKey key = NamespacedKey.fromString(config.namespaceKey());
        if (key == null) {
            throw new IllegalArgumentException(
                    "Invalid namespace-key in firework config: " + config.namespaceKey());
        }
        if (server.getRecipe(key) != null) {
            return;
        }
        ItemStack firework = new ItemStack(Material.FIREWORK_ROCKET);
        FireworkMeta meta = (FireworkMeta) firework.getItemMeta();
        meta.setPower(0);
        CustomModelDataComponent cmd = meta.getCustomModelDataComponent();
        cmd.setFloats(List.of((float) config.customModelData()));
        meta.setCustomModelDataComponent(cmd);
        firework.setItemMeta(meta);
        ShapedRecipe recipe = new ShapedRecipe(key, firework);
        recipe.shape("GGG", "GPG", "GGG");
        recipe.setIngredient('G', Material.GUNPOWDER);
        recipe.setIngredient('P', Material.PAPER);
        recipe.setGroup("Miscellaneous");
        server.addRecipe(recipe);
    }

    public static void unregister(Server server, NamespacedKey key) {
        server.removeRecipe(key);
    }
}
