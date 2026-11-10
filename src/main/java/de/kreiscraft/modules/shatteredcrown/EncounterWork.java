package de.kreiscraft.modules.shatteredcrown;

import de.kreiscraft.core.scheduler.Scheduler;
import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
import org.bukkit.entity.Entity;
import org.bukkit.persistence.PersistentDataType;

/** One attempt's server-thread work. Closed callbacks are inert even if already queued by Paper. */
final class EncounterWork implements AutoCloseable {
    private final Scheduler scheduler;
    private final Set<OwnedTask> tasks = new HashSet<>();
    private final Set<Entity> entities = new HashSet<>();
    private final Set<Runnable> cleanups = new HashSet<>();
    private final Set<EncounterWork> children = new HashSet<>();
    private boolean isolated;
    private final java.util.logging.Logger logger;
    private boolean closed;

    EncounterWork(Scheduler scheduler, java.util.logging.Logger logger) {
        this.scheduler = scheduler;
        this.logger = logger;
    }

    void own(Entity entity, String kind) {
        if (closed) {
            entity.remove();
            throw new IllegalStateException("Encounter has ended");
        }
        entities.add(entity);
        entity.getPersistentDataContainer().set(EncounterRecovery.ARTIFACT, PersistentDataType.STRING, kind);
    }

    boolean owns(Entity entity) {
        return entities.contains(entity) || children.stream().anyMatch(child -> child.owns(entity));
    }

    boolean hasWork() {
        return !tasks.isEmpty() || !entities.isEmpty() || !children.isEmpty();
    }

    boolean isClosed() {
        return closed;
    }

    /** A failed effect releases its own hazards while the encounter coordinator keeps running. */
    EncounterWork child(Runnable cleanup) {
        if (closed) {
            throw new IllegalStateException("Encounter has ended");
        }
        var child = new EncounterWork(scheduler, logger);
        child.isolated = true;
        children.add(child);
        child.onCleanup(() -> {
            children.remove(child);
            cleanup.run();
        });
        return child;
    }

    void run(Runnable effect) {
        if (closed) {
            return;
        }
        try {
            effect.run();
        } catch (RuntimeException error) {
            close();
            logger.log(java.util.logging.Level.WARNING, "[Shattered Crown] Attack failed; effect cleaned up", error);
        }
    }

    Runnable onCleanup(Runnable restore) {
        if (closed) {
            throw new IllegalStateException("Encounter has ended");
        }
        Runnable once = new Runnable() {
            @Override
            public void run() {
                if (cleanups.remove(this)) {
                    restore.run();
                }
            }
        };
        cleanups.add(once);
        return once;
    }

    Scheduler.Task later(Duration delay, Runnable effect) {
        return schedule(delay, null, effect);
    }

    Scheduler.Task repeating(Duration delay, Duration period, Runnable effect) {
        return schedule(delay, period, effect);
    }

    private Scheduler.Task schedule(Duration delay, Duration period, Runnable effect) {
        if (closed) {
            throw new IllegalStateException("Encounter has ended");
        }
        if (delay.isNegative() || period != null && (period.isNegative() || period.isZero())) {
            throw new IllegalArgumentException("Encounter work requires nonnegative delay and positive period");
        }
        OwnedTask owned = new OwnedTask();
        tasks.add(owned);
        Runnable guarded = () -> {
            if (closed || owned.isCancelled()) {
                return;
            }
            try {
                if (isolated) {
                    run(effect);
                } else {
                    effect.run();
                }
            } finally {
                if (period == null) {
                    owned.cancel();
                }
            }
        };
        try {
            owned.delegate = period == null ? scheduler.runLater(delay, guarded)
                    : scheduler.runRepeating(delay, period, guarded);
            if (owned.isCancelled()) {
                cleanSafely(owned.delegate::cancel);
            }
        } catch (RuntimeException error) {
            owned.cancel();
            throw error;
        }
        return owned;
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        Set.copyOf(children).forEach(child -> cleanSafely(child::close));
        children.clear();
        Set.copyOf(tasks).forEach(task -> cleanSafely(task::cancel));
        tasks.clear();
        Set.copyOf(cleanups).forEach(this::cleanSafely);
        entities.forEach(entity -> cleanSafely(entity::remove));
        entities.clear();
    }

    private void cleanSafely(Runnable cleanup) {
        try {
            cleanup.run();
        } catch (RuntimeException error) {
            logger.log(java.util.logging.Level.WARNING, "[Shattered Crown] Owned effect cleanup failed", error);
        }
    }

    private final class OwnedTask implements Scheduler.Task {
        private Scheduler.Task delegate;
        private boolean cancelled;

        @Override
        public void cancel() {
            if (cancelled) {
                return;
            }
            cancelled = true;
            tasks.remove(this);
            if (delegate != null) {
                cleanSafely(delegate::cancel);
            }
        }

        @Override
        public boolean isCancelled() {
            return cancelled || closed;
        }
    }
}
