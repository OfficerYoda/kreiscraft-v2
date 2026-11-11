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
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.entity.CraftEnderCrystal;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Warden;
import org.bukkit.entity.Wither;
import org.bukkit.entity.WitherSkeleton;
import org.bukkit.entity.WitherSkull;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

/** Actual production modules, native battle/registered players/AI/effects; only transport is synthetic. */
public final class FinalWaveProbe extends JavaPlugin implements Listener {
    private static final NamespacedKey ARTIFACT = new NamespacedKey("kreiscraft", "crown_artifact");
    private final List<ServerPlayer> players = new ArrayList<>();
    private final List<Sink> sinks = new ArrayList<>();
    private final List<WitherSkull> skulls = new ArrayList<>();
    private World end;
    private EnderDragon dragon;
    private Wither wither;
    private Warden warden;
    private LivingEntity enderman;
    private EnderCrystal home;
    private int ticks;
    private int stage;
    private int stageTick;
    private int checks;
    private int blasts;
    private int minions;
    private int sonic;
    private int skullHits;
    private int leaked;
    private boolean done;
    private double wardenStart;
    private List<Entity> replaced;
    private List<Mob> homeBosses;
    private Wither activeWither;
    private Entity ordinaryStar;
    private boolean controlSpawn;
    private UUID unloadedWarden;
    private boolean nativeUnloaded;
    private int unloadTick;
    private boolean allyShotStarted;
    private boolean allySkullHit;
    private int arrivalVetoes;
    private Warden idleWarden;
    private int retaliationTick;
    private double wardenAllyHealth;

    @Override
    public void onEnable() {
        if (!Boolean.getBoolean("kreiscraft.final-wave-probe")) {
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
            for (int x = -9; x <= 9; x++) {
                for (int z = -9; z <= 9; z++) {
                    end.setChunkForceLoaded(x, z, true);
                }
            }
            for (int x = 35; x <= 130; x++) {
                for (int z = -8; z <= 8; z++) {
                    end.getBlockAt(x, 150, z).setType(Material.END_STONE);
                    for (int y = 151; y <= 157; y++) {
                        end.getBlockAt(x, y, z).setType(Material.AIR);
                    }
                }
            }
            for (int index = 0; index < 5; index++) {
                register(getServer().getWorlds().getFirst(), index);
            }
            players.getFirst().getBukkitEntity().teleport(new Location(end, 40.5, 151, 0.5));
            getServer().getScheduler().runTaskTimer(this, this::tick, 1, 1);
        } catch (Throwable error) {
            finish(false, error);
        }
    }

    private void register(World world, int index) {
        var server = ((CraftServer) getServer()).getServer();
        var profile = new GameProfile(UUID.randomUUID(), "FinalWave" + index);
        var player = new ServerPlayer(server, ((CraftWorld) world).getHandle(), profile,
                ClientInformation.createDefault());
        player.snapTo(40.5, 151, 0.5, 0, 0);
        var sink = new Sink();
        sinks.add(sink);
        server.getPlayerList().placeNewPlayer(sink, player, CommonListenerCookie.createInitial(profile, false));
        player.getBukkitEntity().setGameMode(index == 0 ? GameMode.SURVIVAL
                : index % 2 == 0 ? GameMode.CREATIVE : GameMode.SPECTATOR);
        players.add(player);
    }

    private void require(boolean condition, String message) {
        checks++;
        if (!condition) {
            throw new IllegalStateException(message + " stage=" + stage + " tick=" + ticks);
        }
    }

    private boolean owned(Entity entity) {
        return entity.getPersistentDataContainer().has(ARTIFACT, PersistentDataType.STRING);
    }

    private List<Mob> defenders() {
        return end.getEntitiesByClass(Mob.class).stream().filter(this::owned).filter(Entity::isValid).toList();
    }

    private List<EnderCrystal> anchors() {
        return end.getEntitiesByClass(EnderCrystal.class).stream().filter(this::owned).toList();
    }

    private void command(String command) {
        require(Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "dragondebug " + command), command);
    }

    private void next() {
        stage++;
        stageTick = ticks;
    }

    private void tick() {
        if (done) {
            return;
        }
        try {
            ticks++;
            players.forEach(player -> player.connection.tick());
            if (ticks >= 3600) {
                throw new IllegalStateException("bounded timeout stage=" + stage);
            }
            var player = players.getFirst().getBukkitEntity();
            if (stage == 4 && ticks - stageTick >= 500 || stage == 5 && ticks - stageTick >= 300) {
                throw new IllegalStateException("native attack timeout stage=" + stage + " skulls=" + skulls.size());
            }
            if (stage == 0) {
                dragon = end.getEnderDragonBattle().getEnderDragon();
                if (dragon == null || !dragon.getPersistentDataContainer()
                        .has(new NamespacedKey("kreiscraft", "crown_original_health"))
                        || !players.getFirst().connection.hasClientLoaded()) {
                    return;
                }
                command("pause");
                dragon.setAI(false); // Isolate final roster/effects, not dragon choreography.
                for (var observer : players.subList(1, 5)) {
                    observer.getBukkitEntity().teleport(new Location(end, 250, 151, 0.5));
                }
                command("wave 3");
                require(anchors().size() == 4 && defenders().size() == 16, "population5 two pairs EACH Anchor");
                require(end.getEntitiesByClass(Warden.class).size() == 8
                        && end.getEntitiesByClass(Wither.class).size() == 8, "native species counts");
                wither = end.getEntitiesByClass(Wither.class).iterator().next();
                home = anchors().stream().min(java.util.Comparator.comparingDouble(
                        crystal -> crystal.getLocation().distanceSquared(wither.getLocation()))).orElseThrow();
                homeBosses = defenders().stream()
                        .filter(mob -> mob.getLocation().distanceSquared(home.getLocation()) < 25).toList();
                require(homeBosses.size() == 4, "two original pairs registered at selected Anchor");
                warden = end.getEntitiesByClass(Warden.class).stream().filter(mob -> !homeBosses.contains(mob))
                        .findFirst().orElseThrow();
                require(wither.getHealth() == 600 && wither.getAttribute(Attribute.MAX_HEALTH).getValue() == 600
                        && wither.getAttribute(Attribute.ATTACK_DAMAGE).getValue() == 40
                        && wither.getAttribute(Attribute.ARMOR).getValue() == 8, "NORMAL enhanced attributes");
                require(wither.getInvulnerableTicks() > 0, "native spawn charge armed");
                require(defenders().stream().allMatch(mob -> !mob.getRemoveWhenFarAway()
                        && mob.getAttribute(Attribute.FOLLOW_RANGE).getValue() >= 384), "persistent arena range");
                defenders().forEach(mob -> mob.setAI(false));
                wither.setAI(true);
                wither.teleport(new Location(end, 48.5, 151, 0.5));
                warden.teleport(new Location(end, 44.5, 151, 5.5));
                enderman = end.spawn(new Location(end, 48.5, 151, 3.5), org.bukkit.entity.Enderman.class);
                ((Mob) enderman).setAI(false);
                player.setInvulnerable(true); // Only blast isolation; removed for actual native attacks below.
                next();
            } else if (stage == 1 && blasts > 0) {
                require(enderman.getHealth() == 40 && warden.getHealth() == 500 && dragon.getHealth() == 300,
                        "real spawn blast protects Enderman/Warden/dragon");
                require(leaked == 0, "all ownership visible before NORMAL native callbacks");
                getLogger().info("FINAL_WAVE spawnBlast count=" + blasts + " power20 attrs600/40/8");
                wither.setAI(false);
                wither.setInvulnerableTicks(0);
                wither.setHealth(301);
                wither.setNoDamageTicks(0);
                wither.damage(10, DamageSource.builder(DamageType.PLAYER_ATTACK).withDirectEntity(player)
                        .withCausingEntity(player).build());
                require(minions == 4 && end.getEntitiesByClass(WitherSkeleton.class).stream().filter(this::owned)
                        .count() == 4, "real damage crosses threshold and spawns four pre-owned minions");
                require(blasts == 2, "one enhanced half-health blast");
                require(!home.isGlowing(), "original gate retains descendants after roaming");
                end.getEntitiesByClass(WitherSkeleton.class).forEach(mob -> mob.setAI(false));
                homeBosses.stream().filter(mob -> mob != wither).forEach(mob -> mob.setHealth(0));
                wither.setHealth(0);
                require(end.getEntitiesByClass(org.bukkit.entity.Item.class).stream()
                        .noneMatch(item -> item.getItemStack().getType() == Material.NETHER_STAR),
                        "native death no stars");
                require(!home.isGlowing(), "parent death does not unlock original gate");
                require(!sinks.getFirst().countdowns.isEmpty(), "native countdown UI packets shown");
                ((CraftEnderCrystal) home).getHandle().hurtServer(((CraftWorld) end).getHandle(),
                        players.getFirst().damageSources().playerAttack(players.getFirst()), 100);
                require(home.isValid(), "real original crystal attack rejected while only minions remain");
                next();
            } else if (stage == 2 && ticks - stageTick >= 220) {
                require(blasts == 3, "real delayed death blast exactly once after countdown");
                require(enderman.getHealth() == 40 && warden.getHealth() == 500 && dragon.getHealth() == 300,
                        "dead parent attributed blast protects allies");
                require(end.getEntitiesByClass(org.bukkit.entity.Item.class).stream()
                        .noneMatch(item -> item.getItemStack().getType() == Material.NETHER_STAR),
                        "no delayed star re-drop");
                getLogger().info("FINAL_WAVE deadParentBlast count=" + blasts + " minions=" + minions);
                require(sinks.getFirst().countdowns.isEmpty() && sinks.getFirst().progressUpdates >= 10,
                        "native countdown advances then hides on natural expiry");
                end.getEntitiesByClass(WitherSkeleton.class).forEach(mob -> mob.setHealth(0));
                require(home.isGlowing(), "minion deaths expose their original floating Anchor");
                ((CraftEnderCrystal) home).getHandle().hurtServer(((CraftWorld) end).getHandle(),
                        players.getFirst().damageSources().playerAttack(players.getFirst()), 100);
                require(!home.isValid() && anchors().size() == 3,
                        "native player destroys only original exposed Anchor");
                // Rebuild only the disposable AI calibration pad destroyed by the verified native blasts.
                for (int x = 35; x <= 130; x++) {
                    for (int z = -8; z <= 8; z++) {
                        end.getBlockAt(x, 150, z).setType(Material.END_STONE);
                        for (int y = 151; y <= 157; y++) {
                            end.getBlockAt(x, y, z).setType(Material.AIR);
                        }
                    }
                }
                // Actual native Warden pursuit/sonic against a loaded Survival player.
                warden.teleport(new Location(end, 65.5, 151, 0.5));
                wardenStart = warden.getX();
                player.teleport(new Location(end, 110.5, 151, 0.5));
                player.setInvulnerable(false);
                player.setHealth(20);
                warden.setAI(true);
                next();
            } else if (stage == 3 && ticks - stageTick >= 40 && warden.getX() > wardenStart + 8) {
                require(warden.getX() > wardenStart + 5 && warden.getTarget() == player,
                        "native Warden distant pursuit, not cosmetic setTarget: " + warden.getLocation()
                                + " target=" + warden.getTarget() + " anger=" + warden.getAngerLevel());
                getLogger()
                        .info("FINAL_WAVE Warden native pursuit start=" + wardenStart + " end=" + warden.getLocation());
                require(anchors().size() == 3 && anchors().stream().noneMatch(Entity::isGlowing),
                        "roaming never defeats other assigned defenders");
                player.teleport(warden.getLocation().clone().add(10, 0, 0));
                player.setNoDamageTicks(0);
                player.setHealth(20);
                // Prevent melee reach while retaining a genuine native sonic attack.
                for (int x = player.getLocation().getBlockX() - 1; x <= player.getLocation().getBlockX() + 1; x++) {
                    for (int z = -1; z <= 1; z++) {
                        end.getBlockAt(x, 160, z).setType(Material.END_STONE);
                    }
                }
                player.teleport(player.getLocation().clone().add(0, 10, 0));
                next();
            } else if (stage == 4 && sonic > 0) {
                require(player.getHealth() < 20, "genuine native sonic damage against Survival fixture");
                if (retaliationTick == 0) {
                    player.setGameMode(GameMode.CREATIVE);
                    require(warden.getTarget() == null, "native brain target cleared on mode transition");
                    warden.setNoDamageTicks(0);
                    warden.damage(1, DamageSource.builder(DamageType.MOB_ATTACK).withDirectEntity(enderman)
                            .withCausingEntity(enderman).build());
                    wardenAllyHealth = warden.getHealth();
                    retaliationTick = ticks;
                    return;
                }
                require(warden.getTarget() == null, "native Warden retaliation cannot retain an allied brain target");
                for (LivingEntity ally : List.of(enderman, dragon, defenders().getLast())) {
                    double health = ally.getHealth();
                    ally.setNoDamageTicks(0);
                    ally.damage(5, DamageSource.builder(DamageType.SONIC_BOOM).withDirectEntity(warden)
                            .withCausingEntity(warden).build());
                    require(ally.getHealth() == health, "owned native sonic attribution protects ally");
                }
                player.setGameMode(GameMode.SURVIVAL);
                player.setHealth(20);
                warden.setAI(false);
                // Native skull physical collision, including shooter attribution and cleanup.
                var survivor = end.getEntitiesByClass(Wither.class).stream().filter(Entity::isValid).findFirst()
                        .orElseThrow();
                activeWither = survivor;
                survivor.setInvulnerableTicks(0);
                survivor.teleport(new Location(end, 95.5, 151, 0.5));
                player.teleport(new Location(end, 105.5, 151, 0.5));
                player.setNoDamageTicks(0);
                survivor.setAI(true); // Genuine native targeting and ranged AI.
                enderman.teleport(new Location(end, 95.5, 151, 3.5));
                next();
            } else if (stage == 5 && skullHits > 0 && (!allyShotStarted || allySkullHit)) {
                require(!skulls.isEmpty() && skulls.stream().allMatch(this::owned), "native skull ownership");
                require(player.getHealth() < 20, "physical native skull impact damages eligible player");
                require(activeWither.getTarget() == player, "native Wither eligible center target");
                for (Wither.Head head : Wither.Head.values()) {
                    require(activeWither.getTarget(head) == null || activeWither.getTarget(head) == player,
                            "native Wither head rejects Enderman/allied targets " + head);
                }
                activeWither.setAI(false);
                if (!allyShotStarted) {
                    allyShotStarted = true;
                    activeWither.rangedAttack(warden, 1); // Controlled physical ally collision, not a target-policy claim.
                    return;
                }
                require(warden.getHealth() == wardenAllyHealth,
                        "physical owned skull collision cannot hurt allied Warden");
                player.teleport(new Location(end, 100, 50, 0));
                var survivor = end.getEntitiesByClass(Wither.class).stream().filter(Entity::isValid).findFirst()
                        .orElseThrow();
                double health = player.getHealth();
                require(!players.getFirst().isChangingDimension() && players.getFirst().connection.hasClientLoaded(),
                        "arrival damage fixture is native-loaded, not dimension-change invulnerable");
                player.setNoDamageTicks(0);
                player.damage(5, DamageSource.builder(DamageType.EXPLOSION).withDirectEntity(survivor)
                        .withCausingEntity(survivor).build());
                require(player.getHealth() == health, "attributed blast arrival protection");
                player.damage(5, DamageSource.builder(DamageType.SONIC_BOOM).withDirectEntity(warden)
                        .withCausingEntity(warden).build());
                player.damage(5, DamageSource.builder(DamageType.WITHER_SKULL).withDirectEntity(skulls.getFirst())
                        .withCausingEntity(survivor).build());
                require(player.getHealth() == health && arrivalVetoes >= 3,
                        "native sonic/skull/blast events explicitly vetoed at arrival");
                player.teleport(survivor.getLocation().clone().add(2, 0, 0));
                var pendingSkull = survivor.launchProjectile(WitherSkull.class);
                pendingSkull.setDirection(new org.bukkit.util.Vector());
                require(pendingSkull.isValid() && owned(pendingSkull),
                        "live pre-owned native skull before replacement");
                survivor.setHealth(0); // Queue another real death countdown, then replace it.
                require(!sinks.getFirst().countdowns.isEmpty(), "replacement has queued native countdown UI");
                replaced = new ArrayList<>(defenders());
                replaced.addAll(skulls);
                command("wave 1");
                require(replaced.stream().noneMatch(Entity::isValid), "debug cleanup roots/minions/skulls");
                require(sinks.getFirst().countdowns.isEmpty(), "debug hides countdown UI synchronously");
                next();
            } else if (stage == 6 && ticks - stageTick >= 240) {
                require(blasts == 3, "old queued countdown inert beyond10s into replacement wave");
                command("wave 3");
                defenders().forEach(mob -> mob.setAI(false));
                var missing = end.getEntitiesByClass(Warden.class).iterator().next();
                unloadedWarden = missing.getUniqueId();
                players.subList(1, 5).forEach(observer -> observer.getBukkitEntity()
                        .teleport(new Location(Bukkit.getWorlds().getFirst(), 0.5, 100, 0.5)));
                // Native DragonBattle keeps the whole main island loaded while a nearby player sustains it.
                // Move presence away briefly and release ONLY this fixture's outer force-load tickets.
                player.teleport(new Location(end, 350.5, 151, 0.5));
                for (int x = 7; x <= 9; x++) {
                    for (int z = -9; z <= 9; z++) {
                        end.setChunkForceLoaded(x, z, false);
                    }
                }
                missing.teleport(new Location(end, 184.5, 151, 40.5));
                require(end.unloadChunkRequest(11, 2), "native out-of-ticket chunk unload requested");
                next();
            } else if (stage == 7 && nativeUnloaded && ticks - unloadTick >= 25) {
                require(nativeUnloaded, "actual native EntitiesUnloadEvent observed for Warden");
                require(end.getEntitiesByClass(Warden.class).size() == 8,
                        "invalid Warden recovered, not permanent gate");
                player.teleport(new Location(end, 40.5, 151, 0.5));
                end.loadChunk(11, 2);
                var dead = end.getEntitiesByClass(Warden.class).iterator().next();
                dead.setHealth(0);
                next();
            } else if (stage == 8 && ticks - stageTick >= 25) {
                require(end.getEntitiesByClass(Warden.class).size() == 7, "actual Warden death not replenished");
                idleWarden = end.getEntitiesByClass(Warden.class).iterator().next();
                idleWarden.teleport(new Location(end, 0.5, end.getHighestBlockYAt(0, 0) + 1, 0.5));
                idleWarden.setAI(true);
                player.teleport(new Location(end, 100, 50, 0));
                next();
            } else if (stage == 9) {
                if ((ticks - stageTick) % 500 == 0) {
                    // A genuine same-tick arrival round trip resets absence, not the Warden's native dig clock.
                    player.teleport(new Location(end, 40.5, 151, 0.5));
                    player.teleport(new Location(end, 100, 50, 0));
                }
                if (ticks - stageTick >= 1300) {
                    require(idleWarden.isValid() && idleWarden.getPose() != org.bukkit.entity.Pose.DIGGING
                            && idleWarden.getTarget() == null,
                            "persistent native Warden does not burrow after65s idle");
                    getLogger().info("FINAL_WAVE native idle Warden retained65s uuid=" + idleWarden.getUniqueId());
                    next();
                }
            } else if (stage == 10) {
                var survivor = end.getEntitiesByClass(Wither.class).iterator().next();
                player.teleport(survivor.getLocation().clone().add(2, 0, 0));
                survivor.setHealth(0);
                require(!sinks.getFirst().countdowns.isEmpty(), "disable has queued native countdown UI");
                replaced = new ArrayList<>(defenders());
                int before = blasts;
                World controlWorld = Bukkit.getWorlds().getFirst();
                controlWorld.setChunkForceLoaded(0, 0, true);
                controlSpawn = true;
                var ordinary = controlWorld.spawn(new Location(controlWorld, 0.5, 100, 0.5), Wither.class);
                controlSpawn = false;
                ordinary.setAI(false);
                require(ordinary.getHealth() == 600, "ordinary module enhancement retained");
                ordinary.setHealth(0);
                ordinaryStar = controlWorld.getEntitiesByClass(org.bukkit.entity.Item.class).stream()
                        .filter(item -> item.getItemStack().getType() == Material.NETHER_STAR).findFirst()
                        .orElseThrow();
                require(ordinaryStar.isInvulnerable() && ordinaryStar.isGlowing(),
                        "ordinary enhanced star re-drop retained");
                // Fresh loaded control isolates deletion from the earlier Enderman's native projectile teleport.
                enderman.remove();
                enderman = end.spawn(new Location(end, 40.5, 151, 3.5), org.bukkit.entity.Enderman.class);
                ((Mob) enderman).setAI(false);
                getServer().getPluginManager().disablePlugin(getServer().getPluginManager().getPlugin("Kreiscraft"));
                require(defenders().isEmpty() && anchors().isEmpty() && enderman.isValid(), "disable scoped cleanup");
                require(sinks.getFirst().countdowns.isEmpty(), "disable hides countdown UI synchronously");
                blasts = before;
                next();
            } else if (stage == 11 && ticks - stageTick >= 240) {
                require(blasts == 3 && replaced.stream().noneMatch(Entity::isValid), "disable pending blast inert");
                require(ordinaryStar.isValid() || players.stream().anyMatch(fixture -> fixture.getBukkitEntity()
                        .getInventory().contains(Material.NETHER_STAR)),
                        "owned cleanup preserves ordinary rewards, including native pickup");
                require(leaked == 0, "no unowned roots/minions/skulls observed");
                finish(true, null);
            }
        } catch (Throwable error) {
            finish(false, error);
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void spawn(CreatureSpawnEvent event) {
        if (event.getEntity() instanceof Wither || event.getEntity() instanceof Warden
                || event.getEntity() instanceof WitherSkeleton) {
            if (!owned(event.getEntity()) && !controlSpawn) {
                leaked++;
            }
            if (event.getEntity() instanceof WitherSkeleton && stage == 1) {
                minions++;
            }
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void unload(org.bukkit.event.world.EntitiesUnloadEvent event) {
        if (unloadedWarden != null && event.getEntities().stream()
                .anyMatch(entity -> entity.getUniqueId().equals(unloadedWarden))) {
            nativeUnloaded = true;
            unloadTick = ticks;
            getLogger().info("FINAL_WAVE native Warden chunk unload=" + unloadedWarden);
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void launch(ProjectileLaunchEvent event) {
        if (event.getEntity() instanceof WitherSkull skull) {
            skulls.add(skull);
            if (!owned(skull)) {
                leaked++;
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void hit(org.bukkit.event.entity.ProjectileHitEvent event) {
        if (event.getEntity() instanceof WitherSkull && event.getHitEntity() == warden) {
            allySkullHit = true;
            getLogger().info("FINAL_WAVE physical skull ally collision");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void explosion(EntityExplodeEvent event) {
        if (event.getEntity() == wither) {
            blasts++;
            getLogger().info("FINAL_WAVE actual blast blocks=" + event.blockList().size() + " count=" + blasts);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void damage(EntityDamageEvent event) {
        if (players.isEmpty() || event.getEntity() != players.getFirst().getBukkitEntity()) {
            return;
        }
        if (event.getDamageSource().getDamageType().equals(DamageType.SONIC_BOOM)) {
            sonic++;
            getLogger().info("FINAL_WAVE sonic final=" + event.getFinalDamage());
        }
        if (event.getDamageSource().getDirectEntity() instanceof WitherSkull) {
            skullHits++;
            getLogger().info("FINAL_WAVE skull impact final=" + event.getFinalDamage());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void arrivalDamage(EntityDamageEvent event) {
        if (stage == 5 && !players.isEmpty() && event.getEntity() == players.getFirst().getBukkitEntity()
                && event.isCancelled()) {
            arrivalVetoes++;
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void death(EntityDeathEvent event) {
        if (event.getEntity() instanceof Wither && owned(event.getEntity())) {
            require(event.getDrops().stream().noneMatch(item -> item.getType() == Material.NETHER_STAR),
                    "native public death drops have no stars");
        }
    }

    private void finish(boolean pass, Throwable error) {
        done = true;
        if (pass) {
            getLogger().info("FINAL_WAVE_PROBE PASS checks=" + checks + " ticks=" + ticks + " blasts=" + blasts
                    + " minions=" + minions + " sonic=" + sonic + " skullHits=" + skullHits);
        } else {
            getLogger().log(java.util.logging.Level.SEVERE, "FINAL_WAVE_PROBE FAIL", error);
        }
        getServer().shutdown();
    }

    private static final class Sink extends Connection {
        private PacketListener listener;
        private final java.util.Set<UUID> countdowns = new java.util.HashSet<>();
        private int progressUpdates;

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
            if (packet instanceof net.minecraft.network.protocol.game.ClientboundBossEventPacket boss) {
                boss.dispatch(new net.minecraft.network.protocol.game.ClientboundBossEventPacket.Handler() {
                    @Override
                    public void add(UUID id, net.minecraft.network.chat.Component name, float progress,
                            net.minecraft.world.BossEvent.BossBarColor color,
                            net.minecraft.world.BossEvent.BossBarOverlay overlay, boolean dark, boolean music,
                            boolean fog) {
                        if (name.getString().equals("Explosion")) {
                            countdowns.add(id);
                        }
                    }

                    @Override
                    public void updateProgress(UUID id, float progress) {
                        if (countdowns.contains(id)) {
                            progressUpdates++;
                        }
                    }

                    @Override
                    public void remove(UUID id) {
                        countdowns.remove(id);
                    }
                });
            }
            if (packet instanceof net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket position
                    && listener instanceof net.minecraft.server.network.ServerGamePacketListenerImpl game) {
                // Real handler clears dimension-change damage immunity; transport ACK is synthetic, not a client.
                game.handleAcceptTeleportPacket(
                        new net.minecraft.network.protocol.game.ServerboundAcceptTeleportationPacket(position.id()));
            }
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
