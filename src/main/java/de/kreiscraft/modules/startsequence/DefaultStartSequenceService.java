package de.kreiscraft.modules.startsequence;

import de.kreiscraft.core.config.ConfigService;
import de.kreiscraft.core.config.ServerConfigSchema;
import de.kreiscraft.core.message.MessageService;
import de.kreiscraft.core.scheduler.Scheduler;
import de.kreiscraft.platform.PaperPlatform;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.World;

public final class DefaultStartSequenceService implements StartSequenceService {

    private static final String MODULE = "start-sequence";
    private static final String HAS_STARTED = "has-started";

    private final Scheduler scheduler;
    private final ConfigService configService;
    private final MessageService messages;
    private final PaperPlatform platform;
    private StartSequenceConfig config;

    private final List<Scheduler.Task> activeTasks = new ArrayList<>();
    private World activeWorld;

    public DefaultStartSequenceService(
            Scheduler scheduler,
            StartSequenceConfig config,
            MessageService messages,
            PaperPlatform platform,
            ConfigService configService) {
        this.scheduler = scheduler;
        this.config = config;
        this.messages = messages;
        this.platform = platform;
        this.configService = configService;
    }

    @Override
    public boolean hasStarted() {
        return configService.module(MODULE).node(HAS_STARTED).getBoolean(false);
    }

    @Override
    public void trigger(World world) {
        if (hasStarted()) {
            throw new IllegalStateException("Season has already started; call reset() first");
        }
        configService.applyEdit(ServerConfigSchema.startSequenceStarted(), true);
        this.activeWorld = world;

        // RusticPrism starts its counter at the configured countdown, decrements it before the
        // first display, and launches the season once it reaches zero. The displayed numbers are
        // therefore (countdown - 1) .. 1, one per second, with the start title one second after
        // the final number.
        long firstNumber = Math.max(0L, config.countdown().toSeconds() - 1L);
        for (long number = firstNumber; number >= 1L; number--) {
            final int displayed = (int) number;
            Duration delay = Duration.ofSeconds(firstNumber - number);
            activeTasks.add(scheduler.runLater(delay, () -> showCountdownTitle(displayed)));
        }
        activeTasks.add(scheduler.runLater(Duration.ofSeconds(firstNumber), () -> {
            showStartedTitle();
            triggerBorderSequence(world);
        }));
    }

    @Override
    public void reset() {
        cancelAllTasks();
        if (activeWorld != null) {
            activeWorld.getWorldBorder().setSize(config.initialBorderSize());
            activeWorld = null;
        }
        configService.applyEdit(ServerConfigSchema.startSequenceStarted(), false);
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
        Title.Times times = Title.Times.times(Duration.ZERO, Duration.ofSeconds(2), Duration.ZERO);
        // Volume and pitch mirror RusticPrism's Sound.sound(..., 100, 100) call.
        Sound sound = Sound.sound(
                org.bukkit.Sound.ENTITY_EXPERIENCE_ORB_PICKUP, Sound.Source.PLAYER, 100f, 100f);
        showToAll(title, Component.empty(), times, sound);
    }

    private void showStartedTitle() {
        Component title = messages.render("start-sequence.started");
        Component subtitle = messages.render("start-sequence.started-subtitle");
        Title.Times times = Title.Times.times(Duration.ZERO, Duration.ofSeconds(4), Duration.ZERO);
        // Volume and pitch mirror RusticPrism's Sound.sound(..., 200, 1) call.
        Sound sound =
                Sound.sound(org.bukkit.Sound.ITEM_GOAT_HORN_SOUND_2, Sound.Source.PLAYER, 200f, 1f);
        showToAll(title, subtitle, times, sound);
    }

    private void showToAll(Component title, Component subtitle, Title.Times times, Sound sound) {
        platform.server().getOnlinePlayers().forEach(p -> {
            p.showTitle(Title.title(title, subtitle, times));
            p.playSound(sound);
        });
    }

    private void triggerBorderSequence(World world) {
        // Bukkit's WorldBorder.changeSize takes a duration in ticks. The configured phases are
        // tick values (RusticPrism parity), so derive the tick count from each Duration.
        world.getWorldBorder()
                .changeSize(config.targetBorderSize(), toTicks(config.phase1Duration()));

        Scheduler.Task phase2Task = scheduler.runLater(config.phase1To2Delay(), () -> world.getWorldBorder()
                .changeSize(config.targetBorderSize(), toTicks(config.phase2Duration())));
        activeTasks.add(phase2Task);

        Scheduler.Task instantTask = scheduler.runLater(config.phase2ToInstantDelay(),
                () -> world.getWorldBorder().setSize(config.targetBorderSize()));
        activeTasks.add(instantTask);
    }

    private static long toTicks(Duration duration) {
        return duration.toMillis() / 50L;
    }
}
