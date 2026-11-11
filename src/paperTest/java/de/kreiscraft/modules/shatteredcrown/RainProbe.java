package de.kreiscraft.modules.shatteredcrown;

import com.mojang.authlib.GameProfile;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
import org.bukkit.craftbukkit.entity.CraftFireball;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.LargeFireball;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

/** Version-pinned genuine PlayerList fixtures; production JAR owns all gameplay and native collisions. */
public final class RainProbe extends JavaPlugin implements Listener {
    private static final NamespacedKey ARTIFACT = new NamespacedKey("kreiscraft", "crown_artifact");
    private static final NamespacedKey UNRELATED = new NamespacedKey("rainprobe", "unrelated");
    private final List<ServerPlayer> fixtures = new ArrayList<>();
    private final List<Sink> sinks = new ArrayList<>();
    private final Map<UUID, Location> positions = new HashMap<>();
    private final List<Integer> pulseTicks = new ArrayList<>();
    private World world;
    private Player player;
    private Player other;
    private EnderDragon dragon;
    private EncounterController encounter;
    private Location locked;
    private Location roof;
    private int scenario = -1;
    private int age;
    private int ticks;
    private int checks;
    private int collisions;
    private int launches;
    private int hitAge;
    private int impactHits;
    private int nativeHits;
    private int deflections;
    private int firstFloor;
    private double collisionHealth;
    private boolean done;
    private boolean measuring;
    private boolean automatic;
    private int automaticWarning = -1;
    private int automaticImpact = -1;
    private double pendingPulseHealth = Double.NaN;
    private double pendingPulseLoss;
    private final int[] warningFrames = new int[12];
    private final Map<Location, Material> groundBefore = new HashMap<>();
    private Location arrivalGround;
    private int outsideRadiusHits;
    private int boundaryRadiusHits;
    private int movementPoolStart = -1;
    private int movementPoolHits;
    private Location movementOrigin;
    private EnderCrystal rainCrystal;
    private boolean crystalCollision;
    private final Map<UUID, List<Integer>> sharedPulses = new HashMap<>();
    private int disableStart = -1;
    private double cancelledHealth;
    private final Map<UUID, Integer> poolBirths = new HashMap<>();
    private int sixSecondExpiries;
    private int eightSecondExpiries;

    @Override
    public void onEnable() {
        if (!Boolean.getBoolean("kreiscraft.rain-probe")) {
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }
        Bukkit.getPluginManager().registerEvents(this, this);
        Bukkit.getScheduler().runTask(this, this::start);
    }

    private void check(boolean condition, String message) {
        checks++;
        if (!condition) {
            throw new IllegalStateException(message + " scenario=" + scenario + " age=" + age);
        }
    }

    private Player fixture(String name, Location position, GameMode mode) {
        var server = ((CraftServer) Bukkit.getServer()).getServer();
        var profile = new GameProfile(UUID.randomUUID(), name);
        var nativePlayer = new ServerPlayer(server, ((CraftWorld) world).getHandle(), profile,
                ClientInformation.createDefault());
        nativePlayer.snapTo(position.getX(), position.getY(), position.getZ(), 0, 0);
        Sink sink = new Sink();
        sinks.add(sink);
        fixtures.add(nativePlayer);
        server.getPlayerList().placeNewPlayer(sink, nativePlayer, CommonListenerCookie.createInitial(profile, false));
        var result = nativePlayer.getBukkitEntity();
        result.setGameMode(mode);
        result.setOp(true);
        var armor = new ItemStack[] {new ItemStack(Material.NETHERITE_BOOTS),
                new ItemStack(Material.NETHERITE_LEGGINGS), new ItemStack(Material.NETHERITE_CHESTPLATE),
                new ItemStack(Material.NETHERITE_HELMET)};
        for (var piece : armor) {
            piece.addEnchantment(org.bukkit.enchantments.Enchantment.PROTECTION, 4);
        }
        result.getInventory().setArmorContents(armor);
        return result;
    }

    private void start() {
        try {
            world = Bukkit.getWorld(NamespacedKey.minecraft("the_end"));
            check(world != null, "main native End");
            world.setGameRule(org.bukkit.GameRule.NATURAL_REGENERATION, false);
            world.setGameRule(org.bukkit.GameRule.DO_MOB_SPAWNING, false);
            if (!Boolean.getBoolean("kreiscraft.rain-restart")) {
                World overworld = Bukkit.getWorld(NamespacedKey.minecraft("overworld"));
                var wither = overworld.spawn(new Location(overworld, 0, 140, 0), org.bukkit.entity.Wither.class);
                check(wither.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getBaseValue() == 600
                        && wither.getAttribute(org.bukkit.attribute.Attribute.ATTACK_DAMAGE).getBaseValue() == 40
                        && wither.getAttribute(org.bukkit.attribute.Attribute.ARMOR).getBaseValue() == 8,
                        "simultaneously enabled real Wither module retains its configured enhancement");
                getLogger().info("RAIN_WITHER_DEPENDENCY PASS nativeAttributes=600/40/8");
                wither.remove(); // Attribute/startup check only, not a second claim of #155's charge/blast matrix.
            }
            for (int x = -12; x <= 12; x++) {
                for (int z = -12; z <= 12; z++) {
                    world.setChunkForceLoaded(x, z, true);
                }
            }
            if (Boolean.getBoolean("kreiscraft.rain-restart")) {
                Bukkit.getScheduler().runTaskLater(this, this::restart, 40);
                return;
            }
            locked = new Location(world, 40.5, world.getHighestBlockYAt(40, 40) + 1, 40.5);
            firstFloor = locked.getBlockY() - 1;
            player = fixture("RainFixture", locked, GameMode.SURVIVAL);
            other = fixture("RainAdventure", locked.clone().add(4, 0, 0), GameMode.CREATIVE);
            fixture("RainCreative", locked.clone().add(10, 0, 0), GameMode.CREATIVE);
            fixture("RainSpectator", locked.clone().add(12, 0, 0), GameMode.SPECTATOR);
            fixture("RainArrival", new Location(world, 45.5, locked.getY(), 40.5), GameMode.SURVIVAL);
            fixture("RainOuter", new Location(world, 220, 100, 0), GameMode.SPECTATOR);
            var production = Bukkit.getPluginManager().getPlugin("Kreiscraft");
            for (var listener : HandlerList.getRegisteredListeners(production)) {
                if (listener.getListener() instanceof EncounterController controller) {
                    encounter = controller;
                    break;
                }
            }
            check(encounter != null, "actual production listener");
            Bukkit.getScheduler().runTaskTimer(this, this::tick, 1, 1);
        } catch (Throwable error) {
            finish(false, error.toString());
        }
    }

    private String command(String text) {
        sinks.getFirst().replies.clear();
        check(Bukkit.dispatchCommand(player, "dragondebug " + text), "dispatch " + text);
        return String.join(" | ", sinks.getFirst().replies);
    }

    private boolean artifact(org.bukkit.entity.Entity entity, String kind) {
        return kind.equals(entity.getPersistentDataContainer().get(ARTIFACT, PersistentDataType.STRING));
    }

    private List<LargeFireball> balls() {
        return world.getEntitiesByClass(LargeFireball.class).stream()
                .filter(ball -> artifact(ball, "rain-projectile")).toList();
    }

    private List<TextDisplay> warnings() {
        return world.getEntitiesByClass(TextDisplay.class).stream()
                .filter(display -> artifact(display, "rain-warning")).toList();
    }

    private List<TextDisplay> pools() {
        return world.getEntitiesByClass(TextDisplay.class).stream()
                .filter(display -> artifact(display, "pool")).toList();
    }

    private void begin() {
        measuring = false;
        command("cancel");
        check(balls().isEmpty() && pools().isEmpty() && warnings().isEmpty(), "scoped cleanup");
        if (roof != null) {
            for (int x = -5; x <= 5; x++) {
                for (int z = -5; z <= 5; z++) {
                    roof.clone().add(x, 0, z).getBlock().setType(Material.AIR);
                }
            }
            roof = null;
        }
        int targetX = scenario == 13 ? 24 : 40;
        locked = new Location(world, targetX + 0.5, world.getHighestBlockYAt(targetX, 40) + 1, 40.5);
        if (scenario == 13) {
            // Use the real original east Anchor at z=0, not a summoned duplicate crystal.
            rainCrystal = world.getEntitiesByClass(EnderCrystal.class).stream()
                    .filter(crystal -> artifact(crystal, "anchor") && crystal.getX() == 24.5).findFirst().orElseThrow();
            locked = new Location(world, rainCrystal.getX(),
                    world.getHighestBlockYAt(24, rainCrystal.getLocation().getBlockZ()) + 1,
                    rainCrystal.getZ());
        }
        crystalCollision = false;
        player.teleport(locked);
        player.setVelocity(new Vector());
        player.setHealth(20);
        player.setAbsorptionAmount(0);
        player.setNoDamageTicks(0);
        other.setGameMode(scenario == 6 || scenario == 12 ? GameMode.ADVENTURE : GameMode.CREATIVE);
        other.teleport(new Location(world, 44.5, world.getHighestBlockYAt(44, 40) + 1, 40.5));
        if (scenario == 12) {
            other.teleport(locked);
        }
        other.setHealth(20);
        for (int index = 2; index <= 3; index++) {
            var recipient = fixtures.get(index).getBukkitEntity();
            recipient.setGameMode(scenario == 6 ? (index == 2 ? GameMode.SURVIVAL : GameMode.ADVENTURE)
                    : index == 2 ? GameMode.CREATIVE : GameMode.SPECTATOR);
            if (scenario == 6) {
                int x = index == 2 ? 40 : 44;
                recipient.teleport(new Location(world, x + 0.5, world.getHighestBlockYAt(x, 44) + 1, 44.5));
                recipient.setHealth(20);
            }
        }
        world.setDifficulty(
                new Difficulty[] {Difficulty.EASY, Difficulty.NORMAL, Difficulty.HARD}[scenario < 3 ? scenario : 1]);
        positions.clear();
        pulseTicks.clear();
        sharedPulses.clear();
        poolBirths.clear();
        sixSecondExpiries = 0;
        eightSecondExpiries = 0;
        collisions = 0;
        launches = 0;
        hitAge = -1;
        impactHits = 0;
        nativeHits = 0;
        deflections = 0;
        outsideRadiusHits = 0;
        boundaryRadiusHits = 0;
        sinks.getFirst().particles = 0;
        sinks.getFirst().sounds = 0;
        age = 0;
        java.util.Arrays.fill(warningFrames, 0);
        measuring = true;
        if (scenario == 11) {
            player.teleport(locked.clone().add(0, 40, 0));
        }
        if (scenario == 4) {
            roof = locked.clone().add(0, 3, 0);
            for (int x = -5; x <= 5; x++) {
                for (int z = -5; z <= 5; z++) {
                    roof.clone().add(x, 0, z).getBlock().setType(Material.END_STONE);
                }
            }
        }
        groundBefore.clear();
        for (int dx = -5; dx <= 5; dx++) {
            for (int dz = -5; dz <= 5; dz++) {
                if (dx * dx + dz * dz <= 25) {
                    int x = locked.getBlockX() + dx;
                    int z = locked.getBlockZ() + dz;
                    var block = world.getBlockAt(x, world.getHighestBlockYAt(x, z), z);
                    groundBefore.put(block.getLocation(), block.getType());
                }
            }
        }
        arrivalGround = world.getBlockAt(45, world.getHighestBlockYAt(45, 40), 40).getLocation();
        String result =
                command("trigger fireball-rain" + (scenario == 6 || scenario == 12 ? "" : " " + player.getName()));
        check(result.contains("Started RAIN"), "production Rain started: " + result);
        check(warnings().size() == (scenario == 6 ? 4 : scenario == 12 ? 2 : 1),
                "every eligible target, no observers/arrival");
        if (scenario == 3 || scenario == 11) {
            player.teleport(locked.clone().add(-10, scenario == 11 ? 40 : 0, 0));
        }
        getLogger().info("RAIN_BEGIN scenario=" + scenario + " tick=" + ticks + " locked=" + locked
                + " difficulty=" + world.getDifficulty() + " combat=" + encounter.combatPopulation()
                + " scaling=" + encounter.scalingPopulation());
    }

    private void tick() {
        if (done) {
            return;
        }
        try {
            ticks++;
            fixtures.forEach(fixture -> fixture.connection.tick());
            check(ticks < 4500, "bounded native matrix");
            if (automatic) {
                automaticTick();
                return;
            }
            if (scenario < 0) {
                dragon = world.getEnderDragonBattle().getEnderDragon();
                if (ticks < 80 || dragon == null || !dragon.getPersistentDataContainer()
                        .has(new NamespacedKey("kreiscraft", "crown_original_health"))) {
                    return;
                }
                check(fixtures.stream().allMatch(fixture -> fixture.connection.hasClientLoaded()
                        && fixture.getBukkitEntity().isOnline()), "genuine PlayerList loaded fixtures");
                check(encounter.scalingPopulation() == 6 && encounter.combatPopulation() == 1,
                        "combat eligibility differs from scaling");
                long opening =
                        world.getEntitiesByClass(Mob.class).stream().filter(mob -> artifact(mob, "defender")).count();
                getLogger().info("RAIN_ROSTER opening=" + opening + " wavePopulation=" + encounter.wavePopulation());
                check(opening == Math.min(encounter.wavePopulation(), 20) * 16,
                        "current mounted opening roster for frozen first-entry units, not later End population");
                // Defeat the opening defenders through genuine native deaths for the pure damage calibration.
                // Separate cover cases deliberately exercise early obstruction; dragon AI remains continuously on.
                world.getEntitiesByClass(Mob.class).stream().filter(mob -> artifact(mob, "defender"))
                        .forEach(mob -> mob.setHealth(0));
                scenario = Integer.getInteger("kreiscraft.rain-start", 0);
                begin();
                return;
            }
            age++;
            check(dragon.hasAI(), "native dragon AI never disabled");
            for (var ball : balls()) {
                var old = positions.put(ball.getUniqueId(), ball.getLocation());
                if (old == null) {
                    throw new IllegalStateException("native launch event was not observed");
                } else {
                    check(Math.abs(ball.getX() - old.getX()) < 0.001 && Math.abs(ball.getZ() - old.getZ()) < 0.001,
                            "no native homing/deflection");
                    check(Math.abs(ball.getY() - old.getY() + (scenario == 10 && age > 30 ? 0 : 0.5)) < 0.001,
                            "native descent (or deliberately stopped timeout fixture)");
                }
            }
            if (scenario == 5 && age == 30) {
                var ball = balls().getFirst();
                Vector before = ball.getVelocity();
                Object owner = ball.getShooter();
                fixtures.getFirst().attack(((CraftFireball) ball).getHandle());
                check(ball.getVelocity().equals(before) && ball.getShooter().equals(owner) && deflections == 1,
                        "actual native Player.attack cannot deflect or reattribute");
                getLogger()
                        .info("RAIN_NO_DEFLECTION age=" + age + " before=" + before + " after=" + ball.getVelocity());
            }
            if (scenario == 0 && (age == 70 || age == 90)) {
                double x = locked.getX() - (age == 70 ? 5.01 : 5);
                player.teleport(new Location(world, x, world.getHighestBlockYAt((int) Math.floor(x), 40) + 1, 40.5));
                player.setVelocity(new Vector());
                player.setHealth(20);
                player.setNoDamageTicks(0);
            }
            if (scenario == 0 && age == 89) {
                check(outsideRadiusHits == 0 && player.getHealth() == 20, "actual radius 5.01 excludes pool damage");
            }
            if (scenario == 0 && age == 97) {
                check(boundaryRadiusHits == 1 && Math.abs(player.getHealth() - 19.1) < 0.001,
                        "inclusive radius five applies the raw Easy pulse's actual 0.9HP loss");
                getLogger().info("RAIN_RADIUS PASS outside=5.01/0hits boundary=5/1hit health=" + player.getHealth());
            }
            if (scenario == 7 && age == 10) {
                command("cancel");
            }
            if (scenario == 8 && age == 30) {
                balls().forEach(org.bukkit.entity.Entity::remove);
            }
            if (scenario == 14 && age == 21) {
                check(encounter.depositBreathPool(player.getLocation(), 5, 6),
                        "sibling pool beside live native Rain projectile");
            }
            if (scenario == 14 && age == 30) {
                check(balls().size() == 1 && pools().size() == 1, "actual projectile and pool both live before cancel");
                command("cancel");
                check(balls().isEmpty() && warnings().isEmpty() && pools().isEmpty(),
                        "actual cancellation drains projectile and siblings");
                cancelledHealth = player.getHealth();
                getLogger().info("RAIN_ACTIVE_CANCEL PASS age=" + age + " health=" + cancelledHealth);
            }
            if (scenario == 10 && age == 30) {
                balls().forEach(ball -> ball.setVelocity(new Vector()));
            }
            if (scenario == 10 && age == 121) {
                check(balls().isEmpty() && command("status").contains("main=idle"),
                        "native stopped-projectile timeout releases main");
                getLogger().info("RAIN_TIMEOUT PASS age=" + age);
            }
            if (age < 60 && scenario != 7 && !(scenario == 14 && age >= 30)) {
                check(warnings().size() == (scenario == 6 ? 4 : scenario == 12 ? 2 : 1),
                        "full three-second locked warning");
                if (scenario != 6) {
                    check(warnings().getFirst().getX() == locked.getX()
                            && warnings().getFirst().getZ() == locked.getZ(), "warning-time ground lock");
                }
            }
            if (scenario == 6 && age == 65) {
                check(collisions == 4 && pools().size() == 4,
                        "four combat targets get Rain impacts/pools; Prey requests only three");
                check(fixtures.get(4).getBukkitEntity().getHealth() == 20 && arrivalGround.getBlock().isEmpty(),
                        "arrival player protected while its actual ground is permanently destroyed");
                check(encounter.depositBreathPool(new Location(world, 42.5,
                        world.getHighestBlockYAt(42, 40) + 1, 40.5), 5, 8), "shared Prey-style pool API");
                player.teleport(new Location(world, 42.5, world.getHighestBlockYAt(42, 40) + 1, 40.5));
                player.setHealth(20);
                player.setNoDamageTicks(0);
                pulseTicks.clear();
                check(pools().size() == 5, "native five-way shared pool overlap");
                pools().forEach(pool -> poolBirths.putIfAbsent(pool.getUniqueId(), age));
            }
            if (scenario == 12 && age == 65) {
                check(collisions == 2 && pools().size() == 2, "co-located native Rain targets leave two shared pools");
            }
            if (scenario == 6 && age >= 66) {
                if (Double.isFinite(pendingPulseHealth)) {
                    check(Math.abs(pendingPulseHealth - player.getHealth() - pendingPulseLoss) < 0.001,
                            "actual shared pool health loss, not only event arguments");
                    getLogger().info("RAIN_POOL_HEALTH age=" + age + " before=" + pendingPulseHealth
                            + " after=" + player.getHealth() + " loss=" + pendingPulseLoss);
                    pendingPulseHealth = Double.NaN;
                }
                player.setNoDamageTicks(0);
                player.setHealth(20);
            }
            if (scenario == 6 && age == 176) {
                check(pools().size() == 1, "Rain six-second pools expire independently before eight-second sibling");
                check(sixSecondExpiries == 4,
                        "actual exclusive six-second removal callbacks, not a pre-callback scheduler sample");
                check(pulseTicks.size() >= 5, "actual damaging overlap pulses");
                check(sharedPulses.size() == 4, "all four actual combat players receive shared native pulses");
                for (var entry : sharedPulses.entrySet()) {
                    check(entry.getValue().size() >= 5, "native shared pulse coverage for " + entry.getKey());
                    for (int index = 1; index < entry.getValue().size(); index++) {
                        check(entry.getValue().get(index) - entry.getValue().get(index - 1) >= 20,
                                "native per-player global pulse ceiling for " + entry.getKey());
                    }
                }
                for (int i = 1; i < pulseTicks.size(); i++) {
                    check(pulseTicks.get(i) - pulseTicks.get(i - 1) >= 20, "global one pulse/player/second");
                }
                getLogger().info("RAIN_OVERLAP pulses=" + pulseTicks + " allPlayers=" + sharedPulses + " remaining="
                        + pools().size());
            }
            if (age == 226) {
                if (scenario == 6) {
                    check(eightSecondExpiries == 1,
                            "shared eight-second pool independently expires at its exact deadline");
                }
                verify();
                if (++scenario <= 14) {
                    begin();
                } else {
                    lifecycle();
                }
            }
        } catch (Throwable error) {
            finish(false, error.toString());
        }
    }

    private void verify() {
        check(balls().isEmpty() && warnings().isEmpty() && pools().isEmpty(),
                "exclusive expiry and projectile cleanup");
        check(command("status").contains("main=idle"), "main completed without waiting for pools");
        check(nativeHits == 0, "zero native fireball damage/explosion duplicates");
        long removed = groundBefore.entrySet().stream().filter(entry -> entry.getValue().isSolid()
                && entry.getKey().getBlock().isEmpty()).count();
        if (scenario < 7 || scenario == 9 || scenario >= 11 && scenario <= 13) {
            check(removed == 81, "actual radius-five crater removes eighty-one sampled native ground blocks");
        } else {
            check(removed == 0, "no crater from cancelled/lost/stopped projectiles");
        }
        if (scenario < 3 || scenario == 5) {
            check(collisions == 1 && impactHits == 1 && Math.abs(collisionHealth - 16) < 0.001,
                    "calibrated once-only clean impact 20->16HP");
            check(hitAge >= 54 && hitAge <= 60, "approximately two-second native player-collision descent");
            check(locked.getBlock().getRelative(0, -1, 0).isEmpty(), "permanent actual ground crater");
            check(locked.getBlock().getRelative(0, -2, 0).getType().isSolid(), "one-block shallow crater");
        } else if (scenario == 3 || scenario == 11) {
            check(collisions == 1 && impactHits == 0 && hitAge >= 59 && hitAge <= 61,
                    "dodge hits locked ground not moved player");
        } else if (scenario == 4) {
            check(collisions == 1 && hitAge < 56 && impactHits == 0 && collisionHealth == 20,
                    "actual earlier cover collision protects player");
            check(roof.getBlock().isEmpty() && locked.getBlock().getRelative(0, -1, 0).getType().isSolid(),
                    "cover crater at actual collision not original floor");
        } else if (scenario == 7 || scenario == 8 || scenario == 10 || scenario == 14) {
            check(collisions == 0 && impactHits == 0, "cancel/lost projectile has no delayed damage/crater");
            if (scenario == 14) {
                check(player.getHealth() == cancelledHealth, "no delayed damage after native active cancellation");
            }
        } else if (scenario == 13) {
            check(crystalCollision && rainCrystal.isValid() && encounter.unlockedAttacks().size() == 2,
                    "actual native Rain crystal collision preserves the owned objective/shield gate");
        } else if (scenario == 12) {
            check(collisions == 2 && hitAge >= 54 && impactHits >= 1,
                    "co-located targets both reach native player/ground collision, not each other at launch");
        } else if (scenario == 9) {
            check(impactHits == 1 && collisionHealth == 20, "ordinary public damage cancellation preserved");
        }
        check(sinks.getFirst().particles > 0 && sinks.getFirst().sounds > 0, "real Paper particle/sound conversion");
        for (int frame = 0; frame < (scenario == 7 ? 2 : scenario == 14 ? 6 : 12); frame++) {
            check(warningFrames[frame] > 0, "actual flame warning particles throughout full warning frame=" + frame);
        }
        getLogger().info("RAIN_CASE PASS scenario=" + scenario + " collisions=" + collisions + " impactHits="
                + impactHits
                + " hitAge=" + hitAge + " collisionHP=" + collisionHealth + " particles=" + sinks.getFirst().particles);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void damage(EntityDamageEvent event) {
        if (!measuring) {
            return;
        }
        if (artifact(event.getEntity(), "rain-projectile")) {
            deflections++;
            return;
        }
        var direct = event.getDamageSource().getDirectEntity();
        if (direct != null && artifact(direct, "rain-projectile")) {
            nativeHits++;
        }
        if (event.getEntity() instanceof Player && event.getDamageSource().getCausingEntity() == dragon
                && event.getDamageSource().getDirectEntity() == dragon
                && event.getDamageSource().getDamageType() == org.bukkit.damage.DamageType.DRAGON_BREATH) {
            if (scenario == 6 && age >= 65 && event.getDamage() < 8) {
                sharedPulses.computeIfAbsent(event.getEntity().getUniqueId(), ignored -> new ArrayList<>()).add(age);
            }
            if (movementPoolStart >= 0 && event.getEntity().equals(player) && event.getDamage() < 8) {
                movementPoolHits++;
            }
            if (event.getEntity().equals(player) && event.getDamage() > 8) {
                impactHits++;
                if (scenario == 9) {
                    event.setCancelled(true);
                }
            } else if (event.getEntity().equals(player) && scenario == 6 && age >= 65) {
                pulseTicks.add(age);
                pendingPulseHealth = player.getHealth();
                pendingPulseLoss = event.getFinalDamage();
                check(Math.abs(event.getDamage() - 3) < 0.001, "raw configured pulse damage unchanged");
                getLogger()
                        .info("RAIN_POOL age=" + age + " base=" + event.getDamage() + " final=" + event.getFinalDamage()
                                + " hpBefore=" + player.getHealth());
            } else if (event.getEntity().equals(player) && scenario == 0) {
                if (age >= 70 && age < 90) {
                    outsideRadiusHits++;
                } else if (age >= 90 && age <= 97) {
                    boundaryRadiusHits++;
                }
            }
        } else if (event.getEntity() instanceof Player) {
            event.setCancelled(true); // Isolate native attack/pool measurements, never disable dragon or defender AI.
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void removed(org.bukkit.event.entity.EntityRemoveEvent event) {
        if (scenario == 6 && artifact(event.getEntity(), "pool")) {
            Integer birth = poolBirths.remove(event.getEntity().getUniqueId());
            if (birth != null) {
                int lifetime = age - birth;
                check(lifetime == 120 || lifetime == 160, "actual native pool lifetime deadline=" + lifetime);
                if (lifetime == 120) {
                    sixSecondExpiries++;
                } else {
                    eightSecondExpiries++;
                }
                getLogger().info("RAIN_POOL_EXPIRE born=" + birth + " removed=" + age + " lifetimeTicks=" + lifetime);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void prime(org.bukkit.event.entity.ExplosionPrimeEvent event) {
        if (measuring && artifact(event.getEntity(), "rain-projectile")) {
            check(event.isCancelled(), "actual native explosion prime cancelled");
            getLogger().info("RAIN_NATIVE_PRIME age=" + age + " cancelled=" + event.isCancelled());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void launch(org.bukkit.event.entity.EntitySpawnEvent event) {
        if (measuring && artifact(event.getEntity(), "rain-projectile")) {
            try {
                launches++;
                check(automatic || age == 20, "native launch after exactly one second");
                Location position = event.getEntity().getLocation();
                check(warnings().stream().anyMatch(warning -> warning.getX() == position.getX()
                        && warning.getZ() == position.getZ()
                        && Math.abs(position.getY() - warning.getY() - 18.5) < 0.001),
                        "real twenty-block locked launch");
                positions.put(event.getEntity().getUniqueId(), position);
                getLogger().info("RAIN_LAUNCH scenario=" + scenario + " age=" + age + " at=" + position
                        + " velocity=" + event.getEntity().getVelocity());
            } catch (Throwable error) {
                finish(false, error.toString());
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void impact(ProjectileHitEvent event) {
        if (measuring && artifact(event.getEntity(), "rain-projectile")) {
            collisions++;
            if (scenario == 6) {
                pools().forEach(pool -> poolBirths.putIfAbsent(pool.getUniqueId(), age));
            }
            if (scenario == 13) {
                crystalCollision = event.getHitEntity() == rainCrystal;
            }
            hitAge = age;
            collisionHealth = player.getHealth();
            double hitPlayerHealth = event.getHitEntity() instanceof Player target ? target.getHealth() : -1;
            if (scenario == 6) {
                check(Math.abs(hitPlayerHealth - 16) < 0.001,
                        "each of four clean native player impacts loses exactly four HP");
            }
            getLogger().info(
                    "RAIN_COLLISION scenario=" + scenario + " age=" + age + " at=" + event.getEntity().getLocation()
                            + " block=" + event.getHitBlock() + " entity=" + event.getHitEntity() + " cancelled="
                            + event.isCancelled()
                            + " hp=" + collisionHealth + " hitPlayerHP=" + hitPlayerHealth + " pools="
                            + pools().size());
            check(command("status").contains(collisions == launches ? "main=idle" : "main=executing"),
                    "main resolves exactly after last actual projectile");
            if (automatic && collisions == launches) {
                automaticImpact = age;
            }
        }
    }

    private void lifecycle() throws Exception {
        measuring = false;
        command("trigger fireball-rain " + player.getName());
        command("wave 2");
        check(warnings().isEmpty() && balls().isEmpty() && pools().isEmpty(), "wave replacement drains Rain scope");
        var second = world.getEntitiesByClass(Mob.class).stream().filter(mob -> artifact(mob, "defender")).toList();
        int population = Math.min(encounter.wavePopulation(), 20);
        int secondTotal = 5 * population + 2 * ((7 * population + 9) / 10) + (3 * population + 1) / 2;
        check(second.size() == secondTotal && second.stream().map(org.bukkit.entity.Entity::getType)
                .collect(java.util.stream.Collectors.toSet())
                .equals(java.util.Set.of(org.bukkit.entity.EntityType.RAVAGER, org.bukkit.entity.EntityType.GUARDIAN,
                        org.bukkit.entity.EntityType.PIGLIN_BRUTE, org.bukkit.entity.EntityType.VINDICATOR,
                        org.bukkit.entity.EntityType.BLAZE, org.bukkit.entity.EntityType.BREEZE,
                        org.bukkit.entity.EntityType.VEX)),
                "actual latest seven-species second-wave roster");
        getLogger().info("RAIN_ROSTER second=" + second.size() + " species="
                + second.stream().map(org.bukkit.entity.Entity::getType).distinct().toList());
        check(!encounter.availableAttacks().contains(EncounterController.AttackFamily.RAIN),
                "wave-two shield has not unlocked Rain");
        for (Mob mob : world.getEntitiesByClass(Mob.class)) {
            if (artifact(mob, "defender")) {
                mob.setHealth(0);
            }
        }
        for (EnderCrystal crystal : world.getEntitiesByClass(EnderCrystal.class)) {
            if (artifact(crystal, "anchor")) {
                ((org.bukkit.craftbukkit.entity.CraftEnderCrystal) crystal).getHandle().hurtServer(
                        ((CraftWorld) world).getHandle(),
                        fixtures.getFirst().damageSources().playerAttack(fixtures.getFirst()), 1);
            }
        }
        check(encounter.availableAttacks().contains(EncounterController.AttackFamily.RAIN)
                && !encounter.availableAttacks().contains(EncounterController.AttackFamily.SMASH),
                "four actual wave-two crystals unlock runnable Rain, not Smash");
        other.setGameMode(GameMode.ADVENTURE);
        other.teleport(new Location(world, 44.5, world.getHighestBlockYAt(44, 40) + 1, 40.5));
        player.teleport(new Location(world, 40.5, world.getHighestBlockYAt(40, 40) + 1, 40.5));
        for (int index = 2; index <= 3; index++) {
            var recipient = fixtures.get(index).getBukkitEntity();
            int x = index == 2 ? 40 : 44;
            recipient.setGameMode(index == 2 ? GameMode.SURVIVAL : GameMode.ADVENTURE);
            recipient.teleport(new Location(world, x + 0.5, world.getHighestBlockYAt(x, 44) + 1, 44.5));
            recipient.setHealth(20);
        }
        age = 0;
        launches = 0;
        collisions = 0;
        automatic = true;
        measuring = true;
        command("resume");
    }

    private void automaticTick() throws Exception {
        age++;
        check(dragon.hasAI(), "automatic native dragon AI remains enabled");
        if (disableStart >= 0) {
            if (age - disableStart == 21) {
                check(balls().size() == 1 && pools().size() == 1 && warnings().size() == 1,
                        "native projectile, warning and sibling pool active at plugin disable");
                Bukkit.getPluginManager().disablePlugin(Bukkit.getPluginManager().getPlugin("Kreiscraft"));
                check(balls().isEmpty() && warnings().isEmpty() && pools().isEmpty(),
                        "actual plugin disable drains native projectile and all sibling entities");
                getLogger().info("RAIN_ACTIVE_DISABLE PASS age=" + age);
                persist();
            }
            return;
        }
        if (movementPoolStart >= 0) {
            if (age - movementPoolStart == 5) {
                check(movementPoolHits == 1 && Math.abs(player.getHealth() - 18.92) < 0.001
                        && dragon.getLocation().distanceSquared(movementOrigin) > 0.001,
                        "shared pool damages during actual native Dive movement, not just a reserved main");
                getLogger().info("RAIN_MOVEMENT_POOL PASS hits=" + movementPoolHits + " HP=20->" + player.getHealth()
                        + " dragonFrom=" + movementOrigin + " dragonTo=" + dragon.getLocation());
                finalCleanup();
            }
            return;
        }
        player.setHealth(20);
        other.setHealth(20);
        player.setNoDamageTicks(0);
        other.setNoDamageTicks(0);
        for (int index = 2; index <= 3; index++) {
            fixtures.get(index).getBukkitEntity().setHealth(20);
            fixtures.get(index).getBukkitEntity().setNoDamageTicks(0);
        }
        String state = command("status");
        if (automaticWarning < 0 && state.contains("attack=RAIN")) {
            automaticWarning = age;
            check(warnings().size() == 4, "automatic Rain targets EVERY living combat player, not Prey's three");
            getLogger().info("RAIN_AUTOMATIC warningAge=" + age + " status=" + state);
        }
        String selected = state.split(", last-selection=")[1].split(",")[0];
        if (automaticImpact >= 0 && !selected.equals("RAIN")) {
            check(collisions == 4 && launches == 4, "automatic native projectiles all actually resolved");
            check(age - automaticImpact >= 100 && age - automaticImpact <= 101, "native post-main five-second gap");
            getLogger().info("RAIN_AUTOMATIC PASS warning=" + automaticWarning + " lastImpact=" + automaticImpact
                    + " nextSelection=" + age + " selected=" + selected + " poolsRemaining=" + pools().size()
                    + " status=" + state);
            player.teleport(new Location(world, 40.5, world.getHighestBlockYAt(40, 40) + 1, 40.5));
            check(command("trigger dive " + player.getName()).contains("Started DIVE"),
                    "production native Dive for sibling pool check");
            player.setHealth(20);
            player.setNoDamageTicks(0);
            check(encounter.depositBreathPool(player.getLocation(), 5, 6),
                    "shared pool deposited during native movement");
            movementOrigin = dragon.getLocation();
            movementPoolStart = age;
        }
    }

    private void finalCleanup() throws Exception {
        measuring = false;
        command("trigger fireball-rain " + player.getName());
        check(encounter.depositBreathPool(player.getLocation(), 5, 6), "native disable sibling pool setup");
        disableStart = age;
    }

    private void persist() {
        var orphan = world.spawn(new Location(world, 150.5, 130, 150.5), LargeFireball.class);
        orphan.getPersistentDataContainer().set(ARTIFACT, PersistentDataType.STRING, "rain-projectile");
        orphan.setVelocity(new Vector());
        var unrelated = world.spawn(new Location(world, 154.5, 130, 150.5), LargeFireball.class);
        unrelated.getPersistentDataContainer().set(UNRELATED, PersistentDataType.BYTE, (byte) 1);
        unrelated.setVelocity(new Vector());
        getConfig().set("first-floor", firstFloor);
        saveConfig();
        world.save();
        finish(true, "nativeCollision+descent+noDeflection+health+craters+sharedPools+cleanup checks=" + checks);
    }

    private void restart() {
        try {
            check(balls().isEmpty(), "restart stale Rain projectile recovered");
            check(world.getEntitiesByClass(LargeFireball.class).stream().anyMatch(
                    ball -> ball.getPersistentDataContainer().has(UNRELATED)), "unrelated native projectile retained");
            check(world.getBlockAt(40, getConfig().getInt("first-floor"), 40).isEmpty(),
                    "permanent crater survives restart");
            getLogger().info("RAIN_RESTART_PROBE PASS checks=" + checks);
            done = true;
            Bukkit.shutdown();
        } catch (Throwable error) {
            finish(false, error.toString());
        }
    }

    private void finish(boolean success, String message) {
        if (done) {
            return;
        }
        done = true;
        getLogger().info("RAIN_PROBE " + (success ? "PASS " : "FAIL ") + message);
        Bukkit.shutdown();
    }

    @Override
    public void onDisable() {
        sinks.forEach(sink -> sink.channel.close());
    }

    private final class Sink extends Connection {
        private PacketListener listener;
        private final List<String> replies = new ArrayList<>();
        private int particles;
        private int sounds;

        private Sink() {
            super(PacketFlow.SERVERBOUND);
            channel = new io.netty.channel.embedded.EmbeddedChannel();
            address = new java.net.InetSocketAddress("127.0.0.1", 0);
        }

        @Override
        public <T extends PacketListener> void setupInboundProtocol(ProtocolInfo<T> protocol, T value) {
            listener = value;
        }

        @Override
        public PacketListener getPacketListener() {
            return listener;
        }

        @Override
        public void send(Packet<?> packet, io.netty.channel.ChannelFutureListener callback, boolean flush) {
            if (packet instanceof net.minecraft.network.protocol.BundlePacket<?> bundle) {
                bundle.subPackets().forEach(nested -> send(nested, null, false));
            } else if (packet instanceof net.minecraft.network.protocol.common.ClientboundKeepAlivePacket keep
                    && listener instanceof net.minecraft.server.network.ServerCommonPacketListenerImpl common) {
                common.handleKeepAlive(
                        new net.minecraft.network.protocol.common.ServerboundKeepAlivePacket(keep.getId()));
            } else if (packet instanceof net.minecraft.network.protocol.game.ClientboundSystemChatPacket chat) {
                replies.add(chat.content().getString());
            } else if (measuring
                    && packet instanceof net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket particle) {
                particles++;
                if (particle.getParticle().getType() == net.minecraft.core.particles.ParticleTypes.FLAME && age < 60) {
                    warningFrames[Math.max(0, age / 5)]++;
                    if (scenario != 6 && !automatic) {
                        check(Math.abs(Math.hypot(particle.getX() - locked.getX(), particle.getZ() - locked.getZ())
                                - 5) < 0.001, "real warning particle positions match the locked radius-five pool");
                    }
                }
                if (particle.getParticle().getType() == net.minecraft.core.particles.ParticleTypes.DRAGON_BREATH) {
                    check(particle.getParticle() instanceof net.minecraft.core.particles.PowerParticleOption power
                            && power.getPower() == 1.0f, "supported native breath particle payload");
                }
            } else if (measuring && packet instanceof net.minecraft.network.protocol.game.ClientboundSoundPacket) {
                sounds++;
            }
        }

        @Override
        public void flushChannel() {}
    }
}
