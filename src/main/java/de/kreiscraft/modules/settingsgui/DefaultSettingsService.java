package de.kreiscraft.modules.settingsgui;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public final class DefaultSettingsService implements SettingsService {

    private static final Duration CLICK_DEBOUNCE = Duration.ofSeconds(1);

    private final SettingsData data;

    public DefaultSettingsService(SettingsData data) {
        this.data = data;
    }

    private Settings settingsFor(UUID playerId) {
        Map<UUID, Settings> map = data.get();
        if (map == null) {
            return Settings.defaults();
        }
        return map.getOrDefault(playerId, Settings.defaults());
    }

    @Override
    public boolean isClickThroughEnabled(UUID playerId) {
        return settingsFor(playerId).clickThrough();
    }

    @Override
    public void setClickThroughEnabled(UUID playerId, boolean enabled) {
        Settings current = settingsFor(playerId);
        Settings updated = new Settings(enabled, current.combatDisplay(), current.lastClickAt());
        data.update(m -> m.put(playerId, updated));
    }

    @Override
    public CombatDisplay getCombatDisplay(UUID playerId) {
        return settingsFor(playerId).combatDisplay();
    }

    @Override
    public void setCombatDisplay(UUID playerId, CombatDisplay display) {
        Settings current = settingsFor(playerId);
        Settings updated = new Settings(current.clickThrough(), display, current.lastClickAt());
        data.update(m -> m.put(playerId, updated));
    }

    /**
     * Toggles click-through for the given player, subject to a 1-second per-player debounce.
     * Debounce timestamp is stored in the {@link Settings#lastClickAt()} field (in-memory only).
     *
     * @return {@code true} if the toggle was applied; {@code false} if rejected by debounce
     */
    public boolean toggleClickThrough(UUID playerId) {
        Settings current = settingsFor(playerId);
        Instant now = Instant.now();
        if (current.lastClickAt() != null
                && Duration.between(current.lastClickAt(), now).compareTo(CLICK_DEBOUNCE) < 0) {
            return false;
        }
        Settings updated = new Settings(!current.clickThrough(), current.combatDisplay(), now);
        data.update(m -> m.put(playerId, updated));
        return true;
    }
}
