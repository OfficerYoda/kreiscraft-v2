package de.kreiscraft.modules.spawn;

import de.kreiscraft.core.event.PriorityListener;
import de.kreiscraft.core.message.MessageService;
import de.kreiscraft.core.scheduler.Scheduler;
import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
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
 * applies a configurable directional velocity boost with a per-player cooldown. Eligibility is
 * cleared when the glide ends, the player quits, or the module is disabled.
 *
 * <p>Config is read fresh on every event via a {@link Supplier} so that {@code onConfigReload} in
 * {@link SpawnModule} takes effect without restart.
 */
public final class GlideListener implements PriorityListener {

    /** 3 Minecraft ticks expressed in milliseconds (3 × 50 ms). */
    private static final Duration TICK_3 = Duration.ofMillis(150);

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

    /** Per-player timestamp of the most recent boost, used to enforce the cooldown. */
    private final Map<UUID, Long> lastBoostTimes = new HashMap<>();

    public GlideListener(SpawnService service, MessageService messages,
            Supplier<SpawnConfig> configSupply, Scheduler scheduler, Server server) {
        this.service = service;
        this.messages = messages;
        this.configSupply = configSupply;
        this.server = server;
        this.task = scheduler.runRepeating(TICK_3, TICK_3, this::updateFlightPermissions);
    }

    // ── repeating task ───────────────────────────────────────────────────────

    private void updateFlightPermissions() {
        for (Player player : server.getOnlinePlayers()) {
            if (player.getGameMode() != GameMode.SURVIVAL) {
                // Creative/adventure players manage their own flight state.
                continue;
            }
            if (service.isInGlideZone(player)) {
                player.setAllowFlight(true);
            } else {
                player.setAllowFlight(false);
                flying.remove(player.getUniqueId());
            }
        }
    }

    // ── event handlers ───────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.NORMAL)
    public void onToggleFlight(PlayerToggleFlightEvent event) {
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
        UUID id = player.getUniqueId();
        flying.add(id);
        // Mark boost-eligible: glide started inside the Glide Zone.
        if (cfg.glideBoost().enabled()) {
            boostEligible.add(id);
        }
        player.setGliding(true);
        // Directional launch: the facing unit vector scaled by multiplyValue.
        if (cfg.glide().multiplyValue() > 0) {
            player.setVelocity(player.getLocation().getDirection().multiply(cfg.glide().multiplyValue()));
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onEntityDamage(EntityDamageEvent event) {
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

    /** Clears boost eligibility and flying state when a glide ends naturally. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityToggleGlide(EntityToggleGlideEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (!event.isGliding()) {
            clearPlayerState(player.getUniqueId());
        }
    }

    /**
     * Applies the sneak boost when the player presses sneak while gliding, is boost-eligible,
     * and has left the Glide Zone. The boost is directional (facing direction × configured
     * strength) and is rate-limited by {@link SpawnConfig.GlideBoostConfig#cooldownSeconds()}.
     */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onToggleSneak(PlayerToggleSneakEvent event) {
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
        UUID id = player.getUniqueId();
        if (!boostEligible.contains(id)) {
            return;
        }
        // Boost applies only after the player has left the Glide Zone.
        if (service.isInGlideZone(player)) {
            return;
        }
        long now = System.currentTimeMillis();
        long cooldownMs = (long) boostCfg.cooldownSeconds() * 1000L;
        Long last = lastBoostTimes.get(id);
        if (last != null && now - last < cooldownMs) {
            return;
        }
        lastBoostTimes.put(id, now);
        player.setVelocity(player.getLocation().getDirection().multiply(boostCfg.boostStrength()));
    }

    /** Clears all per-player state when the player disconnects. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        clearPlayerState(event.getPlayer().getUniqueId());
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private void clearPlayerState(UUID id) {
        flying.remove(id);
        boostEligible.remove(id);
        lastBoostTimes.remove(id);
    }

    /** Cancels the background task and clears all state. Called from {@link SpawnModule#disable()}. */
    public void shutdown() {
        task.cancel();
        flying.clear();
        boostEligible.clear();
        lastBoostTimes.clear();
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
