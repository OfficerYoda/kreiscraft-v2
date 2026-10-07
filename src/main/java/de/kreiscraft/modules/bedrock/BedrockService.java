package de.kreiscraft.modules.bedrock;

import java.util.Collection;
import java.util.UUID;

/**
 * Service interface for detecting and tracking Bedrock (Geyser/Floodgate) players.
 *
 * <p>Bedrock players are identified by a configurable name prefix (default {@code "."}). Known
 * players are persisted across restarts so admins can whitelist them even when they are offline.
 *
 * <p>Pre-login access is modelled as a {@link PendingBedrockAuthorization}: an administrator
 * registers a name before that player has ever connected, the first matching login binds the
 * authorization to the observed UUID, and a bound authorization never silently rebinds. Replacing
 * an authorization requires {@link #remove(String) explicit removal} and re-registration.
 */
public interface BedrockService {

    /**
     * Returns {@code true} if {@code playerName} starts with the configured prefix,
     * indicating a Bedrock player.
     */
    boolean isBedrock(String playerName);

    /**
     * Canonical form of a Bedrock name: the configured prefix followed by the lower-cased name
     * body. Registration and login matching both go through this method so the configured prefix
     * and case cannot disagree.
     */
    String normalize(String name);

    /**
     * Registers a Pending Bedrock Authorization for {@code name}.
     *
     * @return {@link BedrockAuthorizationResult#REGISTERED} when a new unbound authorization was
     *     created, or {@link BedrockAuthorizationResult#ALREADY_REGISTERED} when the normalized
     *     name already had one
     */
    BedrockAuthorizationResult register(String name);

    /**
     * Explicitly removes the authorization registered for {@code name} and revokes its whitelist
     * entry if it had been bound. Re-registering afterwards starts a fresh, unbound authorization.
     *
     * @return {@link BedrockAuthorizationResult#REMOVED} when one existed, or
     *     {@link BedrockAuthorizationResult#NOT_FOUND} otherwise
     */
    BedrockAuthorizationResult remove(String name);

    /**
     * Handles a Bedrock player's pre-login: records the observed identity and binds any matching
     * unbound Pending Bedrock Authorization to the observed UUID. A bound authorization is never
     * rebound to a different UUID.
     *
     * @param name the player's in-game name (including the Bedrock prefix)
     * @param uuid the player's observed UUID
     * @return the outcome of matching this login against the registered authorizations
     */
    BedrockLoginOutcome handleLogin(String name, UUID uuid);

    /** Returns a snapshot of all registered Pending Bedrock Authorizations. */
    Collection<PendingBedrockAuthorization> authorizations();

    /** Returns a snapshot of all Bedrock players seen since the data file was created. */
    Collection<BedrockRecord> knownPlayers();
}
