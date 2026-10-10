package de.kreiscraft.modules.bedrock;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BedrockAuthorizationDataTest {

    @Test
    void load_withoutFile_startsWithEmptyState(@TempDir Path tmp) throws IOException {
        BedrockAuthorizationData data = new BedrockAuthorizationData(tmp);
        data.load();
        assertThat(data.get()).isEmpty();
    }

    @Test
    void boundAuthorization_roundTrips(@TempDir Path tmp) throws IOException {
        UUID uuid = UUID.randomUUID();
        Instant registeredAt = Instant.parse("2026-08-06T12:34:56Z");

        BedrockAuthorizationData data = new BedrockAuthorizationData(tmp);
        data.load();
        data.update(map -> map.put(".steve",
                new PendingBedrockAuthorization(".steve", ".Steve", uuid, registeredAt)));
        data.flushIfDirty();

        BedrockAuthorizationData reloaded = new BedrockAuthorizationData(tmp);
        reloaded.load();

        PendingBedrockAuthorization auth = reloaded.get().get(".steve");
        assertThat(auth).isNotNull();
        assertThat(auth.name()).isEqualTo(".Steve");
        assertThat(auth.boundUuid()).isEqualTo(uuid);
        assertThat(auth.registeredAt()).isEqualTo(registeredAt);
    }

    @Test
    void unboundAuthorization_roundTripsWithoutBoundUuid(@TempDir Path tmp) throws IOException {
        BedrockAuthorizationData data = new BedrockAuthorizationData(tmp);
        data.load();
        data.update(map -> map.put(".steve",
                new PendingBedrockAuthorization(".steve", ".Steve", null, Instant.now())));
        data.flushIfDirty();

        String yaml = Files.readString(tmp.resolve("data/bedrock-authorizations.yml"));
        assertThat(yaml).doesNotContain("bound-uuid");

        BedrockAuthorizationData reloaded = new BedrockAuthorizationData(tmp);
        reloaded.load();
        assertThat(reloaded.get().get(".steve").isBound()).isFalse();
    }

    @Test
    void missingRegisteredAt_loadsAsDeterministicSentinel(@TempDir Path tmp) throws IOException {
        // YAML written without a registered-at key — simulates hand-edited or legacy files.
        Path dir = tmp.resolve("data");
        Files.createDirectories(dir);
        Files.writeString(
                dir.resolve("bedrock-authorizations.yml"),
                "\".steve\":\n  name: \".Steve\"\n");

        BedrockAuthorizationData data = new BedrockAuthorizationData(tmp);
        data.load();

        assertThat(data.get().get(".steve").registeredAt())
                .as("missing registered-at must resolve to Instant.EPOCH, not Instant.now()")
                .isEqualTo(Instant.EPOCH);
    }

    @Test
    void missingRegisteredAt_isIdempotentAcrossMultipleLoads(@TempDir Path tmp) throws IOException {
        Path dir = tmp.resolve("data");
        Files.createDirectories(dir);
        Files.writeString(
                dir.resolve("bedrock-authorizations.yml"),
                "\".steve\":\n  name: \".Steve\"\n");

        BedrockAuthorizationData first = new BedrockAuthorizationData(tmp);
        first.load();

        BedrockAuthorizationData second = new BedrockAuthorizationData(tmp);
        second.load();

        assertThat(first.get().get(".steve").registeredAt())
                .isEqualTo(second.get().get(".steve").registeredAt());
    }
}
