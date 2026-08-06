package de.kreiscraft.modules.anticheat;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DefaultHackLogServiceTest {

    @TempDir
    Path tmp;

    private HackLogData data;
    private DefaultHackLogService service;
    private UUID playerId;

    @BeforeEach
    void setUp() throws IOException {
        data = new HackLogData(tmp);
        data.load();
        service = new DefaultHackLogService(data);
        playerId = UUID.randomUUID();
    }

    @Test
    void record_persistsEntry() {
        HackEntry entry = new HackEntry(playerId, "TestPlayer", HackEntry.Kind.CHEAT, "fly", Instant.now());
        service.record(entry);
        List<HackEntry> result = service.forPlayer(playerId, HackEntry.Kind.CHEAT);
        assertThat(result).hasSize(1);
        assertThat(result.get(0).detail()).isEqualTo("fly");
        assertThat(result.get(0).playerName()).isEqualTo("TestPlayer");
    }

    @Test
    void forPlayer_filtersByKind() {
        Instant now = Instant.now();
        service.record(new HackEntry(playerId, "P", HackEntry.Kind.CHEAT, "killaura", now));
        service.record(new HackEntry(playerId, "P", HackEntry.Kind.XRAY, "diamond_ore", now.plusMillis(1)));

        assertThat(service.forPlayer(playerId, HackEntry.Kind.CHEAT))
                .hasSize(1)
                .extracting(HackEntry::detail)
                .containsExactly("killaura");
        assertThat(service.forPlayer(playerId, HackEntry.Kind.XRAY))
                .hasSize(1)
                .extracting(HackEntry::detail)
                .containsExactly("diamond_ore");
    }

    @Test
    void forPlayer_unknownUuid_returnsEmpty() {
        assertThat(service.forPlayer(UUID.randomUUID(), HackEntry.Kind.CHEAT)).isEmpty();
    }

    @Test
    void record_multipleEntries_preservedAll() {
        Instant now = Instant.now();
        service.record(new HackEntry(playerId, "P", HackEntry.Kind.CHEAT, "fly", now));
        service.record(new HackEntry(playerId, "P", HackEntry.Kind.CHEAT, "speed", now.plusMillis(1)));
        assertThat(service.forPlayer(playerId, HackEntry.Kind.CHEAT)).hasSize(2);
    }

    @Test
    void handshake_markAndCheck() {
        assertThat(service.hasHandshakePassed(playerId)).isFalse();
        service.markHandshakePassed(playerId);
        assertThat(service.hasHandshakePassed(playerId)).isTrue();
    }

    @Test
    void handshake_clearRemovesState() {
        service.markHandshakePassed(playerId);
        service.clearHandshake(playerId);
        assertThat(service.hasHandshakePassed(playerId)).isFalse();
    }

    @Test
    void data_roundTrip_persistsToYaml() throws IOException {
        Instant when = Instant.ofEpochMilli(1_700_000_000_000L);
        service.record(new HackEntry(playerId, "Alice", HackEntry.Kind.XRAY, "diamond_ore", when));
        data.flushIfDirty();

        HackLogData data2 = new HackLogData(tmp);
        data2.load();
        DefaultHackLogService service2 = new DefaultHackLogService(data2);
        List<HackEntry> loaded = service2.forPlayer(playerId, HackEntry.Kind.XRAY);
        assertThat(loaded).hasSize(1);
        assertThat(loaded.get(0).playerName()).isEqualTo("Alice");
        assertThat(loaded.get(0).detail()).isEqualTo("diamond_ore");
        assertThat(loaded.get(0).when()).isEqualTo(when);
    }
}
