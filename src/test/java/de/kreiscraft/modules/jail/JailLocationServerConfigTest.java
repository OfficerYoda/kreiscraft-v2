package de.kreiscraft.modules.jail;

import static org.assertj.core.api.Assertions.assertThat;

import de.kreiscraft.core.config.YamlConfigService;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.bukkit.Location;
import org.bukkit.World;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

/**
 * The jail location is operator-owned Server Configuration: {@code /setjail} writes it through the
 * validated edit boundary into {@code config.yml}. Jail entries and restore locations stay
 * Activity Data in {@code data/jail.yml}.
 */
class JailLocationServerConfigTest {

    @TempDir
    Path tmp;

    private ServerMock server;
    private World world;
    private YamlConfigService config;
    private DefaultJailService service;

    @BeforeEach
    void setUp() throws IOException {
        server = MockBukkit.mock();
        world = server.addSimpleWorld("world");
        config = new YamlConfigService(tmp, getClass().getClassLoader());
        JailData data = new JailData(tmp);
        data.load();
        service = new DefaultJailService(data, config);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void bundledConfig_shipsAnOperatorOwnedJailLocation() {
        assertThat(service.jailLocation())
                .contains(new Location(world, 0, 100, 0, 0, 0));
    }

    @Test
    void jailLocation_returnsEmpty_whenAbsentFromConfig() throws IOException {
        Files.writeString(tmp.resolve("config.yml"), "modules:\n  jail:\n    enabled: true\n");
        config.reload();

        assertThat(service.jailLocation()).isEmpty();
    }

    @Test
    void setJailLocation_thenGet_returnsLocation() {
        Location loc = new Location(world, 10, 64, -5);
        service.setJailLocation(loc);
        assertThat(service.jailLocation()).contains(loc);
    }

    @Test
    void setJailLocation_persistsToConfigAndNotToDataFile() throws IOException {
        Location loc = new Location(world, 5, 100, 5);
        service.setJailLocation(loc);

        ConfigurationNode onDisk = load(tmp.resolve("config.yml"));
        assertThat(onDisk.node("modules", "jail", "location", "world").getString())
                .isEqualTo("world");
        assertThat(onDisk.node("modules", "jail", "location", "x").getDouble()).isEqualTo(5.0);
        assertThat(Files.exists(tmp.resolve("data/jail.yml"))).isFalse();
    }

    @Test
    void setJailLocation_roundTripsThroughAFreshService() throws IOException {
        Location loc = new Location(world, 12, 70, 3);
        service.setJailLocation(loc);

        YamlConfigService reopened = new YamlConfigService(tmp, getClass().getClassLoader());
        JailData data = new JailData(tmp);
        data.load();
        DefaultJailService restarted = new DefaultJailService(data, reopened);

        assertThat(restarted.jailLocation()).contains(loc);
    }

    @Test
    void setJailLocation_appliesImmediately() {
        Location loc = new Location(world, 200, 70, 200);
        service.setJailLocation(loc);
        // The live Server Configuration value is observed without a reload.
        assertThat(service.jailLocation()).contains(loc);
    }

    private static ConfigurationNode load(Path file) throws IOException {
        return YamlConfigurationLoader.builder().path(file).build().load();
    }
}
