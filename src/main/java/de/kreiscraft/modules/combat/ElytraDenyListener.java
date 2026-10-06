package de.kreiscraft.modules.combat;

import de.kreiscraft.core.event.PriorityListener;
import de.kreiscraft.core.message.MessageService;
import de.kreiscraft.core.scheduler.Scheduler;
import java.time.Duration;
import java.util.function.Supplier;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Server;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityToggleGlideEvent;
import org.bukkit.event.player.PlayerAttemptPickupItemEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

/**
 * Enforces the combat elytra ban. While a player is tagged, any worn elytra is removed:
 * it is returned to the inventory when there is space, otherwise it is dropped glowing
 * and pickup-protected for its owner for a bounded window.
 *
 * <p>The removal is swept continuously (mirroring the legacy RusticPrism behaviour of
 * de-equipping every tick while in combat) rather than only on a glide attempt, so a
 * tagged player cannot keep wearing an elytra without trying to fly.
 *
 * <p>Drop protection is time-bounded and its PDC tags are removed once the window
 * expires, so no item stays protected forever.
 */
public final class ElytraDenyListener implements PriorityListener {

    /** PDC key: expiry timestamp (epoch millis) after which drop protection lifts. */
    static final NamespacedKey ELYTRA_DROP_UNTIL_KEY =
            new NamespacedKey("kreiscraft", "combat-elytra-drop-until");

    /** PDC key: UUID string of the player who owns the dropped elytra. */
    static final NamespacedKey ELYTRA_OWNER_KEY =
            new NamespacedKey("kreiscraft", "combat-elytra-drop-owner");

    private static final long DROP_PROTECT_MILLIS = 30_000L;

    /** Re-check cadence for tagged players — every server tick, matching RusticPrism's continuous sweep. */
    private static final Duration SWEEP_PERIOD = Duration.ofMillis(50);

    private final CombatService service;
    private final MessageService messages;
    private final Supplier<CombatConfig> config;
    private final Server server;
    private final Scheduler.Task sweepTask;

    public ElytraDenyListener(CombatService service, MessageService messages, Supplier<CombatConfig> config,
            Scheduler scheduler, Server server) {
        this.service = service;
        this.messages = messages;
        this.config = config;
        this.server = server;
        this.sweepTask = scheduler.runRepeating(SWEEP_PERIOD, SWEEP_PERIOD, this::sweepTaggedPlayers);
    }

    // -------------------------------------------------------------------------
    // Ongoing removal for tagged players
    // -------------------------------------------------------------------------

    private void sweepTaggedPlayers() {
        CombatConfig cfg = config.get();
        if (!cfg.disableElytra()) {
            return;
        }
        for (Player player : server.getOnlinePlayers()) {
            if (isExcludedWorld(player, cfg)) {
                continue;
            }
            if (service.isInCombat(player.getUniqueId()) && unequipElytra(player)) {
                messages.send(player, "combat.elytra-denied");
            }
        }
    }

    private static boolean isExcludedWorld(Player player, CombatConfig cfg) {
        return cfg.excludedWorlds().contains(player.getWorld().getName());
    }

    // -------------------------------------------------------------------------
    // Elytra glide cancellation
    // -------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH)
    public void onToggleGlide(EntityToggleGlideEvent event) {
        if (!event.isGliding()) {
            return; // only intercept start-glide, not stop-glide
        }
        Entity entity = event.getEntity();
        if (!(entity instanceof Player player)) {
            return;
        }

        CombatConfig cfg = config.get();
        if (!service.isInCombat(player.getUniqueId())) {
            return;
        }
        if (!cfg.disableElytra() || isExcludedWorld(player, cfg)) {
            return;
        }

        event.setCancelled(true);
        messages.send(player, "combat.elytra-denied");
        unequipElytra(player);
    }

    // -------------------------------------------------------------------------
    // Removal: return to inventory, or protected drop
    // -------------------------------------------------------------------------

    /**
     * Removes the player's worn elytra. The elytra goes back into the inventory when a
     * storage slot is free; otherwise it is dropped glowing and tagged for its owner.
     *
     * @return {@code true} when an elytra was actually removed
     */
    boolean unequipElytra(Player player) {
        ItemStack chestplate = player.getInventory().getChestplate();
        if (chestplate == null || chestplate.getType() != Material.ELYTRA) {
            return false;
        }
        player.getInventory().setChestplate(null);
        if (hasStorageSpace(player.getInventory())) {
            player.getInventory().addItem(chestplate);
        } else {
            dropProtectedElytra(player, chestplate);
        }
        player.updateInventory();
        return true;
    }

    /** True when at least one of the 36 main inventory slots is free for the returned elytra. */
    private static boolean hasStorageSpace(PlayerInventory inventory) {
        for (ItemStack slot : inventory.getStorageContents()) {
            if (slot == null || slot.getType() == Material.AIR) {
                return true;
            }
        }
        return false;
    }

    private void dropProtectedElytra(Player player, ItemStack elytra) {
        Item dropped = player.getWorld().dropItemNaturally(player.getLocation(), elytra);
        dropped.setGlowing(true);
        PersistentDataContainer pdc = dropped.getPersistentDataContainer();
        pdc.set(ELYTRA_DROP_UNTIL_KEY, PersistentDataType.LONG,
                System.currentTimeMillis() + DROP_PROTECT_MILLIS);
        pdc.set(ELYTRA_OWNER_KEY, PersistentDataType.STRING,
                player.getUniqueId().toString());
        messages.send(player, "combat.elytra-dropped");
    }

    // -------------------------------------------------------------------------
    // Drop protection: pickup
    // -------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH)
    public void onAttemptPickup(PlayerAttemptPickupItemEvent event) {
        PersistentDataContainer pdc = event.getItem().getPersistentDataContainer();
        long expiry = protectionExpiry(pdc);
        if (expiry < 0) {
            return;
        }
        if (System.currentTimeMillis() >= expiry) {
            // Protection expired — clean up tags so future checks are free
            clearProtection(pdc);
            return;
        }

        // Within protection window — owner can always pick it up
        String ownerStr = pdc.get(ELYTRA_OWNER_KEY, PersistentDataType.STRING);
        if (event.getPlayer().getUniqueId().toString().equals(ownerStr)) {
            return; // owner picks up their own elytra — allowed
        }

        event.setCancelled(true);
    }

    // -------------------------------------------------------------------------
    // Drop protection: damage (fire, lava, explosions)
    // -------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Item item)) {
            return;
        }

        PersistentDataContainer pdc = item.getPersistentDataContainer();
        long expiry = protectionExpiry(pdc);
        if (expiry < 0) {
            return;
        }
        if (System.currentTimeMillis() < expiry) {
            event.setCancelled(true);
        } else {
            clearProtection(pdc);
        }
    }

    /** Epoch-millis expiry of the drop protection, or {@code -1} when the item is unprotected. */
    private static long protectionExpiry(PersistentDataContainer pdc) {
        Long expiry = pdc.get(ELYTRA_DROP_UNTIL_KEY, PersistentDataType.LONG);
        return expiry == null ? -1L : expiry;
    }

    private static void clearProtection(PersistentDataContainer pdc) {
        pdc.remove(ELYTRA_DROP_UNTIL_KEY);
        pdc.remove(ELYTRA_OWNER_KEY);
    }

    /** Cancels the sweep task. Called from {@link CombatModule#disable()}. */
    public void shutdown() {
        sweepTask.cancel();
    }
}
