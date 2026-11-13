package de.kreiscraft.modules.dragonegg;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.bukkit.Material;
import org.bukkit.block.ShulkerBox;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.BundleMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

class DragonEggBuffsTest {
    @TempDir
    Path folder;
    private ServerMock server;
    private DragonEggTracker tracker;
    private DragonEggBuffs buffs;

    @BeforeEach
    void setUp() throws Exception {
        server = MockBukkit.mock();
        var data = new DragonEggData(folder);
        data.load();
        tracker = new DragonEggTracker(server, data, System::currentTimeMillis);
        buffs = new DragonEggBuffs(tracker);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void directTrackedInventoryGrantsStrengthOneAndHasteTwoButOrdinaryEggDoesNot() {
        var player = server.addPlayer();
        var egg = new ItemStack(Material.DRAGON_EGG);
        player.getInventory().setItem(0, egg);
        buffs.update(player);
        assertThat(player.getActivePotionEffects()).isEmpty();
        tracker.claim(egg);
        player.getInventory().setItem(0, egg);
        buffs.update(player);
        assertThat(player.getPotionEffect(PotionEffectType.STRENGTH).getAmplifier()).isZero();
        assertThat(player.getPotionEffect(PotionEffectType.HASTE).getAmplifier()).isEqualTo(1);
    }

    @Test
    void deadAndOfflinePlayersDoNotReceiveBuffsEvenWithKeepInventory() {
        var player = server.addPlayer();
        var egg = new ItemStack(Material.DRAGON_EGG);
        tracker.claim(egg);
        player.getInventory().setItem(0, egg);
        player.setHealth(0);
        buffs.update(player);
        assertThat(player.getActivePotionEffects()).isEmpty();
        var offline = server.addPlayer();
        offline.getInventory().setItem(0, egg);
        offline.disconnect();
        buffs.update(offline);
        assertThat(offline.getActivePotionEffects()).isEmpty();
    }

    @Test
    void cursorOpenChestShulkerAndBundleAreNotDirectInventoryButOffhandIs() {
        var player = server.addPlayer();
        var egg = new ItemStack(Material.DRAGON_EGG);
        tracker.claim(egg);
        player.setItemOnCursor(egg);
        var chest = server.createInventory(null, 27);
        chest.setItem(0, egg);
        player.openInventory(chest);
        var shulker = new ItemStack(Material.SHULKER_BOX);
        var meta = (BlockStateMeta) shulker.getItemMeta();
        var box = (ShulkerBox) meta.getBlockState();
        box.getInventory().setItem(0, egg);
        meta.setBlockState(box);
        shulker.setItemMeta(meta);
        player.getInventory().setItem(0, shulker);
        var bundle = new ItemStack(Material.BUNDLE);
        bundle.editMeta(BundleMeta.class, bundleMeta -> bundleMeta.addItem(egg));
        player.getInventory().setItem(1, bundle);
        buffs.update(player);
        assertThat(player.getActivePotionEffects()).isEmpty();
        player.getInventory().setItemInOffHand(egg);
        buffs.update(player);
        assertThat(player.hasPotionEffect(PotionEffectType.STRENGTH)).isTrue();
        assertThat(player.hasPotionEffect(PotionEffectType.HASTE)).isTrue();
    }

    @Test
    void transferAppliesToNewCarrierAndOldCarrierBuffsExpireWithinHalfASecond() {
        var oldCarrier = server.addPlayer();
        var newCarrier = server.addPlayer();
        var egg = new ItemStack(Material.DRAGON_EGG);
        tracker.claim(egg);
        oldCarrier.getInventory().setItem(0, egg);
        buffs.update(oldCarrier);
        oldCarrier.getInventory().clear();
        newCarrier.getInventory().setItem(0, egg);
        buffs.update(oldCarrier);
        buffs.update(newCarrier);
        assertThat(newCarrier.hasPotionEffect(PotionEffectType.STRENGTH)).isTrue();
        assertThat(newCarrier.hasPotionEffect(PotionEffectType.HASTE)).isTrue();
        server.getScheduler().performTicks(10);
        oldCarrier.tick();
        assertThat(oldCarrier.getActivePotionEffects()).isEmpty();
        newCarrier.tick();
        buffs.update(newCarrier);
        assertThat(newCarrier.hasPotionEffect(PotionEffectType.HASTE)).isTrue();
        newCarrier.getInventory().clear();
        buffs.update(newCarrier);
        server.getScheduler().performTicks(10);
        newCarrier.tick();
        assertThat(newCarrier.getActivePotionEffects()).isEmpty();
    }

    @Test
    void strongerAndEqualExternalEffectsAreNeitherDowngradedExtendedNorRemoved() {
        var player = server.addPlayer();
        var egg = new ItemStack(Material.DRAGON_EGG);
        tracker.claim(egg);
        player.getInventory().setItem(0, egg);
        var strength = new PotionEffect(PotionEffectType.STRENGTH, 200, 2);
        var haste = new PotionEffect(PotionEffectType.HASTE, 200, 1);
        var speed = new PotionEffect(PotionEffectType.SPEED, 200, 1);
        player.addPotionEffects(java.util.List.of(strength, haste, speed));
        buffs.update(player);
        assertThat(player.getActivePotionEffects()).containsExactlyInAnyOrder(strength, haste, speed);
        player.getInventory().clear();
        buffs.update(player);
        assertThat(player.getActivePotionEffects()).containsExactlyInAnyOrder(strength, haste, speed);
    }

    @Test
    void sameLevelExternalEffectsAppliedWhileCarryingSurviveDroppingTheEgg() {
        var player = server.addPlayer();
        var egg = new ItemStack(Material.DRAGON_EGG);
        tracker.claim(egg);
        player.getInventory().setItem(0, egg);
        buffs.update(player);
        player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 200, 0, true, false, false));
        player.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, 200, 1, true, false, false));
        player.getInventory().clear();
        buffs.update(player);
        server.getScheduler().performTicks(10);
        player.tick();
        assertThat(player.getPotionEffect(PotionEffectType.STRENGTH).getDuration()).isEqualTo(190);
        assertThat(player.getPotionEffect(PotionEffectType.HASTE).getDuration()).isEqualTo(190);
    }
}
