package de.kreiscraft.platform;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.RegistryOps;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.inventory.ItemStack;

/**
 * Version-specific {@link PlayerDataCodec} backed by the NMS NBT and item-conversion surface.
 *
 * <p>Decodes the {@code Inventory}, {@code equipment}, and {@code EnderItems} of a player-data document into
 * Bukkit {@link ItemStack}s, and encodes edits back into a copy of the complete source document so
 * that every unrelated root tag and item component is preserved. This is the only class
 * that touches NMS for offline player data.
 *
 * <p>The mapped-server tests exercise the actual NMS format and component codecs; {@link
 * OfflinePlayerDataStore} and {@link OfflinePlayerEditSession} tests exercise the file protocol.
 */
public final class NmsPlayerDataCodec implements PlayerDataCodec {

    private static final Map<Integer, String> EQUIPMENT_SLOTS =
            Map.of(36, "feet", 37, "legs", 38, "chest", 39, "head", 40, "offhand");

    private final Supplier<? extends HolderLookup.Provider> registries;

    /** Uses the server's current registries, including datapack-backed item components. */
    public NmsPlayerDataCodec(PaperPlatform platform) {
        this(() -> ((CraftServer) platform.server()).getServer().registryAccess());
    }

    NmsPlayerDataCodec(Supplier<? extends HolderLookup.Provider> registries) {
        this.registries = registries;
    }

    @Override
    public OfflinePlayerSnapshot decode(byte[] nbtBytes) throws IOException {
        CompoundTag root =
                NbtIo.readCompressed(
                        new ByteArrayInputStream(nbtBytes), NbtAccounter.unlimitedHeap());
        RegistryOps<Tag> ops = RegistryOps.create(NbtOps.INSTANCE, registries.get());
        List<SlottedItem> inventory = readItems(root.getListOrEmpty("Inventory"), ops);
        CompoundTag equipment = root.getCompoundOrEmpty("equipment");
        for (var entry : EQUIPMENT_SLOTS.entrySet()) {
            if (equipment.contains(entry.getValue())) {
                inventory.add(new SlottedItem(entry.getKey(),
                        readItem(equipment.get(entry.getValue()), ops, entry.getValue())));
            }
        }
        return new OfflinePlayerSnapshot(inventory, readItems(root.getListOrEmpty("EnderItems"), ops));
    }

    @Override
    public byte[] encode(
            byte[] sourceNbtBytes, List<SlottedItem> inventory, List<SlottedItem> enderItems)
            throws IOException {
        CompoundTag root =
                NbtIo.readCompressed(
                        new ByteArrayInputStream(sourceNbtBytes), NbtAccounter.unlimitedHeap());
        RegistryOps<Tag> ops = RegistryOps.create(NbtOps.INSTANCE, registries.get());
        // Armor and offhand live outside Inventory in current player data. Preserve equipment
        // not represented in the GUI (e.g. body/saddle) and every unrelated root tag.
        CompoundTag equipment = root.getCompoundOrEmpty("equipment").copy();
        EQUIPMENT_SLOTS.values().forEach(equipment::remove);
        List<SlottedItem> ordinary = new ArrayList<>();
        for (SlottedItem item : inventory) {
            String key = EQUIPMENT_SLOTS.get(item.slot());
            if (key == null) {
                ordinary.add(item);
            } else if (!item.stack().getType().isAir()) {
                equipment.put(key, writeItem(item, ops));
            }
        }
        root.put("equipment", equipment);
        root.put("Inventory", writeItems(ordinary, ops));
        root.put("EnderItems", writeItems(enderItems, ops));
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        NbtIo.writeCompressed(root, out);
        return out.toByteArray();
    }

    private static List<SlottedItem> readItems(ListTag list, RegistryOps<Tag> ops) throws IOException {
        List<SlottedItem> out = new ArrayList<>(list.size());
        for (int i = 0; i < list.size(); i++) {
            CompoundTag itemTag = list.getCompoundOrEmpty(i);
            int slot = itemTag.getByteOr("Slot", (byte) 0);
            out.add(new SlottedItem(slot, readItem(itemTag, ops, "slot " + slot)));
        }
        return out;
    }

    private static ItemStack readItem(Tag tag, RegistryOps<Tag> ops, String position) throws IOException {
        try {
            net.minecraft.world.item.ItemStack stack =
                    net.minecraft.world.item.ItemStack.CODEC.parse(ops, tag).getOrThrow();
            // Keep the complete component patch, without a lossy ItemMeta conversion.
            return CraftItemStack.asBukkitMirror(stack.copy());
        } catch (RuntimeException e) {
            throw new IOException("failed to decode item in " + position, e);
        }
    }

    private static ListTag writeItems(List<SlottedItem> items, RegistryOps<Tag> ops) throws IOException {
        ListTag list = new ListTag();
        for (SlottedItem item : items) {
            net.minecraft.world.item.ItemStack nmsStack =
                    CraftItemStack.asNMSCopy(item.stack());
            if (nmsStack.isEmpty()) {
                // Empty slots are represented by absence, matching vanilla player data.
                continue;
            }
            CompoundTag itemTag = writeItem(item, ops);
            itemTag.putByte("Slot", (byte) item.slot());
            list.add(itemTag);
        }
        return list;
    }

    private static CompoundTag writeItem(SlottedItem item, RegistryOps<Tag> ops) throws IOException {
        try {
            Tag encoded = net.minecraft.world.item.ItemStack.CODEC
                    .encodeStart(ops, CraftItemStack.asNMSCopy(item.stack())).getOrThrow();
            if (encoded instanceof CompoundTag itemTag) {
                return itemTag;
            }
            throw new IOException("item in slot " + item.slot() + " did not encode to a tag");
        } catch (RuntimeException e) {
            throw new IOException("failed to encode item in slot " + item.slot(), e);
        }
    }
}
