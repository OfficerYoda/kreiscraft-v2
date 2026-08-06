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
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityToggleGlideEvent;
import org.bukkit.event.player.PlayerAttemptPickupItemEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

public final class ElytraDenyListener implements PriorityListener {

    /** PDC key: expiry timestamp (epoch millis) after which drop protection lifts. */
    static final NamespacedKey ELYTRA_DROP_UNTIL_KEY =
            new NamespacedKey("kreiscraft", "combat-elytra-drop-until");

    /** PDC key: UUID string of the player who owns the dropped elytra. */
    static final NamespacedKey ELYTRA_OWNER_KEY =
            new NamespacedKey("kreiscraft", "combat-elytra-drop-owner");

    private static final long DROP_PROTECT_MILLIS = 30_000L;

    private final CombatService service;
    private final MessageService messages;
    private final Supplier<CombatConfig> config;

    public ElytraDenyListener(CombatService service, MessageService messages, Supplier<CombatConfig> config) {
        this.service = service;
        this.messages = messages;
        this.config = config;
    }

    // -------------------------------------------------------------------------
    // Elytra glide cancellation
    // -------------------------------------------------------------------------

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

        // Strip the elytra from the chest slot and drop it with protection tags
        ItemStack chestplate = player.getInventory().getChestplate();
        if (chestplate != null && chestplate.getType() == Material.ELYTRA) {
            player.getInventory().setChestplate(null);
            Item dropped = player.getWorld().dropItem(player.getLocation(), chestplate);
            PersistentDataContainer pdc = dropped.getPersistentDataContainer();
            pdc.set(ELYTRA_DROP_UNTIL_KEY, PersistentDataType.LONG,
                    System.currentTimeMillis() + DROP_PROTECT_MILLIS);
            pdc.set(ELYTRA_OWNER_KEY, PersistentDataType.STRING,
                    player.getUniqueId().toString());
        }
    }

    // -------------------------------------------------------------------------
    // Drop protection: pickup
    // -------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH)
    public void onAttemptPickup(PlayerAttemptPickupItemEvent event) {
        PersistentDataContainer pdc = event.getItem().getPersistentDataContainer();
        if (!pdc.has(ELYTRA_DROP_UNTIL_KEY, PersistentDataType.LONG))
            return;

        long expiry = pdc.get(ELYTRA_DROP_UNTIL_KEY, PersistentDataType.LONG);
        if (System.currentTimeMillis() >= expiry) {
            // Protection expired — clean up tags so future checks are free
            pdc.remove(ELYTRA_DROP_UNTIL_KEY);
            pdc.remove(ELYTRA_OWNER_KEY);
            return;
        }

        // Within protection window — owner can always pick it up
        String ownerStr = pdc.has(ELYTRA_OWNER_KEY, PersistentDataType.STRING)
                ? pdc.get(ELYTRA_OWNER_KEY, PersistentDataType.STRING)
                : null;
        if (ownerStr != null && event.getPlayer().getUniqueId().toString().equals(ownerStr)) {
            return; // owner picks up their own elytra — allowed
        }

        event.setCancelled(true);
    }

    // -------------------------------------------------------------------------
    // Drop protection: damage (fire, lava, explosions)
    // -------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Item item))
            return;

        PersistentDataContainer pdc = item.getPersistentDataContainer();
        if (!pdc.has(ELYTRA_DROP_UNTIL_KEY, PersistentDataType.LONG))
            return;

        long expiry = pdc.get(ELYTRA_DROP_UNTIL_KEY, PersistentDataType.LONG);
        if (System.currentTimeMillis() < expiry) {
            event.setCancelled(true);
        }
    }
}
