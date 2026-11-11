package de.kreiscraft.modules.shatteredcrown;

import com.mojang.authlib.GameProfile;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.entity.CraftEnderCrystal;
import org.bukkit.craftbukkit.entity.CraftEnderDragon;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Wither;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EnderDragonChangePhaseEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

/** Native whole-attempt extension of the existing disposable Paper probes. Only transport is synthetic. */
public final class ReleaseCandidateProbe extends JavaPlugin implements Listener {
    private static final NamespacedKey ARTIFACT = new NamespacedKey("kreiscraft", "crown_artifact");
    private static final NamespacedKey ORIGINAL = new NamespacedKey("kreiscraft", "crown_original_health");
    private final List<ServerPlayer> fixtures = new ArrayList<>();
    private final List<Sink> sinks = new ArrayList<>();
    private final List<String> selections = new ArrayList<>();
    private final Set<String> ritualStages = new HashSet<>();
    private final Map<Location, Material> committedTerrain = new HashMap<>();
    private World end;
    private Location footing;
    private EncounterController encounter;
    private EnderDragon dragon;
    private UUID firstDragon;
    private int ticks;
    private int stage;
    private int since;
    private int attempt;
    private int checks;
    private int deaths;
    private int respawns;
    private int quits;
    private int joins;
    private int dragonDeaths;
    private int deathExperience;
    private int spawnedExperience;
    private int gateways;
    private int gatewaySlot;
    private int eggForms;
    private int eggVetoes;
    private int arrival = -1;
    private int departure = -1;
    private int completion = -1;
    private String previousAttack = "none";
    private String previousSelection = "none";
    private boolean selectionRejected;
    private boolean requested;
    private boolean finished;
    private long packets;
    private long automaticChat;
    private long actionbars;
    private long titles;
    private long bosses;
    private long particles;
    private long sounds;
    private long replies;
    private UUID unloadMember;
    private boolean unloaded;
    private String diskConfig;
    private List<Entity> replaced = List.of();
    private final Map<UUID, Integer> poolPulseTicks = new HashMap<>();
    private final Set<UUID> pendingPoolDamage = new HashSet<>();
    private int poolHits;
    private int landingPoolHits;
    private int poolHealthDrops;
    private final Map<String, Double> rainGround = new HashMap<>();
    private int expectedRainHeight;
    private boolean worldVerified;
    private boolean worldUnloaded;
    private boolean worldLoaded;
    private UUID worldDragon;
    private UUID controlId;
    private UUID lateOwnedId;
    private UUID lateControlId;
    private boolean lateLoaded;

    @Override
    public void onEnable() {
        if (!Boolean.getBoolean("kreiscraft.release-probe")) {
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        getServer().getPluginManager().registerEvents(this, this);
        getServer().getScheduler().runTask(this, this::start);
    }

    private void check(boolean condition, String reason) {
        checks++;
        if (!condition) {
            throw new IllegalStateException(reason + " stage=" + stage + " ticks=" + ticks);
        }
    }

    private void start() {
        try {
            end = getServer().getWorld(NamespacedKey.minecraft("the_end"));
            check(end != null, "main native End");
            end.setDifficulty(org.bukkit.Difficulty.NORMAL);
            int loadedRadius = Boolean.getBoolean("kreiscraft.release-restart") ? 8 : 12;
            getLogger().info("RELEASE_ENV seed=" + end.getSeed() + " difficulty=" + end.getDifficulty()
                    + " transport=synthetic registered=PlayerList healthReplenishment=true forceLoadedRadius="
                    + loadedRadius);
            end.setGameRule(org.bukkit.GameRule.NATURAL_REGENERATION, false);
            end.setGameRule(org.bukkit.GameRule.DO_MOB_SPAWNING, false);
            for (int x = -loadedRadius; x <= loadedRadius; x++) {
                for (int z = -loadedRadius; z <= loadedRadius; z++) {
                    end.setChunkForceLoaded(x, z, true);
                }
            }
            // No calibration platform: all attacks and retries use accumulated native island terrain.
            footing = ground(40, 40);
            for (int x = 36; x <= 44; x++) {
                for (int z = 36; z <= 44; z++) {
                    var block = end.getBlockAt(x, end.getHighestBlockYAt(x, z), z);
                    committedTerrain.put(block.getLocation(), block.getType());
                }
            }
            var production = getServer().getPluginManager().getPlugin("Kreiscraft");
            for (var listener : HandlerList.getRegisteredListeners(production)) {
                if (listener.getListener() instanceof EncounterController controller) {
                    encounter = controller;
                    break;
                }
            }
            if (Boolean.getBoolean("kreiscraft.release-restart")) {
                enter(100);
                getServer().getScheduler().runTaskTimer(this, this::tick, 1, 1);
                return;
            }
            check(encounter != null, "actual production controller");
            for (int index = 0; index < 4; index++) {
                register(getServer().getWorlds().getFirst(), new GameProfile(UUID.randomUUID(), "Release" + index),
                        GameMode.SURVIVAL);
            }
            fixtures.getFirst().getBukkitEntity().teleport(new Location(end, 100.5, 50, 0.5));
            getServer().getScheduler().runTaskTimer(this, this::tick, 1, 1);
        } catch (Throwable error) {
            fail(error);
        }
    }

    private Location ground(int x, int z) {
        // Real Wither craters can remove a whole column. Walk to surviving native footing; never rebuild it.
        for (int radius = 0; radius <= 48; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) {
                        continue;
                    }
                    var position =
                            new Location(end, x + dx + 0.5, end.getHighestBlockYAt(x + dx, z + dz) + 1, z + dz + 0.5);
                    if (position.getBlock().getRelative(0, -1, 0).getType().isSolid()
                            && position.getBlock().isEmpty() && position.getBlock().getRelative(0, 1, 0).isEmpty()) {
                        return position;
                    }
                }
            }
        }
        throw new IllegalStateException("no surviving native footing near " + x + "," + z);
    }

    private void prepareRain(int height) {
        expectedRainHeight = height;
        rainGround.clear();
        for (int index = 0; index < 4; index++) {
            var position = ground(40 + index % 2 * 6, 40 + index / 2 * 6);
            rainGround.put(position.getBlockX() + ":" + position.getBlockZ(), position.getY());
            var player = fixtures.get(index).getBukkitEntity();
            // A two-block airborne native recipient resolves first, while other real shots remain airborne.
            // This produces genuine production debris/pools, not manually tagged fake interruption residue.
            player.setGravity(index != 0);
            player.teleport(position.add(0, index == 0 ? 2 : 0, 0));
            player.setVelocity(new org.bukkit.util.Vector());
        }
    }

    private boolean mixedRain() {
        return end.getEntities().stream().anyMatch(
                entity -> "debris".equals(entity.getPersistentDataContainer().get(ARTIFACT, PersistentDataType.STRING)))
                && end.getEntities().stream().anyMatch(entity -> "rain-projectile"
                        .equals(entity.getPersistentDataContainer().get(ARTIFACT, PersistentDataType.STRING)));
    }

    private ServerPlayer register(World world, GameProfile profile, GameMode mode) {
        var server = ((CraftServer) getServer()).getServer();
        var player = new ServerPlayer(server, ((CraftWorld) world).getHandle(), profile,
                ClientInformation.createDefault());
        player.snapTo(footing.getX(), footing.getY(), footing.getZ(), 0, 0);
        var sink = new Sink();
        server.getPlayerList().placeNewPlayer(sink, player, CommonListenerCookie.createInitial(profile, false));
        fixtures.add(player);
        sinks.add(sink);
        player.getBukkitEntity().setGameMode(mode);
        player.getBukkitEntity().setOp(true);
        equip(player);
        return player;
    }

    private void equip(ServerPlayer player) {
        var armor = new ItemStack[] {new ItemStack(Material.NETHERITE_BOOTS),
                new ItemStack(Material.NETHERITE_LEGGINGS), new ItemStack(Material.NETHERITE_CHESTPLATE),
                new ItemStack(Material.NETHERITE_HELMET)};
        for (var piece : armor) {
            piece.addEnchantment(org.bukkit.enchantments.Enchantment.PROTECTION, 4);
        }
        player.getBukkitEntity().getInventory().setArmorContents(armor);
        player.getBukkitEntity().setAbsorptionAmount(0);
    }

    private String command(String text) {
        var sink = sinks.getFirst();
        sink.messages.clear();
        requested = true;
        try {
            check(getServer().dispatchCommand(fixtures.getFirst().getBukkitEntity(), "dragondebug " + text), text);
        } finally {
            requested = false;
        }
        check(!sink.messages.isEmpty(), "explicit command reply " + text);
        return String.join(" | ", sink.messages);
    }

    private boolean owned(Entity entity) {
        return entity.isValid() && entity.getPersistentDataContainer().has(ARTIFACT, PersistentDataType.STRING);
    }

    private List<EnderCrystal> anchors() {
        return end.getEntitiesByClass(EnderCrystal.class).stream().filter(this::owned).toList();
    }

    private List<Mob> defenders() {
        return end.getEntitiesByClass(Mob.class).stream().filter(this::owned).toList();
    }

    private void crystalHit(EnderCrystal crystal) {
        ((CraftEnderCrystal) crystal).getHandle().hurtServer(((CraftWorld) end).getHandle(),
                fixtures.getFirst().damageSources().playerAttack(fixtures.getFirst()), 100);
    }

    private void clearWave() {
        var objectives = anchors();
        check(objectives.size() == 4, "four independently defended objectives");
        for (int index : new int[] {2, 0, 3, 1}) {
            var crystal = objectives.get(index);
            var members = defenders().stream().filter(mob -> crystal.getUniqueId().equals(encounter.anchorId(mob)))
                    .toList();
            if (members.isEmpty()) {
                check(encounter.waveNumber() == 3 && crystal.isGlowing(), "empty final Anchor starts exposed");
            } else {
                crystalHit(crystal);
                check(crystal.isValid(), "defenders block player crystal hit");
                var survivor = members.stream().filter(org.bukkit.entity.WitherSkeleton.class::isInstance).findFirst()
                        .orElse(members.getLast());
                for (var member : members) {
                    if (member != survivor) {
                        member.setHealth(0); // Real native deaths; never fabricated reward or death events.
                    }
                }
                crystalHit(crystal);
                check(crystal.isValid(), "last member independently gates crystal");
                survivor.setHealth(0);
                // Enhanced Wither deaths/half-health callbacks can have synchronously added descendants.
                for (var member : defenders().stream()
                        .filter(mob -> crystal.getUniqueId().equals(encounter.anchorId(mob))).toList()) {
                    member.setHealth(0);
                }
            }
            crystalHit(crystal);
            check(!crystal.isValid(), "legal crystal attack after every root and descendant death");
            check(anchors().size() == 3 - java.util.Arrays.asList(2, 0, 3, 1).indexOf(index),
                    "independent out-of-order crystal completion");
        }
        check(anchors().isEmpty(), "all four player-destroyed crystals complete wave");
    }

    private void hitDragon(boolean explosive) {
        var handle = ((CraftEnderDragon) dragon).getHandle();
        handle.invulnerableTime = 0;
        var player = fixtures.getFirst();
        handle.hurt(((CraftWorld) end).getHandle(), handle.head,
                explosive ? player.damageSources().explosion(null, player)
                        : player.damageSources().playerAttack(player),
                100000);
    }

    private void hitRanged() {
        var arrow = end.spawnArrow(fixtures.getFirst().getBukkitEntity().getLocation(),
                new org.bukkit.util.Vector(1, 0, 0), 1, 0);
        arrow.setShooter(fixtures.getFirst().getBukkitEntity());
        var handle = ((CraftEnderDragon) dragon).getHandle();
        handle.invulnerableTime = 0;
        handle.hurt(((CraftWorld) end).getHandle(), handle.head, fixtures.getFirst().damageSources()
                .arrow(((org.bukkit.craftbukkit.entity.CraftArrow) arrow).getHandle(), fixtures.getFirst()), 100000);
        arrow.remove();
    }

    private void enter(int next) {
        stage = next;
        since = ticks;
        getLogger().info("RELEASE_STAGE " + next + " tick=" + ticks + " attempt=" + attempt);
    }

    private void tick() {
        if (finished) {
            return;
        }
        try {
            ticks++;
            check(ticks < 18000, "bounded native release verification");
            fixtures.forEach(player -> player.connection.tick());
            for (var player : fixtures) {
                if (!player.getBukkitEntity().isDead()) {
                    if (pendingPoolDamage.remove(player.getUUID()) && player.getBukkitEntity().getHealth() < 20) {
                        poolHealthDrops++;
                    }
                    player.getBukkitEntity().setHealth(20); // Sustain synthetic endurance; damage remains genuine.
                    player.getBukkitEntity().setFoodLevel(20);
                }
            }
            if (ticks % 100 == 0) {
                var runtime = Runtime.getRuntime();
                getLogger().info("RELEASE_RESOURCE tick=" + ticks + " owned="
                        + end.getEntities().stream().filter(this::owned).count() + " species="
                        + defenders().stream().collect(java.util.stream.Collectors.groupingBy(Entity::getType,
                                java.util.stream.Collectors.counting()))
                        + " pluginTasks="
                        + getServer().getScheduler().getPendingTasks().stream()
                                .filter(task -> task.getOwner().getName().equals("Kreiscraft")).count()
                        + " heapUsed=" + (runtime.totalMemory() - runtime.freeMemory()) + " heapCommitted="
                        + runtime.totalMemory() + " averageTickMs=" + getServer().getAverageTickTime()
                        + " tps=" + java.util.Arrays.toString(getServer().getTPS()) + " packets=" + packets
                        + " gc=" + java.lang.management.ManagementFactory.getGarbageCollectorMXBeans().stream()
                                .map(bean -> bean.getName() + ":" + bean.getCollectionCount() + "/"
                                        + bean.getCollectionTime())
                                .toList());
            }
            switch (stage) {
                case 0 -> {
                    if (ticks == 5) {
                        for (var player : fixtures) {
                            player.getBukkitEntity().teleport(footing);
                        }
                    }
                    dragon = end.getEnderDragonBattle().getEnderDragon();
                    if (dragon == null || !dragon.getPersistentDataContainer().has(ORIGINAL)) {
                        return;
                    }
                    check(dragon.hasAI(), "AI-enabled natural battle dragon");
                    check(encounter.wavePopulation() == 4, "first-entry server-wide frozen population4");
                    check(defenders().size() == 64, "sixteen-per-player mounted/tower opening roster");
                    firstDragon = dragon.getUniqueId();
                    enter(1);
                }
                case 1 -> {
                    if (encounter.remainingGraceSeconds() > 0) {
                        check(dragon.getPhase() == EnderDragon.Phase.CIRCLING, "native attacks suppressed in grace");
                        check(command("status").contains("attack=none"), "custom attacks suppressed in grace");
                    } else {
                        observeRotation(4, Set.of("BOMBARDMENT", "PREY"));
                    }
                }
                case 2 -> {
                    command("pause");
                    command("trigger prey Release1");
                    var victim = fixtures.get(1);
                    victim.hurtServer(((CraftWorld) end).getHandle(), victim.damageSources().genericKill(), 100000);
                    check(deaths == 1 && victim.getBukkitEntity().isDead(), "actual native player death");
                    check(encounter.scalingPopulation() == 4 && encounter.combatPopulation() == 3,
                            "connected dead scales but is never a target");
                    check(command("status").contains("marks=0"), "death cancels old Prey mark");
                    victim.connection
                            .handleClientCommand(new net.minecraft.network.protocol.game.ServerboundClientCommandPacket(
                                    net.minecraft.network.protocol.game.ServerboundClientCommandPacket.Action.PERFORM_RESPAWN));
                    var replacement = victim.connection.player;
                    // Paper132 reuses ServerPlayer (PlayerList.respawn); always follow the handler's result.
                    check(respawns == 1 && !replacement.getBukkitEntity().isDead(),
                            "native handler respawns dead player");
                    check(getServer().getPlayer(victim.getUUID()) == replacement.getBukkitEntity(),
                            "UUID lookup replaced");
                    fixtures.set(1, replacement);
                    replacement.getBukkitEntity().teleport(footing);
                    equip(replacement);
                    enter(3);
                }
                case 3 -> {
                    if (ticks - since < 80) {
                        return;
                    }
                    check(fixtures.get(1).connection.hasClientLoaded() && encounter.combatPopulation() == 4,
                            "native respawn loaded and ordinary return participates");
                    command("trigger prey Release2");
                    var old = fixtures.remove(2);
                    var oldSink = sinks.remove(2);
                    ((CraftServer) getServer()).getServer().getPlayerList().remove(old);
                    oldSink.channel.close();
                    check(quits == 1 && getServer().getPlayer(old.getUUID()) == null, "real quit removes UUID lookup");
                    check(command("status").contains("marks=0"), "quit removes old mark");
                    register(end, old.getGameProfile(), GameMode.SURVIVAL).getBukkitEntity().teleport(footing);
                    check(joins == 5 && encounter.scalingPopulation() == 4, "genuine same-UUID join registration");
                    enter(4);
                }
                case 4 -> {
                    if (ticks - since < 100) {
                        return;
                    }
                    check(encounter.combatPopulation() == 4, "rejoined player loaded and eligible");
                    check(command("status").contains("marks=0") && end.getEntities().stream()
                            .noneMatch(entity -> "pool".equals(entity.getPersistentDataContainer().get(ARTIFACT,
                                    PersistentDataType.STRING))),
                            "no stale respawn/rejoin deposition");
                    command("cancel");
                    clearWave();
                    check(encounter.availableAttacks().contains(EncounterController.AttackFamily.DIVE), "Dive unlock");
                    if (attempt == 0) {
                        for (int index = 4; index < 8; index++) {
                            register(end, new GameProfile(UUID.randomUUID(), "Release" + index), GameMode.SPECTATOR);
                        }
                    }
                    if (attempt == 0) {
                        hitDragon(false);
                    } else {
                        hitRanged();
                    }
                    check(dragon.getHealth() == 650, "oversized native multipart melee/ranged clamps65");
                    enter(5);
                }
                case 5 -> {
                    if (anchors().size() != 4) {
                        return;
                    }
                    check(defenders().size() == (attempt == 0 ? 72 : 36), "frozen all-End seven-species wave");
                    hitDragon(false);
                    check(dragon.getHealth() == 650, "shield rejects oversized multipart followup");
                    hitRanged();
                    check(dragon.getHealth() == 650, "native multipart ranged source cannot bypass shield");
                    clearWave();
                    check(encounter.availableAttacks().contains(EncounterController.AttackFamily.RAIN), "Rain unlock");
                    if (attempt == 0) {
                        for (int index = 8; index < 20; index++) {
                            register(end, new GameProfile(UUID.randomUUID(), "Release" + index), GameMode.SPECTATOR);
                        }
                    }
                    enter(55);
                }
                case 55 -> {
                    if (ticks - since < 80) {
                        return; // Newly registered native players retain genuine client-load damage immunity for60ticks.
                    }
                    if (attempt == 0) {
                        var dead = fixtures.getLast();
                        check(dead.connection.hasClientLoaded(),
                                "larger-population native player loaded before real death");
                        dead.getBukkitEntity().setGameMode(GameMode.SURVIVAL);
                        dead.hurtServer(((CraftWorld) end).getHandle(), dead.damageSources().genericKill(), 100000);
                        check(dead.getBukkitEntity().isDead() && encounter.scalingPopulation() == 20,
                                "later native snapshot includes connected dead player, not only living world roster");
                    }
                    hitDragon(true);
                    check(dragon.getHealth() == 300, "oversized native explosion clamps30");
                    for (var player : fixtures.subList(0, 4)) {
                        player.getBukkitEntity().teleport(new Location(end, 100.5, 50, 0.5));
                    }
                    enter(6);
                }
                case 6 -> {
                    if (anchors().size() != 4) {
                        return;
                    }
                    check(defenders().size() == (attempt == 0 ? 18 : 4), "uncapped interpolated final boss roster");
                    if (ticks - since < 230) {
                        return; // Let all native 220-tick charges and destructive enhanced spawn blasts actually finish.
                    }
                    footing = ground(40, 40);
                    for (var player : fixtures.subList(0, 4)) {
                        player.getBukkitEntity().teleport(footing);
                    }
                    var parent = defenders().stream().filter(Wither.class::isInstance).findFirst().orElseThrow();
                    parent.setNoDamageTicks(0);
                    parent.setHealth(160);
                    parent.damage(40, fixtures.getFirst().getBukkitEntity());
                    check(defenders().size() == (attempt == 0 ? 22 : 8), "enhanced half-health four owned minions");
                    clearWave();
                    check(encounter.availableAttacks().size() == 5, "all five permanent families runnable");
                    command("resume");
                    selections.clear();
                    previousSelection = "none";
                    previousAttack = "none";
                    completion = -1;
                    enter(7);
                    observeRotation(6, Set.of("BOMBARDMENT", "PREY", "DIVE", "RAIN", "SMASH"));
                }
                case 7 -> observeRotation(6, Set.of("BOMBARDMENT", "PREY", "DIVE", "RAIN", "SMASH"));
                case 8 -> {
                    command("pause");
                    footing = ground(40, 40);
                    fixtures.getFirst().getBukkitEntity().teleport(footing);
                    fixtures.getFirst().getBukkitEntity().setVelocity(new org.bukkit.util.Vector());
                    command("trigger landing");
                    check(command("status").contains("attack=LANDING"), "native landing reserved with old hazards");
                    // Manual trigger intentionally replaces old attack work; deposit during approach, before arrival.
                    check(encounter.depositBreathPool(footing, 5, 120), "lingering shared pool during native approach");
                    enter(9);
                }
                case 9 -> {
                    check(ticks - since < 1400, "bounded actual native arrival");
                    if (departure >= 0) {
                        check(departure - arrival == 120, "six-second window starts at actual native arrival");
                        check(landingPoolHits >= 2 && poolHealthDrops >= 2,
                                "actual old-pool damage and HP loss through native landing");
                        if (attempt == 0) {
                            while (fixtures.size() > 4) {
                                var observer = fixtures.removeLast();
                                ((CraftServer) getServer()).getServer().getPlayerList().remove(observer);
                                sinks.removeLast().channel.close();
                            }
                        }
                        end.getEntitiesByClass(ExperienceOrb.class).forEach(Entity::remove); // Remove only earlier defender XP, not rewards under test.
                        gateways = ((CraftWorld) end).getHandle().getDragonFight().gateways.size();
                        gatewaySlot = ((CraftWorld) end).getHandle().getDragonFight().gateways.getLast();
                        command("cancel");
                        hitDragon(false); // LEGAL native lethal hit; native DYING owns rewards and completion.
                        check(dragon.getPhase() == EnderDragon.Phase.DYING, "legal final hit enters native DYING");
                        enter(10);
                    }
                }
                case 10 -> {
                    check(ticks - since < 500, "native death completion bounded");
                    if (!((CraftEnderDragon) dragon).getHandle().isRemoved()) {
                        return;
                    }
                    check(dragonDeaths == attempt + 1, "one genuine native dragon death event per attempt");
                    check(((CraftEnderDragon) dragon).getHandle().dragonDeathTime == 200,
                            "actual native200-tick DYING animation completed");
                    check(deathExperience == (attempt == 0 ? 12000 : 500), "normal first/subsequent native XP payload");
                    int actualExperience = getServer().getWorlds().stream()
                            .flatMap(world -> world.getEntitiesByClass(ExperienceOrb.class).stream())
                            .filter(orb -> dragon.getUniqueId()
                                    .equals(((org.bukkit.craftbukkit.entity.CraftExperienceOrb) orb)
                                            .getHandle().sourceEntityId))
                            .mapToInt(orb -> orb.getExperience()
                                    * ((org.bukkit.craftbukkit.entity.CraftExperienceOrb) orb).getHandle().count)
                            .sum();
                    check(actualExperience == deathExperience,
                            "actual native XP orbs generated once: " + actualExperience);
                    check(end.getEntitiesByClass(ExperienceOrb.class).stream().allMatch(orb -> dragon.getUniqueId()
                            .equals(((org.bukkit.craftbukkit.entity.CraftExperienceOrb) orb)
                                    .getHandle().sourceEntityId)),
                            "actual XP orbs retain native dragon source UUID");
                    check(end.getEnderDragonBattle().hasBeenPreviouslyKilled(), "native battle completed");
                    check(end.getEnderDragonBattle().getEnderDragon() == null, "native battle dragon cleared");
                    check(((CraftWorld) end).getHandle().getDragonFight().gateways.size() == gateways - 1,
                            "one actual gateway per native victory");
                    double angle = 2 * (-Math.PI + Math.PI / 20 * gatewaySlot);
                    check(end.getBlockAt((int) Math.floor(96 * Math.cos(angle)), 75,
                            (int) Math.floor(96 * Math.sin(angle))).getType() == Material.END_GATEWAY,
                            "actual native gateway block generated at the consumed slot");
                    check(end.getEntities().stream().noneMatch(this::owned), "victory drains every owned artifact");
                    if (attempt == 0) {
                        committedTerrain.entrySet()
                                .removeIf(entry -> entry.getKey().getBlock().getType() != Material.AIR);
                        check(!committedTerrain.isEmpty(),
                                "actual production attacks destroyed original native ground");
                    } else {
                        check(committedTerrain.keySet().stream()
                                .allMatch(location -> location.getBlock().getType() == Material.AIR),
                                "committed native terrain remains destroyed across complete ritual attempt and victory");
                    }
                    var portal = end.getEnderDragonBattle().getEndPortalLocation();
                    check(portal != null
                            && end.getBlockAt(portal.clone().add(1, 0, 0)).getType() == Material.END_PORTAL,
                            "native exit portal activated");
                    if (attempt == 0) {
                        check(eggForms == 1 && eggVetoes == 0
                                && end.getBlockAt(0, end.getHighestBlockYAt(0, 0), 0).getType() == Material.DRAGON_EGG,
                                "actual first native egg formation and block");
                    } else {
                        // Approved later terrain damage can destroy the first egg; vanilla does not replace it.
                        check(eggForms == 1 && eggVetoes == 1,
                                "subsequent native egg eligibility, no custom replacement");
                    }
                    getLogger().info("RELEASE_REWARD attempt=" + attempt + " nativeXP=" + deathExperience
                            + " orbsXP=" + actualExperience + " remainingGateways=" + (gateways - 1)
                            + " permanentNativeColumns=" + committedTerrain.size() + " nativeDeathTicks="
                            + ((CraftEnderDragon) dragon).getHandle().dragonDeathTime);
                    while (fixtures.size() > 4) {
                        var old = fixtures.removeLast();
                        ((CraftServer) getServer()).getServer().getPlayerList().remove(old);
                        sinks.removeLast().channel.close();
                    }
                    // Place all four real ritual crystals and let every vanilla stage tick; never force END.
                    var crystals = new ArrayList<EnderCrystal>();
                    for (int[] offset : new int[][] {{3, 0}, {-3, 0}, {0, 3}, {0, -3}}) {
                        crystals.add(
                                end.spawn(portal.clone().add(offset[0] + 0.5, 1, offset[1] + 0.5), EnderCrystal.class));
                    }
                    check(end.getEnderDragonBattle().initiateRespawn(crystals),
                            "public four-crystal native ritual starts");
                    ritualStages.clear();
                    ritualStages.add(end.getEnderDragonBattle().getRespawnPhase().name());
                    enter(11);
                }
                case 11 -> {
                    ritualStages.add(end.getEnderDragonBattle().getRespawnPhase().name());
                    check(ticks - since < 1000, "full native ritual bounded");
                    var newDragon = end.getEnderDragonBattle().getEnderDragon();
                    if (newDragon == null || !newDragon.getPersistentDataContainer().has(ORIGINAL)) {
                        return;
                    }
                    check(!newDragon.getUniqueId().equals(firstDragon),
                            "ritual creates distinct managed native dragon");
                    check(ritualStages.containsAll(Set.of("START", "PREPARING_TO_SUMMON_PILLARS", "SUMMONING_PILLARS",
                            "SUMMONING_DRAGON")), "all genuine native ritual stages " + ritualStages);
                    dragon = newDragon;
                    while (fixtures.size() > 4) {
                        var old = fixtures.removeLast();
                        ((CraftServer) getServer()).getServer().getPlayerList().remove(old);
                        sinks.removeLast().channel.close();
                    }
                    attempt++;
                    arrival = -1;
                    departure = -1;
                    check(encounter.waveNumber() == 1, "fresh retry starts opening wave");
                    selections.clear();
                    previousSelection = "none";
                    previousAttack = "none";
                    completion = -1;
                    enter(attempt == 2 ? 13 : 12);
                }
                case 12 -> {
                    if (encounter.remainingGraceSeconds() == 0) {
                        command("cancel");
                        enter(4);
                    }
                }
                case 13 -> {
                    command("wave 3");
                    fixtures.forEach(player -> player.getBukkitEntity().teleport(new Location(end, 100.5, 50, 0.5)));
                    replaced = new ArrayList<>(end.getEntities().stream().filter(this::owned).toList());
                    enter(14);
                }
                case 14 -> {
                    if (ticks - since < 240) {
                        return;
                    }
                    fixtures.forEach(player -> player.getBukkitEntity().teleport(ground(40, 40)));
                    prepareRain(20);
                    command("trigger fireball-rain");
                    var path = getServer().getPluginManager().getPlugin("Kreiscraft").getDataFolder().toPath()
                            .resolve("config.yml");
                    diskConfig = java.nio.file.Files.readString(path);
                    java.nio.file.Files.writeString(path,
                            diskConfig.replace("    rain-damage: 4.0", "    rain-damage: .NaN"));
                    String invalid = reload();
                    check(invalid.toLowerCase(java.util.Locale.ROOT).contains("fail")
                            || invalid.toLowerCase(java.util.Locale.ROOT).contains("invalid"),
                            "invalid disk reload rejected");
                    check(command("status").contains("attack=RAIN"), "atomic reject retains running action");
                    java.nio.file.Files.writeString(path,
                            diskConfig.replace("    max-health: 1000.0", "    max-health: 900.0")
                                    .replace("    rain-spawn-height: 20.0", "    rain-spawn-height: 22.0"));
                    reload();
                    check(dragon.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue() == 1000,
                            "valid corrected reload keeps current Encounter snapshot");
                    enter(15);
                }
                case 15 -> {
                    check(ticks - since < 90, "bounded native mixed-impact window");
                    if (!mixedRain()) {
                        return;
                    }
                    check(end.getEntities().stream().anyMatch(entity -> "rain-projectile".equals(entity
                            .getPersistentDataContainer().get(ARTIFACT, PersistentDataType.STRING))),
                            "live Rain projectile");
                    check(encounter.depositBreathPool(ground(40, 40), 5, 8), "concurrent shared pool");
                    command("pause");
                    fixtures.forEach(player -> player.getBukkitEntity().setGravity(true));
                    check(command("status").contains("hazards=0") && end.getEntities().stream().noneMatch(entity -> {
                        String kind = entity.getPersistentDataContainer().get(ARTIFACT, PersistentDataType.STRING);
                        return Set.of("rain-projectile", "rain-warning", "pool", "debris")
                                .contains(kind == null ? "" : kind);
                    }), "pause closes actual live projectile/pool/debris work");
                    var wither = defenders().stream().filter(Wither.class::isInstance).findFirst().orElseThrow();
                    wither.setHealth(0); // Queue a real enhanced death countdown before the debug replacement.
                    command("wave 2");
                    check(replaced.stream().noneMatch(Entity::isValid),
                            "wave jump removes old boss/artifact identities");
                    command("trigger dive Release0");
                    enter(16);
                }
                case 16 -> {
                    if (ticks - since < 5) {
                        return;
                    }
                    command("wave 1");
                    check(command("status").contains("hazards=0") && defenders().size() == 64,
                            "wave replacement cancels real native flight and installs current mounted roster");
                    for (var player : fixtures) {
                        player.getBukkitEntity().teleport(new Location(end, 350, 100, 0));
                    }
                    enter(17);
                }
                case 17 -> {
                    if (ticks - since < 1180) {
                        return;
                    }
                    check(dragon.getPersistentDataContainer().has(ORIGINAL), "native return before60s retains attempt");
                    fixtures.getFirst().getBukkitEntity().teleport(ground(40, 40));
                    enter(18);
                }
                case 18 -> {
                    if (ticks - since < 20) {
                        return;
                    }
                    fixtures.getFirst().getBukkitEntity().teleport(new Location(end, 350, 100, 0));
                    enter(19);
                }
                case 19 -> {
                    if (ticks - since < 1210) {
                        return;
                    }
                    check(!dragon.getPersistentDataContainer().has(ORIGINAL) && dragon.getHealth() == 200,
                            "full native absence resets same dragon to original200HP");
                    check(end.getEntities().stream().noneMatch(this::owned), "absence removes every stale artifact");
                    for (var player : fixtures) {
                        player.getBukkitEntity().teleport(ground(40, 40));
                    }
                    enter(20);
                }
                case 20 -> {
                    if (!dragon.getPersistentDataContainer().has(ORIGINAL)) {
                        return;
                    }
                    check(dragon.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue() == 900,
                            "next attempt consumes corrected configuration snapshot");
                    command("wave 3");
                    var missing = defenders().stream().filter(org.bukkit.entity.Warden.class::isInstance).findFirst()
                            .orElseThrow();
                    unloadMember = missing.getUniqueId();
                    for (var player : fixtures) {
                        player.getBukkitEntity().teleport(new Location(end, 350.5, 100, 0.5));
                    }
                    for (int x = 7; x <= 12; x++) {
                        for (int z = -12; z <= 12; z++) {
                            end.setChunkForceLoaded(x, z, false);
                        }
                    }
                    missing.teleport(new Location(end, 184.5, 100, 40.5));
                    check(end.unloadChunkRequest(11, 2), "actual relevant native chunk unload requested");
                    enter(21);
                }
                case 21 -> {
                    check(ticks - since < 500, "native relevant unload bounded");
                    if (!unloaded || ticks - since < 40) {
                        return;
                    }
                    end.loadChunk(11, 2);
                    check(getServer().getEntity(unloadMember) == null, "reloaded stale member cleaned");
                    check(defenders().size() == 4, "actual unload is recovery not defeat or duplicate gate");
                    for (var player : fixtures) {
                        player.getBukkitEntity().teleport(new Location(end, 100.5, 50, 0.5));
                    }
                    enter(22);
                }
                case 22 -> {
                    if (ticks - since < 240) {
                        return;
                    }
                    fixtures.forEach(player -> player.getBukkitEntity().teleport(ground(40, 40)));
                    var parent = defenders().stream().filter(Wither.class::isInstance).findFirst().orElseThrow();
                    parent.setHealth(160);
                    parent.damage(40, fixtures.getFirst().getBukkitEntity());
                    check(defenders().size() == 8, "interrupted active attempt includes actual minions");
                    parent.setHealth(0);
                    prepareRain(22);
                    command("trigger fireball-rain");
                    enter(23);
                }
                case 23 -> {
                    check(ticks - since < 90, "bounded native mixed-impact interruption window");
                    if (!mixedRain()) {
                        return;
                    }
                    check(encounter.depositBreathPool(ground(40, 40), 5, 120), "persist real active pool");
                    var skullParent = defenders().stream().filter(Wither.class::isInstance).findFirst().orElseThrow();
                    end.spawn(skullParent.getLocation().clone().add(0, 3, 0), org.bukkit.entity.WitherSkull.class,
                            skull -> {
                                skull.setShooter(skullParent);
                                check(encounter.inheritOwnership(skull, skullParent),
                                        "native skull pre-spawn public ownership seam");
                                skull.setVelocity(new org.bukkit.util.Vector(0, 0.02, 0));
                            });
                    var kinds = end.getEntities().stream().filter(this::owned)
                            .map(entity -> entity.getPersistentDataContainer()
                                    .get(ARTIFACT, PersistentDataType.STRING))
                            .collect(java.util.stream.Collectors.toSet());
                    check(kinds.containsAll(
                            Set.of("anchor", "defender", "defender-effect", "rain-projectile", "rain-warning", "pool",
                                    "debris")),
                            "all artifact kinds genuinely present before owned interruption " + kinds);
                    if (controlId == null) {
                        var control = end.spawn(ground(-40, 40), org.bukkit.entity.Enderman.class);
                        control.setRemoveWhenFarAway(false);
                        control.setAI(false);
                        control.setGravity(false);
                        controlId = control.getUniqueId();
                    }
                    if (!worldVerified) {
                        worldDragon = dragon.getUniqueId();
                        var creator = org.bukkit.WorldCreator.ofNameAndKey(end.getName(), end.getKey())
                                .environment(World.Environment.THE_END).seed(end.getSeed());
                        replaced = new ArrayList<>(end.getEntities().stream().filter(this::owned).toList());
                        fixtures.forEach(player -> player.getBukkitEntity().teleport(
                                new Location(getServer().getWorlds().getFirst(), 0.5, 100, 0.5)));
                        for (int x = -12; x <= 12; x++) {
                            for (int z = -12; z <= 12; z++) {
                                end.setChunkForceLoaded(x, z, false);
                            }
                        }
                        check(getServer().unloadWorld(end, true) && worldUnloaded,
                                "actual managed-End WorldUnloadEvent");
                        end = getServer().createWorld(creator);
                        check(end != null && worldLoaded, "actual same-key saved End WorldLoadEvent");
                        for (int x = -12; x <= 12; x++) {
                            for (int z = -12; z <= 12; z++) {
                                end.setChunkForceLoaded(x, z, true);
                            }
                        }
                        enter(24);
                        return;
                    }
                    var manifest = new java.util.Properties();
                    check(getServer().getEntity(controlId) != null && getServer().getEntity(lateControlId) != null
                            && getServer().getEntity(lateOwnedId) != null,
                            "persistent controls/minion are live before save");
                    check(getServer().getEntity(lateOwnedId).getLocation().getChunk().getX() == 11
                            && getServer().getEntity(lateOwnedId).getLocation().getChunk().getZ() == 2,
                            "later minion remains inside arena in deliberately later-loaded native chunk");
                    manifest.setProperty("dragon", dragon.getUniqueId().toString());
                    manifest.setProperty("control", controlId.toString());
                    manifest.setProperty("lateOwned", lateOwnedId.toString());
                    manifest.setProperty("lateControl", lateControlId.toString());
                    manifest.setProperty("kinds", kinds.toString());
                    manifest.setProperty("craterY", Integer.toString(end.getHighestBlockYAt(40, 40)));
                    java.nio.file.Files.createDirectories(getDataFolder().toPath());
                    try (var stream = java.nio.file.Files
                            .newOutputStream(getDataFolder().toPath().resolve("interrupted.properties"))) {
                        manifest.store(stream, "Actual saved active native attempt, before owned process interruption");
                    }
                    requested = true;
                    try {
                        check(getServer().dispatchCommand(getServer().getConsoleSender(), "save-all flush"),
                                "save active disposable worlds");
                    } finally {
                        requested = false; // Explicit fixture command broadcasts are not automatic Encounter output.
                    }
                    check(automaticChat == 0 && actionbars == 0 && titles == 0,
                            "no automatic Encounter feed through whole attempts");
                    check(replies > 0 && bosses > 0 && particles > 0 && sounds > 0,
                            "actual requested/debug/world/boss cues retained");
                    getLogger().info("RELEASE_INTERRUPT_READY checks=" + checks + " ticks=" + ticks + " dragonDeaths="
                            + dragonDeaths
                            + " automaticChat=" + automaticChat + " actionbars=" + actionbars + " titles=" + titles
                            + " ritual=" + ritualStages + " kinds=" + kinds);
                    finished = true; // Runner kills only this owned JVM; no plugin shutdown/false orphan construction.
                }
                case 24 -> {
                    if (ticks - since < 40) {
                        return;
                    }
                    dragon = (EnderDragon) getServer().getEntity(worldDragon);
                    check(dragon != null
                            && dragon.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue() == 200
                            && !dragon.getPersistentDataContainer().has(ORIGINAL),
                            "same surviving native dragon restored on real unload/load");
                    check(replaced.stream().noneMatch(Entity::isValid)
                            && end.getEntities().stream().noneMatch(this::owned),
                            "actual world unload/load drains every original artifact identity");
                    check(getServer().getEntity(controlId) != null,
                            "unrelated control UUID survives actual world unload/load");
                    worldVerified = true;
                    fixtures.forEach(player -> player.getBukkitEntity().teleport(ground(40, 40)));
                    enter(25);
                }
                case 25 -> {
                    if (!dragon.getPersistentDataContainer().has(ORIGINAL)) {
                        return;
                    }
                    command("wave 3");
                    fixtures.forEach(player -> player.getBukkitEntity().teleport(new Location(end, 100.5, 50, 0.5)));
                    enter(26);
                }
                case 26 -> {
                    if (ticks - since < 240) {
                        return;
                    }
                    fixtures.forEach(player -> player.getBukkitEntity().teleport(ground(40, 40)));
                    var parent = defenders().stream().filter(Wither.class::isInstance).findFirst().orElseThrow();
                    parent.setHealth(160);
                    parent.damage(40, fixtures.getFirst().getBukkitEntity());
                    check(defenders().size() == 8, "new attempt after actual world load has fresh minions only");
                    parent.setHealth(0);
                    // Prepare saved later-load controls BEFORE Rain's live checkpoint. Same-tick additions/moves
                    // into a new native chunk are not committed by save-all until entity loading has ticked.
                    for (int x = 9; x <= 12; x++) {
                        for (int z = -12; z <= 12; z++) {
                            end.setChunkForceLoaded(x, z, false);
                        }
                    }
                    end.addPluginChunkTicket(11, 2, this); // Non-persistent owned ticket; must NOT eagerly load after restart.
                    var late = defenders().stream().filter(org.bukkit.entity.WitherSkeleton.class::isInstance)
                            .findFirst().orElseThrow();
                    lateOwnedId = late.getUniqueId();
                    late.setAI(false);
                    late.setGravity(false);
                    check(late.teleport(new Location(end, 184.5, 100, 40.5)), "later-load native minion relocation");
                    var farControl = end.spawn(new Location(end, 180.5, 100, 40.5), org.bukkit.entity.Enderman.class);
                    farControl.setRemoveWhenFarAway(false);
                    farControl.setAI(false);
                    farControl.setGravity(false);
                    lateControlId = farControl.getUniqueId();
                    prepareRain(22);
                    command("trigger fireball-rain");
                    enter(23);
                }
                case 100 -> {
                    if (ticks < 40) {
                        return;
                    }
                    var manifest = new java.util.Properties();
                    try (var stream = java.nio.file.Files
                            .newInputStream(getDataFolder().toPath().resolve("interrupted.properties"))) {
                        manifest.load(stream);
                    }
                    firstDragon = UUID.fromString(manifest.getProperty("dragon"));
                    dragon = (EnderDragon) getServer().getEntity(firstDragon);
                    boolean restored = dragon != null && dragon.getHealth() == 200 && dragon.hasAI()
                            && dragon.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue() == 200
                            && !dragon.getPersistentDataContainer().has(ORIGINAL)
                            && !dragon.getPersistentDataContainer()
                                    .has(new NamespacedKey("kreiscraft", "crown_original_phase"));
                    if (!restored) {
                        check(ticks < 200,
                                "actual interrupted-process recovery restores SAME native200HP retryable dragon actual="
                                        + dragon + (dragon == null ? ""
                                                : " health=" + dragon.getHealth() + " max="
                                                        + dragon.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH)
                                                                .getValue()
                                                        + " ai=" + dragon.hasAI() + " phase=" + dragon.getPhase()
                                                        + " original="
                                                        + dragon.getPersistentDataContainer().has(ORIGINAL)));
                        return; // Native chunk entity loading is asynchronous; never manufacture readiness.
                    }
                    check(restored, "observed native interrupted-process recovery before retry");
                    check(end.getEntities().stream().noneMatch(this::owned),
                            "saved active artifacts cleaned on real process restart");
                    check(getServer().getEntity(UUID.fromString(manifest.getProperty("control"))) != null,
                            "unrelated native control UUID retained after interruption");
                    check(end.getHighestBlockYAt(40, 40) == Integer.parseInt(manifest.getProperty("craterY")),
                            "actual interrupted crater remains without rebuilding");
                    check(dragonDeaths == 0 && spawnedExperience == 0, "interruption/restart never grants new rewards");
                    lateOwnedId = UUID.fromString(manifest.getProperty("lateOwned"));
                    lateControlId = UUID.fromString(manifest.getProperty("lateControl"));
                    check(getServer().getEntity(lateControlId) == null,
                            "far saved control was not eagerly loaded at startup");
                    end.setChunkForceLoaded(11, 2, true);
                    enter(102);
                }
                case 101 -> {
                    if (ticks - since < 260) {
                        return;
                    }
                    check(dragon.getUniqueId().equals(firstDragon) && dragon.getPersistentDataContainer().has(ORIGINAL),
                            "same surviving battle starts clean later attempt");
                    check(encounter.waveNumber() == 1 && defenders().size() == 64 && anchors().size() == 4,
                            "later attempt current roster/gates no stale boss or minion");
                    check(dragonDeaths == 0 && spawnedExperience == 0,
                            "old delayed explosion/reward callbacks inert12s into retry");
                    var path = getServer().getPluginManager().getPlugin("Kreiscraft").getDataFolder().toPath()
                            .resolve("config.yml");
                    var original = java.nio.file.Files.readString(path);
                    java.nio.file.Files.writeString(path,
                            original.replace("  wither:\n    enabled: true", "  wither:\n    enabled: false")
                                    .replace("    max-health: 600.0", "    max-health: 650.0"));
                    reload();
                    var tuned = end.spawn(ground(-40, -40), Wither.class);
                    check(tuned.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue() == 650,
                            "Wither false reload is restart-only; registered enhanced listener consumes live tuning");
                    tuned.remove();
                    java.nio.file.Files.writeString(path, original.replace("  shattered-crown:\n    enabled: true",
                            "  shattered-crown:\n    enabled: false"));
                    reload();
                    check(end.getEntities().stream().noneMatch(this::owned)
                            && !dragon.getPersistentDataContainer().has(ORIGINAL),
                            "Crown false reload immediately aborts and restores native dragon");
                    java.nio.file.Files.writeString(path, original);
                    reload();
                    enter(104);
                }
                case 104 -> {
                    if (ticks - since < 260) {
                        return;
                    }
                    check(end.getEntities().stream().noneMatch(this::owned)
                            && !dragon.getPersistentDataContainer().has(ORIGINAL),
                            "Crown true later never hot-reinstalls closed controller; all callbacks remain inert");
                    getServer().getPluginManager()
                            .disablePlugin(getServer().getPluginManager().getPlugin("Kreiscraft"));
                    check(end.getEntities().stream().noneMatch(this::owned), "actual disable drains later attempt");
                    getLogger().info("RELEASE_RESTART_PROBE PASS checks=" + checks + " ticks=" + ticks + " sameDragon="
                            + firstDragon);
                    finished = true;
                    getServer().shutdown();
                }
                case 102 -> {
                    if (ticks - since < 40) {
                        return;
                    }
                    check(lateLoaded && getServer().getEntity(lateOwnedId) == null
                            && getServer().getEntity(lateControlId) != null,
                            "actual later-loaded chunk removes stale native minion, retains unrelated control UUID loaded="
                                    + lateLoaded + " owned=" + getServer().getEntity(lateOwnedId)
                                    + " control=" + getServer().getEntity(lateControlId));
                    if (Boolean.getBoolean("kreiscraft.release-disabled-restart")) {
                        check(encounter == null, "disabled restart installs recovery only, no controller");
                        enter(103);
                    } else {
                        check(encounter != null, "enabled restart actual production controller");
                        footing = ground(40, 40);
                        for (int index = 0; index < 4; index++) {
                            register(end, new GameProfile(UUID.randomUUID(), "Restart" + index), GameMode.SURVIVAL);
                        }
                        enter(101);
                    }
                }
                case 103 -> {
                    if (ticks - since < 260) {
                        return;
                    }
                    check(end.getEntities().stream().noneMatch(this::owned) && dragonDeaths == 0
                            && spawnedExperience == 0,
                            "disabled native process recovery remains inert beyond all old effect deadlines");
                    getLogger().info("RELEASE_DISABLED_RESTART_PROBE PASS checks=" + checks + " ticks=" + ticks
                            + " sameDragon=" + firstDragon);
                    finished = true;
                    getServer().shutdown();
                }
                default -> throw new IllegalStateException("unknown stage");
            }
        } catch (Throwable error) {
            fail(error);
        }
    }

    private void observeRotation(int count, Set<String> families) {
        String status = command("status");
        String attack = status.split(", attack=")[1].split(",")[0];
        String selected = status.split(", last-selection=")[1].split(",")[0];
        if (attack.equals("none") && !previousAttack.equals("none")) {
            completion = ticks;
        }
        if (!selected.equals(previousSelection)) {
            check(families.contains(selected), "only permanently unlocked families selected " + selected);
            if (!selections.isEmpty()) {
                check(!selected.equals(selections.getLast()), "no immediate cross-round repeat");
                check(completion >= 0 && (selectionRejected ? ticks - completion == 20
                        : ticks - completion >= 100 && ticks - completion <= 200),
                        "one-second rejected-setup retry or configured 5-10 seconds AFTER actual main completion");
            }
            selections.add(selected);
            selectionRejected = attack.equals("none");
            if (attack.equals("none")) {
                completion = ticks;
            }
            getLogger().info("RELEASE_ROTATION stage=" + stage + " selected=" + selected + " attack=" + attack
                    + " tick=" + ticks + " previousCompletion=" + completion);
        }
        previousAttack = attack;
        previousSelection = selected;
        if (selections.size() >= count && attack.equals("none")) {
            check(new HashSet<>(selections.subList(0, families.size())).equals(families), "whole shuffled round");
            enter(stage == 1 ? 2 : 8);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void entitiesLoad(org.bukkit.event.world.EntitiesLoadEvent event) {
        if (lateOwnedId != null
                && event.getEntities().stream().anyMatch(entity -> entity.getUniqueId().equals(lateOwnedId))) {
            lateLoaded = true;
            getLogger().info("RELEASE_ACTUAL_LATE_LOAD uuid=" + lateOwnedId + " tick=" + ticks);
        }
    }

    private String reload() {
        sinks.getFirst().messages.clear();
        requested = true;
        try {
            check(getServer().dispatchCommand(fixtures.getFirst().getBukkitEntity(), "kreiscraft reload"),
                    "native config reload command");
        } finally {
            requested = false;
        }
        String result = String.join(" | ", sinks.getFirst().messages);
        getLogger().info("RELEASE_CONFIG_REPLY " + result);
        return result;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void unload(org.bukkit.event.world.EntitiesUnloadEvent event) {
        if (unloadMember != null
                && event.getEntities().stream().anyMatch(entity -> entity.getUniqueId().equals(unloadMember))) {
            unloaded = true;
            getLogger().info("RELEASE_ACTUAL_UNLOAD uuid=" + unloadMember + " tick=" + ticks);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void worldUnload(org.bukkit.event.world.WorldUnloadEvent event) {
        if (event.getWorld() == end) {
            worldUnloaded = true;
            getLogger().info("RELEASE_ACTUAL_WORLD_UNLOAD tick=" + ticks);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void worldLoad(org.bukkit.event.world.WorldLoadEvent event) {
        if (worldUnloaded && event.getWorld().getKey().equals(NamespacedKey.minecraft("the_end"))) {
            worldLoaded = true;
            getLogger().info("RELEASE_ACTUAL_WORLD_LOAD tick=" + ticks);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void death(EntityDeathEvent event) {
        if (event.getEntity() == dragon) {
            dragonDeaths++;
            deathExperience = event.getDroppedExp();
        }
        if (event instanceof PlayerDeathEvent) {
            deaths++;
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void respawn(PlayerRespawnEvent event) {
        respawns++;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void quit(PlayerQuitEvent event) {
        quits++;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void join(PlayerJoinEvent event) {
        joins++;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void spawn(EntitySpawnEvent event) {
        if (event.getEntity() instanceof ExperienceOrb orb && event.getLocation().getWorld() == end) {
            spawnedExperience += orb.getExperience();
        }
        if ((stage == 15 || stage == 23) && event.getEntity() instanceof org.bukkit.entity.LargeFireball
                && "rain-projectile".equals(
                        event.getEntity().getPersistentDataContainer().get(ARTIFACT, PersistentDataType.STRING))) {
            check(event.getLocation().getY() == rainGround
                    .get(event.getLocation().getBlockX() + ":" + event.getLocation().getBlockZ()) + expectedRainHeight,
                    "actual Rain launch uses current/next configuration snapshot height" + expectedRainHeight);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void poolDamage(org.bukkit.event.entity.EntityDamageEvent event) {
        if (event.getEntity() instanceof org.bukkit.entity.Player player
                && event.getDamageSource().getCausingEntity() == dragon
                && event.getCause() == org.bukkit.event.entity.EntityDamageEvent.DamageCause.DRAGON_BREATH
                && event.getDamage() == 3 && event.getFinalDamage() > 0) {
            int tick = getServer().getCurrentTick();
            var previous = poolPulseTicks.put(player.getUniqueId(), tick);
            check(previous == null || tick - previous >= 20, "actual global shared pool pulse rate never doubles");
            poolHits++;
            pendingPoolDamage.add(player.getUniqueId());
            if (stage == 9 && arrival >= 0 && departure < 0) {
                landingPoolHits++;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void pickup(com.destroystokyo.paper.event.player.PlayerPickupExperienceEvent event) {
        if (stage == 10) {
            event.setCancelled(true); // Preserve native generated rewards for measurement, not fabricate or alter them.
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void orbRemoval(org.bukkit.event.entity.EntityRemoveEvent event) {
        if (stage == 10 && event.getEntity() instanceof ExperienceOrb orb) {
            getLogger().info("RELEASE_XP_REMOVAL cause=" + event.getCause() + " value=" + orb.getExperience()
                    + " count=" + ((org.bukkit.craftbukkit.entity.CraftExperienceOrb) orb).getHandle().count
                    + " location=" + orb.getLocation());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void egg(io.papermc.paper.event.block.DragonEggFormEvent event) {
        if (event.getBlock().getWorld() == end) {
            if (event.isCancelled()) {
                eggVetoes++;
            } else {
                eggForms++;
            }
            getLogger().info("RELEASE_NATIVE_EGG cancelled=" + event.isCancelled() + " tick=" + ticks);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void phase(EnderDragonChangePhaseEvent event) {
        if (event.getEntity() != dragon || stage != 9) {
            return;
        }
        if (event.getNewPhase() == EnderDragon.Phase.SEARCH_FOR_BREATH_ATTACK_TARGET && arrival < 0) {
            arrival = getServer().getCurrentTick();
            check(end.getEntities().stream().anyMatch(entity -> "pool".equals(entity.getPersistentDataContainer()
                    .get(ARTIFACT, PersistentDataType.STRING))), "old hazard survives actual native arrival");
        }
        if (event.getNewPhase() == EnderDragon.Phase.LEAVE_PORTAL && arrival >= 0) {
            departure = getServer().getCurrentTick();
        }
    }

    private void fail(Throwable error) {
        finished = true;
        getLogger().log(java.util.logging.Level.SEVERE, "RELEASE_CANDIDATE_PROBE FAIL", error);
        getServer().shutdown();
    }

    private final class Sink extends Connection {
        private PacketListener listener;
        private final List<String> messages = new ArrayList<>();

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
            packets++;
            if (packet instanceof net.minecraft.network.protocol.game.ClientboundBundlePacket bundle) {
                bundle.subPackets().forEach(child -> send(child, null, flush));
            }
            if (packet instanceof net.minecraft.network.protocol.common.ClientboundKeepAlivePacket keep
                    && listener instanceof net.minecraft.server.network.ServerCommonPacketListenerImpl common) {
                common.handleKeepAlive(
                        new net.minecraft.network.protocol.common.ServerboundKeepAlivePacket(keep.getId()));
            }
            if (packet instanceof net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket position
                    && listener instanceof net.minecraft.server.network.ServerGamePacketListenerImpl game) {
                game.handleAcceptTeleportPacket(
                        new net.minecraft.network.protocol.game.ServerboundAcceptTeleportationPacket(position.id()));
            }
            if (packet instanceof net.minecraft.network.protocol.game.ClientboundSystemChatPacket chat) {
                var text = chat.content().getString();
                messages.add(text);
                if (requested) {
                    replies++;
                } else if (!text.contains("joined the game") && !text.contains("left the game")
                        && !text.contains("was killed") && !text.contains("died") && !text.contains("advancement")
                        && !text.contains("completed the challenge") && !text.contains("reached the goal")) {
                    automaticChat++;
                    getLogger().info("RELEASE_UNSOLICITED_CHAT " + text);
                }
                if (chat.overlay()) {
                    actionbars++;
                }
            }
            String type = packet.getClass().getSimpleName();
            if (type.contains("Title") || type.contains("Subtitle")) {
                titles++;
            }
            if (type.contains("BossEvent")) {
                bosses++;
            }
            if (type.contains("Particles")) {
                particles++;
            }
            if (type.contains("Sound")) {
                sounds++;
            }
        }

        @Override
        public void flushChannel() {}
    }
}
