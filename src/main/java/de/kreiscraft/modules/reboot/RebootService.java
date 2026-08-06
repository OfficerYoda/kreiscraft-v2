package de.kreiscraft.modules.reboot;

import java.time.Duration;

public interface RebootService {

    boolean isRunning();

    Duration remaining();

    void start(Duration countdown, String reason);

    void cancel();
}
