package de.kreiscraft.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.papermc.paper.plugin.lifecycle.event.registrar.ReloadableRegistrarEvent;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import net.minecraft.SharedConstants;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.RegistryDataLoader;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.ServerPacksSource;
import net.minecraft.server.packs.resources.MultiPackResourceManager;
import net.minecraft.tags.TagLoader;
import net.minecraft.world.ItemStackWithSlot;
import net.minecraft.world.entity.EntityEquipment;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Items;
import org.bukkit.Material;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class NmsPlayerDataCodecTest {

    private static HolderLookup.Provider registries;

    @TempDir
    Path playerData;

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        var builtIn = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        try (var resources = new MultiPackResourceManager(PackType.SERVER_DATA,
                List.of(ServerPacksSource.createVanillaPackSource()))) {
            TagLoader.loadTagsForExistingRegistries(resources, builtIn, ReloadableRegistrarEvent.Cause.INITIAL)
                    .forEach(tags -> tags.apply());
            var loaded = RegistryDataLoader.load(resources, builtIn.listRegistries().toList(),
                    RegistryDataLoader.WORLDGEN_REGISTRIES, Runnable::run).join();
            registries = HolderLookup.Provider.create(Stream.concat(builtIn.listRegistries(), loaded.listRegistries()));
        }
        BuiltInRegistries.DATA_COMPONENT_INITIALIZERS.build(registries)
                .forEach(pending -> pending.apply());
    }

    @Test
    void decode_offlinePlayerInventory_showsStoredItems() throws Exception {
        CompoundTag item = new CompoundTag();
        item.putString("id", "minecraft:diamond");
        item.putInt("count", 3);
        item.putByte("Slot", (byte) 9);
        ListTag inventory = new ListTag();
        inventory.add(item);
        CompoundTag root = new CompoundTag();
        root.put("Inventory", inventory);
        OfflinePlayerSnapshot snapshot = codec().decode(compress(root));

        assertThat(snapshot.inventory()).hasSize(1);
        assertThat(snapshot.inventory().getFirst().slot()).isEqualTo(9);
        assertThat(snapshot.inventory().getFirst().stack().getType()).isEqualTo(Material.DIAMOND);
        assertThat(snapshot.inventory().getFirst().stack().getAmount()).isEqualTo(3);
    }

    @Test
    void decode_registryBackedComponents_doesNotReplaceEnchantedItemWithAir() throws Exception {
        CompoundTag item = new CompoundTag();
        item.putString("id", "minecraft:diamond_sword");
        item.putInt("count", 1);
        item.putByte("Slot", (byte) 0);
        CompoundTag enchantments = new CompoundTag();
        enchantments.putInt("minecraft:sharpness", 5);
        CompoundTag components = new CompoundTag();
        components.put("minecraft:enchantments", enchantments);
        item.put("components", components);
        ListTag inventory = new ListTag();
        inventory.add(item);
        CompoundTag root = new CompoundTag();
        root.put("Inventory", inventory);

        OfflinePlayerSnapshot snapshot = codec().decode(compress(root));

        assertThat(snapshot.inventory().getFirst().stack().getType()).isEqualTo(Material.DIAMOND_SWORD);
        var stack = CraftItemStack.asNMSCopy(snapshot.inventory().getFirst().stack());
        assertThat(stack.getEnchantments().entrySet()).hasSize(1);
        assertThat(stack.getEnchantments().entrySet().iterator().next().getIntValue()).isEqualTo(5);
        // A read/write round trip must retain the complete component patch, not just the item ID.
        OfflinePlayerSnapshot reloaded =
                codec().decode(codec().encode(compress(root), snapshot.inventory(), List.of()));
        assertThat(net.minecraft.world.item.ItemStack.isSameItemSameComponents(
                stack, CraftItemStack.asNMSCopy(reloaded.inventory().getFirst().stack()))).isTrue();
    }

    @Test
    void decode_equipment_showsArmorAndOffhandInBukkitSlots() throws Exception {
        CompoundTag helmet = new CompoundTag();
        helmet.putString("id", "minecraft:diamond_helmet");
        helmet.putInt("count", 1);
        CompoundTag shield = new CompoundTag();
        shield.putString("id", "minecraft:shield");
        shield.putInt("count", 1);
        CompoundTag equipment = new CompoundTag();
        equipment.put("head", helmet);
        equipment.put("offhand", shield);
        CompoundTag root = new CompoundTag();
        root.put("equipment", equipment);

        OfflinePlayerSnapshot snapshot = codec().decode(compress(root));

        assertThat(snapshot.inventory()).extracting(SlottedItem::slot).containsExactlyInAnyOrder(39, 40);
    }

    @Test
    void encode_equipment_usesVanillaEquipmentInsteadOfInventorySlots() throws Exception {
        CompoundTag root = new CompoundTag();
        CompoundTag equipment = new CompoundTag();
        equipment.put("body", itemTag("minecraft:leather_horse_armor", 1));
        equipment.put("saddle", itemTag("minecraft:saddle", 1));
        root.put("equipment", equipment);
        root.putString("unrelated", "preserve me");
        List<SlottedItem> inventory = List.of(
                new SlottedItem(0, stack(Items.DIAMOND, 3)),
                new SlottedItem(36, stack(Items.DIAMOND_BOOTS, 1)),
                new SlottedItem(37, stack(Items.DIAMOND_LEGGINGS, 1)),
                new SlottedItem(38, stack(Items.DIAMOND_CHESTPLATE, 1)),
                new SlottedItem(39, stack(Items.DIAMOND_HELMET, 1)),
                new SlottedItem(40, stack(Items.SHIELD, 1)));

        CompoundTag edited = decompress(codec().encode(compress(root), inventory, List.of()));

        var vanillaInventory = ItemStackWithSlot.CODEC.listOf().parse(ops(), edited.get("Inventory")).getOrThrow();
        assertThat(vanillaInventory).extracting(ItemStackWithSlot::slot).containsExactly(0);
        EntityEquipment vanillaEquipment = EntityEquipment.CODEC.parse(ops(), edited.get("equipment")).getOrThrow();
        assertThat(vanillaEquipment.get(EquipmentSlot.FEET).getItem()).isEqualTo(Items.DIAMOND_BOOTS);
        assertThat(vanillaEquipment.get(EquipmentSlot.LEGS).getItem()).isEqualTo(Items.DIAMOND_LEGGINGS);
        assertThat(vanillaEquipment.get(EquipmentSlot.CHEST).getItem()).isEqualTo(Items.DIAMOND_CHESTPLATE);
        assertThat(vanillaEquipment.get(EquipmentSlot.HEAD).getItem()).isEqualTo(Items.DIAMOND_HELMET);
        assertThat(vanillaEquipment.get(EquipmentSlot.OFFHAND).getItem()).isEqualTo(Items.SHIELD);
        assertThat(edited.getCompoundOrEmpty("equipment").get("body")).isEqualTo(equipment.get("body"));
        assertThat(edited.getCompoundOrEmpty("equipment").get("saddle")).isEqualTo(equipment.get("saddle"));
        assertThat(edited.get("unrelated")).isEqualTo(root.get("unrelated"));
        assertThat(codec().decode(compress(edited)).inventory()).extracting(SlottedItem::slot)
                .containsExactlyInAnyOrder(0, 36, 37, 38, 39, 40);
    }

    @Test
    void encode_removedEquipment_doesNotLeaveOldArmorOrOffhand() throws Exception {
        CompoundTag root = new CompoundTag();
        CompoundTag equipment = new CompoundTag();
        equipment.put("head", itemTag("minecraft:diamond_helmet", 1));
        equipment.put("offhand", itemTag("minecraft:shield", 1));
        root.put("equipment", equipment);

        CompoundTag edited = decompress(codec().encode(compress(root), List.of(), List.of()));

        assertThat(edited.getCompoundOrEmpty("equipment").contains("head")).isFalse();
        assertThat(edited.getCompoundOrEmpty("equipment").contains("offhand")).isFalse();
        assertThat(codec().decode(compress(edited)).inventory()).isEmpty();
    }

    @Test
    void offlineSession_inventoryEdit_commitsWithBackupAndPreservesEnderChestAndRootTags() throws Exception {
        CompoundTag root = new CompoundTag();
        root.putInt("DataVersion", SharedConstants.getCurrentVersion().dataVersion().version());
        root.putFloat("Health", 17.0f);
        CompoundTag custom = new CompoundTag();
        custom.putString("custom", "unchanged");
        root.put("BukkitValues", custom);
        root.put("Inventory", ItemStackWithSlot.CODEC.listOf().encodeStart(ops(),
                List.of(new ItemStackWithSlot(9, new net.minecraft.world.item.ItemStack(Items.DIAMOND, 3))))
                .getOrThrow());
        root.put("EnderItems", ItemStackWithSlot.CODEC.listOf().encodeStart(ops(),
                List.of(new ItemStackWithSlot(26, new net.minecraft.world.item.ItemStack(Items.EMERALD, 8))))
                .getOrThrow());
        UUID playerId = UUID.randomUUID();
        byte[] sourceBytes = compress(root);
        Path source = playerData.resolve(playerId + ".dat");
        Files.write(source, sourceBytes);
        OfflinePlayerEditSession session =
                new OfflinePlayerDataStore(playerData, codec()).openForEdit(playerId).orElseThrow();

        StagedOfflineWrite write =
                session.stage(List.of(new SlottedItem(35, stack(Items.DIRT, 7))), session.snapshot().enderItems());
        assertThat(Files.readAllBytes(source)).isEqualTo(sourceBytes);
        assertThat(session.commit(write, () -> true)).isEqualTo(OfflineCommitResult.COMMITTED);

        CompoundTag edited = decompress(Files.readAllBytes(source));
        assertThat(edited.get("EnderItems")).isEqualTo(root.get("EnderItems"));
        assertThat(edited.get("DataVersion")).isEqualTo(root.get("DataVersion"));
        assertThat(edited.get("Health")).isEqualTo(root.get("Health"));
        assertThat(edited.get("BukkitValues")).isEqualTo(root.get("BukkitValues"));
        var vanillaInventory = ItemStackWithSlot.CODEC.listOf().parse(ops(), edited.get("Inventory")).getOrThrow();
        assertThat(vanillaInventory).hasSize(1);
        assertThat(vanillaInventory.getFirst().slot()).isEqualTo(35);
        assertThat(vanillaInventory.getFirst().stack().getItem()).isEqualTo(Items.DIRT);
        assertThat(vanillaInventory.getFirst().stack().getCount()).isEqualTo(7);
        try (var files = Files.list(playerData)) {
            Path backup = files.filter(path -> path.toString().endsWith(".backup")).findFirst().orElseThrow();
            assertThat(Files.readAllBytes(backup)).isEqualTo(sourceBytes);
        }
    }

    @Test
    void encode_enderChestEdit_preservesInventoryAndEquipment() throws Exception {
        CompoundTag root = new CompoundTag();
        root.put("Inventory", ItemStackWithSlot.CODEC.listOf().encodeStart(ops(),
                List.of(new ItemStackWithSlot(8, new net.minecraft.world.item.ItemStack(Items.DIAMOND, 3))))
                .getOrThrow());
        CompoundTag equipment = new CompoundTag();
        equipment.put("head", itemTag("minecraft:diamond_helmet", 1));
        root.put("equipment", equipment);
        byte[] source = compress(root);

        CompoundTag edited = decompress(codec().encode(source, codec().decode(source).inventory(),
                List.of(new SlottedItem(26, stack(Items.EMERALD, 8)))));

        assertThat(edited.get("Inventory")).isEqualTo(root.get("Inventory"));
        assertThat(edited.get("equipment")).isEqualTo(root.get("equipment"));
        var enderItems = ItemStackWithSlot.CODEC.listOf().parse(ops(), edited.get("EnderItems")).getOrThrow();
        assertThat(enderItems).hasSize(1);
        assertThat(enderItems.getFirst().slot()).isEqualTo(26);
        assertThat(enderItems.getFirst().stack().getItem()).isEqualTo(Items.EMERALD);
        assertThat(enderItems.getFirst().stack().getCount()).isEqualTo(8);
    }

    @Test
    void decode_invalidItem_failsInsteadOfSilentlyDroppingIt() throws Exception {
        CompoundTag root = new CompoundTag();
        CompoundTag invalid = itemTag("minecraft:not_a_real_item", 1);
        invalid.putByte("Slot", (byte) 9);
        ListTag inventory = new ListTag();
        inventory.add(invalid);
        root.put("Inventory", inventory);
        byte[] source = compress(root);

        assertThatThrownBy(() -> codec().decode(source)).isInstanceOf(IOException.class)
                .hasMessageContaining("slot 9");
    }

    @Test
    void decode_emptyInventory_returnsEmptySnapshot() throws Exception {
        OfflinePlayerSnapshot snapshot = codec().decode(compress(new CompoundTag()));

        assertThat(snapshot.inventory()).isEmpty();
        assertThat(snapshot.enderItems()).isEmpty();
    }

    private static CompoundTag itemTag(String id, int count) {
        CompoundTag item = new CompoundTag();
        item.putString("id", id);
        item.putInt("count", count);
        return item;
    }

    private static ItemStack stack(net.minecraft.world.item.Item item, int count) {
        return CraftItemStack.asCraftMirror(new net.minecraft.world.item.ItemStack(item, count));
    }

    private static RegistryOps<Tag> ops() {
        return RegistryOps.create(NbtOps.INSTANCE, registries);
    }

    private static NmsPlayerDataCodec codec() {
        return new NmsPlayerDataCodec(() -> registries);
    }

    private static byte[] compress(CompoundTag root) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        NbtIo.writeCompressed(root, out);
        return out.toByteArray();
    }

    private static CompoundTag decompress(byte[] bytes) throws IOException {
        return NbtIo.readCompressed(new ByteArrayInputStream(bytes), NbtAccounter.unlimitedHeap());
    }
}
