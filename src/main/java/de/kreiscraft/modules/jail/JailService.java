package de.kreiscraft.modules.jail;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.Location;

/** Service interface for jailing and releasing players. */
public interface JailService {

    /** Returns true if the player with the given UUID is currently jailed. */
    boolean isJailed(UUID uuid);

    /**
     * Jails the player, storing {@code restoreLocation} for teleport-back on release.
     *
     * @param uuid the player UUID
     * @param restoreLocation the player's location at the time of jailing
     * @param reason human-readable reason for jailing
     */
    void jail(UUID uuid, Location restoreLocation, String reason);

    /**
     * Releases a jailed player.
     *
     * @return the {@link JailEntry} that was removed, containing the restore location; empty if
     *     the player was not jailed
     */
    Optional<JailEntry> release(UUID uuid);

    /** Returns an unmodifiable view of all currently jailed players. */
    Map<UUID, JailEntry> allJailed();
}
