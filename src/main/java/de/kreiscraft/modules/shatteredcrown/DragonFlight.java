package de.kreiscraft.modules.shatteredcrown;

import java.time.Duration;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.DoubleFunction;
import org.bukkit.Location;
import org.bukkit.entity.EnderDragon;

/** Tick-sized scripted flight while HOVER keeps native multipart hitboxes alive. */
final class DragonFlight implements AutoCloseable {
    private final EnderDragon dragon;
    private final EncounterWork work;
    private final BooleanSupplier valid;
    private final Runnable finished;
    private final EnderDragon.Phase original;
    private final Runnable release;
    private de.kreiscraft.core.scheduler.Scheduler.Task activeTask;
    private boolean closed;

    DragonFlight(EnderDragon dragon, EncounterWork work, BooleanSupplier valid, Runnable finished) {
        this.dragon = dragon;
        this.work = work;
        this.valid = valid;
        this.finished = finished;
        original = dragon.getPhase();
        release = work.onCleanup(this::close);
    }

    void start(DoubleFunction<Location> path, double seconds, Consumer<Location> step, Runnable next) {
        if (closed) {
            return;
        }
        dragon.setPhase(EnderDragon.Phase.HOVER);
        if (closed || !valid.getAsBoolean()) {
            close();
            return;
        }
        int ticks = Math.max(1, (int) Math.ceil(seconds * 20));
        final int[] tick = {0};
        final de.kreiscraft.core.scheduler.Scheduler.Task[] task = {null};
        task[0] = work.repeating(Duration.ofMillis(50), Duration.ofMillis(50), () -> {
            try {
                if (closed || !valid.getAsBoolean() || !dragon.isValid()
                        || dragon.getPhase() != EnderDragon.Phase.HOVER) {
                    task[0].cancel();
                    close();
                    return;
                }
                Location destination = path.apply(++tick[0] / (double) ticks);
                var direction = destination.toVector().subtract(dragon.getLocation().toVector());
                if (direction.getX() * direction.getX() + direction.getZ() * direction.getZ() > 0.0001) {
                    // The dragon model faces opposite ordinary Bukkit entity yaw.
                    destination.setDirection(direction);
                    destination.setYaw(destination.getYaw() + 180);
                } else {
                    destination.setYaw(dragon.getLocation().getYaw());
                }
                if (!dragon.teleport(destination)) {
                    task[0].cancel();
                    close();
                    return;
                }
                if (closed || !valid.getAsBoolean()) {
                    task[0].cancel();
                    close();
                    return;
                }
                step.accept(dragon.getLocation());
                if (closed || !valid.getAsBoolean()) {
                    task[0].cancel();
                    close();
                    return;
                }
                if (tick[0] >= ticks) {
                    task[0].cancel();
                    if (!closed && valid.getAsBoolean()) {
                        next.run();
                    }
                }
            } catch (RuntimeException error) {
                task[0].cancel();
                close();
                throw error;
            }
        });
        activeTask = task[0];
    }

    static Location between(Location from, Location to, double progress) {
        return from.clone().add(to.toVector().subtract(from.toVector()).multiply(progress));
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        if (activeTask != null) {
            activeTask.cancel();
        }
        release.run();
        finished.run();
        if (dragon.isValid() && !dragon.isDead() && dragon.getPhase() == EnderDragon.Phase.HOVER) {
            dragon.setPhase(original == EnderDragon.Phase.DYING ? EnderDragon.Phase.CIRCLING : original);
        }
    }
}
