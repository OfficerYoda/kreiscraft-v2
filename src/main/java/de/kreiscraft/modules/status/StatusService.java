package de.kreiscraft.modules.status;

import java.util.UUID;

public interface StatusService {

    /**
     * Returns the current status for the given player, or the default status if
     * no entry exists.
     */
    Status get(UUID playerId);

    /** Sets the MiniMessage prefix string for the player. */
    void setPrefix(UUID playerId, String prefixMiniMessage);

    /** Sets the MiniMessage suffix string for the player. */
    void setSuffix(UUID playerId, String suffixMiniMessage);

    /** Sets the ARGB color integer for the player. */
    void setColor(UUID playerId, int argb);

    /** Returns the hardcoded fallback status record. */
    Status defaults();
}
