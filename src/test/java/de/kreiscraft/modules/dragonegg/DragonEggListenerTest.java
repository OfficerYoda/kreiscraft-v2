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
}
