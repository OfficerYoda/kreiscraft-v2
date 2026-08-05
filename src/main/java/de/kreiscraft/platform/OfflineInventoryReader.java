package de.kreiscraft.platform;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.dedicated.DedicatedServer;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.inventory.ItemStack;

/**
 * Reads an offline player's inventory from their {@code <world>/playerdata/<uuid>.dat}
 * NBT file. Used by the future {@code invsee} module to show inventories of
 * players who are not currently online.
 *
 * <p>Not unit-tested (NMS surface).
 */
public final class OfflineInventoryReader {

    private final PaperPlatform platform;

    public OfflineInventoryReader(PaperPlatform platform) {
        this.platform = platform;
    }

    /**
     * @param playerId offline player UUID
     * @return inventory slots that had items; empty list if the .dat file does not exist
     * @throws IOException if the .dat file exists but cannot be read
     */
    public List<SlottedItem> readInventory(UUID playerId) throws IOException {
        Path file = playerDataFile(playerId);
        if (!Files.exists(file)) {
            return List.of();
        }
        CompoundTag root = NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap());
        ListTag inventory = root.getListOrEmpty("Inventory");
        List<SlottedItem> out = new ArrayList<>(inventory.size());
        for (int i = 0; i < inventory.size(); i++) {
            CompoundTag itemTag = inventory.getCompoundOrEmpty(i);
            net.minecraft.world.item.ItemStack nmsStack = net.minecraft.world.item.ItemStack.CODEC
                    .parse(NbtOps.INSTANCE, itemTag)
                    .result()
                    .orElse(net.minecraft.world.item.ItemStack.EMPTY);
            ItemStack bukkit = CraftItemStack.asBukkitCopy(nmsStack);
            int slot = itemTag.getByteOr("Slot", (byte) 0);
            out.add(new SlottedItem(slot, bukkit));
        }
        return out;
    }

    private Path playerDataFile(UUID playerId) {
        DedicatedServer server = ((CraftServer) platform.server()).getServer();
        Path worldDir = server.getServerDirectory().resolve(server.getLevelIdName());
        return worldDir.resolve("playerdata").resolve(playerId + ".dat");
    }
}
