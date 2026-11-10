package de.kreiscraft.platform;

import com.mojang.authlib.GameProfile;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.embedded.EmbeddedChannel;
import java.net.InetSocketAddress;
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
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Husk;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityTeleportEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

/** Native player/AI fixture, not a client. Run beside the real plugin in a disposable build132 JVM. */
public final class DefenderRoamingProbe extends JavaPlugin implements Listener {
    private static final NamespacedKey ARTIFACT = new NamespacedKey("kreiscraft", "crown_artifact");
    private ServerPlayer player;
    private Husk defender;
    private EnderDragon dragon;
    private int ticks;
    private int ownedSpawns;
    private int leakedSpawns;
    private int teleports;
    private boolean melee;
    private boolean done;
    private double startX;
    private int deathTick;
    private Entity arrow;
    private Entity cloud;
    private int checks;

    /** Only transport is synthetic: native registration, ticks, targeting, pathfinding and damage remain real. */
    private static final class Sink extends Connection {
        private PacketListener listener;

        private Sink() {
            super(PacketFlow.SERVERBOUND);
            channel = new EmbeddedChannel();
            address = new InetSocketAddress("127.0.0.1", 0);
        }

        @Override
        public <T extends PacketListener> void setupInboundProtocol(ProtocolInfo<T> protocol, T next) {
            listener = next;
        }

        @Override
        public PacketListener getPacketListener() {
            return listener;
        }

        @Override
        public void send(Packet<?> packet, ChannelFutureListener callback, boolean flush) {
            if (packet instanceof net.minecraft.network.protocol.common.ClientboundKeepAlivePacket keep
                    && listener instanceof net.minecraft.server.network.ServerCommonPacketListenerImpl common) {
                common.handleKeepAlive(
                        new net.minecraft.network.protocol.common.ServerboundKeepAlivePacket(keep.getId()));
            }
        }

        @Override
        public void flushChannel() {}
    }

    @Override
    public void onEnable() {
        if (!Boolean.getBoolean("kreiscraft.defender-probe")) {
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        getServer().getPluginManager().registerEvents(this, this);
        getServer().getScheduler().runTask(this, this::start);
        getServer().getScheduler().runTaskLater(this, () -> finish(false, "timeout"), 1600);
    }

    private void start() {
        try {
            World world = Bukkit.getWorld(NamespacedKey.minecraft("the_end"));
            world.setDifficulty(org.bukkit.Difficulty.NORMAL);
            for (int x = -9; x <= 9; x++) {
                for (int z = -9; z <= 9; z++) {
                    world.setChunkForceLoaded(x, z, true);
                }
            }
            // Isolate pathfinding from random terrain, far above the original Anchor's height.
            for (int x = 37; x <= 125; x++) {
                for (int z = -3; z <= 3; z++) {
                    world.getBlockAt(x, 150, z).setType(Material.END_STONE);
                    for (int y = 151; y <= 155; y++) {
                        world.getBlockAt(x, y, z).setType(Material.AIR);
                    }
                }
            }
            var nativeServer = ((CraftServer) getServer()).getServer();
            var profile = new GameProfile(UUID.randomUUID(), "RoamingFixture");
            player = new ServerPlayer(nativeServer, ((CraftWorld) world).getHandle(), profile,
                    ClientInformation.createDefault());
            player.snapTo(40.5, 151, 0.5, 0, 0);
            nativeServer.getPlayerList().placeNewPlayer(new Sink(), player,
                    CommonListenerCookie.createInitial(profile, false));
            player.getBukkitEntity().setGameMode(GameMode.SURVIVAL);
            getServer().getScheduler().runTaskTimer(this, this::tick, 1, 1);
        } catch (Throwable error) {
            finish(false, error.toString());
        }
    }

    private void tick() {
        if (done) {
            return;
        }
        try {
            ticks++;
            player.connection.tick();
            var recipient = player.getBukkitEntity();
            World world = recipient.getWorld();
            if (defender == null && ticks >= 100) {
                dragon = world.getEnderDragonBattle().getEnderDragon();
                require(dragon != null && dragon.getPersistentDataContainer()
                        .has(new NamespacedKey("kreiscraft", "crown_original_health")), "production native adoption");
                require(ownedSpawns >= 16 && leakedSpawns == 0, "ownership before spawn callbacks");
                require(recipient.isOnline() && player.connection.hasClientLoaded(), "native player registration");
                require(Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "dragondebug pause"), "pause command");
                dragon.setAI(false); // Isolate defender AI; this probe makes no dragon choreography claim.
                defender = world.getEntitiesByClass(Husk.class).stream().filter(this::owned).findFirst().orElseThrow();
                for (Husk other : List.copyOf(world.getEntitiesByClass(Husk.class))) {
                    if (other != defender && owned(other)) {
                        other.setAI(false);
                    }
                }
                defender.teleport(new Location(world, 46.5, 151, 0.5));
                teleports = 0;
                startX = defender.getX();
                recipient.teleport(new Location(world, 120.5, 151, 0.5));
                require(defender.getAttribute(Attribute.FOLLOW_RANGE).getValue() >= 384,
                        "arena-wide native follow range");
                getLogger().info("DEFENDER native battle=" + dragon.getUniqueId() + " ownedSpawns=" + ownedSpawns);
            }
            if (ticks == 240) {
                require(defender.getX() > startX + 6 && teleports == 0,
                        "distant native pursuit without Anchor teleport");
                getLogger().info("DEFENDER distant pursuit start=" + startX + " end=" + defender.getLocation());
                recipient.teleport(defender.getLocation().clone().add(4, 0, 0));
                recipient.setNoDamageTicks(0);
                recipient.setHealth(20);
            }
            if (deathTick == 0 && ticks > 240 && melee && recipient.getHealth() < 20) {
                verifyAllies(world);
                recipient.setGameMode(GameMode.CREATIVE);
                require(defender.getTarget() == null, "mode transition clears target synchronously");
                defender.setHealth(0);
                deathTick = ticks;
                verifyAllies(world); // Dead causing-entity attribution still protects delayed effects.
            }
            if (deathTick > 0 && ticks >= deathTick + 21) {
                require(world.getEntitiesByClass(Husk.class).stream().filter(Entity::isValid).filter(this::owned)
                        .count() == 15,
                        "accepted native death was not replenished");
                var control = world.spawn(new Location(world, 40, 151, 2), org.bukkit.entity.Enderman.class);
                var survivor =
                        world.getEntitiesByClass(Husk.class).stream().filter(this::owned).findFirst().orElseThrow();
                var production = getServer().getPluginManager().getPlugin("Kreiscraft");
                getServer().getPluginManager().disablePlugin(production);
                require(!defender.isValid() && !survivor.isValid() && !arrow.isValid() && !cloud.isValid()
                        && control.isValid(),
                        "owned cleanup preserves unrelated entities");
                finish(true, "ticks=" + ticks + " spawn=" + ownedSpawns
                        + " native distant movement+attributed melee+allies+mode+cleanup");
            }
        } catch (Throwable error) {
            error.printStackTrace();
            finish(false, error.toString());
        }
    }

    private boolean owned(Entity entity) {
        return entity.getPersistentDataContainer().has(ARTIFACT, PersistentDataType.STRING);
    }

    private void verifyAllies(World world) {
        if (arrow == null) {
            var shot = defender.launchProjectile(org.bukkit.entity.Arrow.class);
            shot.setVelocity(new org.bukkit.util.Vector());
            shot.setGravity(false);
            arrow = shot;
            cloud = world.spawn(defender.getLocation(), org.bukkit.entity.AreaEffectCloud.class,
                    effect -> effect.setSource(defender));
            require(owned(arrow) && owned(cloud), "native projectile/cloud inherit ownership before callbacks");
        }
        var enderman = world.spawn(defender.getLocation().clone().add(0, 0, 2), org.bukkit.entity.Enderman.class);
        var ally = world.getEntitiesByClass(Husk.class).stream().filter(mob -> mob != defender && owned(mob))
                .findFirst().orElseThrow();
        for (LivingEntity target : List.of(enderman, ally, dragon)) {
            double health = target.getHealth();
            target.setNoDamageTicks(0);
            target.damage(5, DamageSource.builder(DamageType.MOB_ATTACK).withDirectEntity(defender)
                    .withCausingEntity(defender).build());
            require(target.getHealth() == health, "native owned ally damage canceled");
            target.damage(5, DamageSource.builder(DamageType.ARROW).withDirectEntity(arrow)
                    .withCausingEntity(defender).build());
            require(target.getHealth() == health, "native projectile ally damage canceled");
            target.damage(5, DamageSource.builder(DamageType.INDIRECT_MAGIC).withDirectEntity(cloud)
                    .withCausingEntity(defender).build());
            require(target.getHealth() == health, "native cloud/delayed ally damage canceled");
        }
        enderman.remove();
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void spawning(CreatureSpawnEvent event) {
        if (event.getEntity() instanceof Husk) {
            if (owned(event.getEntity())) {
                ownedSpawns++;
            } else {
                leakedSpawns++;
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void damaged(EntityDamageEvent event) {
        if (event instanceof EntityDamageByEntityEvent hit && hit.getDamager() == defender && player != null
                && event.getEntity() == player.getBukkitEntity() && !event.isCancelled()) {
            melee = true;
            getLogger().info("DEFENDER melee base=" + event.getDamage() + " final=" + event.getFinalDamage());
        }
    }

    @EventHandler
    public void teleported(EntityTeleportEvent event) {
        if (event.getEntity() == defender) {
            teleports++;
        }
    }

    private void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
        checks++;
    }

    private void finish(boolean passed, String detail) {
        if (!done) {
            done = true;
            getLogger()
                    .info("DEFENDER_ROAMING_PROBE " + (passed ? "PASS " : "FAIL ") + "checks=" + checks + " " + detail);
            Bukkit.shutdown();
        }
    }
}
