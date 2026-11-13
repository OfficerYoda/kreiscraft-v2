package de.kreiscraft.modules.dragonegg;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.kreiscraft.core.message.MessageService;
import de.kreiscraft.core.scheduler.BukkitScheduler;
import java.nio.file.Path;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.FallingBlock;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDropItemEvent;
import org.bukkit.event.entity.EntityRemoveEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

class DragonEggListenerTest {
    @TempDir
    Path folder;
    private ServerMock server;
    private DragonEggTracker tracker;
    private DragonEggListener listener;

    @BeforeEach
    void setUp() throws Exception {
        server = MockBukkit.mock();
        server.addSimpleWorld("world");
        var data = new DragonEggData(folder);
        data.load();
        tracker = new DragonEggTracker(server, data, () -> 1_000_000);
        listener = new DragonEggListener(server, tracker,
                new DragonEggCompass(server, tracker, mock(MessageService.class), () -> 1_000_000),
                new BukkitScheduler(MockBukkit.createMockPlugin()));
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void teleportThenFallingBlockDropRetainsIdentityAndUnloadDoesNotDestroyIt() {
        var source = server.getWorld("world").getBlockAt(0, 70, 0);
        source.setType(Material.DRAGON_EGG);
        tracker.claimBlock(source);
        var destination = source.getWorld().getBlockAt(10, 75, 10);
        listener.onTeleport(new BlockFromToEvent(source, destination));
        source.setType(Material.AIR);
        destination.setType(Material.DRAGON_EGG);
        assertThat(tracker.isTrackedBlock(destination)).isTrue();
        FallingBlock falling = mock(FallingBlock.class);
        when(falling.getUniqueId()).thenReturn(UUID.randomUUID());
        when(falling.getLocation()).thenReturn(destination.getLocation());
        when(falling.getPersistentDataContainer())
                .thenReturn(new ItemStack(Material.DRAGON_EGG).getItemMeta().getPersistentDataContainer());
        listener.onFalling(new EntityChangeBlockEvent(falling, destination, Material.AIR.createBlockData()));
        destination.setType(Material.AIR);
        assertThat(tracker.isTrackedEntity(falling)).isTrue();
        var drop = destination.getWorld().dropItem(new Location(destination.getWorld(), 10, 72, 10),
                new ItemStack(Material.DRAGON_EGG));
        listener.onEntityDrop(new EntityDropItemEvent(falling, drop));
        assertThat(tracker.isTrackedEgg(drop.getItemStack())).isTrue();
        assertThat(tracker.isTrackedEntity(drop)).isTrue();
        listener.onRemove(new EntityRemoveEvent(falling, EntityRemoveEvent.Cause.DROP));
        listener.onRemove(new EntityRemoveEvent(drop, EntityRemoveEvent.Cause.UNLOAD));
        tracker.sample();
        assertThat(tracker.ping().orElseThrow().position().y()).isEqualTo(72);
        listener.onRemove(new EntityRemoveEvent(drop, EntityRemoveEvent.Cause.DESPAWN));
        assertThat(tracker.ping()).isEmpty();
    }

    @Test
    void pistonDestroyedEggIsTrackedEvenWhenExcludedFromMovedBlocks() {
        var piston = server.getWorld("world").getBlockAt(0, 70, 0);
        piston.setType(Material.PISTON);
        var egg = piston.getRelative(BlockFace.EAST);
        egg.setType(Material.DRAGON_EGG);
        tracker.claimBlock(egg);
        listener.onPistonExtend(new BlockPistonExtendEvent(piston, java.util.List.of(), BlockFace.EAST));
        egg.setType(Material.AIR);
        var item = egg.getWorld().dropItem(egg.getLocation(), new ItemStack(Material.DRAGON_EGG));
        listener.onSpawn(new ItemSpawnEvent(item));
        server.getScheduler().performOneTick();
        tracker.sample();
        assertThat(tracker.isTrackedEgg(item.getItemStack())).isTrue();
        assertThat(tracker.ping().orElseThrow().position().x()).isEqualTo(1);
    }

    @Test
    void adjacentAdditionalEggDropCannotStealThePendingBlockIdentity() {
        var piston = server.getWorld("world").getBlockAt(0, 70, 0);
        var egg = piston.getRelative(BlockFace.EAST);
        egg.setType(Material.DRAGON_EGG);
        tracker.claimBlock(egg);
        listener.onPistonExtend(new BlockPistonExtendEvent(piston, java.util.List.of(), BlockFace.EAST));
        var extra = egg.getWorld().dropItem(new Location(egg.getWorld(), 2.2, 70.5, 0.5),
                new ItemStack(Material.DRAGON_EGG));
        listener.onSpawn(new ItemSpawnEvent(extra));
        assertThat(tracker.isTrackedEgg(extra.getItemStack())).isFalse();
        var tracked = egg.getWorld().dropItem(new Location(egg.getWorld(), 1.2, 70.5, 0.5),
                new ItemStack(Material.DRAGON_EGG));
        listener.onSpawn(new ItemSpawnEvent(tracked));
        assertThat(tracker.isTrackedEgg(tracked.getItemStack())).isTrue();
    }

    @Test
    void chunkDiscoveryFindsNestedEggInPlacedChestAndPickupMovesItToCursor() {
        var block = server.getWorld("world").getBlockAt(48, 70, 48);
        block.setType(Material.CHEST);
        var chest = (org.bukkit.block.Chest) block.getState();
        chest.getBlockInventory().setItem(0, DragonEggTrackerTest.bundle(new ItemStack(Material.DRAGON_EGG)));
        var chunk = org.mockito.Mockito.spy(block.getChunk());
        org.mockito.Mockito.doReturn(new org.bukkit.block.BlockState[] {chest}).when(chunk).getTileEntities();
        listener.onChunkLoad(new org.bukkit.event.world.ChunkLoadEvent(chunk, false));
        tracker.sample();
        assertThat(tracker.ping().orElseThrow().position().x()).isEqualTo(48);
        var player = server.addPlayer();
        player.setItemOnCursor(chest.getBlockInventory().getItem(0));
        chest.getBlockInventory().clear();
        tracker.refresh();
        assertThat(tracker.holdsTrackedEgg(player)).isTrue();
    }

    @Test
    void nativeEnderChestRejectsCursorAndShiftTransfersOfEveryEggWithoutMutation() {
        server.getPluginManager().registerEvents(listener, MockBukkit.createMockPlugin());
        var player = server.addPlayer();
        var view = player.openInventory(player.getEnderChest());
        ItemStack egg = DragonEggTrackerTest.bundle(new ItemStack(Material.DRAGON_EGG));
        player.setItemOnCursor(egg);
        var click = new org.bukkit.event.inventory.InventoryClickEvent(view,
                org.bukkit.event.inventory.InventoryType.SlotType.CONTAINER, 0,
                org.bukkit.event.inventory.ClickType.LEFT, org.bukkit.event.inventory.InventoryAction.PLACE_ALL);
        server.getPluginManager().callEvent(click);
        assertThat(click.isCancelled()).isTrue();
        assertThat(player.getItemOnCursor()).isEqualTo(egg);
        assertThat(player.getEnderChest().getItem(0)).isNull();
        player.setItemOnCursor(null);
        view.setItem(27, egg);
        var shift = new org.bukkit.event.inventory.InventoryClickEvent(view,
                org.bukkit.event.inventory.InventoryType.SlotType.CONTAINER, 27,
                org.bukkit.event.inventory.ClickType.SHIFT_LEFT,
                org.bukkit.event.inventory.InventoryAction.MOVE_TO_OTHER_INVENTORY);
        server.getPluginManager().callEvent(shift);
        assertThat(shift.isCancelled()).isTrue();
        assertThat(view.getItem(27)).isEqualTo(egg);
    }

    @Test
    void nativeEnderChestRejectsHotbarOffhandAndMixedDragButAllowsOrdinaryItemsAndEggRemoval() {
        server.getPluginManager().registerEvents(listener, MockBukkit.createMockPlugin());
        var player = server.addPlayer();
        var view = player.openInventory(player.getEnderChest());
        ItemStack box = DragonEggTrackerTest.shulker(DragonEggTrackerTest.bundle(new ItemStack(Material.DRAGON_EGG)));
        player.getInventory().setItem(0, box);
        var hotbar = new org.bukkit.event.inventory.InventoryClickEvent(view,
                org.bukkit.event.inventory.InventoryType.SlotType.CONTAINER, 0,
                org.bukkit.event.inventory.ClickType.NUMBER_KEY,
                org.bukkit.event.inventory.InventoryAction.HOTBAR_SWAP, 0);
        server.getPluginManager().callEvent(hotbar);
        assertThat(hotbar.isCancelled()).isTrue();
        assertThat(player.getInventory().getItem(0)).isEqualTo(box);
        player.getInventory().setItemInOffHand(box);
        var offhand = new org.bukkit.event.inventory.InventoryClickEvent(view,
                org.bukkit.event.inventory.InventoryType.SlotType.CONTAINER, 0,
                org.bukkit.event.inventory.ClickType.SWAP_OFFHAND,
                org.bukkit.event.inventory.InventoryAction.HOTBAR_SWAP);
        server.getPluginManager().callEvent(offhand);
        assertThat(offhand.isCancelled()).isTrue();
        assertThat(player.getInventory().getItemInOffHand()).isEqualTo(box);
        player.setItemOnCursor(box);
        var drag = new org.bukkit.event.inventory.InventoryDragEvent(view, null, box, false,
                java.util.Map.of(0, box, 28, new ItemStack(Material.STONE)));
        server.getPluginManager().callEvent(drag);
        assertThat(drag.isCancelled()).isTrue();
        assertThat(player.getItemOnCursor()).isEqualTo(box);
        assertThat(player.getEnderChest().isEmpty()).isTrue();
        player.setItemOnCursor(new ItemStack(Material.STONE));
        var ordinary = new org.bukkit.event.inventory.InventoryClickEvent(view,
                org.bukkit.event.inventory.InventoryType.SlotType.CONTAINER, 0,
                org.bukkit.event.inventory.ClickType.LEFT, org.bukkit.event.inventory.InventoryAction.PLACE_ALL);
        server.getPluginManager().callEvent(ordinary);
        assertThat(ordinary.isCancelled()).isFalse();
        player.setItemOnCursor(null);
        player.getEnderChest().setItem(0, box);
        var remove = new org.bukkit.event.inventory.InventoryClickEvent(view,
                org.bukkit.event.inventory.InventoryType.SlotType.CONTAINER, 0,
                org.bukkit.event.inventory.ClickType.SHIFT_LEFT,
                org.bukkit.event.inventory.InventoryAction.MOVE_TO_OTHER_INVENTORY);
        server.getPluginManager().callEvent(remove);
        assertThat(remove.isCancelled()).isFalse();
    }

    @Test
    void hopperPickupThenTransferSurvivesSourceEntityRemoval() {
        var world = server.getWorld("world");
        var sourceBlock = world.getBlockAt(10, 70, 4);
        sourceBlock.setType(Material.HOPPER);
        var source = ((org.bukkit.block.Container) sourceBlock.getState()).getInventory();
        var targetBlock = world.getBlockAt(20, 70, 4);
        targetBlock.setType(Material.BARREL);
        var target = ((org.bukkit.block.Container) targetBlock.getState()).getInventory();
        ItemStack box = DragonEggTrackerTest.shulker(new ItemStack(Material.DRAGON_EGG));
        var drop = world.dropItem(new Location(world, 5, 70, 4), box);
        listener.onSpawn(new ItemSpawnEvent(drop));
        box = drop.getItemStack();
        listener.onInventoryPickup(new org.bukkit.event.inventory.InventoryPickupItemEvent(source, drop));
        source.setItem(0, box);
        listener.onRemove(new EntityRemoveEvent(drop, EntityRemoveEvent.Cause.PICKUP));
        drop.remove();
        server.getScheduler().performOneTick();
        tracker.sample();
        assertThat(tracker.ping().orElseThrow().position().x()).isEqualTo(10);
        listener.onInventoryMove(new org.bukkit.event.inventory.InventoryMoveItemEvent(source, box, target, true));
        source.clear();
        target.setItem(0, box);
        server.getScheduler().performOneTick();
        tracker.refresh();
        assertThat(tracker.containsTrackedEgg(target.getItem(0))).isTrue();
        tracker.unavailable();
        var chunk = org.mockito.Mockito.spy(targetBlock.getChunk());
        org.mockito.Mockito.doReturn(new org.bukkit.block.BlockState[] {targetBlock.getState()}).when(chunk)
                .getTileEntities();
        listener.onChunkLoad(new org.bukkit.event.world.ChunkLoadEvent(chunk, false));
        assertThat(tracker.claimed()).isTrue();
        tracker.sample();
        assertThat(tracker.ping()).isPresent();
    }

    @Test
    void containedEggFollowsShulkerPlacementAndBreakDrop() {
        var world = server.getWorld("world");
        var player = server.addPlayer();
        ItemStack egg = new ItemStack(Material.DRAGON_EGG);
        tracker.claim(egg);
        ItemStack box = DragonEggTrackerTest.shulker(DragonEggTrackerTest.bundle(egg));
        var block = world.getBlockAt(42, 70, 4);
        var replaced = block.getState();
        block.setType(Material.SHULKER_BOX);
        var container = (org.bukkit.block.ShulkerBox) block.getState();
        container.getInventory().setItem(0, DragonEggTrackerTest.bundle(egg));
        listener.onPlace(new org.bukkit.event.block.BlockPlaceEvent(block, replaced, block,
                box, player, true, org.bukkit.inventory.EquipmentSlot.HAND));
        server.getScheduler().performOneTick();
        tracker.sample();
        assertThat(tracker.ping().orElseThrow().position().x()).isEqualTo(42);
        listener.onBreak(new org.bukkit.event.block.BlockBreakEvent(block, player));
        block.setType(Material.AIR);
        var drop = world.dropItem(new Location(world, 43, 70, 4), box);
        listener.onSpawn(new ItemSpawnEvent(drop));
        server.getScheduler().performOneTick();
        assertThat(tracker.isTrackedEntity(drop)).isTrue();
        assertThat(tracker.containsTrackedEgg(drop.getItemStack())).isTrue();
    }
}
