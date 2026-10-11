package de.kreiscraft.modules.spawn;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.kreiscraft.core.config.YamlConfigService;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

class DefaultSpawnServiceTest {

    @TempDir
    Path tmp;

    private ServerMock server;
    private World world;
    private YamlConfigService config;
    private SpawnConfig moduleConfig;
    private DefaultSpawnService service;

    @BeforeEach
    void setUp() throws IOException {
        server = MockBukkit.mock();
        world = server.addSimpleWorld("world");
        config = new YamlConfigService(tmp, getClass().getClassLoader());
        moduleConfig = new SpawnConfig(50, null, null);
        service = new DefaultSpawnService(config, () -> moduleConfig);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    // ── fresh defaults ──────────────────────────────────────────────────────

    @Test
    void spawnLocation_returnsEmptyBeforeSet() {
        assertThat(service.spawnLocation()).isEmpty();
    }

    @Test
    void freshService_readsNoRetiredDataFile() {
        // There is no migration from data/spawn.yml. A fresh service starts with nothing set even
        // when an old data file happens to exist.
        assertThat(service.spawnLocation()).isEmpty();
        assertThat(Files.exists(tmp.resolve("data/spawn.yml"))).isFalse();
    }

    // ── write through Server Configuration ──────────────────────────────────

    @Test
    void setSpawn_thenGet_returnsLocation() {
        Location loc = new Location(world, 10, 64, -5);
        service.setSpawn(loc);
        assertThat(service.spawnLocation()).contains(loc);
    }

    @Test
    void setSpawn_persistsToConfigAndNotToDataFile() throws IOException {
        Location loc = new Location(world, 5, 100, 5);
        service.setSpawn(loc);

        ConfigurationNode onDisk = load(tmp.resolve("config.yml"));
        assertThat(onDisk.node("modules", "spawn", "location", "world").getString())
                .isEqualTo("world");
        assertThat(onDisk.node("modules", "spawn", "location", "x").getDouble()).isEqualTo(5.0);
        assertThat(Files.exists(tmp.resolve("data/spawn.yml"))).isFalse();
    }

    @Test
    void setSpawn_roundTripsThroughAFreshService() throws IOException {
        Location loc = new Location(world, 12, 70, 3);
        service.setSpawn(loc);

        YamlConfigService reopened =
                new YamlConfigService(tmp, getClass().getClassLoader());
        DefaultSpawnService restarted =
                new DefaultSpawnService(reopened, () -> new SpawnConfig(50, null, null));

        assertThat(restarted.spawnLocation()).contains(loc);
        Player player = mock(Player.class);
        when(player.getLocation()).thenReturn(new Location(world, 12 + 30, 70, 3));
        assertThat(restarted.isInSpawnAccessZone(player)).isTrue(); // 30 <= 50
    }

    // ── Spawn Access Zone (horizontal) ──────────────────────────────────────

    @Test
    void isInSpawnAccessZone_returnsFalse_whenNoSpawnSet() {
        Player player = mock(Player.class);
        when(player.getLocation()).thenReturn(new Location(world, 0, 64, 0));
        assertThat(service.isInSpawnAccessZone(player)).isFalse();
    }

    @Test
    void isInSpawnAccessZone_returnsTrue_whenClose() {
        service.setSpawn(new Location(world, 0, 64, 0));
        Player player = mock(Player.class);
        when(player.getLocation()).thenReturn(new Location(world, 10, 64, 0));
        assertThat(service.isInSpawnAccessZone(player)).isTrue(); // 10 < 50
    }

    @Test
    void isInSpawnAccessZone_returnsFalse_whenFar() {
        service.setSpawn(new Location(world, 0, 64, 0));
        Player player = mock(Player.class);
        when(player.getLocation()).thenReturn(new Location(world, 500, 64, 0));
        assertThat(service.isInSpawnAccessZone(player)).isFalse();
    }

    @Test
    void isInSpawnAccessZone_returnsFalse_whenDifferentWorld() {
        World otherWorld = server.addSimpleWorld("nether");
        service.setSpawn(new Location(world, 0, 64, 0));
        Player player = mock(Player.class);
        when(player.getLocation()).thenReturn(new Location(otherWorld, 0, 64, 0));
        assertThat(service.isInSpawnAccessZone(player)).isFalse();
    }

    @Test
    void isInSpawnAccessZone_ignoresVerticalDistance() {
        // User story #50: /spawn eligibility is based only on horizontal distance, so a player
        // directly above/below spawn within the horizontal radius is still eligible.
        service.setSpawn(new Location(world, 0, 64, 0));
        Player player = mock(Player.class);
        when(player.getLocation()).thenReturn(new Location(world, 30, 320, 0));
        assertThat(service.isInSpawnAccessZone(player)).isTrue();
    }

    @Test
    void isInSpawnAccessZone_includesExactBoundary() {
        service.setSpawn(new Location(world, 0, 64, 0));
        Player player = mock(Player.class);

        when(player.getLocation()).thenReturn(new Location(world, 50, 64, 0));
        assertThat(service.isInSpawnAccessZone(player)).isTrue();

        // Diagonal exactly on the radius: 3-4-5 scaled to 30-40-50.
        when(player.getLocation()).thenReturn(new Location(world, 30, 64, 40));
        assertThat(service.isInSpawnAccessZone(player)).isTrue();

        when(player.getLocation()).thenReturn(new Location(world, 50.001, 64, 0));
        assertThat(service.isInSpawnAccessZone(player)).isFalse();
    }

    @Test
    void isInSpawnAccessZone_usesReloadedSpawnRadius() {
        service.setSpawn(new Location(world, 0, 64, 0));
        Player player = mock(Player.class);
        when(player.getLocation()).thenReturn(new Location(world, 30, 64, 0));
        assertThat(service.isInSpawnAccessZone(player)).isTrue(); // within 50

        moduleConfig = new SpawnConfig(20, null, null);
        assertThat(service.isInSpawnAccessZone(player)).isFalse(); // 30 > reloaded 20
    }

    // ── Glide Zone (three-dimensional) ──────────────────────────────────────

    @Test
    void isInGlideZone_returnsFalse_whenNoSpawnSet() {
        Player player = mock(Player.class);
        when(player.getLocation()).thenReturn(new Location(world, 0, 64, 0));
        assertThat(service.isInGlideZone(player)).isFalse();
    }

    @Test
    void isInGlideZone_returnsFalse_whenDifferentWorld() {
        World otherWorld = server.addSimpleWorld("nether");
        service.setSpawn(new Location(world, 0, 64, 0));
        Player player = mock(Player.class);
        when(player.getLocation()).thenReturn(new Location(otherWorld, 0, 64, 0));
        assertThat(service.isInGlideZone(player)).isFalse();
    }

    @Test
    void isInGlideZone_countsVerticalDistance() {
        service.setSpawn(new Location(world, 0, 64, 0));
        Player player = mock(Player.class);

        // Horizontal 30, vertical 40 → 3D distance exactly 50 → inside.
        when(player.getLocation()).thenReturn(new Location(world, 30, 104, 0));
        assertThat(service.isInGlideZone(player)).isTrue();

        // Same horizontal 30, vertical 41 → 3D distance ~50.8 → outside.
        when(player.getLocation()).thenReturn(new Location(world, 30, 105, 0));
        assertThat(service.isInGlideZone(player)).isFalse();
    }

    @Test
    void isInGlideZone_boundaryIsInclusive() {
        service.setSpawn(new Location(world, 0, 64, 0));
        Player player = mock(Player.class);

        when(player.getLocation()).thenReturn(new Location(world, 50, 64, 0));
        assertThat(service.isInGlideZone(player)).isTrue();

        when(player.getLocation()).thenReturn(new Location(world, 0, 114, 0)); // exactly 50 up
        assertThat(service.isInGlideZone(player)).isTrue();

        when(player.getLocation()).thenReturn(new Location(world, 0, 115, 0)); // 51 up
        assertThat(service.isInGlideZone(player)).isFalse();
    }

    @Test
    void isInGlideZone_usesReloadedSpawnRadius() {
        service.setSpawn(new Location(world, 0, 64, 0));
        Player player = mock(Player.class);
        when(player.getLocation()).thenReturn(new Location(world, 40, 64, 0));
        assertThat(service.isInGlideZone(player)).isTrue();

        moduleConfig = new SpawnConfig(30, null, null);
        assertThat(service.isInGlideZone(player)).isFalse();
    }

    @Test
    void optionalSpawnLocation_onInitialLoad() {
        Optional<Location> opt = service.spawnLocation();
        assertThat(opt).isEmpty();
    }

    private static ConfigurationNode load(Path file) throws IOException {
        return YamlConfigurationLoader.builder().path(file).build().load();
    }
}
