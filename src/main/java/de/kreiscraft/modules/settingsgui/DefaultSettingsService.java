package de.kreiscraft.modules.settingsgui;

import de.kreiscraft.shared.preferences.CombatDisplay;
import de.kreiscraft.shared.preferences.PlayerPreferencesService;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * GUI-facing client over the always-available {@link PlayerPreferencesService}.
 *
 * <p>The settings GUI owns no persistence of its own: every read and write is delegated to the
 * shared preferences boundary, so preferences persist whether or not this module is enabled. The
 * per-player click debounce is transient GUI state and is intentionally kept here rather than in the
 * persisted preference snapshot.
 */
public final class DefaultSettingsService {

    private static final Duration CLICK_DEBOUNCE = Duration.ofSeconds(1);

    /** Fallback shown in the GUI when a player has no stored combat-display preference. */
    private static final CombatDisplay DISPLAY_FALLBACK = CombatDisplay.ACTIONBAR;

    private final PlayerPreferencesService preferences;
    private final Map<UUID, Instant> lastClickAt = new ConcurrentHashMap<>();

    public DefaultSettingsService(PlayerPreferencesService preferences) {
        this.preferences = preferences;
    }

    public boolean isClickThroughEnabled(UUID playerId) {
        return preferences.clickThrough(playerId, true);
    }

    public void setClickThroughEnabled(UUID playerId, boolean enabled) {
        preferences.setClickThrough(playerId, enabled);
    }

    public CombatDisplay getCombatDisplay(UUID playerId) {
        return preferences.combatDisplay(playerId, DISPLAY_FALLBACK);
    }

    public void setCombatDisplay(UUID playerId, CombatDisplay display) {
        preferences.setCombatDisplay(playerId, display);
    }

    /**
     * Toggles click-through for the given player, subject to a 1-second per-player debounce.
     *
     * @return {@code true} if the toggle was applied; {@code false} if rejected by the debounce
     */
    public boolean toggleClickThrough(UUID playerId) {
        Instant now = Instant.now();
        Instant last = lastClickAt.get(playerId);
        if (last != null && Duration.between(last, now).compareTo(CLICK_DEBOUNCE) < 0) {
            return false;
        }
        preferences.setClickThrough(playerId, !isClickThroughEnabled(playerId));
        lastClickAt.put(playerId, now);
        return true;
    }
}
