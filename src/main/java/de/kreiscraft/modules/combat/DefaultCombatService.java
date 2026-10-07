package de.kreiscraft.modules.combat;

import de.kreiscraft.core.message.MessageService;
import de.kreiscraft.core.scheduler.Scheduler;
import de.kreiscraft.platform.PaperPlatform;
import de.kreiscraft.shared.preferences.CombatDisplay;
import de.kreiscraft.shared.preferences.PlayerPreferencesService;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.entity.Player;

public final class DefaultCombatService implements CombatService {

    private record PerPlayerCombat(Instant taggedAt, Duration duration, Scheduler.Task expiryTask) {
    }

    private final Scheduler scheduler;
    private final MessageService messages;
    private final PlayerPreferencesService preferences;
    private final PaperPlatform platform;
    private CombatConfig config;

    private final Map<UUID, PerPlayerCombat> state = new HashMap<>();
    private Scheduler.Task displayTask;

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
        // Start global 1s display tick
        this.displayTask = scheduler.runRepeating(Duration.ofSeconds(1), Duration.ofSeconds(1), this::tickDisplay);
    }

    @Override
    public boolean isInCombat(UUID uuid) {
        return state.containsKey(uuid);
    }

    @Override
    public Duration remaining(UUID uuid) {
        PerPlayerCombat entry = state.get(uuid);
        if (entry == null) {
            return Duration.ZERO;
        }
        Instant expiresAt = entry.taggedAt().plus(entry.duration());
        Instant now = Instant.now();
        if (expiresAt.isBefore(now)) {
            return Duration.ZERO;
        }
        return Duration.between(now, expiresAt);
    }

    @Override
    public void tag(UUID uuid, Duration duration) {
        Duration before = remaining(uuid);
        // Cancel any existing expiry task
        PerPlayerCombat existing = state.get(uuid);
        if (existing != null) {
            existing.expiryTask().cancel();
        }
        Scheduler.Task expiryTask = scheduler.runLater(duration, () -> clear(uuid));
        state.put(uuid, new PerPlayerCombat(Instant.now(), duration, expiryTask));
        fireEvent(new CombatTaggedEvent(uuid, before, duration));
    }

    @Override
    public void clear(UUID uuid) {
        PerPlayerCombat entry = state.get(uuid);
        if (entry == null) {
            return;
        }
        Duration before = remaining(uuid);
        entry.expiryTask().cancel();
        state.remove(uuid);
        fireEvent(new CombatTaggedEvent(uuid, before, Duration.ZERO));
    }

    public void updateConfig(CombatConfig config) {
        this.config = config;
    }

    public void shutdown() {
        if (displayTask != null) {
            displayTask.cancel();
            displayTask = null;
        }
        // Cancel every pending per-player expiry task before clearing state
        for (PerPlayerCombat entry : state.values()) {
            entry.expiryTask().cancel();
        }
        state.clear();
    }

    private void fireEvent(CombatTaggedEvent event) {
        platform.server().getPluginManager().callEvent(event);
    }

    private void tickDisplay() {
        for (UUID uuid : List.copyOf(state.keySet())) {
            Player player = platform.server().getPlayer(uuid);
            if (player == null || !player.isOnline()) {
                continue;
            }
            long secs = remaining(uuid).getSeconds();
            Component msg = messages.renderFor(player, "combat.remaining", "seconds", String.valueOf(secs));
            // No stored preference means the combat module's configured default applies.
            CombatDisplay display = preferences.combatDisplay(uuid, config.defaultDisplay());
            switch (display) {
                case CHAT -> messages.send(player, "combat.remaining", "seconds", String.valueOf(secs));
                case ACTIONBAR -> player.sendActionBar(msg);
                case TITLE -> player.showTitle(Title.title(msg, Component.empty()));
            }
        }
    }
}
