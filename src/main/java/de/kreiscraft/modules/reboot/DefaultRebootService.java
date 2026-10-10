package de.kreiscraft.modules.reboot;

import de.kreiscraft.core.message.MessageService;
import de.kreiscraft.core.scheduler.Scheduler;
import de.kreiscraft.platform.PaperPlatform;
import java.time.Duration;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerKickEvent;

public final class DefaultRebootService implements RebootService {

    private final Scheduler scheduler;
    private final MessageService messages;
    private final PaperPlatform platform;
    private RebootConfig config;

    private boolean running = false;
    private long remainingSeconds = 0;
    private String currentReason = "";
    private String currentInitiator = "";
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
    public void start(Duration countdown, String reason, String initiator) {
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
        this.currentInitiator = initiator;
        this.running = true;
        announceStarted();
        // delay=1s so first tick announces the starting second before decrementing
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
        currentInitiator = "";
        if (task != null) {
            task.cancel();
            task = null;
        }
        messages.broadcast("reboot.cancelled");
    }

    public void updateConfig(RebootConfig config) {
        this.config = config;
    }

    /**
     * Announces the start to every online player and the console, rendering the reason fallback in
     * each recipient's own language. {@code broadcast} cannot carry a per-recipient placeholder, so
     * the announcement is sent per recipient.
     */
    private void announceStarted() {
        String seconds = String.valueOf(remainingSeconds);
        for (Player player : platform.server().getOnlinePlayers()) {
            messages.send(player, "reboot.started", "seconds", seconds, "reason", reasonFor(player));
        }
        CommandSender console = platform.server().getConsoleSender();
        if (console != null) {
            messages.send(console, "reboot.started", "seconds", seconds, "reason", reasonFor(console));
        }
    }

    /**
     * The {@code <reason>} placeholder value: the supplied reason, or the recipient-localized {@code
     * reboot.no-reason} placeholder when none was given.
     */
    private Object reasonFor(CommandSender recipient) {
        return currentReason.isBlank()
                ? messages.renderFor(recipient, "reboot.no-reason")
                : currentReason;
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

        // Announce current value BEFORE decrementing (announces the supplied starting second first)
        if (config.titleSeconds().contains((int) remainingSeconds)) {
            Title.Times times =
                    Title.Times.times(Duration.ofSeconds(1), Duration.ofSeconds(4), Duration.ofSeconds(1));
            String seconds = String.valueOf(remainingSeconds);
            for (Player player : platform.server().getOnlinePlayers()) {
                Component titleMain = messages.renderFor(
                        player,
                        "reboot.countdown-title",
                        "seconds", seconds,
                        "reason", reasonFor(player));
                Component titleSub = messages.renderFor(
                        player,
                        "reboot.countdown-subtitle",
                        "reason", reasonFor(player),
                        "seconds", seconds);
                player.showTitle(Title.title(titleMain, titleSub, times));
            }
        }

        if (config.chatSeconds().contains((int) remainingSeconds)) {
            messages.broadcast("reboot.countdown-chat", "seconds", String.valueOf(remainingSeconds));
        }

        remainingSeconds--;

        if (remainingSeconds <= 0) {
            running = false;
            if (task != null) {
                task.cancel();
                task = null;
            }
            for (Player player : platform.server().getOnlinePlayers()) {
                Component kickMessage = messages.renderFor(
                        player,
                        "reboot.kick-message",
                        "reason", reasonFor(player),
                        "initiator", currentInitiator,
                        "website", "kreiscraft.de");
                player.kick(kickMessage, PlayerKickEvent.Cause.RESTART_COMMAND);
            }
            platform.server().shutdown();
        }
    }
}
