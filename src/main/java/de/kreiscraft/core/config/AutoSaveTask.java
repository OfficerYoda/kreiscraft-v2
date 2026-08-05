package de.kreiscraft.core.config;

import de.kreiscraft.core.scheduler.Scheduler;
import java.time.Duration;
import java.util.List;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class AutoSaveTask {

    private static final Logger LOGGER = Logger.getLogger(AutoSaveTask.class.getName());

    private final Scheduler scheduler;
    private final Duration interval;
    private final Supplier<List<DataFile<?>>> filesProvider;
    private Scheduler.Task task;

    public AutoSaveTask(Scheduler scheduler, Duration interval,
            Supplier<List<DataFile<?>>> filesProvider) {
        this.scheduler = scheduler;
        this.interval = interval;
        this.filesProvider = filesProvider;
    }

    public void start() {
        task = scheduler.runRepeating(interval, interval, this::flushAll);
    }

    public void stop() {
        if (task != null)
            task.cancel();
        flushAll();
    }

    private void flushAll() {
        for (DataFile<?> f : filesProvider.get()) {
            try {
                f.flushIfDirty();
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "auto-save failed for " + f.relativePath(), e);
            }
        }
    }
}
