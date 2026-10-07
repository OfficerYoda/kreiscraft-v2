package de.kreiscraft.modules.bedrock;

import de.kreiscraft.shared.whitelist.WhitelistService;
import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Default implementation of {@link BedrockService}.
 *
 * <p>A registered Pending Bedrock Authorization is bound to the first observed UUID whose
 * normalized name matches. Binding also whitelists the observed identity; a bound authorization is
 * never rebound to a different UUID, so replacing it requires explicit removal and re-registration.
 *
 * <p>Thread-safety note: the data mutations are safe to call from an async context only because the
 * caller wraps {@link #handleLogin(String, UUID)} in {@code Scheduler.runOnMain} — the underlying
 * {@link de.kreiscraft.core.config.DataFile} maps are not thread-safe.
 */
public final class DefaultBedrockService implements BedrockService {

    private final BedrockData playerData;
    private final BedrockAuthorizationData authorizationData;
    private final WhitelistService whitelist;
    private final Logger logger;
    private volatile BedrockConfig config;

    public DefaultBedrockService(
            BedrockData playerData,
            BedrockAuthorizationData authorizationData,
            BedrockConfig config,
            WhitelistService whitelist,
            Logger logger) {
        this.playerData = Objects.requireNonNull(playerData, "playerData");
        this.authorizationData = Objects.requireNonNull(authorizationData, "authorizationData");
        this.config = Objects.requireNonNull(config, "config");
        this.whitelist = Objects.requireNonNull(whitelist, "whitelist");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    @Override
    public boolean isBedrock(String playerName) {
        return playerName.startsWith(config.prefix());
    }

    @Override
    public String normalize(String name) {
        Objects.requireNonNull(name, "name");
        String prefix = config.prefix();
        String body = name;
        if (!prefix.isEmpty() && body.regionMatches(true, 0, prefix, 0, prefix.length())) {
            body = body.substring(prefix.length());
        }
        return prefix + body.toLowerCase(Locale.ROOT);
    }

    @Override
    public BedrockAuthorizationResult register(String name) {
        Objects.requireNonNull(name, "name");
        String key = normalize(name);
        if (authorizationData.get().containsKey(key)) {
            return BedrockAuthorizationResult.ALREADY_REGISTERED;
        }
        authorizationData.update(
                map -> map.put(key, new PendingBedrockAuthorization(key, name, null, Instant.now())));
        return BedrockAuthorizationResult.REGISTERED;
    }

    @Override
    public BedrockAuthorizationResult remove(String name) {
        Objects.requireNonNull(name, "name");
        String key = normalize(name);
        PendingBedrockAuthorization existing = authorizationData.get().get(key);
        if (existing == null) {
            return BedrockAuthorizationResult.NOT_FOUND;
        }
        authorizationData.update(map -> map.remove(key));
        if (existing.boundUuid() != null) {
            whitelist.remove(existing.name());
        }
        return BedrockAuthorizationResult.REMOVED;
    }

    @Override
    public BedrockLoginOutcome handleLogin(String name, UUID uuid) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(uuid, "uuid");
        playerData.update(
                map -> map.computeIfAbsent(uuid, id -> new BedrockRecord(name, id, Instant.now())));

        Map<String, PendingBedrockAuthorization> authorizations = authorizationData.get();
        PendingBedrockAuthorization auth = authorizations.get(normalize(name));
        if (auth == null) {
            return BedrockLoginOutcome.NOT_AUTHORIZED;
        }
        if (!auth.isBound()) {
            authorizationData.update(map -> map.put(auth.normalizedName(), auth.boundTo(uuid)));
            whitelist.addBedrockPlayer(auth.name(), uuid);
            return BedrockLoginOutcome.BOUND;
        }
        if (auth.boundUuid().equals(uuid)) {
            whitelist.addBedrockPlayer(auth.name(), uuid);
            return BedrockLoginOutcome.ALREADY_BOUND;
        }
        logger.warning("bedrock: refusing to rebind authorization '"
                + auth.normalizedName() + "' from " + auth.boundUuid() + " to " + uuid);
        return BedrockLoginOutcome.CONFLICT;
    }

    @Override
    public Collection<PendingBedrockAuthorization> authorizations() {
        return Collections.unmodifiableCollection(authorizationData.get().values());
    }

    @Override
    public Collection<BedrockRecord> knownPlayers() {
        return Collections.unmodifiableCollection(playerData.get().values());
    }

    /** Called by {@link BedrockModule#onConfigReload} to propagate updated config. */
    public void updateConfig(BedrockConfig config) {
        this.config = Objects.requireNonNull(config, "config");
    }
}
