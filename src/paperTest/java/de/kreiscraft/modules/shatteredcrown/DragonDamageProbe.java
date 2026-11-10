package de.kreiscraft.modules.shatteredcrown;

import com.mojang.authlib.GameProfile;
import java.util.UUID;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.bukkit.Difficulty;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.EnderDragon;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import net.minecraft.tags.DamageTypeTags;

/** Disposable server only; real native player health/damage, no client/network transport. */
public final class DragonDamageProbe extends JavaPlugin implements Listener {
    private Player player;
    private EnderDragon dragon;
    private int events;
    private boolean cancel;
    private boolean attributionValid = true;

    @Override
    public void onEnable() {
        if (!Boolean.getBoolean("kreiscraft.damage-probe")) {
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        getServer().getPluginManager().registerEvents(this, this);
        getServer().getScheduler().runTask(this, this::run);
    }

    private void run() {
        try {
            var world = getServer().getWorlds().getFirst();
            world.setDifficulty(Difficulty.NORMAL);
            var server = ((CraftServer) getServer()).getServer();
            var level = ((CraftWorld) world).getHandle();
            var profile = new GameProfile(UUID.randomUUID(), "DamageProbe");
            var handle = new ServerPlayer(server, level, profile, ClientInformation.createDefault());
            handle.connection = new ServerGamePacketListenerImpl(server, new Connection(PacketFlow.SERVERBOUND),
                    handle, CommonListenerCookie.createInitial(profile, false));
            handle.connection
                    .handleAcceptPlayerLoad(new net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket());
            handle.setPos(0, 100, 0);
            player = handle.getBukkitEntity();
            var equipment = new ItemStack[] {new ItemStack(Material.NETHERITE_BOOTS),
                    new ItemStack(Material.NETHERITE_LEGGINGS), new ItemStack(Material.NETHERITE_CHESTPLATE),
                    new ItemStack(Material.NETHERITE_HELMET)};
            for (var piece : equipment) {
                piece.addEnchantment(Enchantment.PROTECTION, 4);
            }
            player.getInventory().setArmorContents(equipment);
            player.setHealth(20);
            player.setNoDamageTicks(0);
            dragon = world.spawn(new Location(world, 30, 100, 30), EnderDragon.class);
            dragon.setInvulnerable(true); // Shielding the source must not veto its outgoing player damage.
            DragonBattleAccess access = ignored -> null;
            var type = ((org.bukkit.craftbukkit.damage.CraftDamageSource) org.bukkit.damage.DamageSource
                    .builder(org.bukkit.damage.DamageType.DRAGON_BREATH)
                    .withDirectEntity(dragon).withCausingEntity(dragon).build()).getHandle();
            if (!type.is(DamageTypeTags.BYPASSES_ARMOR) || type.is(DamageTypeTags.BYPASSES_ENCHANTMENTS)
                    || type.is(DamageTypeTags.BYPASSES_RESISTANCE) || type.is(DamageTypeTags.BYPASSES_INVULNERABILITY)
                    || type.is(DamageTypeTags.BYPASSES_COOLDOWN) || !type.scalesWithDifficulty()) {
                throw new IllegalStateException("dragon-breath tags/scaling changed");
            }
            getLogger().info(
                    "DRAGON_DAMAGE_PROBE tags: armor=true enchantments/resistance/invulnerability/cooldown=false difficulty=true");
            for (Difficulty difficulty : new Difficulty[] {Difficulty.EASY, Difficulty.NORMAL, Difficulty.HARD}) {
                world.setDifficulty(difficulty);
                for (double target : new double[] {6, 8, 12, 4, 0.36, 0.72}) {
                    reset();
                    int before = events;
                    access.referenceDamage(player, dragon, target);
                    check(difficulty + " reference target=" + target, target, 20 - player.getHealth());
                    if (events - before != 1) {
                        throw new IllegalStateException("not exactly one native combat event");
                    }
                }
            }
            world.setDifficulty(Difficulty.NORMAL);
            reset();
            int zeroBefore = events;
            access.referenceDamage(player, dragon, 0);
            check("zero target", 0, 20 - player.getHealth());
            if (events != zeroBefore) {
                throw new IllegalStateException("zero target dispatched damage");
            }
            for (org.bukkit.GameMode mode : new org.bukkit.GameMode[] {org.bukkit.GameMode.CREATIVE,
                    org.bukkit.GameMode.SPECTATOR}) {
                player.setGameMode(mode);
                access.referenceDamage(player, dragon, 6);
                check(mode + " target", 0, 20 - player.getHealth());
            }
            player.setGameMode(org.bukkit.GameMode.SURVIVAL);
            player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 200, 0));
            access.referenceDamage(player, dragon, 6);
            check("Resistance I", 4.8, 20 - player.getHealth());
            player.removePotionEffect(PotionEffectType.RESISTANCE);
            reset();
            player.getAttribute(org.bukkit.attribute.Attribute.MAX_ABSORPTION).setBaseValue(4);
            player.setAbsorptionAmount(4);
            access.referenceDamage(player, dragon, 6);
            check("absorption health loss", 2, 20 - player.getHealth());
            check("absorption consumed", 0, player.getAbsorptionAmount());
            reset();
            int before = events;
            access.referenceDamage(player, dragon, 6);
            access.referenceDamage(player, dragon, 6);
            check("same-tick invulnerability", 6, 20 - player.getHealth());
            if (events - before != 1) {
                throw new IllegalStateException("native invulnerability bypassed");
            }
            reset();
            cancel = true;
            access.referenceDamage(player, dragon, 6);
            check("public cancellation", 0, 20 - player.getHealth());
            cancel = false;
            reset();
            player.setInvulnerable(true);
            access.referenceDamage(player, dragon, 6);
            check("invulnerable player", 0, 20 - player.getHealth());
            player.setInvulnerable(false);
            reset();
            access.bombardmentDamage(player, dragon, 4);
            check("unchanged raw pool units", 1.44, 20 - player.getHealth());
            for (var piece : equipment) {
                piece.removeEnchantment(Enchantment.PROTECTION);
            }
            player.getInventory().setArmorContents(equipment);
            reset();
            access.referenceDamage(player, dragon, 6);
            check("unenchanted netherite armor bypass", 16.6666667, 20 - player.getHealth());
            player.getInventory().setArmorContents(new ItemStack[4]);
            reset();
            access.referenceDamage(player, dragon, 6);
            check("unarmored control", 16.6666667, 20 - player.getHealth());
            world.setDifficulty(Difficulty.PEACEFUL);
            reset();
            access.referenceDamage(player, dragon, 6);
            check("Peaceful", 0, 20 - player.getHealth());
            dragon.remove();
            getLogger().info("DRAGON_DAMAGE_PROBE PASS");
        } catch (Throwable error) {
            getLogger().log(java.util.logging.Level.SEVERE, "DRAGON_DAMAGE_PROBE FAIL", error);
        } finally {
            getServer().shutdown();
        }
    }

    private void reset() {
        player.setHealth(20);
        player.setAbsorptionAmount(0);
        player.setNoDamageTicks(0);
    }

    private void check(String label, double expected, double observed) {
        getLogger().info("DRAGON_DAMAGE_PROBE " + label + " expected=" + expected + " observed=" + observed);
        if (!attributionValid || Math.abs(expected - observed) > 0.001) {
            throw new IllegalStateException(label + ": expected " + expected + ", observed " + observed);
        }
    }

    @EventHandler
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() != player) {
            return;
        }
        events++;
        if (event.getDamageSource().getDirectEntity() != dragon
                || event.getDamageSource().getCausingEntity() != dragon) {
            attributionValid = false;
        }
        if (cancel) {
            event.setCancelled(true);
        }
    }
}
