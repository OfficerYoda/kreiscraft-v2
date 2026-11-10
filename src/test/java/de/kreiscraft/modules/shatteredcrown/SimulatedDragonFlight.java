package de.kreiscraft.modules.shatteredcrown;

import de.kreiscraft.core.scheduler.Scheduler;
import de.kreiscraft.platform.DragonFlightControl;
import java.time.Duration;
import org.bukkit.Location;
import org.bukkit.entity.EnderDragon;

/** MockBukkit has no native phase loop; NmsDragonFlightTest covers the real installation boundary. */
final class SimulatedDragonFlight implements DragonFlightControl {
    private final EnderDragon dragon;
    private final EnderDragon.Phase original;
    private final Scheduler.Task task;
    private boolean closed;

    SimulatedDragonFlight(EnderDragon dragon, Scheduler scheduler, Runnable tick) {
        this.dragon = dragon;
        original = dragon.getPhase();
        dragon.setPhase(EnderDragon.Phase.CHARGE_PLAYER);
        task = scheduler.runRepeating(Duration.ofMillis(50), Duration.ofMillis(50), tick);
    }

    @Override
    public boolean active() {
        return !closed && dragon.getPhase() == EnderDragon.Phase.CHARGE_PLAYER;
    }

    @Override
    public void move(Location destination) {
        dragon.teleport(destination);
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        task.cancel();
        if (dragon.getPhase() == EnderDragon.Phase.CHARGE_PLAYER) {
            dragon.setPhase(original);
        }
    }
}
