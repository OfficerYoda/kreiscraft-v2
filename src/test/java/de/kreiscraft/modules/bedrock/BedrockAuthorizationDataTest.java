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
}
