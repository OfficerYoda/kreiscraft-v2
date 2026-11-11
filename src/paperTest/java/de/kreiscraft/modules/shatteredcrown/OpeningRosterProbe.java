package de.kreiscraft.modules.shatteredcrown;

import com.mojang.authlib.GameProfile;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.embedded.EmbeddedChannel;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.plugin.java.JavaPlugin;

/** Real build132 AI and physical combat; only the registered player's network transport is synthetic. */
public final class OpeningRosterProbe extends JavaPlugin implements Listener {
    private static final NamespacedKey ARTIFACT = new NamespacedKey("kreiscraft", "crown_artifact");
    private ServerPlayer fixture;
    private EnderDragon dragon;
    private List<Mob> roster;
    private final List<Mob> cases = new ArrayList<>();
    private final Set<UUID> hits = new HashSet<>();
    private Mob active;
    private int ticks;
    private int started;
    private int index;
    private int spawns;
    private int checks;
    private double initialX;
    private boolean moved;
    private boolean damageApplied;
    private boolean done;
    private int recoveryTick;
    private int recoveryStage;
    private Mob escapedRider;

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
        if (!Boolean.getBoolean("kreiscraft.opening-probe")) {
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        getServer().getPluginManager().registerEvents(this, this);
        getServer().getScheduler().runTask(this, this::start);
    }

    private void start() {
        try {
            World world = Bukkit.getWorld(NamespacedKey.minecraft("the_end"));
            world.setDifficulty(org.bukkit.Difficulty.NORMAL);
            world.setGameRule(org.bukkit.GameRule.DO_MOB_SPAWNING, false);
            for (int x = -9; x <= 9; x++) {
                for (int z = -9; z <= 9; z++) {
                    world.setChunkForceLoaded(x, z, true);
                }
            }
            for (int x = 37; x <= 125; x++) {
                for (int z = -12; z <= 12; z++) {
                    world.getBlockAt(x, 150, z).setType(Material.END_STONE);
                    for (int y = 151; y <= 170; y++) {
                        world.getBlockAt(x, y, z).setType(Material.AIR);
                    }
                }
            }
            var server = ((CraftServer) getServer()).getServer();
            var profile = new GameProfile(UUID.randomUUID(), "OpeningFixture");
            fixture = new ServerPlayer(server, ((CraftWorld) world).getHandle(), profile,
                    ClientInformation.createDefault());
            fixture.snapTo(40.5, 151, 0.5, 0, 0);
            server.getPlayerList().placeNewPlayer(new Sink(), fixture,
                    CommonListenerCookie.createInitial(profile, false));
            fixture.getBukkitEntity().setGameMode(GameMode.SURVIVAL);
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
            fixture.connection.tick();
            var player = fixture.getBukkitEntity();
            damageApplied |= !hits.isEmpty() && player.getHealth() < 20;
            player.setHealth(20);
            player.setNoDamageTicks(0);
            if (recoveryTick > 0) {
                if (ticks >= recoveryTick + 21) {
                    var current = player.getWorld().getEntitiesByClass(Mob.class).stream().filter(this::owned).toList();
                    getLogger().info("OPENING recovery original=" + cases.getFirst().getLocation()
                            + " valid=" + cases.getFirst().isValid() + " ownedLives=" + current.size() + " spawns="
                            + spawns);
                    require(current.stream().allMatch(mob -> Math.abs(mob.getX()) < 192),
                            "mounted arena escape recovery");
                    require(current.size() == 12, "escape/unload recovery is not a defeat or extra life");
                    if (recoveryStage == 1) {
                        require(cases.getFirst().isValid() && spawns == 24,
                                "loaded escape retains original lives without replacement");
                        require(escapedRider.getVehicle() == cases.getFirst()
                                || cases.getFirst().addPassenger(escapedRider),
                                "reattach same owned rider without another life");
                        player.getWorld().setChunkForceLoaded(-19, 0, false);
                        require(cases.getFirst().teleport(new Location(player.getWorld(), -300, 151, 0),
                                io.papermc.paper.entity.TeleportFlag.EntityState.RETAIN_PASSENGERS),
                                "native unloaded mounted fixture");
                        recoveryStage = 2;
                        recoveryTick = ticks;
                    } else {
                        require(spawns <= 26, "finite replacement budget for two mounted members");
                        verifyCleanup();
                    }
                }
                return;
            }
            if (roster == null && ticks >= 100) {
                dragon = player.getWorld().getEnderDragonBattle().getEnderDragon();
                require(dragon != null && dragon.getPersistentDataContainer()
                        .has(new NamespacedKey("kreiscraft", "crown_original_health")), "native battle adoption");
                require(fixture.connection.hasClientLoaded() && player.isOnline(), "registered native player");
                require(Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "dragondebug wave 1"), "production wave1");
                dragon.setAI(false); // Isolate defender combat, not dragon-flight evidence.
                roster = player.getWorld().getEntitiesByClass(Mob.class).stream().filter(this::owned).toList();
                require(roster.size() == 12 && spawns == 24, "complete fresh roster and pre-callback ownership");
                verifyRoster();
                roster.forEach(mob -> {
                    mob.setAI(false);
                    mob.setVelocity(new org.bukkit.util.Vector());
                    mob.setGravity(false); // Park inactive calibration groups, not production movement behavior.
                });
                for (EntityType type : List.of(EntityType.ZOMBIE_HORSE, EntityType.SPIDER, EntityType.CAVE_SPIDER)) {
                    cases.add(roster.stream().filter(mob -> mob.getType() == type).findFirst().orElseThrow());
                }
                cases.addAll(roster.stream().filter(mob -> mob.getVehicle() == null
                        && mob.getType() != EntityType.ZOMBIE_HORSE && mob.getType() != EntityType.SPIDER
                        && mob.getType() != EntityType.CAVE_SPIDER)
                        .sorted(java.util.Comparator.<Mob>comparingInt(mob -> chain(mob).size())
                                .thenComparing(mob -> mob.getType().name()))
                        .toList());
                // Frozen calibration groups cannot navigate out of uneven native terrain. Keep their initial
                // native spawn/health assertions above, then park inactive roots/passengers in clear air.
                int parked = 0;
                for (var root : roster.stream().filter(mob -> mob.getVehicle() == null).toList()) {
                    require(root.teleport(
                            new Location(player.getWorld(), 20.5 + parked % 4 * 8, 180, -48.5 + parked / 4 * 8),
                            io.papermc.paper.entity.TeleportFlag.EntityState.RETAIN_PASSENGERS),
                            "park inactive calibration group");
                    parked++;
                }
                begin();
            }
            if (active != null) {
                moved |= active.getX() > initialX + 5;
                if (index >= 3 && moved && ticks - started == 220) {
                    fixture.getBukkitEntity().teleport(active.getLocation().clone().add(6, 0, 0));
                }
                var group = chain(active);
                boolean hit = index < 3 ? hits.contains(group.get(1).getUniqueId())
                        : group.stream().anyMatch(mob -> hits.contains(mob.getUniqueId()));
                if (moved && hit && damageApplied) {
                    require(group.stream().allMatch(Entity::isValid), "living intact group after physical combat");
                    getLogger().info("OPENING combat root=" + active.getType() + " movement="
                            + (active.getX() - initialX) + " chain=" + group.stream().map(Entity::getType).toList()
                            + " attributedHits=" + hits);
                    group.forEach(mob -> mob.setAI(false));
                    // Park frozen passenger poses in clear air, never against the calibration floor.
                    require(active.teleport(new Location(player.getWorld(), 40.5, 180, -9 + index * 3),
                            io.papermc.paper.entity.TeleportFlag.EntityState.RETAIN_PASSENGERS),
                            "park completed calibration group away from projectile path");
                    group.forEach(mob -> {
                        // Disabling gravity does not stop residual movement into the calibration floor.
                        mob.setVelocity(new org.bukkit.util.Vector());
                        mob.setGravity(false);
                    });
                    if (++index == cases.size()) {
                        escapedRider = (Mob) cases.getFirst().getPassengers().getFirst();
                        player.getWorld().setChunkForceLoaded(-19, 0, true);
                        require(cases.getFirst().teleport(new Location(player.getWorld(), -300, 151, 0),
                                io.papermc.paper.entity.TeleportFlag.EntityState.RETAIN_PASSENGERS),
                                "escaped mounted fixture");
                        recoveryStage = 1;
                        recoveryTick = ticks;
                    } else {
                        begin();
                    }
                } else if (ticks - started > 500) {
                    throw new IllegalStateException("native combat timeout root=" + active.getType()
                            + " x=" + active.getX() + " moved=" + moved + " hits=" + hits);
                }
            }
            if (ticks > 3800) {
                throw new IllegalStateException("overall timeout");
            }
        } catch (Throwable error) {
            error.printStackTrace();
            finish(false, error.toString());
        }
    }

    private List<Mob> chain(Mob root) {
        var result = new ArrayList<Mob>();
        Mob current = root;
        while (true) {
            result.add(current);
            if (current.getPassengers().isEmpty()) {
                return result;
            }
            current = (Mob) current.getPassengers().getFirst();
        }
    }

    private void verifyRoster() {
        var roots = roster.stream().filter(mob -> mob.getVehicle() == null).toList();
        require(roots.size() == 4, "exactly four groups per player across all Anchors");
        var represented = roster.stream().map(Entity::getType).collect(java.util.stream.Collectors.toSet());
        require(represented.containsAll(List.of(EntityType.HUSK, EntityType.ZOMBIE, EntityType.DROWNED,
                EntityType.SKELETON, EntityType.STRAY, EntityType.BOGGED)),
                "single-player native undead roster represented");
        int armored = 0;
        for (var mob : roster) {
            require(!mob.getCanPickupItems(), "natural pickup disabled");
            require(mob.getHealth() == 40, "native snapshotted health");
            var armor = java.util.Arrays.stream(mob.getEquipment().getArmorContents())
                    .filter(item -> item != null && !item.getType().isAir()).toList();
            require(armor.size() <= 2 && armor.stream().allMatch(item -> item.getEnchantments().isEmpty()
                    && (item.getType().name().startsWith("LEATHER_")
                            || item.getType().name().startsWith("CHAINMAIL_")
                            || item.getType().name().startsWith("IRON_"))),
                    "native light armor only");
            if (!armor.isEmpty()) {
                armored++;
            }
        }
        require(armored == 3, "3/9 armor-capable undead at35 percent tuning");
        for (var root : roots) {
            var group = chain(root);
            boolean mounted = root.getType() == EntityType.ZOMBIE_HORSE || root.getType() == EntityType.SPIDER
                    || root.getType() == EntityType.CAVE_SPIDER;
            require(mounted ? group.size() == 2 : group.size() == 6,
                    "actual finite native passenger chain");
            if (root.getType() == EntityType.ZOMBIE_HORSE) {
                require(group.get(1).getEquipment().getItemInMainHand().getType() == Material.IRON_SPEAR,
                        "supported build132 spear equipment");
            }
        }
    }

    private void begin() {
        active = cases.get(index);
        getLogger().info("OPENING pre-relocation type=" + active.getType() + " valid=" + active.isValid()
                + " dead=" + active.isDead() + " location=" + active.getLocation());
        require(active.teleport(new Location(active.getWorld(), 46.5, 151, 0.5),
                io.papermc.paper.entity.TeleportFlag.EntityState.RETAIN_PASSENGERS), "mounted native teleport");
        fixture.getBukkitEntity().teleport(new Location(active.getWorld(), 90.5, 151, 0.5));
        chain(active).forEach(mob -> {
            mob.setGravity(true);
            mob.setAI(true);
        });
        initialX = active.getX();
        hits.clear();
        moved = false;
        damageApplied = false;
        started = ticks;
        getLogger().info(
                "OPENING begin " + active.getType() + " chain=" + chain(active).stream().map(Entity::getType).toList());
    }

    private void verifyCleanup() {
        var unrelated =
                active.getWorld().spawn(new Location(active.getWorld(), 40, 151, 8), org.bukkit.entity.Enderman.class);
        getServer().getPluginManager().disablePlugin(getServer().getPluginManager().getPlugin("Kreiscraft"));
        require(roster.stream().noneMatch(Entity::isValid) && unrelated.isValid(), "complete scoped cleanup");
        require(active.getWorld().getEntities().stream().noneMatch(this::owned), "no surviving owned effects");
        finish(true, "checks=" + checks + " ticks=" + ticks + " mounted+physicalCombat+chain+cleanup");
    }

    private boolean owned(Entity entity) {
        return entity.getPersistentDataContainer().has(ARTIFACT);
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void spawned(CreatureSpawnEvent event) {
        if (event.getSpawnReason() == CreatureSpawnEvent.SpawnReason.CUSTOM
                && event.getEntityType() != EntityType.ENDERMAN && event.getEntityType() != EntityType.ENDER_DRAGON) {
            require(owned(event.getEntity()), "ownership before NORMAL spawn callback " + event.getEntity().getType());
            spawns++;
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void died(org.bukkit.event.entity.EntityDeathEvent event) {
        if (roster != null && roster.contains(event.getEntity()) && !done) {
            var damage = event.getEntity().getLastDamageCause();
            finish(false, "unexpected calibration member death " + event.getEntityType() + " cause="
                    + (damage == null ? "unknown" : damage.getCause()) + " location="
                    + event.getEntity().getLocation());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void launched(org.bukkit.event.entity.ProjectileLaunchEvent event) {
        if (active != null && event.getEntity().getShooter() instanceof Mob shooter
                && chain(active).contains(shooter)) {
            getLogger().info("OPENING native launch " + shooter.getType() + " source=" + shooter.getLocation()
                    + " velocity=" + event.getEntity().getVelocity());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void collided(org.bukkit.event.entity.ProjectileHitEvent event) {
        if (active != null && event.getEntity().getShooter() instanceof Mob shooter
                && chain(active).contains(shooter)) {
            getLogger().info("OPENING native collision " + shooter.getType() + " target=" + event.getHitEntity()
                    + " block=" + event.getHitBlock());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void damaged(EntityDamageByEntityEvent event) {
        if (fixture != null && event.getEntity() == fixture.getBukkitEntity() && active != null) {
            Entity source = event.getDamager();
            if (source instanceof Projectile shot && shot.getShooter() instanceof Entity shooter) {
                source = shooter;
                require(owned(shot), "physical native arrow owned");
            }
            if (chain(active).contains(source) && event.getFinalDamage() > 0) {
                hits.add(source.getUniqueId());
                getLogger().info("OPENING actual damage " + source.getType() + " cause=" + event.getCause()
                        + " final=" + event.getFinalDamage());
            }
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
            getLogger().info("OPENING_ROSTER_PROBE " + (passed ? "PASS " : "FAIL ") + detail);
            Bukkit.shutdown();
        }
    }
}
