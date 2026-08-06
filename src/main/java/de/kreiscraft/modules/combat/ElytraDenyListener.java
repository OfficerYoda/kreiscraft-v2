package de.kreiscraft.modules.combat;

import de.kreiscraft.core.event.PriorityListener;
import de.kreiscraft.core.message.MessageService;
import java.util.function.Supplier;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityToggleGlideEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

public final class ElytraDenyListener implements PriorityListener {

    /** PDC key used to mark the drop time + 30 s, for external invulnerability-enforcement logic. */
    static final NamespacedKey ELYTRA_DROP_KEY = new NamespacedKey("kreiscraft", "combat-elytra-drop-until");

    private static final long DROP_PROTECT_MILLIS = 30_000L;

    private final CombatService service;
    private final MessageService messages;
    private final Supplier<CombatConfig> config;

    public ElytraDenyListener(CombatService service, MessageService messages, Supplier<CombatConfig> config) {
        this.service = service;
        this.messages = messages;
        this.config = config;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onToggleGlide(EntityToggleGlideEvent event) {
        if (!event.isGliding())
            return; // only intercept start-glide, not stop-glide

        Entity entity = event.getEntity();
        if (!(entity instanceof Player player))
            return;

        CombatConfig cfg = config.get();

        if (!service.isInCombat(player.getUniqueId()))
            return;
        if (!cfg.disableElytra())
            return;

        event.setCancelled(true);
        messages.send(player, "combat.elytra-denied");

        // Strip the elytra from the chest slot and drop it
        ItemStack chestplate = player.getInventory().getChestplate();
        if (chestplate != null && chestplate.getType() == Material.ELYTRA) {
            player.getInventory().setChestplate(null);
            Item dropped = player.getWorld().dropItem(player.getLocation(), chestplate);
            // Tag the item so external logic can grant invulnerability for 30 s to non-owners
            dropped.getPersistentDataContainer().set(
                    ELYTRA_DROP_KEY, PersistentDataType.LONG, System.currentTimeMillis() + DROP_PROTECT_MILLIS);
        }
    }
}
