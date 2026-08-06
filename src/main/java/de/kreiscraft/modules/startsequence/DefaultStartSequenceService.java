package de.kreiscraft.modules.startsequence;

import de.kreiscraft.core.message.MessageService;
import de.kreiscraft.core.scheduler.Scheduler;
import de.kreiscraft.platform.PaperPlatform;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.World;

public final class DefaultStartSequenceService implements StartSequenceService {

    private final Scheduler scheduler;
    private final MessageService messages;
    private final PaperPlatform platform;
    private final StartSequenceData data;
    private StartSequenceConfig config;

    private final List<Scheduler.Task> activeTasks = new ArrayList<>();
    private World activeWorld;

    public DefaultStartSequenceService(
            Scheduler scheduler,
            StartSequenceConfig config,
            MessageService messages,
            PaperPlatform platform,
            StartSequenceData data) {
        this.scheduler = scheduler;
        this.config = config;
        this.messages = messages;
        this.platform = platform;
        this.data = data;
    }

    @Override
    public boolean hasStarted() {
        return data.hasStarted();
    }

    @Override
    public void trigger(World world) {
        if (data.hasStarted()) {
            throw new IllegalStateException("Season has already started; call reset() first");
        }
        data.setStarted(true);
        this.activeWorld = world;

        long countdownSeconds = config.countdown().toSeconds();
        for (long s = countdownSeconds; s >= 0; s--) {
            final long tick = s;
            Duration delay = Duration.ofSeconds(countdownSeconds - s);
            Scheduler.Task task = scheduler.runLater(delay, () -> {
                if (tick > 0) {
                    showCountdownTitle((int) tick);
                } else {
                    showStartedTitle();
                    triggerBorderSequence(world);
                }
            });
            activeTasks.add(task);
        }
    }

    @Override
    public void reset() {
        cancelAllTasks();
        if (activeWorld != null) {
            activeWorld.getWorldBorder().setSize(config.initialBorderSize());
            activeWorld = null;
        }
        data.setStarted(false);
    }

    public void updateConfig(StartSequenceConfig newConfig) {
        this.config = newConfig;
    }

    public void shutdown() {
        cancelAllTasks();
    }

    private void cancelAllTasks() {
        activeTasks.forEach(Scheduler.Task::cancel);
        activeTasks.clear();
    }

    private void showCountdownTitle(int seconds) {
        Component title =
                messages.render("start-sequence.countdown-title", "seconds", String.valueOf(seconds));
        Title.Times times =
                Title.Times.times(Duration.ZERO, Duration.ofMillis(1200), Duration.ofMillis(300));
        platform.server().getOnlinePlayers().forEach(p -> p.showTitle(Title.title(title, Component.empty(), times)));
    }

    private void showStartedTitle() {
        Component title = messages.render("start-sequence.started");
        Title.Times times =
                Title.Times.times(Duration.ZERO, Duration.ofSeconds(4), Duration.ZERO);
        platform.server().getOnlinePlayers().forEach(p -> p.showTitle(Title.title(title, Component.empty(), times)));
    }

    private void triggerBorderSequence(World world) {
        world.getWorldBorder().changeSize(config.targetBorderSize(), config.phase1Duration().toSeconds());

        Scheduler.Task phase2Task = scheduler.runLater(config.phase1To2Delay(), () -> world.getWorldBorder()
                .changeSize(config.targetBorderSize(), config.phase2Duration().toSeconds()));
        activeTasks.add(phase2Task);

        Scheduler.Task instantTask = scheduler.runLater(config.phase2ToInstantDelay(),
                () -> world.getWorldBorder().setSize(config.targetBorderSize()));
        activeTasks.add(instantTask);
    }
}
