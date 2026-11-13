package de.kreiscraft.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import net.minecraft.server.dedicated.DedicatedServer;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.ServerPacksSource;
import net.minecraft.server.packs.resources.MultiPackResourceManager;
import net.minecraft.tags.TagLoader;
import net.minecraft.world.ItemStackWithSlot;
import net.minecraft.world.entity.EntityEquipment;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.LevelResource;
import org.bukkit.Material;
import org.bukkit.craftbukkit.CraftServer;
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
                List.of(ServerPacksSource.createVanillaPackSource().fullResources()))) {
            TagLoader.loadTagsForExistingRegistries(resources, builtIn, ReloadableRegistrarEvent.Cause.INITIAL)
                    .forEach(tags -> tags.apply());
            var loaded = RegistryDataLoader.load(resources, builtIn.listRegistries().toList(),
                    RegistryDataLoader.WORLD_REGISTRIES, Runnable::run, ReloadableRegistrarEvent.Cause.INITIAL).join();
            registries = HolderLookup.Provider.create(Stream.concat(builtIn.listRegistries(), loaded.listRegistries()));
        }
        BuiltInRegistries.DATA_COMPONENT_INITIALIZERS.build(registries)
                .forEach(pending -> pending.apply());
        org.bukkit.craftbukkit.CraftRegistry.setMinecraftRegistry(builtIn);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"barrel", "chest"})
    void durableContainerReadbackChecksCallerSelectedBlockItemsAndMarker(String type) throws Exception {
        var world = mock(org.bukkit.craftbukkit.CraftWorld.class);
        var level = mock(net.minecraft.server.level.ServerLevel.class);
        when(world.getHandle()).thenReturn(level);
        when(world.getWorldFolder()).thenReturn(playerData.toFile());
        when(world.getName()).thenReturn("world");
        when(level.dimension()).thenReturn(net.minecraft.world.level.Level.OVERWORLD);
        when(level.registryAccess()).thenReturn(RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));
        var region = playerData.resolve("region");
        Files.createDirectories(region);
        var block = new CompoundTag();
        block.putInt("x", 12);
        block.putInt("y", 70);
        block.putInt("z", 4);
        block.putString("id", "minecraft:" + type);
        var marker = new CompoundTag();
        marker.putString("test:marker", "test-token");
        block.put("PublicBukkitValues", marker);
        var items = new ListTag();
        items.add(itemTag("minecraft:diamond", 3));
        items.getCompoundOrEmpty(0).putByte("Slot", (byte) 2);
        block.put("Items", items);
        var blocks = new ListTag();
        blocks.add(block);
        var chunk = new CompoundTag();
        chunk.put("block_entities", blocks);
        var info = new net.minecraft.world.level.chunk.storage.RegionStorageInfo("world",
                net.minecraft.world.level.Level.OVERWORLD, "chunk");
        try (var storage = new net.minecraft.world.level.chunk.storage.RegionFileStorage(info, region, false)) {
            storage.write(new net.minecraft.world.level.ChunkPos(0, 0), chunk);
        }
        var location = new org.bukkit.Location(world, 12, 70, 4);
        var expected = List.of(new SlottedItem(2,
                CraftItemStack.asBukkitMirror(new net.minecraft.world.item.ItemStack(Items.DIAMOND, 3))));
        var blockType = org.bukkit.NamespacedKey.minecraft(type);
        var key = new org.bukkit.NamespacedKey("test", "marker");
        NmsContainerStorage.saveAndVerify(location, blockType, expected, key, "test-token");
        assertThatThrownBy(() -> NmsContainerStorage.saveAndVerify(location, blockType, expected, key, "wrong-token"))
                .isInstanceOf(IOException.class);
        assertThatThrownBy(
                () -> NmsContainerStorage.saveAndVerify(location, org.bukkit.NamespacedKey.minecraft("hopper"),
                        expected, key, "test-token")).isInstanceOf(IOException.class);
        assertThatThrownBy(() -> NmsContainerStorage.saveAndVerify(location, blockType,
                List.of(new SlottedItem(3, expected.getFirst().stack())), key, "test-token"))
                        .isInstanceOf(IOException.class);
        verify(world, org.mockito.Mockito.times(4)).save(true);
    }

    @Test
    void openForEdit_serverResolvedDirectory_findsInventoryAndEnderChest() throws Exception {
        DedicatedServer server = mock(DedicatedServer.class);
        CraftServer craftServer = mock(CraftServer.class);
        when(craftServer.getServer()).thenReturn(server);
        // The server's working directory need not be its configured world container.
        when(server.getServerDirectory()).thenReturn(playerData);
        when(server.getLevelIdName()).thenReturn("custom-world");
        Path directory = playerData.resolve("world-container/custom-world")
                .resolve(LevelResource.PLAYER_DATA_DIR.id());
        when(server.getWorldPath(LevelResource.PLAYER_DATA_DIR)).thenReturn(directory);
        Files.createDirectories(directory);
        UUID playerId = UUID.randomUUID();
        Path source = directory.resolve(playerId + ".dat");
        CompoundTag root = new CompoundTag();
        root.put("Inventory", ItemStackWithSlot.CODEC.listOf().encodeStart(ops(),
                List.of(new ItemStackWithSlot(9, new net.minecraft.world.item.ItemStack(Items.DIAMOND, 3))))
                .getOrThrow());
        root.put("EnderItems", ItemStackWithSlot.CODEC.listOf().encodeStart(ops(),
                List.of(new ItemStackWithSlot(26, new net.minecraft.world.item.ItemStack(Items.EMERALD, 8))))
                .getOrThrow());
        byte[] sourceBytes = compress(root);
        Files.write(source, sourceBytes);
        Path resolved = PaperPlayerDataPaths.playerDataDirectory(new PaperPlatform(null, craftServer));

        var opened = new OfflinePlayerDataStore(resolved, codec()).openForEdit(playerId);

        assertThat(opened).as("server-saved player data must not be reported as missing").isPresent();
        OfflinePlayerEditSession session = opened.orElseThrow();
        assertThat(session.source()).isEqualTo(source);
        assertThat(session.snapshot().inventory().getFirst().stack().getType()).isEqualTo(Material.DIAMOND);
        assertThat(session.snapshot().enderItems().getFirst().stack().getType()).isEqualTo(Material.EMERALD);
        assertThat(Files.readAllBytes(source)).isEqualTo(sourceBytes);
        verify(server).getWorldPath(LevelResource.PLAYER_DATA_DIR);
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

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    void offlineNestedBundleRemovalPreservesContainersSiblingComponentsAndOtherPlayerData(boolean shulker)
            throws Exception {
        var egg = new net.minecraft.world.item.ItemStack(Items.DRAGON_EGG);
        var inner = new net.minecraft.world.item.ItemStack(Items.BUNDLE);
        inner.set(net.minecraft.core.component.DataComponents.BUNDLE_CONTENTS,
                net.minecraft.world.item.component.BundleContents.EMPTY.copyWithContents(Stream.of(egg)));
        var outer = new net.minecraft.world.item.ItemStack(Items.BUNDLE);
        outer.set(net.minecraft.core.component.DataComponents.BUNDLE_CONTENTS,
                new net.minecraft.world.item.component.BundleContents(List.of(
                        net.minecraft.world.item.ItemStackTemplate.fromNonEmptyStack(inner),
                        new net.minecraft.world.item.ItemStackTemplate(Items.DIAMOND, 3))));
        if (shulker) {
            var box = new net.minecraft.world.item.ItemStack(Items.SHULKER_BOX);
            box.set(net.minecraft.core.component.DataComponents.CONTAINER,
                    net.minecraft.world.item.component.ItemContainerContents.fromItems(List.of(outer)));
            outer = box;
        }
        CompoundTag root = new CompoundTag();
        root.putFloat("Health", 17);
        root.put("Inventory", ItemStackWithSlot.CODEC.listOf().encodeStart(ops(),
                List.of(new ItemStackWithSlot(9, outer))).getOrThrow());
        root.put("EnderItems", ItemStackWithSlot.CODEC.listOf().encodeStart(ops(),
                List.of(new ItemStackWithSlot(3, new net.minecraft.world.item.ItemStack(Items.EMERALD, 8))))
                .getOrThrow());
        UUID id = UUID.randomUUID();
        var source = playerData.resolve(id + ".dat");
        Files.write(source, compress(root));
        var session = new OfflinePlayerDataStore(playerData, codec()).openForEdit(id).orElseThrow();
        ItemStack containing = session.snapshot().inventoryAt(9).clone();
        assertThat(de.kreiscraft.shared.inventory.ItemContents.editFirst(containing, child -> {
            if (child.getType() != Material.DRAGON_EGG) {
                return false;
            }
            child.setAmount(0);
            return true;
        })).isTrue();
        var staged = session.stage(List.of(new SlottedItem(9, containing)), session.snapshot().enderItems());
        assertThat(session.commit(staged, () -> true)).isEqualTo(OfflineCommitResult.COMMITTED);
        var edited = decompress(Files.readAllBytes(source));
        assertThat(edited.get("Health")).isEqualTo(root.get("Health"));
        assertThat(edited.get("EnderItems")).isEqualTo(root.get("EnderItems"));
        var decoded = ItemStackWithSlot.CODEC.listOf().parse(ops(), edited.get("Inventory")).getOrThrow();
        var container = decoded.getFirst().stack();
        if (shulker) {
            assertThat(container.getItem()).isEqualTo(Items.SHULKER_BOX);
            container = container.get(net.minecraft.core.component.DataComponents.CONTAINER).copyOne();
        }
        var children = container.get(net.minecraft.core.component.DataComponents.BUNDLE_CONTENTS)
                .itemCopies().toList();
        assertThat(children).hasSize(2);
        assertThat(children.getFirst().getItem()).isEqualTo(Items.BUNDLE);
        assertThat(children.getFirst().get(net.minecraft.core.component.DataComponents.BUNDLE_CONTENTS).isEmpty())
                .isTrue();
        assertThat(children.get(1).getItem()).isEqualTo(Items.DIAMOND);
        assertThat(children.get(1).getCount()).isEqualTo(3);
    }

    @Test
    void returnStorageVerifiesActualRegionNbtAndRejectsMissingOrCorruptBarrel() throws Exception {
        var world = mock(org.bukkit.craftbukkit.CraftWorld.class);
        var level = mock(net.minecraft.server.level.ServerLevel.class);
        when(world.getHandle()).thenReturn(level);
        when(level.dimension()).thenReturn(net.minecraft.world.level.Level.OVERWORLD);
        when(level.registryAccess()).thenReturn(RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));
        when(world.getWorldFolder()).thenReturn(playerData.toFile());
        when(world.getName()).thenReturn("world");
        when(world.getUID()).thenReturn(UUID.randomUUID());
        var location = new org.bukkit.Location(world, 12, 70, 4);
        ItemStack egg = stack(Items.DRAGON_EGG, 1);
        var folder = playerData.resolve("region");
        Files.createDirectories(folder);
        var info = new net.minecraft.world.level.chunk.storage.RegionStorageInfo("world",
                net.minecraft.world.level.Level.OVERWORLD, "chunk");
        var barrel = new net.minecraft.world.level.block.entity.BarrelBlockEntity(
                new net.minecraft.core.BlockPos(12, 70, 4),
                net.minecraft.world.level.block.Blocks.BARREL.defaultBlockState());
        barrel.setItem(13, CraftItemStack.asNMSCopy(egg));
        barrel.persistentDataContainer.set(new org.bukkit.NamespacedKey("kreiscraft", "egg_return"),
                org.bukkit.persistence.PersistentDataType.STRING, "token");
        var entities = new ListTag();
        entities.add(barrel.saveWithFullMetadata(registries));
        var chunk = new CompoundTag();
        chunk.put("block_entities", entities);
        var pos = new net.minecraft.world.level.ChunkPos(0, 0);
        try (var storage = new net.minecraft.world.level.chunk.storage.RegionFileStorage(info, folder, true)) {
            storage.write(pos, chunk);
        }
        var expected = List.of(new SlottedItem(13, egg));
        var type = org.bukkit.NamespacedKey.minecraft("barrel");
        var marker = new org.bukkit.NamespacedKey("kreiscraft", "egg_return");
        NmsContainerStorage.saveAndVerify(location, type, expected, marker, "token");
        verify(world).save(true);
        assertThatThrownBy(() -> NmsContainerStorage.saveAndVerify(location, type, expected, marker, "wrong-token"))
                .isInstanceOf(IOException.class).hasMessageContaining("not persisted intact");
        try (var storage = new net.minecraft.world.level.chunk.storage.RegionFileStorage(info, folder, true)) {
            storage.write(pos, new CompoundTag());
        }
        assertThatThrownBy(() -> NmsContainerStorage.saveAndVerify(location, type, expected, marker, "token"))
                .isInstanceOf(IOException.class).hasMessageContaining("missing");
    }

    private static CompoundTag itemTag(String id, int count) {
        CompoundTag item = new CompoundTag();
        item.putString("id", id);
        item.putInt("count", count);
        return item;
    }

    private static ItemStack stack(net.minecraft.world.item.Item item, int count) {
        return CraftItemStack.asBukkitMirror(new net.minecraft.world.item.ItemStack(item, count));
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
