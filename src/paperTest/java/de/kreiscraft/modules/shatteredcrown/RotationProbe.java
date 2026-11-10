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
import org.bukkit.craftbukkit.entity.CraftEnderCrystal;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Mob;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EnderDragonChangePhaseEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

/** Disposable real Paper automatic rotation and native landing, with synthetic transport only. */
public final class RotationProbe extends JavaPlugin implements Listener {
    private static final NamespacedKey ARTIFACT = new NamespacedKey("kreiscraft", "crown_artifact");
    private final List<String> starts = new ArrayList<>();
    private World end;
    private ServerPlayer fixture;
    private EnderDragon dragon;
    private EncounterController encounter;
    private Sink sink;
    private Location pad;
    private String previous = "none";
    private String status;
    private int ticks;
    private int stage;
    private int completion = -1;
    private int arrival = -1;
    private int landingHits;
    private boolean deposited;
    private boolean finished;

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
        getServer().getScheduler().runTask(this, this::start);
    }

    private void start() {
        try {
            end = getServer().getWorld(NamespacedKey.minecraft("the_end"));
            require(end != null, "main End");
            end.setGameRule(org.bukkit.GameRule.NATURAL_REGENERATION, false);
            for (int x = -9; x <= 9; x++) {
                for (int z = -9; z <= 9; z++) {
                    end.setChunkForceLoaded(x, z, true);
                }
            }
            for (int x = 15; x <= 70; x++) {
                for (int z = 13; z <= 67; z++) {
                    end.getBlockAt(x, 150, z).setType(Material.END_STONE);
                    for (int y = 151; y <= 175; y++) {
                        end.getBlockAt(x, y, z).setType(Material.AIR);
                    }
                }
            }
            pad = new Location(end, 40.5, 151, 40.5);
            var server = ((CraftServer) getServer()).getServer();
            var profile = new GameProfile(UUID.randomUUID(), "RotationFixture");
            fixture = new ServerPlayer(server, ((CraftWorld) end).getHandle(), profile,
                    ClientInformation.createDefault());
            fixture.snapTo(pad.getX(), pad.getY(), pad.getZ(), 0, 0);
            sink = new Sink();
            server.getPlayerList().placeNewPlayer(sink, fixture, CommonListenerCookie.createInitial(profile, false));
            fixture.getBukkitEntity().setGameMode(GameMode.SURVIVAL);
            fixture.getBukkitEntity().setOp(true);
            var plugin = getServer().getPluginManager().getPlugin("Kreiscraft");
            for (var listener : HandlerList.getRegisteredListeners(plugin)) {
                if (listener.getListener() instanceof EncounterController controller) {
                    encounter = controller;
                    break;
                }
            }
            require(encounter != null, "actual production controller listener");
            getServer().getScheduler().runTaskTimer(this, this::tick, 1, 1);
        } catch (Throwable error) {
            fail(error);
        }
    }

    private void command(String command) {
        require(getServer().dispatchCommand(fixture.getBukkitEntity(), "dragondebug " + command), command);
    }

    private String status() {
        sink.replies.clear();
        command("status");
        status = String.join(" | ", sink.replies);
        require(status.contains("mode="), "observable command status");
        return status;
    }

    private void clearWave() {
        var objectives = end.getEntitiesByClass(EnderCrystal.class).stream().filter(this::owned).toList();
        require(objectives.size() == 4, "four native objectives");
        for (var defender : end.getEntitiesByClass(Mob.class).stream().filter(this::owned).toList()) {
            defender.setHealth(0);
        }
        var source = fixture.damageSources().playerAttack(fixture);
        for (var crystal : objectives) {
            ((CraftEnderCrystal) crystal).getHandle().hurtServer(((CraftWorld) end).getHandle(), source, 100);
            require(!crystal.isValid(), "native player crystal damage");
        }
    }

    private boolean owned(org.bukkit.entity.Entity entity) {
        return entity.getPersistentDataContainer().has(ARTIFACT, PersistentDataType.STRING);
    }

    private void tick() {
        if (finished) {
            return;
        }
        try {
            ticks++;
            fixture.connection.tick();
            require(ticks < 4000, "bounded rotation/landing timeout stage=" + stage);
            if (stage == 0) {
                dragon = end.getEnderDragonBattle().getEnderDragon();
                if (ticks < 80 || dragon == null || !dragon.getPersistentDataContainer()
                        .has(new NamespacedKey("kreiscraft", "crown_original_health"))) {
                    return;
                }
                require(fixture.connection.hasClientLoaded() && fixture.getBukkitEntity().isOnline(),
                        "registered loaded fixture");
                require(dragon.hasAI() && encounter.waveNumber() == 1, "native battle adoption with AI enabled");
                if (Boolean.getBoolean("kreiscraft.rotation-landing-only")) {
                    command("wave 3");
                    clearWave();
                    command("resume");
                    stage = 2;
                } else {
                    stage = 1;
                }
            }
            // Maintain a live measurable damage recipient; do not suppress native combat or dragon AI.
            fixture.getBukkitEntity().setHealth(20);
            fixture.getBukkitEntity().setFoodLevel(20);
            String state = status();
            String attack = state.split(", attack=")[1].split(",")[0];
            if (stage == 2 && ticks % 100 == 0) {
                getLogger().info("ROTATION_BOUNDARY landing=" + attack + " dragon=" + dragon.getLocation()
                        + " podium=" + dragon.getPodium() + " phase=" + dragon.getPhase() + " status=" + state);
            }
            if (stage == 1 && encounter.remainingGraceSeconds() > 0) {
                require(attack.equals("none"), "quiet opening grace");
                return;
            }
            if (stage == 1) {
                if (starts.isEmpty()) {
                    require(!attack.equals("none"), "first warning at exact grace expiry");
                }
                if (attack.equals("none") && !previous.equals("none")) {
                    completion = ticks;
                    getLogger().info("ROTATION main completed at=" + ticks);
                }
                if (!attack.equals("none") && !attack.equals(previous)) {
                    require(attack.equals("BOMBARDMENT") || attack.equals("PREY"), "only opening unlocks");
                    if (!starts.isEmpty()) {
                        require(!attack.equals(starts.getLast()), "no immediate round repeat");
                        require(ticks - completion == 100,
                                "exact 5-second POST-completion gap " + (ticks - completion));
                    }
                    starts.add(attack);
                    getLogger().info("ROTATION warning=" + attack + " tick=" + ticks);
                }
                previous = attack;
                if (starts.size() >= 4 && attack.equals("none")) {
                    command("wave 3");
                    require(status().contains("mode=manual"), "wave debug leaves paused");
                    require(encounter.unlockedAttacks().contains(EncounterController.AttackFamily.RAIN)
                            && !encounter.availableAttacks().contains(EncounterController.AttackFamily.RAIN),
                            "Rain unlocked but unavailable");
                    clearWave();
                    require(encounter.availableAttacks().contains(EncounterController.AttackFamily.SMASH),
                            "third completion unlocks Smash");
                    command("resume");
                    stage = 2;
                }
            } else if (stage == 2) {
                if (attack.equals("PREY") && !deposited) {
                    // Public shared pool seam: extend a real owned pool for bounded natural-arrival coverage.
                    require(encounter.depositBreathPool(pad, 5, 60), "public native pool deposit");
                    deposited = true;
                    getLogger().info("ROTATION old pool deposited tick=" + ticks);
                }
                if (arrival >= 0) {
                    require(state.contains("hazards=1") || !state.contains("hazards=0"),
                            "old pools survive native arrival");
                    stage = 3;
                }
            } else if (stage == 3) {
                if (!attack.equals("LANDING")) {
                    require(getServer().getCurrentTick() - arrival == 120, "six seconds from actual native arrival");
                    require(landingHits >= 2, "old pool damage continues through native landing");
                    command("cancel");
                    require(status().contains("hazards=0") && status.contains("mode=manual"),
                            "cancel drains hazards and pauses");
                    require(dragon.hasAI(), "native AI retained");
                    getServer().getPluginManager()
                            .disablePlugin(getServer().getPluginManager().getPlugin("Kreiscraft"));
                    require(end.getEntities().stream().noneMatch(this::owned), "production disable cleanup");
                    finished = true;
                    getLogger().info("ROTATION_PROBE PASS ticks=" + ticks + " starts=" + starts + " landingHits="
                            + landingHits
                            + " nativeBattle+grace+rounds+postCompletion+unlocks+nativeArrival120+oldPoolDamage+cleanup AI=true");
                    getServer().shutdown();
                }
            }
        } catch (Throwable error) {
            fail(error);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void phase(EnderDragonChangePhaseEvent event) {
        if (event.getEntity() == dragon && deposited && arrival < 0
                && status != null && status.contains("attack=LANDING")
                && event.getNewPhase() == EnderDragon.Phase.SEARCH_FOR_BREATH_ATTACK_TARGET
                && Math.abs(dragon.getY()
                        - (end.getHighestBlockYAt(dragon.getPodium().getBlockX(), dragon.getPodium().getBlockZ())
                                + 1)) <= 8
                && dragon.getLocation().toVector().setY(0)
                        .distanceSquared(dragon.getPodium().toVector().setY(0)) <= 64) {
            arrival = getServer().getCurrentTick();
            getLogger()
                    .info("ROTATION actual native arrival serverTick=" + arrival + " location=" + dragon.getLocation());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void damage(EntityDamageEvent event) {
        if (fixture != null && event.getEntity() == fixture.getBukkitEntity() && arrival >= 0
                && event.getCause() == EntityDamageEvent.DamageCause.DRAGON_BREATH && event.getFinalDamage() > 0
                && event.getDamage() == 1 && event.getDamageSource().getCausingEntity() == dragon) {
            landingHits++;
        }
    }

    private static void require(boolean condition, String reason) {
        if (!condition) {
            throw new IllegalStateException(reason);
        }
    }

    private void fail(Throwable error) {
        finished = true;
        getLogger().log(java.util.logging.Level.SEVERE, "ROTATION_PROBE FAIL ticks=" + ticks + " status=" + status,
                error);
        getServer().shutdown();
    }

    private static final class Sink extends Connection {
        private PacketListener listener;
        private final List<String> replies = new ArrayList<>();

        private Sink() {
            super(PacketFlow.SERVERBOUND);
            channel = new io.netty.channel.embedded.EmbeddedChannel();
            address = new java.net.InetSocketAddress("127.0.0.1", 0);
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
        public void send(Packet<?> packet, io.netty.channel.ChannelFutureListener callback, boolean flush) {
            if (packet instanceof net.minecraft.network.protocol.common.ClientboundKeepAlivePacket keep
                    && listener instanceof net.minecraft.server.network.ServerCommonPacketListenerImpl common) {
                common.handleKeepAlive(
                        new net.minecraft.network.protocol.common.ServerboundKeepAlivePacket(keep.getId()));
            }
            if (packet instanceof net.minecraft.network.protocol.game.ClientboundSystemChatPacket chat) {
                replies.add(chat.content().getString());
            }
        }

        @Override
        public void flushChannel() {}
    }
}
