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
            for (int x = -1; x <= 1; x++) {
                for (int z = -1; z <= 1; z++) {
                    roof.clone().add(x, 0, z).getBlock().setType(Material.AIR);
                }
            }
            roof = null;
        }
        locked.setY(world.getHighestBlockYAt(40, 40) + 1);
        player.teleport(locked);
        player.setVelocity(new Vector());
        player.setHealth(20);
        player.setAbsorptionAmount(0);
        player.setNoDamageTicks(0);
        other.setGameMode(scenario == 6 ? GameMode.ADVENTURE : GameMode.CREATIVE);
        other.teleport(new Location(world, 44.5, world.getHighestBlockYAt(44, 40) + 1, 40.5));
        other.setHealth(20);
        world.setDifficulty(
                new Difficulty[] {Difficulty.EASY, Difficulty.NORMAL, Difficulty.HARD}[scenario < 3 ? scenario : 1]);
        positions.clear();
        pulseTicks.clear();
        collisions = 0;
        launches = 0;
        hitAge = -1;
        impactHits = 0;
        nativeHits = 0;
        deflections = 0;
        sinks.getFirst().particles = 0;
        sinks.getFirst().sounds = 0;
        age = 0;
        measuring = true;
        if (scenario == 4) {
            roof = locked.clone().add(0, 3, 0);
            for (int x = -1; x <= 1; x++) {
                for (int z = -1; z <= 1; z++) {
                    roof.clone().add(x, 0, z).getBlock().setType(Material.END_STONE);
                }
            }
        }
        String result = command("trigger fireball-rain" + (scenario == 6 ? "" : " " + player.getName()));
        check(result.contains("Started RAIN"), "production Rain started: " + result);
        check(warnings().size() == (scenario == 6 ? 2 : 1), "every eligible target, no observers/arrival");
        if (scenario == 3) {
            player.teleport(locked.clone().add(10, 0, 0));
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
                // Defeat the opening defenders through genuine native deaths for the pure damage calibration.
                // Separate cover cases deliberately exercise early obstruction; dragon AI remains continuously on.
                world.getEntitiesByClass(Mob.class).stream().filter(mob -> artifact(mob, "defender"))
                        .forEach(mob -> mob.setHealth(0));
                scenario = 0;
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
                    check(Math.abs(ball.getY() - old.getY() + 0.5) < 0.001, "native half-block descent each tick");
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
            if (scenario == 7 && age == 10) {
                command("cancel");
            }
            if (scenario == 8 && age == 30) {
                balls().forEach(org.bukkit.entity.Entity::remove);
            }
            if (age < 60 && scenario != 7) {
                check(warnings().size() == (scenario == 6 ? 2 : 1), "full three-second locked warning");
                if (scenario != 6) {
                    check(warnings().getFirst().getX() == locked.getX()
                            && warnings().getFirst().getZ() == locked.getZ(), "warning-time ground lock");
                }
            }
            if (scenario == 6 && age == 65) {
                check(collisions == 2 && pools().size() == 2, "actual Rain pools at both impacts");
                check(encounter.depositBreathPool(new Location(world, 42.5,
                        world.getHighestBlockYAt(42, 40) + 1, 40.5), 5, 8), "shared Prey-style pool API");
                player.teleport(new Location(world, 42.5, world.getHighestBlockYAt(42, 40) + 1, 40.5));
                player.setHealth(20);
                player.setNoDamageTicks(0);
                pulseTicks.clear();
                check(pools().size() == 3, "native three-way pool overlap");
            }
            if (scenario == 6 && age >= 66) {
                player.setNoDamageTicks(0);
                player.setHealth(20);
            }
            if (scenario == 6 && age == 175) {
                check(pools().size() == 1, "Rain six-second pools expire independently before eight-second sibling");
                check(pulseTicks.size() >= 5, "actual damaging overlap pulses");
                for (int i = 1; i < pulseTicks.size(); i++) {
                    check(pulseTicks.get(i) - pulseTicks.get(i - 1) >= 20, "global one pulse/player/second");
                }
                getLogger().info("RAIN_OVERLAP pulses=" + pulseTicks + " remaining=" + pools().size());
            }
            if (age == 225) {
                verify();
                if (++scenario <= 9) {
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
        if (scenario < 3 || scenario == 5) {
            check(collisions == 1 && impactHits == 1 && Math.abs(collisionHealth - 16) < 0.001,
                    "calibrated once-only clean impact 20->16HP");
            check(hitAge >= 54 && hitAge <= 60, "approximately two-second native player-collision descent");
            check(locked.getBlock().getRelative(0, -1, 0).isEmpty(), "permanent actual ground crater");
            check(locked.getBlock().getRelative(0, -2, 0).getType().isSolid(), "one-block shallow crater");
        } else if (scenario == 3) {
            check(collisions == 1 && impactHits == 0 && hitAge >= 59 && hitAge <= 61,
                    "dodge hits locked ground not moved player");
        } else if (scenario == 4) {
            check(collisions == 1 && hitAge < 56 && impactHits == 0 && collisionHealth == 20,
                    "actual earlier cover collision protects player");
            check(roof.getBlock().isEmpty() && locked.getBlock().getRelative(0, -1, 0).getType().isSolid(),
                    "cover crater at actual collision not original floor");
        } else if (scenario == 7 || scenario == 8) {
            check(collisions == 0 && impactHits == 0, "cancel/lost projectile has no delayed damage/crater");
        } else if (scenario == 9) {
            check(impactHits == 1 && collisionHealth == 20, "ordinary public damage cancellation preserved");
        }
        check(sinks.getFirst().particles > 0 && sinks.getFirst().sounds > 0, "real Paper particle/sound conversion");
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
        if (event.getEntity().equals(player) && event.getDamageSource().getCausingEntity() == dragon
                && event.getDamageSource().getDamageType() == org.bukkit.damage.DamageType.DRAGON_BREATH) {
            if (event.getDamage() > 8) {
                impactHits++;
                if (scenario == 9) {
                    event.setCancelled(true);
                }
            } else if (scenario == 6 && age >= 65) {
                pulseTicks.add(age);
                check(Math.abs(event.getDamage() - 3) < 0.001, "raw configured pulse damage unchanged");
                getLogger()
                        .info("RAIN_POOL age=" + age + " base=" + event.getDamage() + " final=" + event.getFinalDamage()
                                + " hpBefore=" + player.getHealth());
            }
        } else if (event.getEntity() instanceof Player) {
            event.setCancelled(true); // Isolate native attack/pool measurements, never disable dragon or defender AI.
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
            hitAge = age;
            collisionHealth = player.getHealth();
            getLogger().info(
                    "RAIN_COLLISION scenario=" + scenario + " age=" + age + " at=" + event.getEntity().getLocation()
                            + " block=" + event.getHitBlock() + " entity=" + event.getHitEntity() + " cancelled="
                            + event.isCancelled()
                            + " hp=" + collisionHealth + " pools=" + pools().size());
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
        player.setHealth(20);
        other.setHealth(20);
        player.setNoDamageTicks(0);
        other.setNoDamageTicks(0);
        String state = command("status");
        if (automaticWarning < 0 && state.contains("attack=RAIN")) {
            automaticWarning = age;
            check(warnings().size() == 2, "automatic Rain targets EVERY living combat player");
            getLogger().info("RAIN_AUTOMATIC warningAge=" + age + " status=" + state);
        }
        if (automaticImpact >= 0 && !state.contains("main=idle")) {
            check(collisions == 2 && launches == 2, "automatic native projectiles both actually resolved");
            check(age - automaticImpact >= 100 && age - automaticImpact <= 101, "native post-main five-second gap");
            getLogger().info("RAIN_AUTOMATIC PASS warning=" + automaticWarning + " lastImpact=" + automaticImpact
                    + " nextWarning=" + age + " poolsRemaining=" + pools().size());
            finalCleanup();
        }
    }

    private void finalCleanup() throws Exception {
        measuring = false;
        command("trigger fireball-rain " + player.getName());
        Bukkit.getPluginManager().disablePlugin(Bukkit.getPluginManager().getPlugin("Kreiscraft"));
        check(balls().isEmpty() && warnings().isEmpty() && pools().isEmpty(),
                "actual plugin disable drains callbacks/entities");
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
                    && packet instanceof net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket) {
                particles++;
            } else if (measuring && packet instanceof net.minecraft.network.protocol.game.ClientboundSoundPacket) {
                sounds++;
            }
        }

        @Override
        public void flushChannel() {}
    }
}
