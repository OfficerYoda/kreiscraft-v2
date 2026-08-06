package de.kreiscraft.modules.settingsgui;

import java.util.UUID;

/**
 * Provides per-player settings state. Published on the {@link
 * de.kreiscraft.core.service.ServiceRegistry} so that other modules (e.g. combat,
 * clickthrough) can query player preferences.
 *
 * <p>This interface is a hard contract: Batch B modules depend on it. Do not remove or rename
 * any of these methods.
 */
public interface SettingsService {

    boolean isClickThroughEnabled(UUID playerId);

    void setClickThroughEnabled(UUID playerId, boolean enabled);

    CombatDisplay getCombatDisplay(UUID playerId);

    void setCombatDisplay(UUID playerId, CombatDisplay display);

    /**
     * Toggles click-through for the given player, subject to a 1-second debounce.
     *
     * @return {@code true} if the toggle was applied; {@code false} if rejected due to debounce
     */
    boolean toggleClickThrough(UUID playerId);
}
