package de.kreiscraft.modules.bot;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.bukkit.Location;
import org.bukkit.World;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

class BotDataTest {

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
    void load_parsesBotsSection(@TempDir Path tmp) throws IOException {
        // Write a YAML file with a bot entry
        Path botYml = tmp.resolve("bot.yml");
        Files.writeString(botYml,
                "bots:\n" +
                        "  testbot:\n" +
                        "    name: testbot\n" +
                        "    location:\n" +
                        "      world: world\n" +
                        "      x: 1.0\n" +
                        "      y: 64.0\n" +
                        "      z: -3.0\n" +
                        "      yaw: 0.0\n" +
                        "      pitch: 0.0\n" +
                        "    time: 42\n");

        BotData data = new BotData(tmp);
        data.load();

        Map<String, BotRecord> bots = data.get();
        assertThat(bots).containsKey("testbot");
        BotRecord rec = bots.get("testbot");
        assertThat(rec.name()).isEqualTo("testbot");
        assertThat(rec.time()).isEqualTo(42);
        assertThat(rec.location().getWorld()).isEqualTo(world);
        assertThat(rec.location().getX()).isEqualTo(1.0);
        assertThat(rec.location().getY()).isEqualTo(64.0);
        assertThat(rec.location().getZ()).isEqualTo(-3.0);
    }

    @Test
    void roundTrip_saveAndReload_returnsIdenticalContent(@TempDir Path tmp) throws IOException {
        Location loc = new Location(world, 10.0, 65.0, -20.0, 90f, 0f);
        BotRecord original = new BotRecord("mybot", loc, 7);

        BotData data = new BotData(tmp);
        data.load();
        data.update(m -> m.put("mybot", original));
        data.flushIfDirty();

        // Reload fresh instance
        BotData reloaded = new BotData(tmp);
        reloaded.load();

        Map<String, BotRecord> bots = reloaded.get();
        assertThat(bots).containsKey("mybot");
        BotRecord rec = bots.get("mybot");
        assertThat(rec.name()).isEqualTo("mybot");
        assertThat(rec.time()).isEqualTo(7);
        assertThat(rec.location()).isEqualTo(loc);
    }

    @Test
    void load_emptyFile_returnsEmptyMap(@TempDir Path tmp) throws IOException {
        BotData data = new BotData(tmp);
        data.load();
        assertThat(data.get()).isEmpty();
    }
}
