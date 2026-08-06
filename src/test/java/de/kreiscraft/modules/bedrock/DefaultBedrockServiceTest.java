package de.kreiscraft.modules.bedrock;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DefaultBedrockServiceTest {

    private DefaultBedrockService service(Path tmp, String prefix) throws IOException {
        BedrockData data = new BedrockData(tmp);
        data.load();
        return new DefaultBedrockService(data, new BedrockConfig(prefix));
    }

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
    void seen_addsPlayerToKnownList(@TempDir Path tmp) throws IOException {
        DefaultBedrockService svc = service(tmp, ".");
        UUID uuid = UUID.randomUUID();
        svc.seen(".steve", uuid);
        assertThat(svc.knownPlayers()).hasSize(1);
        assertThat(svc.knownPlayers().iterator().next().name()).isEqualTo(".steve");
    }

    @Test
    void seen_deduplicatesByUuid(@TempDir Path tmp) throws IOException {
        DefaultBedrockService svc = service(tmp, ".");
        UUID uuid = UUID.randomUUID();
        svc.seen(".steve", uuid);
        svc.seen(".steve", uuid); // second call — same UUID
        assertThat(svc.knownPlayers()).hasSize(1);
    }

    @Test
    void seen_preservesFirstSeenTimestamp(@TempDir Path tmp) throws IOException {
        DefaultBedrockService svc = service(tmp, ".");
        UUID uuid = UUID.randomUUID();
        svc.seen(".steve", uuid);
        java.time.Instant first = svc.knownPlayers().iterator().next().firstSeen();
        svc.seen(".steve", uuid); // duplicate
        java.time.Instant second = svc.knownPlayers().iterator().next().firstSeen();
        assertThat(second).isEqualTo(first);
    }

    @Test
    void knownPlayers_returnsAllDistinctPlayers(@TempDir Path tmp) throws IOException {
        DefaultBedrockService svc = service(tmp, ".");
        svc.seen(".alice", UUID.randomUUID());
        svc.seen(".bob", UUID.randomUUID());
        assertThat(svc.knownPlayers()).hasSize(2);
    }

    @Test
    void updateConfig_changesPrefix(@TempDir Path tmp) throws IOException {
        DefaultBedrockService svc = service(tmp, ".");
        assertThat(svc.isBedrock("_alice")).isFalse();
        svc.updateConfig(new BedrockConfig("_"));
        assertThat(svc.isBedrock("_alice")).isTrue();
    }
}
