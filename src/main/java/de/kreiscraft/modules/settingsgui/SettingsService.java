package de.kreiscraft.modules.settingsgui;

import java.util.UUID;

/**
 * Provides per-player settings state. Published on the {@link
 * de.kreiscraft.core.service.ServiceRegistry} so that other modules (e.g. combat, clickthrough)
 * can query player preferences.
 *
     * <p>This interface is a hard contract: dependent modules (combat, clickthrough) depend on exactly these four methods. Do
 * not remove or rename them.
 */
public interface SettingsService {

    boolean isClickThroughEnabled(UUID playerId);

    void setClickThroughEnabled(UUID playerId, boolean enabled);

    CombatDisplay getCombatDisplay(UUID playerId);

    void setCombatDisplay(UUID playerId, CombatDisplay display);
}
