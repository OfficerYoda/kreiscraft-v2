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
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityRemoveEvent;
import org.bukkit.event.entity.EntityTeleportEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

/** Real production commands, registered native players, AI-enabled battle and natural debris collisions. */
public final class SmashEncounterProbe extends JavaPlugin implements Listener {
    private static final NamespacedKey ARTIFACT = new NamespacedKey("kreiscraft", "crown_artifact");
    private final List<ServerPlayer> fixtures = new ArrayList<>();
    private final List<Sink> sinks = new ArrayList<>();
    private Player player;
    private Player arrival;
    private EnderDragon dragon;
    private World world;
    private Location target;
    private int ticks;
    private int age;
    private int scenario = -1;
    private int checks;
    private int hits;
    private int cancelled;
    private int collisionRemovals;
    private int teleports;
    private int placements;
    private int drops;
    private int debrisDamage;
    private int lastParticles;
    private int warningAge;
    private int impactAge;
    private int floorY;
    private int initialSolid;
    private boolean measuring;
    private boolean done;

    @Override
    public void onEnable() {
        if (!Boolean.getBoolean("kreiscraft.smash-probe")) {
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        Bukkit.getPluginManager().registerEvents(this, this);
        if (Boolean.getBoolean("kreiscraft.smash-restart")) {
            Bukkit.getScheduler().runTaskLater(this, this::restartCheck, 40);
            return;
        }
        Bukkit.getScheduler().runTask(this, this::start);
        Bukkit.getScheduler().runTaskLater(this, () -> finish(false, "timeout"), 3000);
    }

    private Player fixture(String name, Location location) {
        var server = ((CraftServer) Bukkit.getServer()).getServer();
        var profile = new GameProfile(UUID.randomUUID(), name);
        var nativePlayer = new ServerPlayer(server, ((CraftWorld) world).getHandle(), profile,
                ClientInformation.createDefault());
        nativePlayer.snapTo(location.getX(), location.getY(), location.getZ(), 0, 0);
        Sink sink = new Sink();
        sinks.add(sink);
        fixtures.add(nativePlayer);
        server.getPlayerList().placeNewPlayer(sink, nativePlayer, CommonListenerCookie.createInitial(profile, false));
        Player result = nativePlayer.getBukkitEntity();
        result.setGameMode(GameMode.SURVIVAL);
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
            world.setGameRule(org.bukkit.GameRule.NATURAL_REGENERATION, false);
            for (int x = -12; x <= 12; x++) {
                for (int z = -12; z <= 12; z++) {
                    world.setChunkForceLoaded(x, z, true);
                }
            }
            // Real main-island ground, away from the seed-dependent height/width of obsidian spikes.
            target = new Location(world, 40.5, world.getHighestBlockYAt(40, 40) + 1, 40.5);
            player = fixture("SmashFixture", target);
            arrival = fixture("SmashArrival", target.clone().add(5, 0, 0));
            arrival.setGameMode(GameMode.CREATIVE);
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
        Sink sink = sinks.getFirst();
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

    private boolean artifact(org.bukkit.entity.Entity entity, String kind) {
        return kind.equals(entity.getPersistentDataContainer().get(ARTIFACT, PersistentDataType.STRING));
    }

    private List<FallingBlock> debris() {
        return world.getEntitiesByClass(FallingBlock.class).stream().filter(entity -> artifact(entity, "debris"))
                .toList();
    }

    private List<TextDisplay> warnings() {
        return world.getEntitiesByClass(TextDisplay.class).stream().filter(entity -> artifact(entity, "sweep"))
                .toList();
    }

    private int solidPatch() {
        int count = 0;
        for (int x = 35; x <= 45; x++) {
            for (int z = 35; z <= 45; z++) {
                if (Math.hypot(x - 40, z - 40) <= 5 && world.getBlockAt(x, floorY, z).getType().isSolid()) {
                    count++;
                }
            }
        }
        return count;
    }

    private void begin() {
        measuring = false;
        command("pause", "main=idle");
        // Every case consumes fresh real main-End ground, descending into earlier permanent craters.
        target.setY(world.getHighestBlockYAt(40, 40) + 1);
        floorY = target.getBlockY() - 1;
        initialSolid = solidPatch();
        check(initialSolid > 0, "real supported impact column");
        player.setGameMode(GameMode.SURVIVAL);
        player.teleport(target);
        player.setVelocity(new org.bukkit.util.Vector());
        player.setHealth(20);
        player.setAbsorptionAmount(0);
        player.setNoDamageTicks(0);
        arrival.setGameMode(scenario == 7 ? GameMode.SURVIVAL : GameMode.CREATIVE);
        arrival.teleport(target.clone().add(5, 0, 0));
        arrival.setHealth(20);
        arrival.setNoDamageTicks(0);
        world.setDifficulty(new Difficulty[] {Difficulty.EASY, Difficulty.NORMAL, Difficulty.HARD}[scenario % 3]);
        hits = 0;
        cancelled = 0;
        collisionRemovals = 0;
        placements = 0;
        drops = 0;
        debrisDamage = 0;
        teleports = 0;
        warningAge = 0;
        impactAge = 0;
        sinks.getFirst().particles = 0;
        sinks.getFirst().sounds = 0;
        lastParticles = 0;
        measuring = true;
        age = 0;
        if (scenario == 3) {
            command("wave 3", "wave=3");
            world.getEntities().stream()
                    .filter(entity -> entity instanceof LivingEntity && artifact(entity, "defender"))
                    .forEach(entity -> ((LivingEntity) entity).setHealth(0)); // Real deaths, including charged native Withers.
            world.getEntities().stream()
                    .filter(entity -> entity instanceof LivingEntity && artifact(entity, "defender"))
                    .forEach(entity -> ((LivingEntity) entity).setHealth(0)); // Re-enumerate synchronous descendants.
            world.getEntitiesByClass(EnderCrystal.class).stream().filter(entity -> artifact(entity, "anchor"))
                    .forEach(entity -> {
                        var level = ((CraftWorld) world).getHandle();
                        ((org.bukkit.craftbukkit.entity.CraftEnderCrystal) entity).getHandle()
                                .hurtServer(level, level.damageSources().playerAttack(fixtures.getFirst()), 1);
                    });
            check(command("status", "unlocked=").contains("SMASH"), "automatic final-wave unlock");
            measuring = false;
            command("resume", "mode=automatic");
        } else {
            command("trigger smash", "Started SMASH");
            check(warnings().size() == 1, "visible Smash warning");
        }
    }

    private void tick() {
        if (done) {
            return;
        }
        try {
            ticks++;
            fixtures.forEach(fixture -> fixture.connection.tick());
            if (scenario == 3 && !measuring) {
                check(age < 1200, "full shuffled round reaches automatic Smash");
                if (!command("status", "mode=automatic").contains("attack=SMASH")) {
                    return;
                }
                target.setY(world.getHighestBlockYAt(40, 40) + 1);
                floorY = target.getBlockY() - 1;
                initialSolid = solidPatch();
                player.setHealth(20);
                measuring = true;
            }
            if (scenario > 7) {
                return;
            }
            if (scenario < 0) {
                var battle = world.getEnderDragonBattle();
                dragon = battle == null ? null : battle.getEnderDragon();
                if (ticks < 80 || dragon == null || !dragon.getPersistentDataContainer()
                        .has(new NamespacedKey("kreiscraft", "crown_original_health"))) {
                    check(ticks < 240, "native battle adoption timeout");
                    return;
                }
                check(fixtures.stream().allMatch(fixture -> fixture.connection.hasClientLoaded()
                        && fixture.getBukkitEntity().isOnline()), "registered native fixtures/load");
                check(dragon.hasAI() && dragon.getDragonBattle().equals(battle), "genuine managed battle/AI");
                scenario = 0;
                begin();
                return;
            }
            age++;
            if (warningAge == 0 && !warnings().isEmpty()) {
                warningAge = age;
                lastParticles = sinks.getFirst().particles;
            }
            int warningTick = age - warningAge;
            if (warningAge > 0 && warningTick >= 5 && warningTick < 80 && warningTick % 5 == 0 && scenario != 4) {
                check(sinks.getFirst().particles > lastParticles, "particles throughout FULL four-second warning");
                lastParticles = sinks.getFirst().particles;
                check(solidPatch() == initialSolid && hits == 0, "no early crater/damage");
            }
            if (warningAge > 0 && warningTick == 79 && scenario != 4) {
                check(Math.abs(dragon.getY() - target.getY() - 18) < 1, "physical rise completes warning");
                check(sinks.getFirst().sounds > 0, "warning sound");
                check(!warnings().getFirst().text().equals(net.kyori.adventure.text.Component.empty()),
                        "floating countdown");
            }
            if (scenario == 4 && age == 40) {
                command("cancel", "main=idle");
            }
            if (impactAge == 0 && !debris().isEmpty()) {
                impactAge = age;
                check(warningTick >= 98 && warningTick <= 102, "one-second physical plunge");
                check(solidPatch() < initialSolid && world.getBlockAt(40, floorY, 40).isEmpty(),
                        "actual ground removal");
                check(world.getBlockAt(40, floorY - 1, 40).getType().isSolid(), "one-block-deep crater");
                check(Math.abs(dragon.getY() - target.getY() - 2) < 0.1, "damage at real impact height");
                check(debris().stream().allMatch(block -> !block.canHurtEntities() && !block.getDropItem()
                        && block.getCancelDrop()), "debris native safety flags");
                check(debris().stream().anyMatch(block -> block.getVelocity().getY() > 0.5), "flying removed blocks");
                command("status", "main=executing");
                if (scenario == 5) {
                    command("cancel", "main=idle");
                    check(debris().isEmpty(), "post-impact cancel removes only debris");
                }
            }
            if (impactAge > 0 && age - impactAge == 58 && scenario != 5) {
                check(Math.abs(dragon.getY() - target.getY() - 2) < 0.1, "three-second grounded recovery");
                command("status", "main=executing");
            }
            if (impactAge > 0 && age - impactAge == 98 && scenario != 5) {
                check(Math.abs(dragon.getY() - target.getY() - 18) < 1, "two-second physical climb");
                command("status", "main=executing");
            }
            if (scenario == 4 && age >= 220 || impactAge > 0 && age - impactAge >= 101) {
                command("pause", "main=idle");
                check(debris().isEmpty() && warnings().isEmpty(), "bounded temporary work");
                check(teleports == 0 && dragon.hasAI() && dragon.getPhase() != EnderDragon.Phase.HOVER,
                        "native movement, no teleport/HOVER/noAI");
                if (scenario == 4) {
                    check(solidPatch() == initialSolid && hits == 0, "pre-impact cancel has no late crater");
                } else {
                    check(solidPatch() < initialSolid, "permanent crater survives cleanup");
                    check(placements == 0 && drops == 0 && debrisDamage == 0, "no debris blocks/items/damage");
                    if (scenario != 5) {
                        check(collisionRemovals > 0, "natural falling-block collision discarded debris");
                    }
                    check(hits == (scenario == 6 ? 0 : 1) && cancelled == (scenario == 6 ? 1 : 0), "once-only damage");
                    check(Math.abs(player.getHealth() - (scenario == 6 ? 20 : 8)) < 0.001, "native reference 12HP");
                    check(arrival.getHealth() == 20, "protected arrival player untouched");
                }
                getLogger().info("SMASH_MATRIX scenario=" + scenario + " difficulty=" + world.getDifficulty()
                        + " health=" + player.getHealth() + " hits=" + hits + " crater=" + (initialSolid - solidPatch())
                        + " collisions=" + collisionRemovals + " particles=" + sinks.getFirst().particles);
                scenario++;
                if (scenario > 7) {
                    measuring = false;
                    damagedFollowups();
                } else {
                    begin();
                }
            } else {
                check(age < 1200, "Smash completion timeout");
            }
        } catch (RuntimeException error) {
            getLogger().log(java.util.logging.Level.SEVERE, "Smash probe failed", error);
            finish(false, error.toString());
        }
    }

    private void resetPlayer() {
        target.setY(world.getHighestBlockYAt(40, 40) + 1);
        player.teleport(target);
        player.setVelocity(new org.bukkit.util.Vector());
        player.setHealth(20);
        player.setNoDamageTicks(0);
    }

    private void damagedFollowups() {
        resetPlayer();
        world.setDifficulty(Difficulty.NORMAL);
        command("trigger dive", "Started DIVE");
        Bukkit.getScheduler().runTaskLater(this, () -> guarded(() -> {
            check(Math.abs(player.getHealth() - 12) < 0.001, "native Dive follow-up over permanent craters");
            command("pause", "main=idle");
            resetPlayer();
            command("trigger bombardment", "Started BOMBARDMENT");
            Bukkit.getScheduler().runTaskLater(this, () -> guarded(() -> {
                check(Math.abs(player.getHealth() - 14) < 0.001, "native Bombardment follow-up over permanent craters");
                command("pause", "main=idle");
                resetPlayer();
                command("trigger prey", "Started PREY");
                Bukkit.getScheduler().runTaskLater(this, () -> guarded(() -> {
                    check(player.getHealth() < 20, "native Prey follow-up over permanent craters");
                    command("status", "hazards=1");
                    command("cancel", "main=idle");
                    check(debris().isEmpty(), "repeated cleanup");
                    saveRestartArtifacts();
                }), 85);
            }), 65);
        }), 105);
    }

    private void guarded(Runnable effect) {
        if (done) {
            return;
        }
        try {
            effect.run();
        } catch (RuntimeException error) {
            getLogger().log(java.util.logging.Level.SEVERE, "Smash follow-up failed", error);
            finish(false, error.toString());
        }
    }

    private void saveRestartArtifacts() {
        // Simulate orphaned saved debris after a process interruption, not live Encounter work.
        var stale = world.spawn(target.clone().add(0, 20, 0), FallingBlock.class, entity -> {
            entity.setBlockData(Material.END_STONE.createBlockData());
            entity.setGravity(false);
            entity.setDropItem(false);
            entity.setCancelDrop(true);
            entity.setHurtEntities(false);
            entity.getPersistentDataContainer().set(ARTIFACT, PersistentDataType.STRING, "debris");
        });
        var unrelated = world.spawn(target.clone().add(2, 20, 0), FallingBlock.class, entity -> {
            entity.setBlockData(Material.END_STONE.createBlockData());
            entity.setGravity(false);
        });
        world.getPersistentDataContainer().set(new NamespacedKey("smashprobe", "stale"), PersistentDataType.STRING,
                stale.getUniqueId().toString());
        world.getPersistentDataContainer().set(new NamespacedKey("smashprobe", "unrelated"), PersistentDataType.STRING,
                unrelated.getUniqueId().toString());
        world.getPersistentDataContainer().set(new NamespacedKey("smashprobe", "floor"), PersistentDataType.INTEGER,
                floorY);
        Bukkit.getPluginManager().disablePlugin(Bukkit.getPluginManager().getPlugin("Kreiscraft"));
        check(stale.isValid() && unrelated.isValid() && warnings().isEmpty(),
                "shutdown retains only orphan fixture/unrelated");
        world.save();
        finish(true, "checks=" + checks + " nativeBattle+warning+flight+damage+craters+collisions+followups+cleanup");
    }

    private void restartCheck() {
        guarded(() -> {
            world = Bukkit.getWorld(NamespacedKey.minecraft("the_end"));
            world.getChunkAt(2, 2).load();
            var data = world.getPersistentDataContainer();
            UUID stale = UUID.fromString(data.get(new NamespacedKey("smashprobe", "stale"), PersistentDataType.STRING));
            UUID unrelated =
                    UUID.fromString(data.get(new NamespacedKey("smashprobe", "unrelated"), PersistentDataType.STRING));
            int savedFloor = data.get(new NamespacedKey("smashprobe", "floor"), PersistentDataType.INTEGER);
            check(world.getEntity(stale) == null, "actual process restart recovers saved orphan debris");
            check(world.getEntity(unrelated) instanceof FallingBlock, "restart preserves unrelated falling block");
            check(world.getBlockAt(40, savedFloor, 40).isEmpty(), "permanent crater survives process restart");
            getLogger().info(
                    "SMASH_RESTART_PROBE PASS checks=" + checks + " nativePersistence+scopedRecovery+permanentCrater");
            finish(true, "restart");
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void isolate(EntityDamageEvent event) {
        if (event.getEntity() == player || event.getEntity() == arrival) {
            if (scenario <= 7 && (!measuring || Math.abs(event.getFinalDamage() - 12) > 0.001)
                    || event.getDamageSource().getCausingEntity() != dragon
                    || event.getCause() != EntityDamageEvent.DamageCause.DRAGON_BREATH || scenario == 6) {
                event.setCancelled(true); // Ambient damage is not the Smash damage under test.
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void damage(EntityDamageEvent event) {
        if (!measuring) {
            return;
        }
        if (event.getDamageSource().getDirectEntity() instanceof FallingBlock block && artifact(block, "debris")) {
            debrisDamage++;
        }
        if (event.getEntity() == player && event.getCause() == EntityDamageEvent.DamageCause.DRAGON_BREATH
                && Math.abs(event.getFinalDamage() - 12) < 0.001) {
            check(event.getDamageSource().getDirectEntity() == dragon
                    && event.getDamageSource().getCausingEntity() == dragon, "real dragon attribution");
            check(Math.abs(dragon.getY() - target.getY() - 2) < 0.1, "damage exactly at physical impact");
            if (event.isCancelled()) {
                cancelled++;
            } else {
                hits++;
            }
        }
    }

    @EventHandler
    public void remove(EntityRemoveEvent event) {
        if (measuring && artifact(event.getEntity(), "debris") && event.getEntity().isOnGround()
                && event.getCause() == EntityRemoveEvent.Cause.DESPAWN) {
            collisionRemovals++;
        }
    }

    @EventHandler
    public void placement(org.bukkit.event.entity.EntityChangeBlockEvent event) {
        if (measuring && artifact(event.getEntity(), "debris")) {
            placements++;
        }
    }

    @EventHandler
    public void drop(org.bukkit.event.entity.EntityDropItemEvent event) {
        if (measuring && artifact(event.getEntity(), "debris")) {
            drops++;
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
        getLogger().info("SMASH_ENCOUNTER_PROBE " + (passed ? "PASS " : "FAIL ") + detail);
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
        private boolean explicit;

        private Sink() {
            super(PacketFlow.SERVERBOUND);
            channel = new EmbeddedChannel();
            address = new InetSocketAddress("127.0.0.1", 0);
        }

        @Override
        public <T extends PacketListener> void setupInboundProtocol(ProtocolInfo<T> protocol, T targetListener) {
            listener = targetListener;
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
            } else if (explicit
                    && packet instanceof net.minecraft.network.protocol.game.ClientboundSystemChatPacket chat) {
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
