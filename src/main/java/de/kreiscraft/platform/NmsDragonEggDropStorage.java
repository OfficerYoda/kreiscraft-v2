package de.kreiscraft.platform;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.storage.RegionFileStorage;
import net.minecraft.world.level.chunk.storage.RegionStorageInfo;
import org.bukkit.Location;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.inventory.ItemStack;

/** Paper flush plus disk read-back: world save itself can log and swallow a failed chunk write. */
public final class NmsDragonEggDropStorage {
    private NmsDragonEggDropStorage() {}

    public static void saveAndVerify(Location location, String token, ItemStack egg) throws IOException {
        var world = (CraftWorld) location.getWorld();
        world.save(true);
        var folder = world.getWorldFolder().toPath().resolve("region");
        int x = location.getBlockX() >> 4;
        int z = location.getBlockZ() >> 4;
        try (var file = FileChannel.open(folder.resolve("r." + (x >> 5) + "." + (z >> 5) + ".mca"),
                StandardOpenOption.WRITE)) {
            file.force(true);
        }
        var external = folder.resolve("c." + x + "." + z + ".mcc");
        if (Files.exists(external)) {
            try (var file = FileChannel.open(external, StandardOpenOption.WRITE)) {
                file.force(true);
            }
        }
        try (var directory = FileChannel.open(folder, StandardOpenOption.READ)) {
            directory.force(true);
        }
        var info = new RegionStorageInfo(world.getName(), world.getHandle().dimension(), "chunk");
        try (var storage = new RegionFileStorage(info, folder, false)) {
            var chunk = storage.read(new ChunkPos(x, z));
            if (chunk != null) {
                for (var tag : chunk.getListOrEmpty("block_entities")) {
                    var barrel = tag.asCompound().orElseThrow(() -> new IOException("invalid saved block entity"));
                    if (barrel.getIntOr("x", Integer.MIN_VALUE) != location.getBlockX()
                            || barrel.getIntOr("y", Integer.MIN_VALUE) != location.getBlockY()
                            || barrel.getIntOr("z", Integer.MIN_VALUE) != location.getBlockZ()) {
                        continue;
                    }
                    var items = barrel.getListOrEmpty("Items");
                    if (!barrel.getStringOr("id", "").equals("minecraft:barrel") || items.size() != 1
                            || items.getCompoundOrEmpty(0).getByteOr("Slot", (byte) -1) != 13
                            || !barrel.getCompoundOrEmpty("PublicBukkitValues")
                                    .getStringOr("kreiscraft:egg_return", "").equals(token)) {
                        throw new IOException("return barrel was not persisted intact");
                    }
                    var ops = RegistryOps.create(NbtOps.INSTANCE, world.getHandle().registryAccess());
                    var saved = net.minecraft.world.item.ItemStack.CODEC.parse(ops, items.get(0)).getOrThrow();
                    if (CraftItemStack.asBukkitMirror(saved).equals(egg)) {
                        return;
                    }
                    throw new IOException("return egg failed disk read-back");
                }
            }
            throw new IOException("return barrel missing from saved chunk");
        } catch (RuntimeException e) {
            throw new IOException("failed to verify saved dragon egg barrel", e);
        }
    }
}
