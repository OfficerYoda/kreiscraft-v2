package de.kreiscraft.modules.startsequence;

import de.kreiscraft.core.config.ConfigService;
import de.kreiscraft.core.config.ServerConfigSchema;
import de.kreiscraft.core.message.MessageService;
import de.kreiscraft.core.scheduler.Scheduler;
import de.kreiscraft.platform.PaperPlatform;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.World;
import org.bukkit.entity.Player;

public final class DefaultStartSequenceService implements StartSequenceService {

    private static final String MODULE = "start-sequence";
    private static final String HAS_STARTED = "has-started";

    private final Scheduler scheduler;
    private final ConfigService configService;
    private final MessageService messages;
    private final PaperPlatform platform;
    private final Supplier<StartSequenceConfig> config;

    private final List<Scheduler.Task> activeTasks = new ArrayList<>();
    private World activeWorld;

    public DefaultStartSequenceService(
            Scheduler scheduler,
            Supplier<StartSequenceConfig> config,
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

        // Render the full promise before starting: countdown, countdown - 1, ..., 1, one number
        // per second, then the start title one second after the final number.
        long seconds = Math.max(0L, config.get().countdown().toSeconds());
        for (long number = seconds; number >= 1L; number--) {
            final int displayed = (int) number;
            Duration delay = Duration.ofSeconds(seconds - number);
            activeTasks.add(scheduler.runLater(delay, () -> showCountdownTitle(displayed)));
        }
        activeTasks.add(scheduler.runLater(Duration.ofSeconds(seconds), () -> {
            showStartedTitle();
            startBorderTransition(world);
        }));
    }

    @Override
    public void reset() {
        cancelAllTasks();
        if (activeWorld != null) {
            activeWorld.getWorldBorder().setSize(config.get().initialBorderSize());
            activeWorld = null;
        }
        configService.applyEdit(ServerConfigSchema.startSequenceStarted(), false);
    }

    public void shutdown() {
        cancelAllTasks();
    }

    private void cancelAllTasks() {
        activeTasks.forEach(Scheduler.Task::cancel);
        activeTasks.clear();
    }

    private void showCountdownTitle(int seconds) {
        String value = String.valueOf(seconds);
        Title.Times times = Title.Times.times(Duration.ZERO, Duration.ofSeconds(2), Duration.ZERO);
        // Full-volume experience-orb pickup for the countdown tick.
        Sound sound = Sound.sound(
                org.bukkit.Sound.ENTITY_EXPERIENCE_ORB_PICKUP, Sound.Source.PLAYER, 100f, 100f);
        showToAll(
                player -> messages.renderFor(player, "start-sequence.countdown-title", "seconds", value),
                player -> Component.empty(),
                times,
                sound);
    }

    private void showStartedTitle() {
        Title.Times times = Title.Times.times(Duration.ZERO, Duration.ofSeconds(4), Duration.ZERO);
        // Full-volume, low-pitch goat horn for the start title.
        Sound sound =
                Sound.sound(org.bukkit.Sound.ITEM_GOAT_HORN_SOUND_2, Sound.Source.PLAYER, 200f, 1f);
        showToAll(
                player -> messages.renderFor(player, "start-sequence.started"),
                player -> messages.renderFor(player, "start-sequence.started-subtitle"),
                times,
                sound);
    }

    private void showToAll(
            Function<Player, Component> titleFor,
            Function<Player, Component> subtitleFor,
            Title.Times times,
            Sound sound) {
        platform.server().getOnlinePlayers().forEach(p -> {
            p.showTitle(Title.title(titleFor.apply(p), subtitleFor.apply(p), times));
            p.playSound(sound);
        });
    }

    private void startBorderTransition(World world) {
        world.getWorldBorder()
                .changeSize(config.get().targetBorderSize(), toTicks(config.get().borderDuration()));
    }

    private static long toTicks(Duration duration) {
        return duration.toMillis() / 50L;
    }
}
