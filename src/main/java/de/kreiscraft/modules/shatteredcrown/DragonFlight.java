package de.kreiscraft.modules.shatteredcrown;

import de.kreiscraft.platform.DragonFlightControl;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.DoubleFunction;
import org.bukkit.Location;
import org.bukkit.entity.EnderDragon;

/** Scripted segments advanced by the native dragon tick, not a teleport scheduler. */
final class DragonFlight implements AutoCloseable {
    private final EnderDragon dragon;
    private final DragonBattleAccess access;
    private final BooleanSupplier valid;
    private final Runnable finished;
    private final Runnable release;
    private final de.kreiscraft.core.scheduler.Scheduler.Task watchdog;
    private DragonFlightControl control;
    private DoubleFunction<Location> path;
    private Consumer<Location> step;
    private Runnable next;
    private int ticks;
    private int tick;
    private boolean closed;

    DragonFlight(EnderDragon dragon, DragonBattleAccess access, EncounterWork work, BooleanSupplier valid,
            Runnable finished) {
        this.dragon = dragon;
        this.access = access;
        this.valid = valid;
        this.finished = finished;
        release = work.onCleanup(this::close);
        watchdog = work.repeating(java.time.Duration.ofMillis(250), java.time.Duration.ofMillis(250), () -> {
            if (!valid.getAsBoolean() || control != null && !control.active()) {
                dragon.getServer().getLogger()
                        .info("[Shattered Crown] Flight cancelled: target, warning or native phase lost");
                close();
            }
        });
    }

    void start(DoubleFunction<Location> path, double seconds, Consumer<Location> step, Runnable next) {
        if (closed) {
            return;
        }
        this.path = path;
        this.step = step;
        this.next = next;
        ticks = Math.max(1, (int) Math.ceil(seconds * 20));
        tick = 0;
        if (control == null) {
            try {
                DragonFlightControl acquired = access.controlFlight(dragon, this::tick);
                if (closed) {
                    acquired.close();
                } else {
                    control = acquired;
                }
            } catch (RuntimeException error) {
                close();
                dragon.getServer().getLogger().log(java.util.logging.Level.WARNING,
                        "[Shattered Crown] Native flight acquisition failed", error);
            }
        }
    }

    private void tick() {
        try {
            if (closed || control == null || !control.active() || !valid.getAsBoolean()) {
                close();
                return;
            }
            Location destination = path.apply(++tick / (double) ticks);
            var direction = destination.toVector().subtract(dragon.getLocation().toVector());
            if (direction.getX() * direction.getX() + direction.getZ() * direction.getZ() > 0.0001) {
                destination.setDirection(direction);
                destination.setYaw(destination.getYaw() + 180);
            } else {
                destination.setYaw(dragon.getLocation().getYaw());
            }
            control.move(destination);
            if (closed || !valid.getAsBoolean()) {
                close();
                return;
            }
            step.accept(dragon.getLocation());
            if (closed || !valid.getAsBoolean()) {
                close();
            } else if (tick >= ticks) {
                next.run();
            }
        } catch (RuntimeException error) {
            close();
            dragon.getServer().getLogger().log(java.util.logging.Level.WARNING,
                    "[Shattered Crown] Native flight interrupted", error);
        }
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
        watchdog.cancel();
        release.run();
        finished.run();
        if (control != null) {
            control.close();
        }
    }
}
