package de.kreiscraft.modules.bedrock;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A pre-login authorization for a Bedrock player, registered by name before that player's first
 * login.
 *
 * <p>The authorization is keyed by its {@link #normalizedName() normalized name} — the configured
 * Bedrock prefix plus the lower-cased name body — so that registration and login matching agree on
 * a single canonical identity. {@link #name()} is the registered name carrying the configured
 * prefix, which keeps whitelist additions and removals on the Bedrock path. It starts unbound
 * ({@link #boundUuid()} is {@code null}) and is
 * bound permanently to the first matching observed UUID by
 * {@link BedrockService#handleLogin(String, UUID)}. A bound authorization never silently rebinds;
 * replacing it requires explicit removal and re-registration.
 */
public record PendingBedrockAuthorization(
        String normalizedName, String name, UUID boundUuid, Instant registeredAt) {

    public PendingBedrockAuthorization {
        Objects.requireNonNull(normalizedName, "normalizedName");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(registeredAt, "registeredAt");
    }

    /** Whether this authorization has been bound to an observed UUID. */
    public boolean isBound() {
        return boundUuid != null;
    }

    /** Returns a copy bound to {@code uuid}. */
    public PendingBedrockAuthorization boundTo(UUID uuid) {
        Objects.requireNonNull(uuid, "uuid");
        return new PendingBedrockAuthorization(normalizedName, name, uuid, registeredAt);
    }
}
