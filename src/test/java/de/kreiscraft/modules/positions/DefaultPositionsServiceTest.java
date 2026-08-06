package de.kreiscraft.modules.positions;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.Location;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

class DefaultPositionsServiceTest {

    private ServerMock server;

    @TempDir
    Path tmp;

    private PositionsData data;
    private DefaultPositionsService service;

    @BeforeEach
    void setUp() throws IOException {
        server = MockBukkit.mock();
        server.addSimpleWorld("world");
        data = new PositionsData(tmp);
        data.load();
        service = new DefaultPositionsService(data);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void forPlayer_returnsEmpty_whenNoPositions() {
        UUID uuid = UUID.randomUUID();
        assertThat(service.forPlayer(uuid)).isEmpty();
    }

    @Test
    void set_thenGet_returnsEntry() {
        UUID uuid = UUID.randomUUID();
        Location loc = new Location(server.getWorld("world"), 10, 64, -5);

        service.set(uuid, "home", loc);

        Optional<PositionEntry> entry = service.get(uuid, "home");
        assertThat(entry).isPresent();
        assertThat(entry.get().location().getX()).isEqualTo(loc.getX());
        assertThat(entry.get().location().getY()).isEqualTo(loc.getY());
        assertThat(entry.get().location().getZ()).isEqualTo(loc.getZ());
    }

    @Test
    void forPlayer_returnsAllPositions() {
        UUID uuid = UUID.randomUUID();
        Location loc1 = new Location(server.getWorld("world"), 0, 64, 0);
        Location loc2 = new Location(server.getWorld("world"), 100, 64, 100);

        service.set(uuid, "spawn", loc1);
        service.set(uuid, "mine", loc2);

        Map<String, PositionEntry> positions = service.forPlayer(uuid);
        assertThat(positions).containsKeys("spawn", "mine");
        assertThat(positions).hasSize(2);
    }

    @Test
    void get_returnsEmpty_whenNotFound() {
        UUID uuid = UUID.randomUUID();
        assertThat(service.get(uuid, "nonexistent")).isEmpty();
    }

    @Test
    void remove_deletesEntry() {
        UUID uuid = UUID.randomUUID();
        Location loc = new Location(server.getWorld("world"), 5, 64, 5);
        service.set(uuid, "temp", loc);
        assertThat(service.get(uuid, "temp")).isPresent();

        service.remove(uuid, "temp");

        assertThat(service.get(uuid, "temp")).isEmpty();
    }

    @Test
    void remove_isNoop_whenNotFound() {
        UUID uuid = UUID.randomUUID();
        // Should not throw
        service.remove(uuid, "doesnotexist");
        assertThat(service.forPlayer(uuid)).isEmpty();
    }

    @Test
    void set_overwritesExistingEntry() {
        UUID uuid = UUID.randomUUID();
        Location first = new Location(server.getWorld("world"), 0, 64, 0);
        Location second = new Location(server.getWorld("world"), 50, 100, 50);

        service.set(uuid, "home", first);
        service.set(uuid, "home", second);

        Optional<PositionEntry> entry = service.get(uuid, "home");
        assertThat(entry).isPresent();
        assertThat(entry.get().location().getX()).isEqualTo(second.getX());
        assertThat(entry.get().location().getY()).isEqualTo(second.getY());
    }

    @Test
    void forPlayer_returnsUnmodifiableMap_cannotBeDirectlyMutated() {
        UUID uuid = UUID.randomUUID();
        Location loc = new Location(server.getWorld("world"), 0, 64, 0);
        service.set(uuid, "home", loc);

        Map<String, PositionEntry> view = service.forPlayer(uuid);
        assertThat(view).hasSize(1);

        // The returned view is unmodifiable — direct put should throw
        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> view.put("bad", new PositionEntry(loc)))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
