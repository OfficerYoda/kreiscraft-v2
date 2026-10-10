package de.kreiscraft.modules.spawn;

import de.kreiscraft.core.event.PriorityListener;
import de.kreiscraft.core.message.MessageService;
import de.kreiscraft.core.scheduler.Scheduler;
import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityToggleGlideEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;

/**
 * Grants elytra-glide flight to survival players inside the Glide Zone and cancels FALL /
 * FLY_INTO_WALL damage while gliding.
 *
 * <p><b>Glide initiation:</b> a survival player may only start a glide inside the three-dimensional
 * Glide Zone (centered on spawn, radius = Spawn Radius). A double-jump outside the zone is refused
 * explicitly with {@code spawn.glide-outside-zone} instead of failing silently.
 *
 * <p><b>Persistent glide boost:</b> when a player starts gliding inside the Glide Zone they become
 * boost-eligible for that glide. While still gliding (even after leaving the zone), pressing sneak
 * applies one configurable directional velocity boost. Eligibility is consumed by that boost and
 * cleared when the glide ends, the player quits, or the module is disabled.
 *
 * <p>Config is read fresh on every event via a {@link Supplier} so that {@code onConfigReload} in
 * {@link SpawnModule} takes effect without restart.
 */
public final class GlideListener implements PriorityListener {

    /** 5 Minecraft ticks expressed in milliseconds (5 × 50 ms). */
    private static final Duration TICK_5 = Duration.ofMillis(250);

    private final SpawnService service;
    private final MessageService messages;
    private final Supplier<SpawnConfig> configSupply;
    private final Server server;
    private final Scheduler.Task task;

    /** UUIDs of players currently in the "gliding" state managed by this listener. */
    private final Set<UUID> flying = new HashSet<>();

    /**
     * UUIDs of players whose current glide was STARTED inside the Glide Zone — eligible to
     * receive a sneak-triggered boost until the glide ends or they disconnect.
     */
    private final Set<UUID> boostEligible = new HashSet<>();

    public GlideListener(SpawnService service, MessageService messages,
            Supplier<SpawnConfig> configSupply, Scheduler scheduler, Server server) {
        this.service = service;
        this.messages = messages;
        this.configSupply = configSupply;
        this.server = server;
        this.task = scheduler.runRepeating(TICK_5, TICK_5, this::updateFlightPermissions);
    }

    // ── repeating task ───────────────────────────────────────────────────────

    private void updateFlightPermissions() {
        boolean enabled = configSupply.get().glide().enabled();
        for (Player player : server.getOnlinePlayers()) {
            if (player.getGameMode() != GameMode.SURVIVAL) {
                // Creative/adventure players manage their own flight state.
                continue;
            }
            if (enabled && service.isInGlideZone(player)) {
                player.setAllowFlight(true);
            } else {
                player.setAllowFlight(false);
                if (!enabled || !player.isGliding()) {
                    clearPlayerState(player.getUniqueId());
                }
            }
        }
    }

    /** Applies a reloaded glide enablement value immediately. */
    public void reload() {
        updateFlightPermissions();
    }

    // ── event handlers ───────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.NORMAL)
    public void onToggleFlight(PlayerToggleFlightEvent event) {
        if (!configSupply.get().glide().enabled()) {
            return;
        }
        Player player = event.getPlayer();
        if (player.getGameMode() != GameMode.SURVIVAL) {
            return;
        }
        if (!service.isInGlideZone(player)) {
            // Only report when a glide zone actually exists in this world, so players in other
            // dimensions or on a server with no spawn set are not spammed.
            Location spawn = service.spawnLocation().orElse(null);
            if (spawn != null && spawn.getWorld() != null
                    && spawn.getWorld().equals(player.getWorld())) {
                messages.send(player, "spawn.glide-outside-zone");
            }
            return;
        }
        SpawnConfig cfg = configSupply.get();
        event.setCancelled(true);
        UUID uuid = player.getUniqueId();
        flying.add(uuid);
        // Mark boost-eligible: glide started inside the Glide Zone.
        if (cfg.glideBoost().enabled()) {
            boostEligible.add(uuid);
        }

        player.setGliding(true);
        // Directional launch: the facing unit vector scaled by multiplyValue.
        if (cfg.glide().multiplyValue() > 0) {
            player.setVelocity(player.getLocation().getDirection().multiply(cfg.glide().multiplyValue()));
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onEntityDamage(EntityDamageEvent event) {
        if (!configSupply.get().glide().enabled()) {
            return;
        }
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (!flying.contains(player.getUniqueId())) {
            return;
        }
        EntityDamageEvent.DamageCause cause = event.getCause();
        if (cause == EntityDamageEvent.DamageCause.FALL
                || cause == EntityDamageEvent.DamageCause.FLY_INTO_WALL) {
            event.setCancelled(true);
        }
    }

    /**
     * Keeps a plugin-managed glide active while the player is airborne. Paper reverts
     * {@link Player#setGliding(boolean)} on the next tick when no Elytra is equipped, so its
     * stop-glide event must be cancelled. Landing still ends the glide and clears its state.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityToggleGlide(EntityToggleGlideEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (!event.isGliding()) {
            UUID uuid = player.getUniqueId();
            if (flying.contains(uuid) && !isOnGround(player)) {
                event.setCancelled(true);
                return;
            }
            clearPlayerState(uuid);
        }
    }

    /**
     * Applies the sneak boost when the player presses sneak while gliding, is boost-eligible,
     * The boost is directional (facing direction × configured strength) and consumes eligibility.
     */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onToggleSneak(PlayerToggleSneakEvent event) {
        if (!configSupply.get().glide().enabled()) {
            return;
        }
        if (!event.isSneaking()) {
            return;
        }
        Player player = event.getPlayer();
        if (!player.isGliding()) {
            return;
        }
        SpawnConfig cfg = configSupply.get();
        SpawnConfig.GlideBoostConfig boostCfg = cfg.glideBoost();
        if (!boostCfg.enabled()) {
            return;
        }
        UUID uuid = player.getUniqueId();
        if (!boostEligible.remove(uuid)) {
            return;
        }
        player.setVelocity(player.getLocation().getDirection().multiply(boostCfg.boostStrength()));
    }

    /** Clears all per-player state when the player disconnects. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        clearPlayerState(event.getPlayer().getUniqueId());
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    /**
     * Paper deprecates this flag for authorization decisions because clients can influence it. Here
     * it is only used to let Paper's own stop-glide transition through when the player lands.
     */
    @SuppressWarnings("deprecation")
    private static boolean isOnGround(Player player) {
        return player.isOnGround();
    }

    private void clearPlayerState(UUID id) {
        flying.remove(id);
        boostEligible.remove(id);
    }

    /** Cancels the background task and clears all state. Called from {@link SpawnModule#disable()}. */
    public void shutdown() {
        task.cancel();
        flying.clear();
        boostEligible.clear();
    }

    /** Exposed for tests — returns whether the player is currently tracked as gliding. */
    public boolean isFlying(UUID playerId) {
        return flying.contains(playerId);
    }

    /** Exposed for tests — returns whether the player is boost-eligible for their current glide. */
    public boolean isBoostEligible(UUID playerId) {
        return boostEligible.contains(playerId);
    }
}
