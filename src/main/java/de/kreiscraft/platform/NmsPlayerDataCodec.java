package de.kreiscraft.platform;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.inventory.ItemStack;

/**
 * Version-specific {@link PlayerDataCodec} backed by the NMS NBT and item-conversion surface.
 *
 * <p>Decodes the {@code Inventory} and {@code EnderItems} lists of a player-data document into
 * Bukkit {@link ItemStack}s, and encodes edits back into a copy of the complete source document so
 * that every other root tag and item component is preserved byte-for-byte. This is the only class
 * that touches NMS for offline player data.
 *
 * <p>Not unit-tested (NMS surface); {@link OfflinePlayerDataStore} and {@link
 * OfflinePlayerEditSession} tests exercise the file protocol with a substitute codec.
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

    @Override
    public byte[] encode(
            byte[] sourceNbtBytes, List<SlottedItem> inventory, List<SlottedItem> enderItems)
            throws IOException {
        CompoundTag root =
                NbtIo.readCompressed(
                        new ByteArrayInputStream(sourceNbtBytes), NbtAccounter.unlimitedHeap());
        // Replace only the two editable lists; every other root tag survives untouched.
        root.put("Inventory", writeItems(inventory));
        root.put("EnderItems", writeItems(enderItems));
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        NbtIo.writeCompressed(root, out);
        return out.toByteArray();
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

    private static ListTag writeItems(List<SlottedItem> items) throws IOException {
        ListTag list = new ListTag();
        for (SlottedItem item : items) {
            net.minecraft.world.item.ItemStack nmsStack =
                    CraftItemStack.asNMSCopy(item.stack());
            if (nmsStack.isEmpty()) {
                // Empty slots are represented by absence, matching vanilla player data.
                continue;
            }
            Tag encoded;
            try {
                encoded =
                        net.minecraft.world.item.ItemStack.CODEC
                                .encodeStart(NbtOps.INSTANCE, nmsStack)
                                .getOrThrow();
            } catch (RuntimeException e) {
                throw new IOException("failed to encode item in slot " + item.slot(), e);
            }
            if (!(encoded instanceof CompoundTag itemTag)) {
                throw new IOException("item in slot " + item.slot() + " did not encode to a tag");
            }
            itemTag.putByte("Slot", (byte) item.slot());
            list.add(itemTag);
        }
        return list;
    }
}
