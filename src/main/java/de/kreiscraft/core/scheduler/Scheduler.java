package de.kreiscraft.core.scheduler;

import java.time.Duration;

public interface Scheduler {

    Task runOnMain(Runnable task);

    /**
     * Schedules {@code task} to run on a background thread, off the main server thread.
     *
     * <p>Use this for blocking work (network, disk) that must not stall the main thread or a shared
     * I/O thread such as the Netty chat pipeline. Bukkit API must not be touched from {@code task};
     * hop back to the main thread with {@link #runOnMain(Runnable)}.
     */
    Task runAsync(Runnable task);

    Task runLater(Duration delay, Runnable task);

    Task runRepeating(Duration delay, Duration period, Runnable task);

    boolean isMainThread();

    interface Task {
        void cancel();

        boolean isCancelled();
    }
}
