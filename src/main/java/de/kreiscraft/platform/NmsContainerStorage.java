package de.kreiscraft.platform;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.List;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.storage.RegionFileStorage;
import net.minecraft.world.level.chunk.storage.RegionStorageInfo;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.inventory.CraftItemStack;

/** Paper flush plus disk read-back: world save itself can log and swallow a failed chunk write. */
public final class NmsContainerStorage {
    private NmsContainerStorage() {}

    public static void saveAndVerify(Location location, NamespacedKey blockEntityType, List<SlottedItem> expectedItems,
            NamespacedKey markerKey, String markerValue) throws IOException {
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
                    var container = tag.asCompound().orElseThrow(() -> new IOException("invalid saved block entity"));
                    if (container.getIntOr("x", Integer.MIN_VALUE) != location.getBlockX()
                            || container.getIntOr("y", Integer.MIN_VALUE) != location.getBlockY()
                            || container.getIntOr("z", Integer.MIN_VALUE) != location.getBlockZ()) {
                        continue;
                    }
                    var items = container.getListOrEmpty("Items");
                    if (!container.getStringOr("id", "").equals(blockEntityType.toString())
                            || items.size() != expectedItems.size()
                            || !container.getCompoundOrEmpty("PublicBukkitValues")
                                    .getStringOr(markerKey.toString(), "").equals(markerValue)) {
                        throw new IOException("container was not persisted intact");
                    }
                    var ops = RegistryOps.create(NbtOps.INSTANCE, world.getHandle().registryAccess());
                    for (var expected : expectedItems) {
                        var matches = items.stream().filter(item -> item.asCompound().orElseThrow()
                                .getByteOr("Slot", (byte) -1) == expected.slot()).toList();
                        if (matches.size() != 1 || !CraftItemStack.asBukkitMirror(
                                net.minecraft.world.item.ItemStack.CODEC.parse(ops, matches.getFirst()).getOrThrow())
                                .equals(expected.stack())) {
                            throw new IOException("container item failed disk read-back");
                        }
                    }
                    return;
                }
            }
            throw new IOException("container missing from saved chunk");
        } catch (RuntimeException e) {
            throw new IOException("failed to verify saved container", e);
        }
    }
}
