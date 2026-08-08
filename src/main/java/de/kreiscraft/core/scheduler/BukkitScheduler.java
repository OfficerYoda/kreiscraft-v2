package de.kreiscraft.core.scheduler;

import java.time.Duration;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

public final class BukkitScheduler implements Scheduler {

    private final Plugin plugin;

    public BukkitScheduler(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public Task runOnMain(Runnable task) {
        if (isMainThread()) {
            task.run();
            return AlreadyDone.INSTANCE;
        }
        BukkitTask bt = Bukkit.getScheduler().runTask(plugin, task);
        return new BukkitTaskAdapter(bt);
    }

    @Override
    public Task runLater(Duration delay, Runnable task) {
        return new BukkitTaskAdapter(
                Bukkit.getScheduler().runTaskLater(plugin, task, toTicks(delay)));
    }

    @Override
    public Task runRepeating(Duration delay, Duration period, Runnable task) {
        return new BukkitTaskAdapter(
                Bukkit.getScheduler().runTaskTimer(plugin, task, toTicks(delay), toTicks(period)));
    }

    @Override
    public boolean isMainThread() {
        return Bukkit.isPrimaryThread();
    }

    private static long toTicks(Duration duration) {
        return Math.max(1L, duration.toMillis() / 50L);
    }

    private record BukkitTaskAdapter(BukkitTask task) implements Task {

        @Override
        public void cancel() {
            task.cancel();
        }

        @Override
        public boolean isCancelled() {
            return task.isCancelled();
        }
    }


    private enum AlreadyDone implements Task {
        INSTANCE;

        @Override
        public void cancel() {}

        @Override
        public boolean isCancelled() {
            return true;
        }
    }
}
