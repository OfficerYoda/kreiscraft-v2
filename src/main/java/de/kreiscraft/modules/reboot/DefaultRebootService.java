package de.kreiscraft.modules.reboot;

import de.kreiscraft.core.message.MessageService;
import de.kreiscraft.core.scheduler.Scheduler;
import de.kreiscraft.platform.PaperPlatform;
import java.time.Duration;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.entity.Player;

public final class DefaultRebootService implements RebootService {

    private final Scheduler scheduler;
    private final MessageService messages;
    private final PaperPlatform platform;
    private RebootConfig config;

    private boolean running = false;
    private long remainingSeconds = 0;
    private String currentReason = "";
    private Scheduler.Task task;

    public DefaultRebootService(
            Scheduler scheduler, MessageService messages, PaperPlatform platform, RebootConfig config) {
        this.scheduler = scheduler;
        this.messages = messages;
        this.platform = platform;
        this.config = config;
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public Duration remaining() {
        return Duration.ofSeconds(remainingSeconds);
    }

    @Override
    public void start(Duration countdown, String reason) {
        long seconds = countdown.getSeconds();
        if (running && seconds >= remainingSeconds) {
            // Only shorten the countdown; ignore if new value is >= current
            return;
        }
        if (running && task != null) {
            task.cancel();
        }
        this.remainingSeconds = seconds;
        this.currentReason = reason;
        this.running = true;
        messages.broadcast("reboot.started", "seconds", String.valueOf(remainingSeconds), "reason", currentReason);
        // delay=1s so first decrement happens after 1 real second
        task = scheduler.runRepeating(Duration.ofSeconds(1), Duration.ofSeconds(1), this::tick);
    }

    @Override
    public void cancel() {
        if (!running) {
            return;
        }
        running = false;
        remainingSeconds = 0;
        currentReason = "";
        if (task != null) {
            task.cancel();
            task = null;
        }
        messages.broadcast("reboot.cancelled");
    }

    public void updateConfig(RebootConfig config) {
        this.config = config;
    }

    public void shutdown() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        running = false;
    }

    private void tick() {
        if (!running) {
            return;
        }
        // Decrement first so we display the new count and check for zero
        remainingSeconds--;

        if (config.titleSeconds().contains((int) remainingSeconds)) {
            Component titleComponent = messages.render(
                    "reboot.countdown-title", "seconds", String.valueOf(remainingSeconds));
            Title adventureTitle = Title.title(titleComponent, Component.empty());
            for (Player player : platform.server().getOnlinePlayers()) {
                player.showTitle(adventureTitle);
            }
        }

        if (config.chatSeconds().contains((int) remainingSeconds)) {
            messages.broadcast("reboot.countdown-chat", "seconds", String.valueOf(remainingSeconds));
        }

        if (remainingSeconds <= 0) {
            running = false;
            if (task != null) {
                task.cancel();
                task = null;
            }
            Component kickMessage = messages.render("reboot.kick-message");
            for (Player player : platform.server().getOnlinePlayers()) {
                player.kick(kickMessage);
            }
            platform.server().shutdown();
        }
    }
}
