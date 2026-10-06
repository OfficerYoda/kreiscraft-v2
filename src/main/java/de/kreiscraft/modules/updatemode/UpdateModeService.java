package de.kreiscraft.modules.updatemode;

import org.bukkit.entity.Player;

public interface UpdateModeService {

    boolean isActive();

    /**
     * Sets the persisted maintenance-mode state. Activating ({@code active == true}) also
     * immediately disconnects every online player who may not bypass and notifies the rest.
     */
    void setActive(boolean active);

    /** Whether the given player may stay online while maintenance mode is active. */
    boolean isBypassed(Player player);
}
