package de.kreiscraft.core.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.bukkit.Location;
import org.bukkit.World;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LocationsTest {

    private ServerMock server;
    private World world;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        world = server.addSimpleWorld("world");
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void toMap_roundTrip() {
        Location original = new Location(world, 1.5, 64.0, -3.5, 90f, 45f);
        Map<String, Object> map = Locations.toMap(original);

        assertThat(map).containsEntry("world", "world")
                .containsEntry("x", 1.5)
                .containsEntry("y", 64.0)
                .containsEntry("z", -3.5)
                .containsEntry("yaw", 90.0)
                .containsEntry("pitch", 45.0);

        Location restored = Locations.fromMap(map, server::getWorld);
        assertThat(restored).isEqualTo(original);
    }

    @Test
    void distanceSquaredIgnoringWorld_computesCorrectly() {
        Location a = new Location(world, 0, 0, 0);
        Location b = new Location(world, 3, 0, 4);
        assertThat(Locations.distanceSquaredIgnoringWorld(a, b)).isEqualTo(25.0);
    }
}
