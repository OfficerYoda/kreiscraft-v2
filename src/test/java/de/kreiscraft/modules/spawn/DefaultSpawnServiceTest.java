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
        moduleConfig = new SpawnConfig(50, null, null, null, null);
        service = new DefaultSpawnService(config, moduleConfig);
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
        // ADR-10: no migration from data/spawn.yml. A fresh service starts with nothing set even
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
    void setSpawn_withRadius_persistsRangeToConfig() throws IOException {
        service.setSpawn(new Location(world, 0, 64, 0), 75);

        ConfigurationNode onDisk = load(tmp.resolve("config.yml"));
        assertThat(onDisk.node("modules", "spawn", "teleport-range").getInt()).isEqualTo(75);
    }

    @Test
    void setSpawn_roundTripsThroughAFreshService() throws IOException {
        Location loc = new Location(world, 12, 70, 3);
        service.setSpawn(loc, 42);

        YamlConfigService reopened =
                new YamlConfigService(tmp, getClass().getClassLoader());
        DefaultSpawnService restarted =
                new DefaultSpawnService(reopened, new SpawnConfig(50, null, null, null, null));

        assertThat(restarted.spawnLocation()).contains(loc);
        Player player = mock(Player.class);
        when(player.getLocation()).thenReturn(new Location(world, 12 + 30, 70, 3));
        assertThat(restarted.isInSpawnRadius(player)).isTrue(); // 30 <= 42
    }

    // ── radius checks ───────────────────────────────────────────────────────

    @Test
    void isInSpawnRadius_returnsFalse_whenNoSpawnSet() {
        Player player = mock(Player.class);
        when(player.getLocation()).thenReturn(new Location(world, 0, 64, 0));
        assertThat(service.isInSpawnRadius(player)).isFalse();
    }

    @Test
    void isInSpawnRadius_returnsTrue_whenClose() {
        service.setSpawn(new Location(world, 0, 64, 0));
        Player player = mock(Player.class);
        when(player.getLocation()).thenReturn(new Location(world, 10, 64, 0));
        assertThat(service.isInSpawnRadius(player)).isTrue(); // 10 < configured 100
    }

    @Test
    void isInSpawnRadius_returnsFalse_whenFar() {
        service.setSpawn(new Location(world, 0, 64, 0));
        Player player = mock(Player.class);
        when(player.getLocation()).thenReturn(new Location(world, 500, 64, 0));
        assertThat(service.isInSpawnRadius(player)).isFalse();
    }

    @Test
    void isInSpawnRadius_returnsFalse_whenDifferentWorld() {
        World otherWorld = server.addSimpleWorld("nether");
        service.setSpawn(new Location(world, 0, 64, 0));
        Player player = mock(Player.class);
        when(player.getLocation()).thenReturn(new Location(otherWorld, 0, 64, 0));
        assertThat(service.isInSpawnRadius(player)).isFalse();
    }

    @Test
    void setSpawn_withRadius_appliesImmediately() {
        service.setSpawn(new Location(world, 0, 64, 0), 10);
        Player player = mock(Player.class);
        when(player.getLocation()).thenReturn(new Location(world, 25, 64, 0));
        // The live Server Configuration value (10) wins over the module default (50).
        assertThat(service.isInSpawnRadius(player)).isFalse();
    }

    @Test
    void isInSpawnRadius_fallsBackToModuleConfig_whenKeyAbsent() throws IOException {
        Files.writeString(tmp.resolve("config.yml"), "modules:\n  spawn:\n    enabled: true\n");
        config.reload();

        service.setSpawn(new Location(world, 0, 64, 0));
        Player player = mock(Player.class);
        when(player.getLocation()).thenReturn(new Location(world, 30, 64, 0));
        service.updateConfig(new SpawnConfig(20, null, null, null, null));

        assertThat(service.isInSpawnRadius(player)).isFalse(); // 30 > module default 20
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
