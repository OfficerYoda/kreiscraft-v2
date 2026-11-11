package de.kreiscraft.modules.shatteredcrown;

import com.mojang.authlib.GameProfile;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.embedded.EmbeddedChannel;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketListener;
import net.minecraft.network.ProtocolInfo;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import org.bukkit.Bukkit;
import org.bukkit.Difficulty;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityTeleportEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

/** Disposable Paper server, actual production plugin and native registered player; network sink only. */
public final class DiveEncounterProbe extends JavaPlugin implements Listener {
    private static final NamespacedKey ARTIFACT = new NamespacedKey("kreiscraft", "crown_artifact");
    private ServerPlayer nativePlayer;
    private Player player;
    private EnderDragon dragon;
    private World world;
    private Sink sink;
    private Location pad;
    private int ticks;
    private int age;
    private int scenario = -1;
    private int hits;
    private int cancelled;
    private int teleports;
    private int checks;
    private boolean measuring;
    private boolean done;
    private boolean automaticDive;
    private int lingeringPoolParticles;

    @Override
    public void onEnable() {
        if (!Boolean.getBoolean("kreiscraft.dive-probe")) {
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        Bukkit.getPluginManager().registerEvents(this, this);
        Bukkit.getScheduler().runTask(this, this::start);
        Bukkit.getScheduler().runTaskLater(this, () -> finish(false, "timeout"), 4000);
    }

    private void start() {
        try {
            world = Bukkit.getWorld(NamespacedKey.minecraft("the_end"));
            world.setGameRule(org.bukkit.GameRule.NATURAL_REGENERATION, false);
            for (int x = -12; x <= 12; x++) {
                for (int z = -12; z <= 12; z++) {
                    world.setChunkForceLoaded(x, z, true);
                }
            }
            for (int x = 15; x <= 70; x++) {
                for (int z = 13; z <= 67; z++) {
                    world.getBlockAt(x, 150, z).setType(Material.END_STONE);
                    for (int y = 151; y <= 180; y++) {
                        world.getBlockAt(x, y, z).setType(Material.AIR);
                    }
                }
            }
            pad = new Location(world, 40.5, 151, 40.5);
            var server = ((CraftServer) Bukkit.getServer()).getServer();
            var profile = new GameProfile(UUID.randomUUID(), "DiveFixture");
            nativePlayer = new ServerPlayer(server, ((CraftWorld) world).getHandle(), profile,
                    ClientInformation.createDefault());
            nativePlayer.snapTo(pad.getX(), pad.getY(), pad.getZ(), 0, 0);
            sink = new Sink();
            server.getPlayerList().placeNewPlayer(sink, nativePlayer,
                    CommonListenerCookie.createInitial(profile, false));
            player = nativePlayer.getBukkitEntity();
            player.setGameMode(GameMode.SURVIVAL);
            player.setOp(true);
            var armor = new ItemStack[] {new ItemStack(Material.NETHERITE_BOOTS),
                    new ItemStack(Material.NETHERITE_LEGGINGS), new ItemStack(Material.NETHERITE_CHESTPLATE),
                    new ItemStack(Material.NETHERITE_HELMET)};
            for (var piece : armor) {
                piece.addEnchantment(org.bukkit.enchantments.Enchantment.PROTECTION, 4);
            }
            player.getInventory().setArmorContents(armor);
            Bukkit.getScheduler().runTaskTimer(this, this::tick, 1, 1);
        } catch (RuntimeException error) {
            finish(false, error.toString());
        }
    }

    private void check(boolean condition, String reason) {
        checks++;
        if (!condition) {
            throw new IllegalStateException(reason + " scenario=" + scenario + " age=" + age);
        }
    }

    private String command(String command, String expected) {
        sink.replies.clear();
        sink.explicit = true;
        try {
            check(Bukkit.dispatchCommand(player, "dragondebug " + command), "dispatch " + command);
        } finally {
            sink.explicit = false;
        }
        String reply = String.join(" | ", sink.replies);
        check(reply.contains(expected), command + " reply=" + reply);
        return reply;
    }

    private long warnings() {
        return world.getEntitiesByClass(TextDisplay.class).stream()
                .filter(entity -> "lane".equals(entity.getPersistentDataContainer().get(ARTIFACT,
                        PersistentDataType.STRING)))
                .count();
    }

    private void begin() {
        measuring = false;
        command("pause", "main=idle");
        player.setGameMode(GameMode.SURVIVAL);
        player.teleport(pad);
        player.setVelocity(new org.bukkit.util.Vector());
        player.setHealth(20);
        player.setAbsorptionAmount(0);
        player.setNoDamageTicks(0);
        world.setDifficulty(new Difficulty[] {Difficulty.EASY, Difficulty.NORMAL, Difficulty.HARD}[scenario % 3]);
        if (scenario == 3) {
            world.getEntities().stream().filter(entity -> entity instanceof LivingEntity && "defender".equals(
                    entity.getPersistentDataContainer().get(ARTIFACT, PersistentDataType.STRING)))
                    .forEach(entity -> ((LivingEntity) entity).damage(10000, player));
            world.getEntitiesByClass(EnderCrystal.class).stream().filter(entity -> "anchor".equals(
                    entity.getPersistentDataContainer().get(ARTIFACT, PersistentDataType.STRING)))
                    .forEach(entity -> {
                        var level = ((CraftWorld) world).getHandle();
                        ((org.bukkit.craftbukkit.entity.CraftEnderCrystal) entity).getHandle()
                                .hurtServer(level, level.damageSources().playerAttack(nativePlayer), 1);
                    });
        }
        hits = 0;
        cancelled = 0;
        teleports = 0;
        sink.particles = 0;
        lingeringPoolParticles = 0;
        sink.sounds = 0;
        sink.unsolicited = 0;
        measuring = true;
        if (scenario == 3 || scenario >= 9) {
            automaticDive = false;
            command("resume", "mode=automatic");
        } else if (scenario >= 6) {
            command("trigger bombardment", "Started BOMBARDMENT");
        } else {
            command("trigger dive", "Started DIVE");
            check(warnings() == 1, "physical warning");
        }
        age = 0;
    }

    private void tick() {
        if (done) {
            return;
        }
        try {
            ticks++;
            age++;
            nativePlayer.connection.tick();
            if (scenario < 0) {
                var battle = world.getEnderDragonBattle();
                dragon = battle == null ? null : battle.getEnderDragon();
                if (ticks < 80 || dragon == null || !dragon.getPersistentDataContainer()
                        .has(new NamespacedKey("kreiscraft", "crown_original_health"))) {
                    check(ticks < 240, "native battle adoption timeout");
                    return;
                }
                check(player.isOnline() && Bukkit.getPlayer(player.getUniqueId()) == player
                        && nativePlayer.connection.hasClientLoaded(), "native registration/load");
                check(dragon.hasAI() && dragon.getDragonBattle().equals(battle), "real managed battle/AI");
                scenario = 0;
                begin();
                return;
            }
            if (scenario == 4 && age == 20) {
                player.setGameMode(GameMode.CREATIVE);
            }
            if (scenario != 3 && age == 39) {
                check(hits == 0 && player.getHealth() == 20, "no damage during warning");
            }
            if ((scenario == 3 || scenario >= 9) && (hits == 0 || warnings() != 0)) {
                String expectedAttack = scenario == 3 ? "DIVE" : "BOMBARDMENT";
                if (!automaticDive && command("status", "mode=automatic").contains("attack=" + expectedAttack)) {
                    automaticDive = true;
                    sink.particles = 0;
                    sink.sounds = 0;
                    player.setHealth(20);
                }
                check(age < 600, "automatic " + expectedAttack + " completion timeout");
                return;
            }
            if (scenario != 3 && age < 120) {
                return;
            }
            check(teleports == 0 && dragon.hasAI() && dragon.getPhase() != EnderDragon.Phase.HOVER,
                    "no teleport/HOVER/noAI");
            check((scenario >= 6 || sink.particles == 0) && sink.unsolicited == 0, "particle-free Dive/quiet attacks");
            command("status", "main=idle");
            check(warnings() == 0, "completed warning removed");
            if (scenario <= 3 || scenario >= 6) {
                int expectedLoss = scenario >= 6 ? 6 : 8;
                check(hits == 1 && Math.abs(20 - player.getHealth() - expectedLoss) < 0.001,
                        "once-only calibrated hit");
                check(sink.sounds > 0, "warning sound");
            } else {
                check(hits == 0 && player.getHealth() == 20 && cancelled == (scenario == 5 ? 1 : 0),
                        "invalid target/public cancellation");
            }
            getLogger().info("DIVE_MATRIX route=" + (scenario == 3 || scenario >= 9 ? "automatic" : "debug")
                    + " scenario=" + scenario + " difficulty=" + world.getDifficulty() + " health="
                    + player.getHealth() + " hits=" + hits + " cancelled=" + cancelled
                    + " particles=" + sink.particles + " teleports=" + teleports);
            getLogger().info(
                    "DIVE_PARTICLE_TYPES " + sink.particleTypes + " provenLingeringPool=" + lingeringPoolParticles);
            measuring = false;
            command("cancel", "flight=false");
            if (++scenario == 12) {
                finish(true, "checks=" + checks
                        + " nativeBattle+productionDive+Bombardment+threeDifficulties+automatic+cleanup");
            } else {
                begin();
            }
        } catch (RuntimeException error) {
            getLogger().log(java.util.logging.Level.SEVERE, "Dive probe failed", error);
            finish(false, error.toString());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void isolate(EntityDamageEvent event) {
        if (event.getEntity() == player && ((scenario == 3 || scenario >= 9) && !automaticDive
                || Math.abs(event.getFinalDamage() - (scenario >= 6 ? 6 : 8)) > 0.001
                || event.getCause() != EntityDamageEvent.DamageCause.DRAGON_BREATH
                || event.getDamageSource().getCausingEntity() != dragon || scenario == 5)) {
            event.setCancelled(true); // Isolate unrelated ambient damage, not the actual production Dive path.
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void observe(EntityDamageEvent event) {
        if (!measuring || (scenario == 3 || scenario >= 9) && !automaticDive || event.getEntity() != player
                || Math.abs(event.getFinalDamage() - (scenario >= 6 ? 6 : 8)) > 0.001
                || event.getCause() != EntityDamageEvent.DamageCause.DRAGON_BREATH) {
            return;
        }
        check(event.getDamageSource().getDirectEntity() == dragon
                && event.getDamageSource().getCausingEntity() == dragon, "dragon attribution");
        if (scenario < 6) {
            check(dragon.getY() <= player.getY() + 5, "actual low physical dragon at damage");
            var body = ((org.bukkit.craftbukkit.entity.CraftEnderDragon) dragon).getHandle().getSubEntities()[2];
            check(body.getBukkitEntity().getLocation().distanceSquared(dragon.getLocation()) < 64,
                    "real native body follows pass");
        }
        if (event.isCancelled()) {
            cancelled++;
        } else {
            hits++;
        }
    }

    @EventHandler
    public void teleport(EntityTeleportEvent event) {
        if (measuring && event.getEntity() == dragon) {
            teleports++;
        }
    }

    private void finish(boolean passed, String detail) {
        if (done) {
            return;
        }
        done = true;
        getLogger().info("DIVE_ENCOUNTER_PROBE " + (passed ? "PASS " : "FAIL ") + detail);
        Bukkit.shutdown();
    }

    @Override
    public void onDisable() {
        if (sink != null) {
            sink.channel.close();
        }
    }

    private final class Sink extends Connection {
        private PacketListener listener;
        private final List<String> replies = new ArrayList<>();
        private int particles;
        private int sounds;
        private int unsolicited;
        private boolean explicit;
        private final java.util.Set<String> particleTypes = new java.util.HashSet<>();

        private Sink() {
            super(PacketFlow.SERVERBOUND);
            channel = new EmbeddedChannel();
            address = new InetSocketAddress("127.0.0.1", 0);
        }

        @Override
        public <T extends PacketListener> void setupInboundProtocol(ProtocolInfo<T> protocol, T target) {
            listener = target;
        }

        @Override
        public PacketListener getPacketListener() {
            return listener;
        }

        @Override
        public void send(Packet<?> packet, ChannelFutureListener callback, boolean flush) {
            if (packet instanceof net.minecraft.network.protocol.BundlePacket<?> bundle) {
                bundle.subPackets().forEach(nested -> send(nested, null, false));
            } else if (packet instanceof net.minecraft.network.protocol.common.ClientboundKeepAlivePacket keep
                    && listener instanceof net.minecraft.server.network.ServerCommonPacketListenerImpl common) {
                common.handleKeepAlive(
                        new net.minecraft.network.protocol.common.ServerboundKeepAlivePacket(keep.getId()));
            } else if (packet instanceof net.minecraft.network.protocol.game.ClientboundSystemChatPacket chat) {
                if (explicit) {
                    replies.add(chat.content().getString());
                } else if (measuring) {
                    unsolicited++;
                }
            } else if (measuring) {
                if (packet instanceof net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket particle) {
                    String type = net.minecraft.core.registries.BuiltInRegistries.PARTICLE_TYPE
                            .getKey(particle.getParticle().getType()).toString();
                    particleTypes.add(type);
                    if (scenario == 3 && automaticDive && type.equals("minecraft:dragon_breath")
                            && world.getEntities().stream().anyMatch(entity -> "pool".equals(
                                    entity.getPersistentDataContainer().get(ARTIFACT, PersistentDataType.STRING)))) {
                        lingeringPoolParticles++;
                    } else {
                        particles++;
                    }
                } else if (packet instanceof net.minecraft.network.protocol.game.ClientboundSoundPacket) {
                    sounds++;
                }
            }
        }

        @Override
        public void flushChannel() {}
    }
}
