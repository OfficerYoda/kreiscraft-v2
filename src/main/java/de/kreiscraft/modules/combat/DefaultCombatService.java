package de.kreiscraft.modules.combat;

import de.kreiscraft.core.message.MessageService;
import de.kreiscraft.core.scheduler.Scheduler;
import de.kreiscraft.platform.PaperPlatform;
import de.kreiscraft.shared.preferences.CombatDisplay;
import de.kreiscraft.shared.preferences.PlayerPreferencesService;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.entity.Player;

public final class DefaultCombatService implements CombatService {

    /**
     * Per-player combat state. The remaining duration is a whole-second counter stepped by a
     * per-player task, so the displayed countdown is driven entirely by the {@link Scheduler} clock
     * rather than by wall-clock sampling.
     */
    private static final class PerPlayerCombat {
        private long remainingSeconds;
        private Scheduler.Task task;

        private PerPlayerCombat(long remainingSeconds) {
            this.remainingSeconds = remainingSeconds;
        }
    }

    private final Scheduler scheduler;
    private final MessageService messages;
    private final PlayerPreferencesService preferences;
    private final PaperPlatform platform;
    private CombatConfig config;

    private final Map<UUID, PerPlayerCombat> state = new HashMap<>();

    public DefaultCombatService(
            Scheduler scheduler,
            MessageService messages,
            PlayerPreferencesService preferences,
            PaperPlatform platform,
            CombatConfig config) {
        this.scheduler = scheduler;
        this.messages = messages;
        this.preferences = preferences;
        this.platform = platform;
        this.config = config;
    }

    @Override
    public boolean isInCombat(UUID uuid) {
        return state.containsKey(uuid);
    }

    @Override
    public Duration remaining(UUID uuid) {
        PerPlayerCombat entry = state.get(uuid);
        return entry == null ? Duration.ZERO : Duration.ofSeconds(entry.remainingSeconds);
    }

    @Override
    public void tag(UUID uuid, Duration duration) {
        Duration before = remaining(uuid);
        // Cancel any existing countdown before replacing the state.
        PerPlayerCombat existing = state.get(uuid);
        if (existing != null && existing.task != null) {
            existing.task.cancel();
        }

        long seconds = wholeSeconds(duration);
        PerPlayerCombat entry = new PerPlayerCombat(seconds);
        state.put(uuid, entry);
        fireEvent(new CombatTaggedEvent(uuid, before, duration));

        // Show the full duration the moment the tag is applied; the sweep would otherwise make the
        // first displayed second depend on where the global tick happened to fall.
        display(uuid, seconds);
        if (seconds > 0) {
            entry.task = scheduler.runRepeating(
                    Duration.ofSeconds(1), Duration.ofSeconds(1), () -> tick(uuid));
        }
    }

    @Override
    public void clear(UUID uuid) {
        PerPlayerCombat entry = state.get(uuid);
        if (entry == null) {
            return;
        }
        Duration before = remaining(uuid);
        if (entry.task != null) {
            entry.task.cancel();
        }
        state.remove(uuid);
        fireEvent(new CombatTaggedEvent(uuid, before, Duration.ZERO));
    }

    public void updateConfig(CombatConfig config) {
        this.config = config;
    }

    public void shutdown() {
        for (PerPlayerCombat entry : state.values()) {
            if (entry.task != null) {
                entry.task.cancel();
            }
        }
        state.clear();
    }

    private void tick(UUID uuid) {
        PerPlayerCombat entry = state.get(uuid);
        if (entry == null) {
            return;
        }
        entry.remainingSeconds--;
        if (entry.remainingSeconds <= 0) {
            clear(uuid);
            return;
        }
        display(uuid, entry.remainingSeconds);
    }

    /** Renders the current remaining seconds to the player in their preferred combat display. */
    private void display(UUID uuid, long seconds) {
        Player player = platform.server().getPlayer(uuid);
        if (player == null || !player.isOnline()) {
            return;
        }
        String value = String.valueOf(seconds);
        Component msg = messages.renderFor(player, "combat.remaining", "seconds", value);
        // No stored preference means the combat module's configured default applies.
        CombatDisplay display = preferences.combatDisplay(uuid, config.defaultDisplay());
        switch (display) {
            case CHAT -> messages.send(player, "combat.remaining", "seconds", value);
            case ACTIONBAR -> player.sendActionBar(msg);
            case TITLE -> player.showTitle(Title.title(msg, Component.empty()));
        }
    }

    /** Rounds a duration up to whole seconds so a 4.5s tag still starts its display at 5. */
    private static long wholeSeconds(Duration duration) {
        long millis = duration.toMillis();
        return millis <= 0 ? 0 : (millis + 999) / 1000;
    }

    private void fireEvent(CombatTaggedEvent event) {
        platform.server().getPluginManager().callEvent(event);
    }
}
