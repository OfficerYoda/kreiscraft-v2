package de.kreiscraft.modules.dragonegg;

import de.kreiscraft.shared.inventory.ItemContents;
import de.kreiscraft.modules.dragonegg.DragonEggData.Custody;
import java.util.Optional;
import java.util.List;
import java.util.UUID;
import java.util.function.LongSupplier;
import org.bukkit.Location;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Server;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.entity.Entity;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.DoubleChestInventory;
import org.bukkit.persistence.PersistentDataType;
import org.spongepowered.configurate.objectmapping.ConfigSerializable;

/** Single-egg identity and Activity Data; all calls run on the server thread. */
public final class DragonEggTracker {
    public static final NamespacedKey EGG_KEY = new NamespacedKey("kreiscraft", "tracked_dragon_egg");
    private final Server server;
    private final DragonEggData data;
    private final LongSupplier now;

    public DragonEggTracker(Server server, DragonEggData data, LongSupplier now) {
        this.server = server;
        this.data = data;
        this.now = now;
    }

    public boolean isTrackedEgg(ItemStack item) {
        return item != null && item.getType() == Material.DRAGON_EGG
                && matchesIdentity(item.getPersistentDataContainer().get(EGG_KEY, PersistentDataType.STRING));
    }

    public boolean containsTrackedEgg(ItemStack item) {
        return ItemContents.any(item, this::isTrackedEgg);
    }

    public boolean claimContained(ItemStack item) {
        if (data.get().returnJournal != null) {
            return false;
        }
        return containsTrackedEgg(item) || (!claimed() && ItemContents.editFirst(item, this::claim));
    }

    public boolean claim(ItemStack item) {
        if (isTrackedEgg(item)) {
            return data.get().kind != Custody.DESTROYED;
        }
        if (item == null || item.getType() != Material.DRAGON_EGG || item.getAmount() != 1
                || data.get().eggId != null) {
            return false;
        }
        data.update(s -> s.eggId = UUID.randomUUID().toString());
        item.editMeta(
                meta -> meta.getPersistentDataContainer().set(EGG_KEY, PersistentDataType.STRING, data.get().eggId));
        return true;
    }

    public boolean claimed() {
        return data.get().eggId != null;
    }

    public boolean matchesIdentity(String id) {
        return id != null && id.equals(data.get().eggId) && data.get().kind != Custody.DESTROYED;
    }

    public boolean claimBlock(Block block) {
        if (block.getType() != Material.DRAGON_EGG || claimed()) {
            return isTrackedBlock(block);
        }
        data.update(s -> s.eggId = UUID.randomUUID().toString());
        trackBlock(block);
        return true;
    }

    public boolean isTrackedBlock(Block block) {
        return block.getType() == Material.DRAGON_EGG && data.get().kind == Custody.BLOCK
                && Position.of(block.getLocation()).equals(data.get().position);
    }

    public void trackBlock(Block block) {
        if (!claimed() || data.get().kind == Custody.DESTROYED || data.get().returnJournal != null) {
            return;
        }
        data.update(s -> moveTo(s, Custody.BLOCK, null, Position.of(block.getLocation())));
    }

    public void destroy() {
        if (data.get().returnJournal != null) {
            return;
        }
        data.update(s -> {
            moveTo(s, Custody.DESTROYED, null, null);
            s.sampledPosition = null;
        });
    }

    public boolean isTrackedEntity(Entity entity) {
        return data.get().carrier != null && data.get().carrier.equals(entity.getUniqueId().toString())
                && (data.get().kind == Custody.ITEM || data.get().kind == Custody.FALLING
                        || data.get().kind == Custody.INVENTORY_ENTITY);
    }

    public void trackItem(Item item) {
        ItemStack stack = item.getItemStack();
        if (claimContained(stack)) {
            // Bukkit returns a copy on some implementations; persist the marked stack explicitly.
            item.setItemStack(stack);
            trackEntity(item, Custody.ITEM);
        }
    }

    public void trackFalling(FallingBlock falling) {
        falling.getPersistentDataContainer().set(EGG_KEY, PersistentDataType.STRING, data.get().eggId);
        trackEntity(falling, Custody.FALLING);
    }

    public void mark(ItemStack egg) {
        if (egg != null && egg.getType() == Material.DRAGON_EGG && claimed() && data.get().kind != Custody.DESTROYED) {
            egg.editMeta(meta -> meta.getPersistentDataContainer().set(EGG_KEY, PersistentDataType.STRING,
                    data.get().eggId));
        }
    }

    private void trackEntity(Entity entity, Custody kind) {
        if (data.get().returnJournal != null) {
            return;
        }
        data.update(s -> moveTo(s, kind, entity.getUniqueId().toString(), Position.of(entity.getLocation())));
    }

    public void rememberEntity(Entity entity) {
        if (isTrackedEntity(entity)) {
            trackEntity(entity, data.get().kind);
        }
    }

    public void entitiesLoaded(Chunk chunk, List<Entity> entities) {
        var s = data.get();
        if ((s.kind == Custody.ITEM || s.kind == Custody.FALLING || s.kind == Custody.INVENTORY_ENTITY)
                && s.position != null
                && s.position.world().equals(chunk.getWorld().getUID().toString())
                && ((int) Math.floor(s.position.x()) >> 4) == chunk.getX()
                && ((int) Math.floor(s.position.z()) >> 4) == chunk.getZ()
                && entities.stream().noneMatch(this::isTrackedEntity)) {
            unavailable();
        }
    }

    /** Identity remains recoverable, but no location is asserted while custody is unknown. */
    public void unavailable() {
        if (data.get().kind != Custody.DESTROYED && data.get().returnJournal == null) {
            data.update(s -> moveTo(s, Custody.UNKNOWN, null, null));
        }
    }

    public boolean holdsTrackedEgg(Player player) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (containsTrackedEgg(item)) {
                return true;
            }
        }
        return containsTrackedEgg(player.getItemOnCursor());
    }

    public void trackHolder(Player player) {
        if (data.get().eggId != null && data.get().kind != Custody.DESTROYED && data.get().returnJournal == null) {
            data.update(s -> {
                moveTo(s, Custody.PLAYER, player.getUniqueId().toString(), Position.of(player.getLocation()));
                s.clearDeadline();
                s.lastOnlineAt = now.getAsLong();
            });
        }
    }

    /** Observe live inventories, using the actual chest half rather than the double-chest midpoint. */
    public boolean observeInventory(Inventory inventory) {
        if (inventory instanceof DoubleChestInventory chest) {
            return observeInventory(chest.getLeftSide()) || observeInventory(chest.getRightSide());
        }
        Location location = inventoryLocation(inventory);
        if (location == null || inventory.getType() == org.bukkit.event.inventory.InventoryType.ENDER_CHEST) {
            return false;
        }
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (claimContained(stack)) {
                inventory.setItem(slot, stack);
                trackInventory(inventory);
                return true;
            }
        }
        return false;
    }

    /** Accepted pickup events transfer custody before Bukkit removes the item entity. */
    public void trackInventory(Inventory inventory) {
        Location location = inventoryLocation(inventory);
        if (!claimed() || data.get().kind == Custody.DESTROYED || location == null
                || data.get().returnJournal != null) {
            return;
        }
        var holder = inventory.getHolder();
        if (holder instanceof Player player) {
            trackHolder(player);
        } else if (holder instanceof Entity entity) {
            trackEntity(entity, Custody.INVENTORY_ENTITY);
        } else {
            data.update(s -> moveTo(s, Custody.CONTAINER, null, Position.of(location)));
        }
    }

    /** Used inside one Activity Data update, including durable return completion. */
    void moveTo(DragonEggData.State state, Custody custody, String holder, Position location) {
        state.kind = custody;
        state.carrier = holder;
        state.position = location;
        if (custody != Custody.PLAYER || !java.util.Objects.equals(holder, state.offlineCarrier)) {
            state.clearDeadline();
        }
    }

    public void refresh() {
        if (data.get().kind == Custody.DESTROYED || data.get().returnJournal != null) {
            return;
        }
        for (Player player : server.getOnlinePlayers()) {
            if (observePlayer(player)) {
                return;
            }
        }
        if (data.get().kind == Custody.PLAYER) {
            Player player = server.getPlayer(UUID.fromString(data.get().carrier));
            if (player != null) {
                unavailable();
            }
        } else if (data.get().kind == Custody.CONTAINER) {
            Location location = data.get().position.location(server);
            if (location != null
                    && location.getWorld().isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4)) {
                var state = location.getBlock().getState();
                if (!(state instanceof Container container) || !observeInventory(containerInventory(container))) {
                    unavailable();
                }
            }
        } else if (data.get().kind == Custody.INVENTORY_ENTITY) {
            Entity entity = server.getEntity(UUID.fromString(data.get().carrier));
            if (entity instanceof InventoryHolder holder && !observeInventory(holder.getInventory())) {
                unavailable();
            }
        } else if (data.get().kind == Custody.ITEM || data.get().kind == Custody.FALLING) {
            Entity entity = server.getEntity(UUID.fromString(data.get().carrier));
            if (entity != null) {
                trackEntity(entity, data.get().kind);
            }
            // Absence is not destruction: entity chunks may be unloaded or not loaded yet.
        } else if (data.get().kind == Custody.BLOCK) {
            Location location = data.get().position.location(server);
            if (location != null
                    && location.getWorld().isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4)
                    && location.getBlock().getType() != Material.DRAGON_EGG) {
                destroy();
            }
        }
    }

    public static Inventory containerInventory(Container container) {
        return container instanceof org.bukkit.block.Chest chest ? chest.getBlockInventory() : container.getInventory();
    }

    private static Location inventoryLocation(Inventory inventory) {
        var holder = inventory.getHolder();
        Location location = holder instanceof org.bukkit.block.BlockState block
                ? (block.isPlaced() ? block.getLocation() : null)
                : inventory.getLocation();
        if (location != null && !(holder instanceof Entity)
                && (location.getWorld() == null || !(location.getBlock().getState() instanceof Container live)
                        || (!containerInventory(live).equals(inventory) && !live.getInventory().equals(inventory)))) {
            // Paper may clear a broken container's owner while its detached view keeps location/contents.
            return null;
        }
        return location;
    }

    public boolean observePlayer(Player player) {
        if (data.get().returnJournal != null) {
            return false;
        }
        if (!claimed()) {
            for (int slot = 0; slot < player.getInventory().getSize(); slot++) {
                ItemStack item = player.getInventory().getItem(slot);
                if (claimContained(item)) {
                    player.getInventory().setItem(slot, item);
                    break;
                }
            }
            ItemStack cursor = player.getItemOnCursor();
            if (claimContained(cursor)) {
                player.setItemOnCursor(cursor);
            }
        }
        if (holdsTrackedEgg(player)) {
            trackHolder(player);
            return true;
        }
        return false;
    }

    public void sample() {
        refresh();
        if (data.get().position != null
                && (data.get().sampledPosition == null || now.getAsLong() - data.get().sampledAt >= 120_000)) {
            data.update(s -> {
                s.sampledPosition = s.position;
                s.sampledAt = now.getAsLong();
            });
        }
    }

    public Optional<Ping> ping() {
        var state = data.get();
        return state.kind == Custody.DESTROYED || state.position == null || state.sampledPosition == null
                ? Optional.empty()
                : Optional.of(new Ping(state.sampledPosition, state.sampledAt));
    }

    @ConfigSerializable
    public record Position(String world, String dimension, double x, double y, double z) {
        public static Position of(Location location) {
            return new Position(location.getWorld().getUID().toString(), location.getWorld().getKey().toString(),
                    location.getX(), location.getY(), location.getZ());
        }

        public Location location(Server server) {
            var loadedWorld = server.getWorld(UUID.fromString(world));
            return loadedWorld == null ? null : new Location(loadedWorld, x, y, z);
        }
    }

    public record Ping(Position position, long sampledAt) {
    }
}
