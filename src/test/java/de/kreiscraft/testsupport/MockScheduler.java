package de.kreiscraft.testsupport;

import de.kreiscraft.core.scheduler.Scheduler;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public final class MockScheduler implements Scheduler {

    private long virtualMillis = 0;
    private final List<ScheduledEntry> entries = new ArrayList<>();

    @Override
    public Task runOnMain(Runnable task) {
        task.run();
        ScheduledEntry entry = new ScheduledEntry(Long.MAX_VALUE, null, task);
        entry.cancelled = true;
        return entry;
    }

    @Override
    public Task runLater(Duration delay, Runnable task) {
        ScheduledEntry e = new ScheduledEntry(virtualMillis + delay.toMillis(), null, task);
        entries.add(e);
        return e;
    }

    @Override
    public Task runRepeating(Duration delay, Duration period, Runnable task) {
        ScheduledEntry e = new ScheduledEntry(virtualMillis + delay.toMillis(), period.toMillis(), task);
        entries.add(e);
        return e;
    }

    @Override
    public boolean isMainThread() {
        return true;
    }

    public void advance(Duration duration) {
        long target = virtualMillis + duration.toMillis();
        boolean firedThisPass;
        do {
            firedThisPass = false;
            ScheduledEntry next = null;
            for (ScheduledEntry e : entries) {
                if (!e.cancelled && e.nextFire <= target
                        && (next == null || e.nextFire < next.nextFire)) {
                    next = e;
                }
            }
            if (next != null) {
                virtualMillis = next.nextFire;
                next.runnable.run();
                if (next.periodMillis == null) {
                    next.cancelled = true;
                } else {
                    next.nextFire += next.periodMillis;
                }
                firedThisPass = true;
            }
        } while (firedThisPass);
        virtualMillis = target;
    }

    private static final class ScheduledEntry implements Task {
        long nextFire;
        final Long periodMillis;
        final Runnable runnable;
        boolean cancelled;

        ScheduledEntry(long nextFire, Long periodMillis, Runnable runnable) {
            this.nextFire = nextFire;
            this.periodMillis = periodMillis;
            this.runnable = runnable;
        }

        @Override
        public void cancel() {
            cancelled = true;
        }

        @Override
        public boolean isCancelled() {
            return cancelled;
        }
    }
}
