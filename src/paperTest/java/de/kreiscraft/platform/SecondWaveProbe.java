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
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Mob;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

/** Real registered players, native seven-species AI/damage on unmodified main-End ground; synthetic transport only. */
public final class SecondWaveProbe extends JavaPlugin implements Listener {
    private static final NamespacedKey ARTIFACT = new NamespacedKey("kreiscraft", "crown_artifact");
    private static final List<EntityType> SPECIES = List.of(EntityType.GUARDIAN, EntityType.RAVAGER,
            EntityType.PIGLIN_BRUTE, EntityType.VINDICATOR, EntityType.BLAZE, EntityType.BREEZE, EntityType.VEX);
    private ServerPlayer fixture;
    private World end;
    private EnderDragon dragon;
    private Mob active;
    private Location origin;
    private int ticks;
    private int started;
    private int species;
    private int checks;
    private boolean finished;
    private boolean hit;
    private double healthBefore;
    private double hpLoss;
    private boolean moved;
    private boolean positioned;
    private final java.util.ArrayList<ServerPlayer> observers = new java.util.ArrayList<>();
    private int scaledAt;
    private int diedAt;
    private boolean veto;
    private boolean spawningWave;
    private int rootCallbacks;
    private int leakedRoots;
    private Entity latestShot;

    @Override
    public void onEnable() {
        if (!Boolean.getBoolean("kreiscraft.second-wave-probe")) {
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        getServer().getPluginManager().registerEvents(this, this);
        getServer().getScheduler().runTask(this, this::start);
    }

    private void start() {
        try {
            end = Bukkit.getWorld(NamespacedKey.minecraft("the_end"));
            end.setDifficulty(org.bukkit.Difficulty.NORMAL);
            // Isolate roster AI from random Enderman gaze/crowding without altering terrain or native attacks.
            end.setGameRule(org.bukkit.GameRule.DO_MOB_SPAWNING, false);
            end.setGameRule(org.bukkit.GameRule.NATURAL_REGENERATION, false);
            for (int x = -9; x <= 9; x++) {
                for (int z = -9; z <= 9; z++) {
                    end.setChunkForceLoaded(x, z, true);
                }
            }
            var server = ((CraftServer) getServer()).getServer();
            var profile = new GameProfile(UUID.randomUUID(), "SixSpecies");
            fixture = new ServerPlayer(server, ((CraftWorld) end).getHandle(), profile,
                    ClientInformation.createDefault());
            var ground = ground(42, 20);
            fixture.snapTo(ground.getX(), ground.getY(), ground.getZ(), 0, 0);
            server.getPlayerList().placeNewPlayer(new Sink(), fixture,
                    CommonListenerCookie.createInitial(profile, false));
            fixture.getBukkitEntity().setGameMode(GameMode.SURVIVAL);
            fixture.getBukkitEntity().getAttribute(Attribute.MAX_HEALTH).setBaseValue(1000);
            fixture.getBukkitEntity().setHealth(1000);
            getServer().getScheduler().runTaskTimer(this, this::tick, 1, 1);
        } catch (Throwable error) {
            finish(false, error.toString());
        }
    }

    private Location ground(int x, int z) {
        var block = end.getHighestBlockAt(x, z);
        require(block.getType() == org.bukkit.Material.END_STONE && block.getY() < 90, "natural End footing");
        return block.getLocation().add(0.5, 1, 0.5);
    }

    private void tick() {
        if (finished) {
            return;
        }
        try {
            ticks++;
            fixture.connection.tick();
            observers.forEach(observer -> observer.connection.tick());
            var player = fixture.getBukkitEntity();
            if (scaledAt > 0) {
                if (diedAt > 0 && ticks - diedAt >= 21) {
                    require(defenders().size() == 71, "native accepted death was not replenished");
                    var unrelated = end.spawn(ground(42, 20), org.bukkit.entity.Guardian.class);
                    getServer().getPluginManager()
                            .disablePlugin(getServer().getPluginManager().getPlugin("Kreiscraft"));
                    require(defenders().isEmpty() && unrelated.isValid(), "scoped owned cleanup");
                    finish(true,
                            "six native movement+targeting+attributed attacks+policies+population8+recovery ticks="
                                    + ticks);
                } else if (diedAt == 0 && ticks - scaledAt >= 21) {
                    require(defenders().size() == 72, "native departures did not resize frozen wave");
                    require(defenders().stream().filter(mob -> mob.getType() == EntityType.GUARDIAN).count() == 8,
                            "native missing Guardian replaced, not defeated or duplicated");
                    require(defenders().stream().filter(mob -> mob.getType() == EntityType.GUARDIAN)
                            .allMatch(mob -> mob.getAttribute(Attribute.MAX_HEALTH).getBaseValue() == 30),
                            "replacement tuning");
                    defenders().stream().filter(mob -> mob.getType() == EntityType.BREEZE).findFirst().orElseThrow()
                            .setHealth(0);
                    diedAt = ticks;
                }
                return;
            }
            if (active == null) {
                if (ticks < 100) {
                    return;
                }
                dragon = end.getEnderDragonBattle().getEnderDragon();
                require(dragon != null && dragon.getPersistentDataContainer()
                        .has(new NamespacedKey("kreiscraft", "crown_original_health")), "native battle adopted");
                require(player.isOnline() && fixture.connection.hasClientLoaded(), "registered loaded native player");
                command("wave 2");
                dragon.setAI(false); // Isolate roster AI, never claim native dragon choreography.
                require(defenders().size() == 9, "single-player debug baseline exactly9 defenders");
                require(rootCallbacks == 9 && leakedRoots == 0,
                        "owned roots initialized before NORMAL spawn callbacks");
                require(end.getEntitiesByClass(org.bukkit.entity.EnderCrystal.class).stream().filter(this::owned)
                        .count() == 4, "four production Anchors");
                nextSpecies();
            }
            int elapsed = ticks - started;
            moved |= active.getLocation().distanceSquared(origin) > 1;
            if (elapsed == 120) {
                require(active.getTarget() == player, "native eligible target " + active.getType());
                getLogger().info("SECOND_WAVE movement species=" + active.getType() + " from=" + origin
                        + " to=" + active.getLocation() + " target=" + player.getLocation());
                var nativeMob = ((org.bukkit.craftbukkit.entity.CraftMob) active).getHandle();
                getLogger().info("SECOND_WAVE navigation=" + nativeMob.getNavigation().getPath() + " goals="
                        + nativeMob.getGoalSelector().getAvailableGoals().stream()
                                .map(goal -> goal.getGoal().getClass().getName() + ":" + goal.isRunning()).toList());
                if (active.getType() == EntityType.GUARDIAN) {
                    require(active.getX() > origin.getX() + 4, "dry Guardian directed land pursuit");
                    require(!active.isInWater(), "Guardian out of water");
                }
            }
            if (elapsed >= 120 && moved && !positioned) {
                player.teleport(ground((int) active.getX() + (active.getType() == EntityType.RAVAGER ? 4 : 8),
                        (int) active.getZ()));
                player.setNoDamageTicks(0);
                positioned = true;
            }
            if (elapsed > 120 && hit && hpLoss > 0 && moved) {
                require(player.getHealth() < healthBefore, "actual attributed HP loss " + active.getType());
                getLogger().info("SECOND_WAVE combat species=" + active.getType() + " ticks=" + elapsed
                        + " attributedEventHp=" + hpLoss + " actualHpDelta=" + (healthBefore - player.getHealth())
                        + " location=" + active.getLocation());
                active.setAI(false);
                verifyPolicies();
                if (++species == SPECIES.size()) {
                    verifyScaling();
                } else {
                    nextSpecies();
                }
            }
            if (elapsed > 500) {
                throw new IllegalStateException("native combat timeout " + active.getType() + " target="
                        + active.getTarget() + " moved=" + moved + " hit=" + hit + " hpLoss=" + hpLoss);
            }
        } catch (Throwable error) {
            error.printStackTrace();
            finish(false, error.toString());
        }
    }

    private void nextSpecies() {
        for (Mob mob : defenders()) {
            mob.setAI(false);
            // Keep earlier combatants from physically screening later projectile collisions; no roster deaths.
            mob.teleport(ground(-42, -20));
        }
        active = defenders().stream().filter(mob -> mob.getType() == SPECIES.get(species)).findFirst().orElseThrow();
        origin = ground(42, 20);
        active.teleport(origin);
        active.setAI(true);
        var player = fixture.getBukkitEntity();
        player.teleport(ground(active.getType() == EntityType.GUARDIAN ? 90 : 64, 20));
        player.setFireTicks(0);
        player.setHealth(1000);
        player.setNoDamageTicks(0);
        healthBefore = player.getHealth();
        hit = false;
        hpLoss = 0;
        moved = false;
        positioned = false;
        started = ticks;
    }

    private void verifyPolicies() {
        var control = end.spawn(ground(-40, -20), org.bukkit.entity.Enderman.class);
        control.setAI(false);
        var ally = defenders().stream().filter(mob -> mob != active).findFirst().orElseThrow();
        var type = switch (active.getType()) {
            case GUARDIAN -> org.bukkit.damage.DamageType.INDIRECT_MAGIC;
            case BLAZE -> org.bukkit.damage.DamageType.FIREBALL;
            case BREEZE -> org.bukkit.damage.DamageType.WIND_CHARGE;
            default -> org.bukkit.damage.DamageType.MOB_ATTACK;
        };
        Entity direct = active instanceof org.bukkit.entity.Blaze || active instanceof org.bukkit.entity.Breeze
                ? latestShot
                : active;
        var source =
                org.bukkit.damage.DamageSource.builder(type).withDirectEntity(direct).withCausingEntity(active).build();
        for (org.bukkit.entity.LivingEntity target : List.of(control, ally, dragon)) {
            double health = target.getHealth();
            target.setNoDamageTicks(0);
            target.damage(5, source);
            require(target.getHealth() == health, "native allied HP unchanged " + active.getType());
        }
        var crystal = end.getEntitiesByClass(org.bukkit.entity.EnderCrystal.class).stream().filter(this::owned)
                .findFirst().orElseThrow();
        var nativeMob = ((org.bukkit.craftbukkit.entity.CraftMob) active).getHandle();
        ((org.bukkit.craftbukkit.entity.CraftEnderCrystal) crystal).getHandle().hurtServer(
                ((CraftWorld) end).getHandle(), nativeMob.damageSources().mobAttack(nativeMob), 100);
        require(crystal.isValid() && !crystal.isGlowing(), "native owned attacks cannot destroy defended objectives");
        var player = fixture.getBukkitEntity();
        var position = player.getLocation();
        player.teleport(new Location(end, 100, 50, 0));
        double health = player.getHealth();
        player.setNoDamageTicks(0);
        player.damage(5, source);
        require(player.getHealth() == health && active.getTarget() == null,
                "native arrival protection and target drop");
        player.teleport(position);
        veto = true;
        player.setNoDamageTicks(0);
        player.damage(5, source);
        veto = false;
        require(player.getHealth() == health, "native public combat cancellation retained");
        control.remove();
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void veto(EntityDamageEvent event) {
        if (veto && event.getEntity() == fixture.getBukkitEntity()) {
            event.setCancelled(true);
        }
    }

    private void verifyScaling() {
        var server = ((CraftServer) getServer()).getServer();
        for (int index = 1; index <= 7; index++) {
            var profile = new GameProfile(UUID.randomUUID(), "SixObserver" + index);
            var observer = new ServerPlayer(server, ((CraftWorld) end).getHandle(), profile,
                    ClientInformation.createDefault());
            observer.snapTo(42.5, 61, 20.5, 0, 0);
            server.getPlayerList().placeNewPlayer(new Sink(), observer,
                    CommonListenerCookie.createInitial(profile, false));
            observer.getBukkitEntity().setGameMode(index % 2 == 0 ? GameMode.CREATIVE : GameMode.SPECTATOR);
            observers.add(observer);
        }
        require(end.getPlayers().size() == 8 && Bukkit.getOnlinePlayers().size() == 8, "native all-End population8");
        var old = defenders();
        var oldEffects = end.getEntitiesByClass(org.bukkit.entity.Projectile.class).stream()
                .filter(effect -> effect.getPersistentDataContainer().has(ARTIFACT)).toList();
        command("wave 2");
        require(old.stream().noneMatch(Entity::isValid), "native debug replacement removes old seven-species roots");
        require(oldEffects.stream().noneMatch(Entity::isValid),
                "native debug replacement removes attributed projectiles");
        require(rootCallbacks == 81 && leakedRoots == 0, "72 scaled roots owned before public callbacks");
        require(defenders().size() == 72, "native population8 repeats complete seven-species units");
        for (EntityType type : SPECIES) {
            require(defenders().stream().filter(mob -> mob.getType() == type)
                    .count() == (type == EntityType.PIGLIN_BRUTE || type == EntityType.VEX ? 16 : 8),
                    "native species count " + type);
        }
        observers.forEach(observer -> observer.getBukkitEntity().teleport(
                new Location(getServer().getWorlds().getFirst(), 0, 150, 0)));
        defenders().stream().filter(mob -> mob.getType() == EntityType.GUARDIAN).findFirst().orElseThrow().remove();
        require(defenders().size() == 71, "native missing entity is not an accepted death");
        scaledAt = ticks;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void damage(EntityDamageEvent event) {
        if (fixture != null && event.getEntity() == fixture.getBukkitEntity()) {
            getLogger().info("SECOND_WAVE observedPlayerDamage source="
                    + event.getDamageSource().getDamageType().getKey()
                    + " causing=" + event.getDamageSource().getCausingEntity() + " canceled=" + event.isCancelled());
        }
        if (fixture != null && event.getEntity() == fixture.getBukkitEntity() && !event.isCancelled()
                && event.getDamageSource().getCausingEntity() == active && event.getFinalDamage() > 0) {
            hit = true;
            hpLoss += event.getFinalDamage();
            getLogger().info("SECOND_WAVE nativeDamage species=" + active.getType() + " source="
                    + event.getDamageSource().getDamageType().getKey() + " direct="
                    + event.getDamageSource().getDirectEntity().getType() + " base=" + event.getDamage()
                    + " final=" + event.getFinalDamage() + " tick=" + ticks);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void launched(org.bukkit.event.entity.ProjectileLaunchEvent event) {
        if (event.getEntity().getShooter() == active) {
            latestShot = event.getEntity();
            require(event.getEntity().getPersistentDataContainer().has(ARTIFACT),
                    "native projectile ownership before callback");
            getLogger().info(
                    "SECOND_WAVE launch species=" + active.getType() + " projectile=" + event.getEntity().getType()
                            + " owned=" + event.getEntity().getPersistentDataContainer().has(ARTIFACT)
                            + " canceled=" + event.isCancelled());
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void spawned(org.bukkit.event.entity.CreatureSpawnEvent event) {
        if (spawningWave && SPECIES.contains(event.getEntityType())) {
            rootCallbacks++;
            if (!event.getEntity().getPersistentDataContainer().has(ARTIFACT)
                    || event.getEntity().getAttribute(Attribute.MAX_HEALTH).getBaseValue() != event.getEntity()
                            .getAttribute(Attribute.MAX_HEALTH).getDefaultValue()) {
                leakedRoots++;
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void collided(org.bukkit.event.entity.ProjectileHitEvent event) {
        if (event.getEntity().getShooter() == active) {
            getLogger().info("SECOND_WAVE collision species=" + active.getType() + " entity=" + event.getHitEntity()
                    + " block=" + event.getHitBlock() + " canceled=" + event.isCancelled());
        }
    }

    private List<Mob> defenders() {
        return end.getEntitiesByClass(Mob.class).stream().filter(this::owned).toList();
    }

    private boolean owned(Entity entity) {
        return entity.isValid() && entity.getPersistentDataContainer().has(ARTIFACT, PersistentDataType.STRING);
    }

    private void command(String text) {
        spawningWave = text.startsWith("wave ");
        try {
            require(Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "dragondebug " + text), "command " + text);
        } finally {
            spawningWave = false;
        }
    }

    private void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
        checks++;
    }

    private void finish(boolean passed, String detail) {
        if (!finished) {
            finished = true;
            getLogger().info("SECOND_WAVE_PROBE " + (passed ? "PASS " : "FAIL ") + "checks=" + checks + " " + detail);
            Bukkit.shutdown();
        }
    }

    private static final class Sink extends Connection {
        private PacketListener listener;

        private Sink() {
            super(PacketFlow.SERVERBOUND);
            channel = new EmbeddedChannel();
            address = new InetSocketAddress("127.0.0.1", 0);
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
}
