package de.kreiscraft.platform;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.inventory.ItemStack;

/**
 * Version-specific {@link PlayerDataCodec} backed by the NMS NBT and item-conversion surface.
 *
 * <p>Decodes the {@code Inventory} and {@code EnderItems} lists of a player-data document into
 * Bukkit {@link ItemStack}s. Every other root tag is ignored and never rewritten.
 *
 * <p>Not unit-tested (NMS surface); {@link OfflinePlayerDataStore} tests exercise the file protocol
 * with a substitute codec.
 */
public final class NmsPlayerDataCodec implements PlayerDataCodec {

    @Override
    public OfflinePlayerSnapshot decode(byte[] nbtBytes) throws IOException {
        CompoundTag root =
                NbtIo.readCompressed(
                        new ByteArrayInputStream(nbtBytes), NbtAccounter.unlimitedHeap());
        return new OfflinePlayerSnapshot(
                readItems(root.getListOrEmpty("Inventory")),
                readItems(root.getListOrEmpty("EnderItems")));
    }

    private static List<SlottedItem> readItems(ListTag list) {
        List<SlottedItem> out = new ArrayList<>(list.size());
        for (int i = 0; i < list.size(); i++) {
            CompoundTag itemTag = list.getCompoundOrEmpty(i);
            net.minecraft.world.item.ItemStack nmsStack =
                    net.minecraft.world.item.ItemStack.CODEC
                            .parse(NbtOps.INSTANCE, itemTag)
                            .result()
                            .orElse(net.minecraft.world.item.ItemStack.EMPTY);
            ItemStack bukkit = CraftItemStack.asBukkitCopy(nmsStack);
            int slot = itemTag.getByteOr("Slot", (byte) 0);
            out.add(new SlottedItem(slot, bukkit));
        }
        return out;
    }
}
