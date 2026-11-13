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
    void nestedBundleOnCursorIsClaimedAndDroppedContainerKeepsIdentity() {
        var player = server.addPlayer();
        player.teleport(new Location(server.getWorld("world"), 12, 70, 4));
        ItemStack inner = bundle(new ItemStack(Material.DRAGON_EGG));
        player.setItemOnCursor(bundle(inner));
        tracker.sample();
        assertThat(tracker.holdsTrackedEgg(player)).isTrue();
        assertThat(tracker.ping().orElseThrow().position().x()).isEqualTo(12);
        var dropped = player.getWorld().dropItem(new Location(player.getWorld(), 30, 70, 4),
                player.getItemOnCursor());
        player.setItemOnCursor(null);
        tracker.trackItem(dropped);
        now.addAndGet(120_000);
        tracker.sample();
        assertThat(tracker.isTrackedEntity(dropped)).isTrue();
        assertThat(tracker.ping().orElseThrow().position().x()).isEqualTo(30);
    }

    static ItemStack bundle(ItemStack item) {
        ItemStack bundle = new ItemStack(Material.BUNDLE);
        bundle.editMeta(org.bukkit.inventory.meta.BundleMeta.class, meta -> meta.setItems(java.util.List.of(item)));
        return bundle;
    }

    static ItemStack shulker(ItemStack item) {
        ItemStack box = new ItemStack(Material.SHULKER_BOX);
        box.editMeta(org.bukkit.inventory.meta.BlockStateMeta.class, meta -> {
            var state = (org.bukkit.block.ShulkerBox) meta.getBlockState();
            state.getInventory().setItem(0, item);
            meta.setBlockState(state);
        });
        return box;
    }

    @Test
    void nestedShulkerCopiesAreMarkedAndPlayerMovementStillUsesTwoMinuteSamples() {
        var player = server.addPlayer();
        player.teleport(new Location(server.getWorld("world"), 10, 70, 4));
        player.getInventory().setItem(0, shulker(bundle(shulker(new ItemStack(Material.DRAGON_EGG)))));
        tracker.sample();
        assertThat(tracker.containsTrackedEgg(player.getInventory().getItem(0))).isTrue();
        player.teleport(new Location(player.getWorld(), 80, 70, 4));
        now.addAndGet(119_999);
        tracker.sample();
        assertThat(tracker.ping().orElseThrow().position().x()).isEqualTo(10);
        now.incrementAndGet();
        tracker.sample();
        assertThat(tracker.ping().orElseThrow().position().x()).isEqualTo(80);
    }

    @Test
    void containerPositionPersistsAndRefreshDoesNotLoadItsChunk() throws Exception {
        var world = server.getWorld("world");
        var block = world.getBlockAt(48, 70, 48);
        block.setType(Material.BARREL);
        var container = (org.bukkit.block.Container) block.getState();
        container.getInventory().setItem(0, shulker(bundle(new ItemStack(Material.DRAGON_EGG))));
        assertThat(tracker.observeInventory(container.getInventory())).isTrue();
        tracker.sample();
        var original = tracker.ping();
        data.flushIfDirty();
        data = new DragonEggData(folder);
        data.load();
        var unloaded = spy(world);
        doReturn(false).when(unloaded).isChunkLoaded(3, 3);
        var isolated = mock(Server.class);
        when(isolated.getWorld(world.getUID())).thenReturn(unloaded);
        tracker = new DragonEggTracker(isolated, data, now::get);
        container.getInventory().clear();
        tracker.refresh();
        assertThat(tracker.ping()).isEqualTo(original);
        org.mockito.Mockito.verify(unloaded, org.mockito.Mockito.never()).getBlockAt(48, 70, 48);
        doReturn(true).when(unloaded).isChunkLoaded(3, 3);
        tracker.refresh();
        assertThat(tracker.ping()).isEmpty();
        assertThat(tracker.claim(new ItemStack(Material.DRAGON_EGG))).isFalse();
    }

    @Test
    void doubleChestUsesTheEggHalfNotTheCenter() {
        var world = server.getWorld("world");
        var leftBlock = world.getBlockAt(15, 70, 0);
        var rightBlock = world.getBlockAt(16, 70, 0);
        leftBlock.setType(Material.CHEST);
        rightBlock.setType(Material.CHEST);
        var left = ((org.bukkit.block.Chest) leftBlock.getState()).getBlockInventory();
        var right = ((org.bukkit.block.Chest) rightBlock.getState()).getBlockInventory();
        right.setItem(0, bundle(new ItemStack(Material.DRAGON_EGG)));
        var doubleChest = mock(org.bukkit.inventory.DoubleChestInventory.class);
        when(doubleChest.getLeftSide()).thenReturn(left);
        when(doubleChest.getRightSide()).thenReturn(right);
        assertThat(tracker.observeInventory(doubleChest)).isTrue();
        tracker.sample();
        assertThat(tracker.ping().orElseThrow().position().x()).isEqualTo(16);
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
    void legacyUppercaseCustodyLoadsAndStillSavesAsUppercase() throws Exception {
        var block = server.getWorld("world").getBlockAt(48, 70, 48);
        block.setType(Material.DRAGON_EGG);
        java.nio.file.Files.createDirectories(folder.resolve("data"));
        java.nio.file.Files.writeString(folder.resolve("data/dragon-egg.yml"),
                "egg-id: 00000000-0000-0000-0000-000000000001\nkind: BLOCK\nposition:\n  world: "
                        + block.getWorld().getUID()
                        + "\n  dimension: minecraft:overworld\n  x: 48\n  y: 70\n  z: 48\n");
        data.load();
        assertThat(tracker.isTrackedBlock(block)).isTrue();
        tracker.trackBlock(block);
        data.flushIfDirty();
        assertThat(java.nio.file.Files.readString(folder.resolve("data/dragon-egg.yml"))).contains("kind: BLOCK");
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"GHOST", "REMOVED"})
    void unknownCustodyValuesFailClosed(String custody) throws Exception {
        java.nio.file.Files.createDirectories(folder.resolve("data"));
        java.nio.file.Files.writeString(folder.resolve("data/dragon-egg.yml"), "kind: " + custody + "\n");
        assertThatThrownBy(data::load).isInstanceOf(java.io.IOException.class);
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
