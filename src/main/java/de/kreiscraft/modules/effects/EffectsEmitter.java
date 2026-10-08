package de.kreiscraft.modules.effects;

import de.kreiscraft.core.scheduler.Scheduler;
import java.time.Duration;
import java.util.Optional;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Server;
import org.bukkit.entity.Player;

/**
 * Emits each enabled player's configured particle on a fixed tick schedule.
 *
 * <p>Emission is independent of movement: the task runs on the {@link Scheduler} clock and visits
 * every online player, rather than reacting to {@code PlayerMoveEvent}. The schedule defaults to
 * {@link EffectsConfig#DEFAULT_SCHEDULE} and can be replaced by {@link #reschedule(Duration)} so a
 * configuration reload takes effect without a restart.
 */
public final class EffectsEmitter {

    private final DefaultEffectsService service;
    private final Server server;
    private final Scheduler scheduler;

    private Scheduler.Task task;

    public EffectsEmitter(
            DefaultEffectsService service, Server server, Scheduler scheduler, Duration schedule) {
        this.service = service;
        this.server = server;
        this.scheduler = scheduler;
        reschedule(schedule);
    }

    /**
     * Cancels the running task and starts a new one at {@code schedule}. Called on enable and by
     * {@code EffectsModule.onConfigReload} so the schedule is hot-reloadable.
     */
    public void reschedule(Duration schedule) {
        if (task != null) {
            task.cancel();
        }
        this.task = scheduler.runRepeating(Duration.ZERO, schedule, this::emit);
    }

    /** Cancels the schedule. Called from {@link EffectsModule#disable()}. */
    public void disable() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    private void emit() {
        EffectsConfig cfg = service.getConfig();
        for (Player player : server.getOnlinePlayers()) {
            if (!service.isEnabled(player.getUniqueId())) {
                continue;
            }
            Optional<Particle> particle = service.selectedParticle(player.getUniqueId());
            if (particle.isEmpty()) {
                continue;
            }
            Location location = player.getLocation();
            // Use the 9-arg Paper overload so the operator-configured `force` flag (send particle
            // to players regardless of their particle settings) is honoured. `extra` (speed) is 0.0
            // and data is null — correct for data-less particles.
            player.getWorld()
                    .<Void>spawnParticle(
                            particle.get(),
                            location,
                            cfg.count(),
                            cfg.offsetX(),
                            cfg.offsetY(),
                            cfg.offsetZ(),
                            0.0,
                            null,
                            cfg.force());
        }
    }
}
