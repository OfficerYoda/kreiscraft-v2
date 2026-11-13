package de.kreiscraft.modules.dragonegg;

import de.kreiscraft.core.scheduler.Scheduler;
import java.time.Duration;
import java.util.Objects;
import java.util.List;
import org.bukkit.Chunk;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Entity;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityDropItemEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityRemoveEvent;
import org.bukkit.event.entity.ItemMergeEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

public final class DragonEggListener implements Listener {
    private final Server server;
    private final DragonEggTracker tracker;
    private final DragonEggCompass compass;
    private final Scheduler scheduler;
    private Location pendingDrop;
    private boolean active = true;

    public DragonEggListener(Server server, DragonEggTracker tracker, DragonEggCompass compass, Scheduler scheduler) {
        this.server = server;
        this.tracker = tracker;
        this.compass = compass;
        this.scheduler = scheduler;
    }

    public void start() {
        server.getOnlinePlayers().forEach(tracker::observePlayer);
        server.getWorlds().forEach(world -> {
            world.getEntities().forEach(this::discoverEntity);
            for (Chunk chunk : world.getLoadedChunks()) {
                discoverChunk(chunk);
                if (chunk.isEntitiesLoaded()) {
                    tracker.entitiesLoaded(chunk, List.of(chunk.getEntities()));
                }
            }
        });
    }

    public void stop() {
        active = false;
    }

    private void discoverEntity(Entity entity) {
        if (entity instanceof Item item) {
            tracker.trackItem(item);
            ItemStack stack = item.getItemStack();
            if (compass.isTrackingCompass(stack)) {
                compass.update(stack);
                item.setItemStack(stack);
            }
        } else if (entity instanceof FallingBlock falling) {
            String id = falling.getPersistentDataContainer().get(DragonEggTracker.EGG_KEY, PersistentDataType.STRING);
            if (tracker.matchesIdentity(id)) {
                tracker.trackFalling(falling);
            }
        }
    }

    private void discoverChunk(Chunk chunk) {
        if (tracker.claimed() || !chunk.contains(Material.DRAGON_EGG.createBlockData())) {
            return;
        }
        // ponytail: only the first egg-containing loaded chunk is scanned, not the whole world.
        for (int y = chunk.getWorld().getMinHeight(); y < chunk.getWorld().getMaxHeight(); y++) {
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    if (tracker.claimBlock(chunk.getBlock(x, y, z))) {
                        return;
                    }
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (tracker.claim(event.getItemInHand())) {
            tracker.trackBlock(event.getBlockPlaced());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(BlockFromToEvent event) {
        if (tracker.isTrackedBlock(event.getBlock()) || tracker.claimBlock(event.getBlock())) {
            tracker.trackBlock(event.getToBlock());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFalling(EntityChangeBlockEvent event) {
        if (!(event.getEntity() instanceof FallingBlock falling)) {
            return;
        }
        if (event.getTo() == Material.AIR
                && (tracker.isTrackedBlock(event.getBlock()) || tracker.claimBlock(event.getBlock()))) {
            tracker.trackFalling(falling);
        } else if (event.getTo() == Material.DRAGON_EGG && tracker.isTrackedEntity(falling)) {
            tracker.trackBlock(event.getBlock());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDrop(EntityDropItemEvent event) {
        if (tracker.isTrackedEntity(event.getEntity())) {
            ItemStack stack = event.getItemDrop().getItemStack();
            if (stack.getType() == Material.DRAGON_EGG) {
                tracker.mark(stack);
                event.getItemDrop().setItemStack(stack);
                tracker.trackItem(event.getItemDrop());
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (tracker.isTrackedBlock(event.getBlock()) || tracker.claimBlock(event.getBlock())) {
            if (!event.isDropItems() || event.getPlayer().getGameMode() == GameMode.CREATIVE) {
                tracker.destroy();
            } else {
                awaitDrop(event.getBlock());
            }
        }
    }

    private void awaitDrop(Block block) {
        Location origin = block.getLocation();
        pendingDrop = origin;
        tracker.unavailable();
        scheduler.runLater(Duration.ofMillis(50), () -> {
            if (active && Objects.equals(pendingDrop, origin)) {
                pendingDrop = null;
                tracker.destroy();
            }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSpawn(ItemSpawnEvent event) {
        Item item = event.getEntity();
        Location spawnedAt = item.getLocation();
        // ponytail: correlate vanilla block drops only within their source block and tick;
        // use a source-aware drop event if Paper adds one for pistons/explosions.
        if (item.getItemStack().getType() == Material.DRAGON_EGG && pendingDrop != null
                && pendingDrop.getWorld().equals(item.getWorld())
                && pendingDrop.getBlockX() == spawnedAt.getBlockX()
                && pendingDrop.getBlockY() == spawnedAt.getBlockY()
                && pendingDrop.getBlockZ() == spawnedAt.getBlockZ()) {
            ItemStack stack = item.getItemStack();
            tracker.mark(stack);
            item.setItemStack(stack);
            pendingDrop = null;
        }
        discoverEntity(item);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (tracker.isTrackedEgg(event.getItem().getItemStack())) {
            if (event.getEntity() instanceof Player player && event.getRemaining() == 0) {
                tracker.trackHolder(player);
            } else {
                tracker.unavailable();
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMerge(ItemMergeEvent event) {
        if (tracker.isTrackedEntity(event.getEntity())) {
            tracker.trackItem(event.getTarget());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRemove(EntityRemoveEvent event) {
        if (!tracker.isTrackedEntity(event.getEntity())) {
            return;
        }
        if (event.getCause() == EntityRemoveEvent.Cause.UNLOAD
                || event.getCause() == EntityRemoveEvent.Cause.PLAYER_QUIT) {
            tracker.rememberEntity(event.getEntity());
        } else {
            tracker.destroy();
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityExplosion(EntityExplodeEvent event) {
        event.blockList().stream().filter(tracker::isTrackedBlock).findFirst().ifPresent(this::awaitDrop);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockExplosion(BlockExplodeEvent event) {
        event.blockList().stream().filter(tracker::isTrackedBlock).findFirst().ifPresent(this::awaitDrop);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        pistonDrop(event.getBlock(), event.getDirection(), event.getBlocks());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        pistonDrop(event.getBlock(), event.getDirection(), event.getBlocks());
    }

    private void pistonDrop(Block piston, BlockFace direction, List<Block> moved) {
        // Destroyed blocks are not necessarily in Bukkit's moved-block list.
        Block front = piston.getRelative(direction);
        if (tracker.isTrackedBlock(front)) {
            awaitDrop(front);
            return;
        }
        for (Block block : moved) {
            if (tracker.isTrackedBlock(block)) {
                awaitDrop(block);
                return;
            }
            Block destination = block.getRelative(direction);
            if (tracker.isTrackedBlock(destination)) {
                awaitDrop(destination);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(EntityDeathEvent event) {
        if (event.getEntity() instanceof Player player && tracker.holdsTrackedEgg(player)
                && !event.getDrops().stream().anyMatch(tracker::isTrackedEgg)) {
            // Keep-inventory deaths retain the egg; otherwise no drop means it was destroyed.
            scheduler.runLater(Duration.ofMillis(50), () -> {
                if (active && !tracker.holdsTrackedEgg(player)) {
                    tracker.destroy();
                }
            });
        } else if (event.getEntity() instanceof EnderDragon && !tracker.claimed()) {
            scheduler.runLater(Duration.ofMillis(50), () -> {
                if (active) {
                    start();
                }
            });
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        tracker.observePlayer(event.getPlayer());
        compass.updateInventory(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        if (tracker.holdsTrackedEgg(event.getPlayer())) {
            tracker.trackHolder(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkLoad(ChunkLoadEvent event) {
        discoverChunk(event.getChunk());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntitiesLoad(EntitiesLoadEvent event) {
        event.getEntities().forEach(this::discoverEntity);
        tracker.entitiesLoaded(event.getChunk(), event.getEntities());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldLoad(WorldLoadEvent event) {
        for (Chunk chunk : event.getWorld().getLoadedChunks()) {
            discoverChunk(chunk);
        }
    }

    @EventHandler
    public void onCraft(PrepareItemCraftEvent event) {
        ItemStack result = event.getInventory().getResult();
        if (compass.isTrackingCompass(result)) {
            compass.update(result);
            event.getInventory().setResult(result);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction().isRightClick() && compass.isTrackingCompass(event.getItem())) {
            compass.report(event.getPlayer(), event.getItem());
        }
    }
}
