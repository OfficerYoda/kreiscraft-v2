package de.kreiscraft.modules.dragonegg;

import de.kreiscraft.core.message.MessageService;
import java.util.Objects;
import java.util.function.LongSupplier;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.inventory.meta.CompassMeta;
import org.bukkit.persistence.PersistentDataType;

public final class DragonEggCompass {
    public static final NamespacedKey KEY = new NamespacedKey("kreiscraft", "dragon_egg_compass");
    private final Server server;
    private final DragonEggTracker tracker;
    private final MessageService messages;
    private final LongSupplier now;

    public DragonEggCompass(Server server, DragonEggTracker tracker, MessageService messages, LongSupplier now) {
        this.server = server;
        this.tracker = tracker;
        this.messages = messages;
        this.now = now;
    }

    public ShapelessRecipe recipe() {
        ItemStack result = new ItemStack(Material.COMPASS);
        result.editMeta(meta -> {
            meta.getPersistentDataContainer().set(KEY, PersistentDataType.BYTE, (byte) 1);
            meta.displayName(messages.render("dragon-egg.compass-name"));
        });
        update(result);
        return new ShapelessRecipe(KEY, result).addIngredient(Material.COMPASS).addIngredient(Material.DRAGON_HEAD);
    }

    public boolean isTrackingCompass(ItemStack item) {
        return item != null && item.getType() == Material.COMPASS
                && item.getPersistentDataContainer().has(KEY, PersistentDataType.BYTE);
    }

    public void update(ItemStack item) {
        if (!isTrackingCompass(item)) {
            return;
        }
        Location target = tracker.ping().map(p -> p.position().location(server)).orElse(null);
        CompassMeta meta = (CompassMeta) item.getItemMeta();
        if (!Objects.equals(meta.getLodestone(), target) || meta.isLodestoneTracked()) {
            meta.setLodestone(target);
            meta.setLodestoneTracked(false);
            item.setItemMeta(meta);
        }
    }

    public void report(Player player, ItemStack item) {
        if (!isTrackingCompass(item)) {
            return;
        }
        update(item);
        var ping = tracker.ping();
        if (ping.isEmpty()) {
            messages.send(player, "dragon-egg.unavailable");
            return;
        }
        var target = ping.orElseThrow();
        long seconds = Math.max(0, now.getAsLong() - target.sampledAt()) / 1000;
        if (!target.position().world().equals(player.getWorld().getUID().toString())) {
            messages.send(player, "dragon-egg.dimension", "dimension", target.position().dimension(), "seconds",
                    seconds);
        } else {
            messages.send(player, "dragon-egg.ping", "distance",
                    Math.round(player.getLocation().distance(target.position().location(server))),
                    "seconds", seconds);
        }
    }

    public void updateInventory(Player player) {
        var inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack item = inventory.getItem(slot);
            if (isTrackingCompass(item)) {
                update(item);
                inventory.setItem(slot, item);
            }
        }
        ItemStack cursor = player.getItemOnCursor();
        if (isTrackingCompass(cursor)) {
            update(cursor);
            player.setItemOnCursor(cursor);
        }
    }
}
