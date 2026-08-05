package de.kreiscraft.core.scheduler;

import java.time.Duration;

public interface Scheduler {

    Task runOnMain(Runnable task);

    Task runLater(Duration delay, Runnable task);

    Task runRepeating(Duration delay, Duration period, Runnable task);

    boolean isMainThread();

    interface Task {
        void cancel();

        boolean isCancelled();
    }
}
