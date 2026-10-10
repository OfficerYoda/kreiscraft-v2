package de.kreiscraft.modules.positions;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;
import org.bukkit.Location;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

class PositionsDataTest {

    private ServerMock server;

    @TempDir
    Path tmp;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        server.addSimpleWorld("world");
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void loadEmpty_returnsEmptyMap() throws IOException {
        PositionsData data = new PositionsData(tmp);
        data.load();
        assertThat(data.get()).isEmpty();
    }

    @Test
    void updateAndFlush_persistsData() throws IOException {
        UUID uuid = UUID.randomUUID();
        Location loc = new Location(server.getWorld("world"), 10.0, 64.0, -5.0);

        PositionsData data = new PositionsData(tmp);
        data.load();
        data.update(m -> m.computeIfAbsent(uuid, k -> new java.util.LinkedHashMap<>())
                .put("home", new PositionEntry(loc)));
        data.flushIfDirty();

        String content = Files.readString(tmp.resolve("data/positions.yml"));
        assertThat(content).contains(uuid.toString());
        assertThat(content).contains("home");
        assertThat(content).contains("location");
    }

    @Test
    void reload_restoresPersistedData() throws IOException {
        UUID uuid = UUID.randomUUID();
        Location loc = new Location(server.getWorld("world"), 1.5, 100.0, -3.5, 90f, 45f);

        PositionsData data = new PositionsData(tmp);
        data.load();
        data.update(m -> m.computeIfAbsent(uuid, k -> new java.util.LinkedHashMap<>())
                .put("base", new PositionEntry(loc)));
        data.flushIfDirty();

        PositionsData data2 = new PositionsData(tmp);
        data2.load();
        PositionEntry loaded = data2.get().get(uuid).get("base");

        assertThat(loaded).isNotNull();
        assertThat(loaded.location().getX()).isEqualTo(loc.getX());
        assertThat(loaded.location().getY()).isEqualTo(loc.getY());
        assertThat(loaded.location().getZ()).isEqualTo(loc.getZ());
        assertThat(loaded.location().getWorld()).isNotNull();
        assertThat(loaded.location().getWorld().getName()).isEqualTo("world");
    }

    @Test
    void reload_restoresPositionProvenance() throws IOException {
        UUID owner = UUID.randomUUID();
        UUID saver = UUID.randomUUID();
        Instant savedAt = Instant.parse("2026-10-08T12:34:56Z");
        Location location = new Location(server.getWorld("world"), 1, 2, 3);
        PositionsData data = new PositionsData(tmp);
        data.load();
        data.update(m -> m.computeIfAbsent(owner, ignored -> new java.util.LinkedHashMap<>())
                .put("home", new PositionEntry(location, saver, "Alex", savedAt)));
        data.flushIfDirty();

        PositionsData reloaded = new PositionsData(tmp);
        reloaded.load();

        PositionEntry entry = reloaded.get().get(owner).get("home");
        assertThat(entry.savedBy()).isEqualTo(saver);
        assertThat(entry.savedByName()).isEqualTo("Alex");
        assertThat(entry.savedAt()).isEqualTo(savedAt);
    }

    @Test
    void reload_acceptsLegacyPositionWithoutProvenance() throws IOException {
        UUID owner = UUID.randomUUID();
        Files.createDirectories(tmp.resolve("data"));
        Files.writeString(tmp.resolve("data/positions.yml"), """
                %s:
                  legacy:
                    location:
                      world: world
                      x: 1.0
                      y: 64.0
                      z: 2.0
                      yaw: 0.0
                      pitch: 0.0
                """.formatted(owner));

        PositionsData data = new PositionsData(tmp);
        data.load();

        PositionEntry entry = data.get().get(owner).get("legacy");
        assertThat(entry.location().getWorld().getName()).isEqualTo("world");
        assertThat(entry.savedBy()).isNull();
        assertThat(entry.savedByName()).isNull();
        assertThat(entry.savedAt()).isNull();
    }

    @Test
    void multipleUuids_savedAndLoaded() throws IOException {
        UUID uuid1 = UUID.randomUUID();
        UUID uuid2 = UUID.randomUUID();
        Location loc1 = new Location(server.getWorld("world"), 0, 64, 0);
        Location loc2 = new Location(server.getWorld("world"), 100, 64, 100);

        PositionsData data = new PositionsData(tmp);
        data.load();
        data.update(m -> {
            m.computeIfAbsent(uuid1, k -> new java.util.LinkedHashMap<>())
                    .put("spawn", new PositionEntry(loc1));
            m.computeIfAbsent(uuid2, k -> new java.util.LinkedHashMap<>())
                    .put("home", new PositionEntry(loc2));
        });
        data.flushIfDirty();

        PositionsData data2 = new PositionsData(tmp);
        data2.load();

        assertThat(data2.get()).containsKey(uuid1);
        assertThat(data2.get()).containsKey(uuid2);
        assertThat(data2.get().get(uuid1)).containsKey("spawn");
        assertThat(data2.get().get(uuid2)).containsKey("home");
    }

    @Test
    void flushIfDirty_isNoop_whenClean() throws IOException {
        PositionsData data = new PositionsData(tmp);
        data.load();
        data.flushIfDirty();
        assertThat(Files.exists(tmp.resolve("data/positions.yml"))).isFalse();
    }
}
