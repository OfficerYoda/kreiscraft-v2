package de.kreiscraft.modules.spawn;

import de.kreiscraft.core.event.PriorityListener;
import de.kreiscraft.core.scheduler.Scheduler;
import java.time.Duration;
import java.util.HashSet;
import java.util.Optional;
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
import org.bukkit.event.player.PlayerToggleFlightEvent;

/**
 * Grants elytra-glide flight to players inside the spawn radius (survival only,
 * spawn world only) and cancels FALL / FLY_INTO_WALL damage while gliding.
 *
 * <p>When a player double-jumps to start gliding, an initial velocity boost is
 * applied: {@code player.direction × GlideConfig.multiplyValue}. This matches the
 * original legacy behaviour (from {@code SpawnGlideElytra.multiplyvalue}).
 *
 * <p>Legacy bug fixed: the original code cancelled ALL damage types while the
 * player was flying. This implementation cancels only
 * {@link EntityDamageEvent.DamageCause#FALL} and
 * {@link EntityDamageEvent.DamageCause#FLY_INTO_WALL}.
 *
 * <p>Config is read fresh on every event via a {@link Supplier} so that
 * {@code onConfigReload} in {@link SpawnModule} takes effect without restart.
 */
public final class GlideListener implements PriorityListener {

    /** 3 Minecraft ticks expressed in milliseconds (3 × 50 ms). */
    private static final Duration TICK_3 = Duration.ofMillis(150);

    private final SpawnService service;
    private final Supplier<SpawnConfig> configSupply;
    private final Server server;
    private final Scheduler.Task task;

    /** UUIDs of players currently in the "gliding" state managed by this listener. */
    private final Set<UUID> flying = new HashSet<>();

    public GlideListener(SpawnService service, Supplier<SpawnConfig> configSupply,
            Scheduler scheduler, Server server) {
        this.service = service;
        this.configSupply = configSupply;
        this.server = server;
        this.task = scheduler.runRepeating(TICK_3, TICK_3, this::updateFlightPermissions);
    }

    // ── repeating task ───────────────────────────────────────────────────────

    private void updateFlightPermissions() {
        SpawnConfig.GlideConfig cfg = configSupply.get().glide();
        for (Player player : server.getOnlinePlayers()) {
            if (isEligibleForGlide(player, cfg)) {
                player.setAllowFlight(true);
            } else if (player.getGameMode() == GameMode.SURVIVAL) {
                // Only revoke flight for survival players; creative/adventure manage their own.
                player.setAllowFlight(false);
                flying.remove(player.getUniqueId());
            }
        }
    }

    // ── event handlers ───────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.NORMAL)
    public void onToggleFlight(PlayerToggleFlightEvent event) {
        Player player = event.getPlayer();
        SpawnConfig.GlideConfig cfg = configSupply.get().glide();
        if (!isEligibleForGlide(player, cfg)) {
            return;
        }
        event.setCancelled(true);
        flying.add(player.getUniqueId());
        player.setGliding(true);
        // Apply initial launch boost (legacy: direction × multiplyValue).
        if (cfg.multiplyValue() > 0) {
            player.setVelocity(player.getLocation().getDirection().multiply(cfg.multiplyValue()));
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

    // ── helpers ──────────────────────────────────────────────────────────────

    private boolean isEligibleForGlide(Player player, SpawnConfig.GlideConfig cfg) {
        if (player.getGameMode() != GameMode.SURVIVAL) {
            return false;
        }
        Optional<Location> spawnOpt = service.spawnLocation();
        if (spawnOpt.isEmpty()) {
            return false;
        }
        Location spawn = spawnOpt.get();
        if (spawn.getWorld() == null || !spawn.getWorld().equals(player.getWorld())) {
            return false;
        }
        return player.getLocation().distance(spawn) <= cfg.spawnRadius();
    }

    /** Cancels the background task. Called from {@link SpawnModule#disable()}. */
    public void shutdown() {
        task.cancel();
    }

    /** Exposed for tests — returns whether the player is currently tracked as gliding. */
    public boolean isFlying(UUID playerId) {
        return flying.contains(playerId);
    }
}
