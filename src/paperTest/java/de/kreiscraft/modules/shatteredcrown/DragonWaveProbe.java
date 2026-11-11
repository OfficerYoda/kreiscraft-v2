package de.kreiscraft.modules.shatteredcrown;

import com.mojang.authlib.GameProfile;
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
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.entity.CraftEnderDragon;
import org.bukkit.craftbukkit.entity.CraftEnderCrystal;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Mob;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

/** Version-pinned, disposable native fixture. Production commands/events, no copied gameplay classes. */
public final class DragonWaveProbe extends JavaPlugin {
    private static final NamespacedKey ARTIFACT = new NamespacedKey("kreiscraft", "crown_artifact");
    private final List<ServerPlayer> fixtures = new ArrayList<>();
    private World end;
    private EnderDragon dragon;
    private Mob retiredDefender;
    private int ticks;
    private int stage;
    private boolean finished;

    @Override
    public void onEnable() {
        if (!Boolean.getBoolean("kreiscraft.wave-probe")) {
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        getServer().getScheduler().runTask(this, () -> {
            try {
                end = getServer().getWorld(NamespacedKey.minecraft("the_end"));
                require(end != null, "main End missing");
                for (int x = -9; x <= 9; x++) {
                    for (int z = -9; z <= 9; z++) {
                        end.setChunkForceLoaded(x, z, true);
                    }
                }
                // Only a fixture pad; Anchor terrain is native main-island terrain.
                for (int x = 38; x <= 42; x++) {
                    for (int z = -2; z <= 2; z++) {
                        end.getBlockAt(x, 150, z).setType(Material.END_STONE);
                    }
                }
                for (int index = 0; index < 4; index++) {
                    register(getServer().getWorlds().getFirst(), index);
                }
                fixtures.getFirst().getBukkitEntity().teleport(new Location(end, 40.5, 151, 0.5));
                getServer().getScheduler().runTaskTimer(this, this::tick, 1, 1);
            } catch (Throwable error) {
                fail(error);
            }
        });
    }

    private void register(World world, int index) {
        var server = ((CraftServer) getServer()).getServer();
        var profile = new GameProfile(UUID.randomUUID(), "WaveProbe" + index);
        var player = new ServerPlayer(server, ((CraftWorld) world).getHandle(), profile,
                ClientInformation.createDefault());
        player.snapTo(40.5, 151, 0.5, 0, 0);
        server.getPlayerList().placeNewPlayer(new Sink(), player, CommonListenerCookie.createInitial(profile, false));
        fixtures.add(player);
        player.getBukkitEntity().setGameMode(index == 0 ? GameMode.SURVIVAL : GameMode.SPECTATOR);
        player.getBukkitEntity().setInvulnerable(true); // Isolate objective/damage routing, not defender AI acceptance.
    }

    private List<EnderCrystal> anchors() {
        return end.getEntitiesByClass(EnderCrystal.class).stream().filter(this::owned).toList();
    }

    private List<Mob> defenders() {
        return end.getEntitiesByClass(Mob.class).stream().filter(this::owned).toList();
    }

    private boolean owned(org.bukkit.entity.Entity entity) {
        return entity.isValid() && entity.getPersistentDataContainer().has(ARTIFACT, PersistentDataType.STRING);
    }

    private void command(String command) {
        require(getServer().dispatchCommand(getServer().getConsoleSender(), "dragondebug " + command), command);
    }

    private void clearWave() {
        var objectives = anchors();
        require(objectives.size() == 4, "four objectives before clearing");
        for (var defender : defenders()) {
            defender.setHealth(0); // Genuine native death callbacks, not fabricated EntityDeathEvents.
        }
        require(anchors().size() == 4, "defender deaths alone cannot complete wave");
        var level = ((CraftWorld) end).getHandle();
        var source = fixtures.getFirst().damageSources().playerAttack(fixtures.getFirst());
        for (int index : new int[] {2, 0, 3, 1}) {
            var crystal = objectives.get(index);
            ((CraftEnderCrystal) crystal).getHandle().hurtServer(level, source, 100);
            require(!crystal.isValid(), "independent crystal removal " + index);
        }
        require(anchors().isEmpty(), "all crystals destroyed");
    }

    private void hitDragon(boolean explosive) {
        var handle = ((CraftEnderDragon) dragon).getHandle();
        handle.invulnerableTime = 0;
        var player = fixtures.getFirst();
        var source = explosive ? player.damageSources().explosion(null, player)
                : player.damageSources().playerAttack(player);
        handle.hurt(((CraftWorld) end).getHandle(), handle.head, source, 100000);
    }

    private void tick() {
        if (finished) {
            return;
        }
        try {
            ticks++;
            fixtures.forEach(player -> player.connection.tick());
            if (ticks > 800) {
                throw new IllegalStateException("bounded native wave probe timeout stage=" + stage);
            }
            if (stage == 0) {
                dragon = end.getEnderDragonBattle().getEnderDragon();
                if (dragon == null || !dragon.getPersistentDataContainer()
                        .has(new NamespacedKey("kreiscraft", "crown_original_health"))) {
                    return;
                }
                require(getServer().getOnlinePlayers().size() == 4, "registered server population");
                require(anchors().size() == 4 && defenders().size() == 16, "first-entry wave1 population4");
                command("pause");
                dragon.setAI(false); // Native battle/adoption established; isolate native damage from flight.
                retiredDefender = defenders().getFirst();
                clearWave();
                for (var observer : fixtures.subList(1, 4)) {
                    observer.getBukkitEntity().teleport(new Location(end, 250, 151, 0));
                }
                register(end, 4);
                hitDragon(false);
                require(dragon.getHealth() == 650, "oversized native head melee clamped65");
                stage = 1;
            } else if (stage == 1 && anchors().size() == 4) {
                require(defenders().size() == 32, "wave2 allEnd population5 frozen units2");
                verifyRetiredAttribution();
                hitDragon(false);
                require(dragon.getHealth() == 650, "shield protects native followup");
                clearWave();
                dragon.setHealth(310);
                hitDragon(true);
                require(dragon.getHealth() == 300, "native explosive damage clamped30");
                stage = 2;
            } else if (stage == 2 && anchors().size() == 4) {
                require(defenders().size() == 16, "wave3 frozen units2 Warden/Wither pairs");
                verifyRetiredAttribution();
                clearWave();
                require(dragon.getHealth() == 300, "crystals cannot damage managed dragon");
                for (int wave : new int[] {1, 2, 3}) {
                    var old = anchors();
                    command("wave " + wave);
                    require(old.stream().noneMatch(org.bukkit.entity.Entity::isValid), "debug replaces old objectives");
                    require(anchors().size() == 4 && defenders().size() == (wave == 3 ? 16 : 32),
                            "fresh defended debug wave" + wave);
                    require(dragon.getHealth() == (wave == 1 ? 1000 : wave == 2 ? 650 : 300), "debug health" + wave);
                    command("status");
                }
                clearWave();
                command("resume");
                command("pause");
                getServer().getPluginManager().disablePlugin(getServer().getPluginManager().getPlugin("Kreiscraft"));
                require(anchors().isEmpty() && defenders().isEmpty(), "disable removes owned waves");
                getLogger()
                        .info("DRAGON_WAVE_PROBE PASS ticks=" + ticks
                                + " nativeBattle+population+gates+retiredAttribution+debug+cleanup");
                finished = true;
                getServer().shutdown();
            }
        } catch (Throwable error) {
            fail(error);
        }
    }

    private void verifyRetiredAttribution() {
        var unrelated =
                end.spawn(fixtures.getFirst().getBukkitEntity().getLocation(), org.bukkit.entity.Enderman.class);
        var source = org.bukkit.damage.DamageSource.builder(org.bukkit.damage.DamageType.EXPLOSION)
                .withDirectEntity(retiredDefender).withCausingEntity(retiredDefender).build();
        for (org.bukkit.entity.LivingEntity ally : List.of(unrelated, defenders().getFirst(), dragon)) {
            double health = ally.getHealth();
            ally.setNoDamageTicks(0);
            ally.damage(5, source);
            require(ally.getHealth() == health, "native retired-parent blast remains allied after gate");
        }
        unrelated.remove();
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }

    private void fail(Throwable error) {
        finished = true;
        getLogger().log(java.util.logging.Level.SEVERE, "DRAGON_WAVE_PROBE FAIL", error);
        getServer().shutdown();
    }

    private static final class Sink extends Connection {
        private PacketListener listener;

        private Sink() {
            super(PacketFlow.SERVERBOUND);
            channel = new io.netty.channel.embedded.EmbeddedChannel();
            address = new java.net.InetSocketAddress("127.0.0.1", 0);
        }

        @Override
        public <T extends PacketListener> void setupInboundProtocol(ProtocolInfo<T> protocol, T listener) {
            this.listener = listener;
        }

        @Override
        public PacketListener getPacketListener() {
            return listener;
        }

        @Override
        public void send(Packet<?> packet, io.netty.channel.ChannelFutureListener callback, boolean flush) {
            if (packet instanceof net.minecraft.network.protocol.common.ClientboundKeepAlivePacket keep
                    && listener instanceof net.minecraft.server.network.ServerCommonPacketListenerImpl common) {
                common.handleKeepAlive(
                        new net.minecraft.network.protocol.common.ServerboundKeepAlivePacket(keep.getId()));
            }
        }

        @Override
        public void flushChannel() {}
    }
}
