package de.kreiscraft.modules.dragonegg;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import de.kreiscraft.core.message.MessageService;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicLong;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.CompassMeta;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

class DragonEggCompassTest {
    @TempDir
    Path folder;
    private ServerMock server;
    private DragonEggTracker tracker;
    private DragonEggCompass compass;
    private MessageService messages;
    private final AtomicLong now = new AtomicLong(1_000_000);

    @BeforeEach
    void setUp() throws Exception {
        server = MockBukkit.mock();
        server.addSimpleWorld("world");
        var data = new DragonEggData(folder);
        data.load();
        tracker = new DragonEggTracker(server, data, now::get);
        messages = mock(MessageService.class);
        compass = new DragonEggCompass(server, tracker, messages, now::get);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void recipeUsesExactlyCompassAndDragonHeadAndDoesNotChangeOrdinaryCompasses() {
        assertThat(compass.recipe().getIngredientList()).extracting(ItemStack::getType)
                .containsExactlyInAnyOrder(Material.COMPASS, Material.DRAGON_HEAD);
        assertThat(compass.isTrackingCompass(compass.recipe().getResult())).isTrue();
        ItemStack ordinary = new ItemStack(Material.COMPASS);
        var before = ordinary.clone();
        compass.update(ordinary);
        assertThat(ordinary).isEqualTo(before);
    }

    @Test
    void rightClickReportsSampleDistanceAndAgeOrTargetDimension() {
        var player = server.addPlayer();
        player.teleport(new Location(server.getWorld("world"), 0, 70, 0));
        var block = player.getWorld().getBlockAt(3, 70, 4);
        block.setType(Material.DRAGON_EGG);
        tracker.claimBlock(block);
        tracker.sample();
        now.addAndGet(30_000);
        ItemStack tracking = compass.recipe().getResult();
        compass.report(player, tracking);
        verify(messages).send(player, "dragon-egg.ping", "distance", 5L, "seconds", 30L);
        assertThat(((CompassMeta) tracking.getItemMeta()).getLodestone()).isEqualTo(block.getLocation());
        player.teleport(new Location(server.addSimpleWorld("other"), 0, 70, 0));
        compass.report(player, tracking);
        verify(messages).send(player, "dragon-egg.dimension", "dimension", block.getWorld().getKey().toString(),
                "seconds", 30L);
        tracker.destroy();
        compass.report(player, tracking);
        verify(messages).send(player, "dragon-egg.unavailable");
    }
}
