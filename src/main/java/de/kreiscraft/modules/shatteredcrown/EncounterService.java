package de.kreiscraft.modules.shatteredcrown;

import de.kreiscraft.core.scheduler.Scheduler;
import java.time.Duration;
import java.util.function.Consumer;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Mob;

/** Cross-module ownership and effects, released on Encounter termination or debug wave replacement. */
public interface EncounterService {
    boolean ownsCombatant(Entity entity);

    /** Registers descendant membership before public spawn callbacks. */
    Mob spawnCombatant(Location location, EntityType type, Entity parent, Consumer<Mob> initialize);

    Scheduler.Task runRepeating(Duration delay, Duration period, Runnable effect);

    /** Returns an idempotent action for normal expiry as well as owned cleanup. */
    Runnable onCleanup(Runnable restore);
}
