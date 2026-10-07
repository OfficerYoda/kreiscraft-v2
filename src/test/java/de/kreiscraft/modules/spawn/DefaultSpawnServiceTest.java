package de.kreiscraft.modules.spawn;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.junit.jupiter.api.AfterEach;

class DefaultSpawnServiceTest {

    private ServerMock server;
    private World world;
    private SpawnData data;
    private SpawnConfig config;
    private DefaultSpawnService service;

    @BeforeEach
    void setUp(@TempDir Path tmp) throws IOException {
        server = MockBukkit.mock();
        world = server.addSimpleWorld("world");
        data = new SpawnData(tmp);
        data.load();
        config = new SpawnConfig(50, null, null, null, null);
        service = new DefaultSpawnService(data, config);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void spawnLocation_returnsEmptyBeforeSet() {
        assertThat(service.spawnLocation()).isEmpty();
    }

    @Test
    void setSpawn_thenGet_returnsLocation() {
        Location loc = new Location(world, 10, 64, -5);
        service.setSpawn(loc);
        assertThat(service.spawnLocation()).isPresent();
        assertThat(service.spawnLocation().get()).isEqualTo(loc);
    }

    @Test
    void isInSpawnRadius_returnsFalse_whenNoSpawnSet() {
        Player player = mock(Player.class);
        when(player.getLocation()).thenReturn(new Location(world, 0, 64, 0));
        assertThat(service.isInSpawnRadius(player)).isFalse();
    }

    @Test
    void isInSpawnRadius_returnsTrue_whenClose() {
        Location spawn = new Location(world, 0, 64, 0);
        service.setSpawn(spawn);
        Player player = mock(Player.class);
        when(player.getLocation()).thenReturn(new Location(world, 10, 64, 0));
        assertThat(service.isInSpawnRadius(player)).isTrue(); // 10 < 50 range
    }

    @Test
    void isInSpawnRadius_returnsFalse_whenFar() {
        Location spawn = new Location(world, 0, 64, 0);
        service.setSpawn(spawn);
        Player player = mock(Player.class);
        when(player.getLocation()).thenReturn(new Location(world, 100, 64, 0));
        assertThat(service.isInSpawnRadius(player)).isFalse(); // 100 > 50 range
    }

    @Test
    void isInSpawnRadius_returnsFalse_whenDifferentWorld() {
        World otherWorld = server.addSimpleWorld("nether");
        Location spawn = new Location(world, 0, 64, 0);
        service.setSpawn(spawn);
        Player player = mock(Player.class);
        when(player.getLocation()).thenReturn(new Location(otherWorld, 0, 64, 0));
        assertThat(service.isInSpawnRadius(player)).isFalse();
    }

    @Test
    void updateConfig_changesRange() {
        Location spawn = new Location(world, 0, 64, 0);
        service.setSpawn(spawn);
        Player player = mock(Player.class);
        when(player.getLocation()).thenReturn(new Location(world, 30, 64, 0));

        // initially out of range (range=50, distance=30 → in range → true)
        assertThat(service.isInSpawnRadius(player)).isTrue();

        // shrink range to 20
        service.updateConfig(new SpawnConfig(20, null, null, null, null));
        assertThat(service.isInSpawnRadius(player)).isFalse(); // 30 > 20
    }

    @Test
    void setSpawn_persistsToData() {
        Location loc = new Location(world, 5, 100, 5);
        service.setSpawn(loc);
        assertThat(data.get().getLocation()).isEqualTo(loc);
    }

    @Test
    void setSpawn_withRadius_persistsRange() {
        Location loc = new Location(world, 0, 64, 0);
        service.setSpawn(loc, 75);
        assertThat(data.get().getTeleportRange()).isEqualTo(75);
        assertThat(data.get().getLocation()).isEqualTo(loc);
    }

    @Test
    void isInSpawnRadius_usesPersistedRange_overConfigDefault() {
        // config default is 50; persisted range will be 10
        Location spawn = new Location(world, 0, 64, 0);
        service.setSpawn(spawn, 10);
        Player player = mock(Player.class);
        when(player.getLocation()).thenReturn(new Location(world, 25, 64, 0)); // distance = 25
        // config range 50 would return true, but persisted range 10 < 25 → false
        assertThat(service.isInSpawnRadius(player)).isFalse();
    }

    @Test
    void optionalSpawnLocation_onInitialLoad() {
        Optional<Location> opt = service.spawnLocation();
        assertThat(opt).isEmpty();
    }
}
