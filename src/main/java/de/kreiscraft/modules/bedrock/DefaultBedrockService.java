package de.kreiscraft.modules.bedrock;

import de.kreiscraft.shared.whitelist.WhitelistService;
import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.logging.Logger;

/**
 * Default implementation of {@link BedrockService}.
 *
 * <p>A registered Pending Bedrock Authorization is bound to the first observed UUID whose
 * normalized name matches. Binding also whitelists the observed identity; a bound authorization is
 * never rebound to a different UUID, so replacing it requires explicit removal and re-registration.
 *
 * <p>The module config is read live through the supplied {@link Supplier} so a reload is observed
 * without any push. Detection runs off the main thread ({@code AsyncPlayerPreLoginEvent}), so the
 * module holds the backing config in a {@code volatile} field.
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
    private final Supplier<BedrockConfig> config;

    public DefaultBedrockService(
            BedrockData playerData,
            BedrockAuthorizationData authorizationData,
            Supplier<BedrockConfig> config,
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
        return playerName.startsWith(config.get().prefix());
    }

    @Override
    public String normalize(String name) {
        Objects.requireNonNull(name, "name");
        String prefix = config.get().prefix();
        String body = name;
        if (!prefix.isEmpty() && body.regionMatches(true, 0, prefix, 0, prefix.length())) {
            body = body.substring(prefix.length());
        }
        return prefix + body.toLowerCase(Locale.ROOT);
    }

    /**
     * Ensures {@code name} carries the configured prefix while preserving the body's case. The
     * stored name is used for whitelist additions and removals, so a registration made without a
     * prefix still routes through the Bedrock whitelist path.
     */
    private String prefixed(String name) {
        String prefix = config.get().prefix();
        if (prefix.isEmpty() || name.regionMatches(true, 0, prefix, 0, prefix.length())) {
            return name;
        }
        return prefix + name;
    }

    @Override
    public BedrockAuthorizationResult register(String name) {
        Objects.requireNonNull(name, "name");
        String key = normalize(name);
        if (authorizationData.get().containsKey(key)) {
            return BedrockAuthorizationResult.ALREADY_REGISTERED;
        }
        authorizationData.update(
                map -> map.put(
                        key,
                        new PendingBedrockAuthorization(key, prefixed(name), null, Instant.now())));
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
}
