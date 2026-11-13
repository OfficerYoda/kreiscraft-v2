package de.kreiscraft.modules.dragonegg;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicLong;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

class DragonEggTrackerTest {
    @TempDir
    Path folder;
    private ServerMock server;
    private DragonEggData data;
    private DragonEggTracker tracker;
    private final AtomicLong now = new AtomicLong(1_000_000);

    @BeforeEach
    void setUp() throws Exception {
        server = MockBukkit.mock();
        server.addSimpleWorld("world");
        data = new DragonEggData(folder);
        data.load();
        tracker = new DragonEggTracker(server, data, now::get);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void singleEggFollowsHolderButCompassOnlyUsesTwoMinuteSamples() {
        var player = server.addPlayer();
        player.teleport(new Location(server.getWorld("world"), 0, 64, 0));
        ItemStack egg = new ItemStack(Material.DRAGON_EGG);
        assertThat(tracker.claim(egg)).isTrue();
        assertThat(tracker.claim(new ItemStack(Material.DRAGON_EGG))).isFalse();
        player.getInventory().setItem(0, egg);
        tracker.trackHolder(player);
        tracker.sample();
        assertThat(tracker.ping().orElseThrow().position().x()).isZero();
        player.teleport(new Location(player.getWorld(), 100, 64, 0));
        tracker.refresh();
        now.addAndGet(119_999);
        tracker.sample();
        assertThat(tracker.ping().orElseThrow().position().x()).isZero();
        now.incrementAndGet();
        tracker.sample();
        assertThat(tracker.ping().orElseThrow().position().x()).isEqualTo(100);
    }

    @Test
    void blockAndPingPersistWithoutLoadingChunksAndDestroyedEggIsNotReplaced() throws Exception {
        var block = server.getWorld("world").getBlockAt(48, 70, 48);
        block.setType(Material.DRAGON_EGG);
        assertThat(tracker.claimBlock(block)).isTrue();
        tracker.sample();
        var ping = tracker.ping().orElseThrow();
        data.flushIfDirty();
        data = new DragonEggData(folder);
        data.load();
        tracker = new DragonEggTracker(server, data, now::get);
        assertThat(tracker.ping()).contains(ping);
        assertThat(tracker.isTrackedBlock(block)).isTrue();
        tracker.destroy();
        data.flushIfDirty();
        data.load();
        assertThat(tracker.ping()).isEmpty();
        assertThat(tracker.claim(new ItemStack(Material.DRAGON_EGG))).isFalse();
        assertThat(tracker.claimBlock(block)).isFalse();
    }

    @Test
    void droppedEggMovesAndHolderDisconnectKeepsLastKnownLocation() throws Exception {
        ItemStack egg = new ItemStack(Material.DRAGON_EGG);
        tracker.claim(egg);
        var item = server.getWorld("world").dropItem(new Location(server.getWorld("world"), 3, 70, 4), egg);
        tracker.trackItem(item);
        tracker.sample();
        item.teleport(new Location(item.getWorld(), 20, 70, 4));
        now.addAndGet(120_000);
        tracker.sample();
        assertThat(tracker.ping().orElseThrow().position().x()).isEqualTo(20);
        var player = server.addPlayer();
        player.teleport(new Location(item.getWorld(), 40, 70, 4));
        player.getInventory().setItem(0, egg);
        tracker.trackHolder(player);
        item.remove();
        player.disconnect();
        data.flushIfDirty();
        data.load();
        now.addAndGet(120_000);
        tracker.sample();
        assertThat(tracker.ping().orElseThrow().position().x()).isEqualTo(40);
    }

    @Test
    void unloadedChunkRetainsPingButReloadedMissingBlockInvalidatesIt() throws Exception {
        var world = server.getWorld("world");
        var block = world.getBlockAt(48, 70, 48);
        block.setType(Material.DRAGON_EGG);
        tracker.claimBlock(block);
        tracker.sample();
        var original = tracker.ping();
        data.flushIfDirty();
        data.load();
        var unloaded = spy(world);
        doReturn(false).when(unloaded).isChunkLoaded(3, 3);
        var isolated = mock(Server.class);
        when(isolated.getWorld(world.getUID())).thenReturn(unloaded);
        tracker = new DragonEggTracker(isolated, data, now::get);
        block.setType(Material.AIR);
        tracker.refresh();
        assertThat(tracker.ping()).isEqualTo(original);
        doReturn(true).when(unloaded).isChunkLoaded(3, 3);
        tracker.refresh();
        assertThat(tracker.ping()).isEmpty();
    }

    @Test
    void loadedEntityChunkWithoutTrackedItemMustNotKeepReportingAnOldLocation() {
        ItemStack egg = new ItemStack(Material.DRAGON_EGG);
        tracker.claim(egg);
        var world = server.getWorld("world");
        var drop = world.dropItem(new Location(world, 48, 70, 48), egg);
        tracker.trackItem(drop);
        tracker.sample();
        drop.remove();
        tracker.entitiesLoaded(world.getChunkAt(3, 3), java.util.List.of());
        assertThat(tracker.ping()).isEmpty();
        assertThat(tracker.claim(new ItemStack(Material.DRAGON_EGG))).isFalse();
    }

    @Test
    void malformedActivityDataFailsClosedRatherThanInventingAnOrigin() throws Exception {
        java.nio.file.Files.createDirectories(folder.resolve("data"));
        java.nio.file.Files.writeString(folder.resolve("data/dragon-egg.yml"),
                "egg-id: not-a-uuid\nkind: BLOCK\nposition: {}\n");
        assertThatThrownBy(data::load).isInstanceOf(java.io.IOException.class);
    }

    @Test
    void firstEggAcquiredWhileOnlineIsClaimedIncludingCursorPossession() {
        var player = server.addPlayer();
        player.teleport(new Location(server.getWorld("world"), 7, 70, 8));
        player.setItemOnCursor(new ItemStack(Material.DRAGON_EGG));
        tracker.sample();
        assertThat(tracker.holdsTrackedEgg(player)).isTrue();
        assertThat(tracker.ping().orElseThrow().position().x()).isEqualTo(7);
    }
}
