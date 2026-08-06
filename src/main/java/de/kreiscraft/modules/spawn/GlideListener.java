package de.kreiscraft.modules.spawn;

import de.kreiscraft.core.event.PriorityListener;
import de.kreiscraft.core.scheduler.Scheduler;
import de.kreiscraft.modules.spawn.SpawnConfig.GlideConfig;
import java.time.Duration;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
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
 * <p>Legacy bug fixed: the original code cancelled ALL damage types while the
 * player was flying. This implementation cancels only
 * {@link EntityDamageEvent.DamageCause#FALL} and
 * {@link EntityDamageEvent.DamageCause#FLY_INTO_WALL}.
 */
public final class GlideListener implements PriorityListener {

    /** 3 Minecraft ticks expressed in milliseconds (3 × 50 ms). */
    private static final Duration TICK_3 = Duration.ofMillis(150);

    private final SpawnService service;
    private final GlideConfig config;
    private final Server server;
    private final Scheduler.Task task;

    /** UUIDs of players currently in the "gliding" state managed by this listener. */
    private final Set<UUID> flying = new HashSet<>();

    public GlideListener(SpawnService service, GlideConfig config,
            Scheduler scheduler, Server server) {
        this.service = service;
        this.config = config;
        this.server = server;
        this.task = scheduler.runRepeating(TICK_3, TICK_3, this::updateFlightPermissions);
    }

    // ── repeating task ───────────────────────────────────────────────────────

    private void updateFlightPermissions() {
        for (Player player : server.getOnlinePlayers()) {
            if (isEligibleForGlide(player)) {
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
        if (!isEligibleForGlide(player))
            return;
        event.setCancelled(true);
        flying.add(player.getUniqueId());
        player.setGliding(true);
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onEntityDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player))
            return;
        if (!flying.contains(player.getUniqueId()))
            return;
        EntityDamageEvent.DamageCause cause = event.getCause();
        if (cause == EntityDamageEvent.DamageCause.FALL
                || cause == EntityDamageEvent.DamageCause.FLY_INTO_WALL) {
            event.setCancelled(true);
        }
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private boolean isEligibleForGlide(Player player) {
        if (player.getGameMode() != GameMode.SURVIVAL)
            return false;
        Optional<Location> spawnOpt = service.spawnLocation();
        if (spawnOpt.isEmpty())
            return false;
        Location spawn = spawnOpt.get();
        if (spawn.getWorld() == null || !spawn.getWorld().equals(player.getWorld()))
            return false;
        return player.getLocation().distance(spawn) <= config.spawnRadius();
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
