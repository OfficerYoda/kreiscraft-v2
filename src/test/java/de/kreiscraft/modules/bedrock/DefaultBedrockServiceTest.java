package de.kreiscraft.modules.bedrock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import de.kreiscraft.shared.whitelist.WhitelistService;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DefaultBedrockServiceTest {

    private final WhitelistService whitelist = mock(WhitelistService.class);

    private DefaultBedrockService service(Path tmp, String prefix) throws IOException {
        return service(tmp, () -> new BedrockConfig(prefix));
    }

    private DefaultBedrockService service(Path tmp, Supplier<BedrockConfig> config) throws IOException {
        BedrockData players = new BedrockData(tmp);
        players.load();
        BedrockAuthorizationData authorizations = new BedrockAuthorizationData(tmp);
        authorizations.load();
        return new DefaultBedrockService(
                players,
                authorizations,
                config,
                whitelist,
                Logger.getLogger("DefaultBedrockServiceTest"));
    }

    // ── detection and normalization ─────────────────────────────────────────

    @Test
    void isBedrock_whenNameStartsWithPrefix_returnsTrue(@TempDir Path tmp) throws IOException {
        DefaultBedrockService svc = service(tmp, ".");
        assertThat(svc.isBedrock(".steve")).isTrue();
    }

    @Test
    void isBedrock_whenNameLacksPrefix_returnsFalse(@TempDir Path tmp) throws IOException {
        DefaultBedrockService svc = service(tmp, ".");
        assertThat(svc.isBedrock("Alice")).isFalse();
    }

    @Test
    void isBedrock_usesConfigurablePrefix(@TempDir Path tmp) throws IOException {
        DefaultBedrockService svc = service(tmp, "_");
        assertThat(svc.isBedrock("_steve")).isTrue();
        assertThat(svc.isBedrock(".steve")).isFalse();
    }

    @Test
    void normalize_prependsPrefixAndLowercasesBody(@TempDir Path tmp) throws IOException {
        DefaultBedrockService svc = service(tmp, ".");
        assertThat(svc.normalize(".Steve")).isEqualTo(".steve");
        assertThat(svc.normalize("Steve")).isEqualTo(".steve");
    }

    @Test
    void normalize_usesConfiguredPrefix(@TempDir Path tmp) throws IOException {
        DefaultBedrockService svc = service(tmp, "_");
        assertThat(svc.normalize("Alice")).isEqualTo("_alice");
        assertThat(svc.normalize("_ALICE")).isEqualTo("_alice");
    }

    // ── registration ────────────────────────────────────────────────────────

    @Test
    void register_createsUnboundAuthorization(@TempDir Path tmp) throws IOException {
        DefaultBedrockService svc = service(tmp, ".");

        BedrockAuthorizationResult result = svc.register(".Steve");

        assertThat(result).isEqualTo(BedrockAuthorizationResult.REGISTERED);
        assertThat(svc.authorizations()).hasSize(1);
        PendingBedrockAuthorization auth = svc.authorizations().iterator().next();
        assertThat(auth.normalizedName()).isEqualTo(".steve");
        assertThat(auth.name()).isEqualTo(".Steve");
        assertThat(auth.isBound()).isFalse();
    }

    @Test
    void register_deduplicatesByNormalizedName(@TempDir Path tmp) throws IOException {
        DefaultBedrockService svc = service(tmp, ".");
        svc.register(".Steve");

        BedrockAuthorizationResult result = svc.register(".steve"); // same normalized name

        assertThat(result).isEqualTo(BedrockAuthorizationResult.ALREADY_REGISTERED);
        assertThat(svc.authorizations()).hasSize(1);
    }

    @Test
    void register_normalizesPrefixAndCase(@TempDir Path tmp) throws IOException {
        DefaultBedrockService svc = service(tmp, ".");
        svc.register("Steve"); // no prefix, capital S

        BedrockAuthorizationResult result = svc.register(".STEVE");

        assertThat(result).isEqualTo(BedrockAuthorizationResult.ALREADY_REGISTERED);
        assertThat(svc.authorizations()).hasSize(1);
    }

    @Test
    void register_withoutPrefix_storesPrefixedNameForWhitelist(@TempDir Path tmp)
            throws IOException {
        DefaultBedrockService svc = service(tmp, ".");
        svc.register("Steve"); // no prefix

        PendingBedrockAuthorization auth = svc.authorizations().iterator().next();
        assertThat(auth.name()).isEqualTo(".Steve");

        UUID uuid = UUID.randomUUID();
        svc.handleLogin(".Steve", uuid);
        verify(whitelist).addBedrockPlayer(".Steve", uuid);
    }

    // ── login binding ───────────────────────────────────────────────────────

    @Test
    void handleLogin_withoutAuthorization_recordsPlayerAndReturnsNotAuthorized(@TempDir Path tmp)
            throws IOException {
        DefaultBedrockService svc = service(tmp, ".");
        UUID uuid = UUID.randomUUID();

        BedrockLoginOutcome outcome = svc.handleLogin(".steve", uuid);

        assertThat(outcome).isEqualTo(BedrockLoginOutcome.NOT_AUTHORIZED);
        assertThat(svc.knownPlayers()).hasSize(1);
        assertThat(svc.authorizations()).isEmpty();
        verify(whitelist, never()).addBedrockPlayer(any(), any());
    }

    @Test
    void handleLogin_firstMatch_bindsToObservedUuidAndWhitelists(@TempDir Path tmp)
            throws IOException {
        DefaultBedrockService svc = service(tmp, ".");
        svc.register(".Steve");
        UUID uuid = UUID.randomUUID();

        BedrockLoginOutcome outcome = svc.handleLogin(".Steve", uuid);

        assertThat(outcome).isEqualTo(BedrockLoginOutcome.BOUND);
        PendingBedrockAuthorization auth = svc.authorizations().iterator().next();
        assertThat(auth.boundUuid()).isEqualTo(uuid);
        verify(whitelist).addBedrockPlayer(".Steve", uuid);
    }

    @Test
    void handleLogin_matchesNormalizedPrefixAndCase(@TempDir Path tmp) throws IOException {
        DefaultBedrockService svc = service(tmp, ".");
        svc.register("Steve"); // ".steve" after normalization
        UUID uuid = UUID.randomUUID();

        BedrockLoginOutcome outcome = svc.handleLogin(".STEVE", uuid);

        assertThat(outcome).isEqualTo(BedrockLoginOutcome.BOUND);
        assertThat(svc.authorizations().iterator().next().boundUuid()).isEqualTo(uuid);
    }

    @Test
    void handleLogin_sameUuid_returnsAlreadyBoundWithoutChange(@TempDir Path tmp)
            throws IOException {
        DefaultBedrockService svc = service(tmp, ".");
        svc.register(".Steve");
        UUID uuid = UUID.randomUUID();
        svc.handleLogin(".Steve", uuid);
        Instant registeredAt = svc.authorizations().iterator().next().registeredAt();

        BedrockLoginOutcome outcome = svc.handleLogin(".Steve", uuid);

        assertThat(outcome).isEqualTo(BedrockLoginOutcome.ALREADY_BOUND);
        PendingBedrockAuthorization auth = svc.authorizations().iterator().next();
        assertThat(auth.boundUuid()).isEqualTo(uuid);
        assertThat(auth.registeredAt()).isEqualTo(registeredAt);
    }

    @Test
    void handleLogin_differentUuid_doesNotRebind(@TempDir Path tmp) throws IOException {
        DefaultBedrockService svc = service(tmp, ".");
        svc.register(".Steve");
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        svc.handleLogin(".Steve", first);

        BedrockLoginOutcome outcome = svc.handleLogin(".Steve", second);

        assertThat(outcome).isEqualTo(BedrockLoginOutcome.CONFLICT);
        assertThat(svc.authorizations().iterator().next().boundUuid()).isEqualTo(first);
        verify(whitelist).addBedrockPlayer(".Steve", first);
        verify(whitelist, never()).addBedrockPlayer(any(), eq(second));
    }

    // ── explicit removal and replacement lifecycle ──────────────────────────

    @Test
    void remove_unknown_returnsNotFound(@TempDir Path tmp) throws IOException {
        DefaultBedrockService svc = service(tmp, ".");

        assertThat(svc.remove(".nobody")).isEqualTo(BedrockAuthorizationResult.NOT_FOUND);
    }

    @Test
    void remove_existing_revokesWhitelistEntry(@TempDir Path tmp) throws IOException {
        DefaultBedrockService svc = service(tmp, ".");
        svc.register(".Steve");
        svc.handleLogin(".Steve", UUID.randomUUID());

        BedrockAuthorizationResult result = svc.remove(".steve");

        assertThat(result).isEqualTo(BedrockAuthorizationResult.REMOVED);
        assertThat(svc.authorizations()).isEmpty();
        verify(whitelist).remove(".Steve");
    }

    @Test
    void remove_withCustomPrefix_revokesUsingPrefixedName(@TempDir Path tmp) throws IOException {
        DefaultBedrockService svc = service(tmp, "_");
        svc.register("Alice"); // no prefix
        svc.handleLogin("_Alice", UUID.randomUUID());

        assertThat(svc.remove("_alice")).isEqualTo(BedrockAuthorizationResult.REMOVED);
        verify(whitelist).remove("_Alice");
    }

    @Test
    void replacement_requiresRemovalAndReRegistration(@TempDir Path tmp) throws IOException {
        DefaultBedrockService svc = service(tmp, ".");
        svc.register(".Steve");
        UUID first = UUID.randomUUID();
        svc.handleLogin(".Steve", first);

        // A different UUID cannot take over the bound authorization.
        assertThat(svc.handleLogin(".Steve", UUID.randomUUID()))
                .isEqualTo(BedrockLoginOutcome.CONFLICT);

        // Explicit removal followed by re-registration resets the authorization.
        assertThat(svc.remove(".Steve")).isEqualTo(BedrockAuthorizationResult.REMOVED);
        assertThat(svc.register(".Steve")).isEqualTo(BedrockAuthorizationResult.REGISTERED);
        assertThat(svc.authorizations().iterator().next().isBound()).isFalse();

        UUID replacement = UUID.randomUUID();
        assertThat(svc.handleLogin(".Steve", replacement)).isEqualTo(BedrockLoginOutcome.BOUND);
        assertThat(svc.authorizations().iterator().next().boundUuid()).isEqualTo(replacement);
    }

    // ── known players and config ────────────────────────────────────────────

    @Test
    void handleLogin_deduplicatesKnownPlayersByUuid(@TempDir Path tmp) throws IOException {
        DefaultBedrockService svc = service(tmp, ".");
        UUID uuid = UUID.randomUUID();
        svc.handleLogin(".steve", uuid);
        svc.handleLogin(".steve", uuid);
        assertThat(svc.knownPlayers()).hasSize(1);
    }

    @Test
    void configSwap_changesPrefixUsedForNormalization(@TempDir Path tmp) throws IOException {
        AtomicReference<BedrockConfig> config = new AtomicReference<>(new BedrockConfig("."));
        DefaultBedrockService svc = service(tmp, config::get);
        assertThat(svc.normalize("Alice")).isEqualTo(".alice");
        config.set(new BedrockConfig("_"));
        assertThat(svc.normalize("Alice")).isEqualTo("_alice");
    }
}
