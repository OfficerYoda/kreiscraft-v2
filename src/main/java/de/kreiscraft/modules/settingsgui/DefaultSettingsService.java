package de.kreiscraft.modules.settingsgui;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class DefaultSettingsService implements SettingsService {

    private static final Duration CLICK_DEBOUNCE = Duration.ofSeconds(1);

    private final SettingsData data;
    private final Map<UUID, Instant> lastClickTimes = new ConcurrentHashMap<>();

    public DefaultSettingsService(SettingsData data) {
        this.data = data;
    }

    private Settings settingsFor(UUID playerId) {
        Map<UUID, Settings> map = data.get();
        if (map == null)
            return Settings.defaults();
        return map.getOrDefault(playerId, Settings.defaults());
    }

    @Override
    public boolean isClickThroughEnabled(UUID playerId) {
        return settingsFor(playerId).clickThrough();
    }

    @Override
    public void setClickThroughEnabled(UUID playerId, boolean enabled) {
        Settings current = settingsFor(playerId);
        Settings updated = new Settings(enabled, current.combatDisplay());
        data.update(m -> m.put(playerId, updated));
    }

    @Override
    public CombatDisplay getCombatDisplay(UUID playerId) {
        return settingsFor(playerId).combatDisplay();
    }

    @Override
    public void setCombatDisplay(UUID playerId, CombatDisplay display) {
        Settings current = settingsFor(playerId);
        Settings updated = new Settings(current.clickThrough(), display);
        data.update(m -> m.put(playerId, updated));
    }

    @Override
    public boolean toggleClickThrough(UUID playerId) {
        Instant now = Instant.now();
        Instant last = lastClickTimes.get(playerId);
        if (last != null && Duration.between(last, now).compareTo(CLICK_DEBOUNCE) < 0) {
            return false;
        }
        lastClickTimes.put(playerId, now);
        setClickThroughEnabled(playerId, !isClickThroughEnabled(playerId));
        return true;
    }
}
