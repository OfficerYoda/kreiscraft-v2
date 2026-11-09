package de.kreiscraft.modules.shatteredcrown;

import de.kreiscraft.core.scheduler.Scheduler;
import de.kreiscraft.core.message.MessageService;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.Set;
import java.util.HashSet;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.TextDisplay;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.ComplexEntityPart;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.entity.Mob;
import org.bukkit.entity.EntityType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.event.entity.EntityTransformEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.event.world.WorldUnloadEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;

/** Owns one managed battle's attempt, objectives and cancellable work on the server thread. */
public final class EncounterController implements Listener, AutoCloseable {
    private static final NamespacedKey PROTECTED_SHOT = new NamespacedKey("kreiscraft", "crown_protected_shot");
    private static final NamespacedKey ORIGINAL_HEALTH = new NamespacedKey("kreiscraft", "crown_original_health");
    private final Server server;
    private final DragonBattleAccess battles;
    private ShatteredCrownConfig nextConfig;
    private ShatteredCrownConfig config;
    private final Scheduler scheduler;
    private final MessageService messages;
    private final List<Mob> defenders = new ArrayList<>();
    private final Set<UUID> unavailableWorlds = new HashSet<>();
    private Scheduler.Task discovery;
    private Scheduler.Task absence;
    private EnderDragon dragon;
    private final List<Anchor> anchors = new ArrayList<>();
    private UUID completed;
    private EncounterArena arena;
    private double originalMaxHealth;
    private int retryDelay;
    private EncounterWork work;
    private boolean closed;
    private Scheduler.Task bombardmentCadence;
    private Scheduler.Task bombardmentWarning;
    private Scheduler.Task bombardmentImpact;
    private TextDisplay bombardmentIndicator;
    private int targetCursor;

    private static final class Anchor {
        private EnderCrystal crystal;
        private final Location home;
        private final List<Mob> wave = new ArrayList<>();

        private Anchor(EnderCrystal crystal, Location home) {
            this.crystal = crystal;
            this.home = home;
        }
    }

    public EncounterController(Server server, DragonBattleAccess battles,
            ShatteredCrownConfig config, Scheduler scheduler, MessageService messages) {
        this.server = server;
        this.battles = battles;
        this.config = config;
        this.nextConfig = config;
        this.scheduler = scheduler;
        this.messages = messages;
    }

    public void start() {
        if (closed) {
            return;
        }
        server.getWorlds().forEach(world -> world.getEntities().forEach(this::recoverStale));
        if (discovery == null) {
            discovery = scheduler.runRepeating(Duration.ZERO, Duration.ofSeconds(1), this::pulse);
        }
    }

    /** Validated Server Configuration takes effect only when a fresh attempt starts. */
    public void configure(ShatteredCrownConfig next) {
        nextConfig = next;
        retryDelay = 0;
    }

    private void pulse() {
        if (closed) {
            return;
        }
        if (retryDelay > 0) {
            retryDelay--;
            return;
        }
        try {
            discover();
        } catch (RuntimeException exception) {
            cleanup(true);
            retryDelay = 30;
            server.getLogger().log(java.util.logging.Level.WARNING,
                    "[Shattered Crown] Encounter aborted; retry in 30 seconds", exception);
        }
    }

    private void discover() {
        if (dragon != null) {
            if (!dragon.isValid()) {
                cleanup(false);
                return;
            }
            maintainDefenders();
            updateAbsence(null, false);
            showStatus();
            return;
        }
        config = nextConfig;
        World world = server.getWorld(NamespacedKey.fromString(config.worldKey()));
        if (world == null || unavailableWorlds.contains(world.getUID())
                || world.getEnvironment() != World.Environment.THE_END || world.getPlayers().isEmpty()) {
            return;
        }
        var battle = battles.battle(world);
        if (battle == null || battle.getEnderDragon() == null
                || battle.getEnderDragon().getUniqueId().equals(completed)) {
            return;
        }
        var candidate = battle.getEnderDragon();
        var candidateArena = new EncounterArena(candidate.getPodium(), config);
        if (candidate.isDead() || candidate.getHealth() <= 0
                || world.getPlayers().stream().noneMatch(candidateArena::participant)) {
            return;
        }
        List<Location> grounds = candidateArena.openingGround();
        if (grounds.size() != 4) {
            retryDelay = 30;
            server.getLogger().warning("[Shattered Crown] No usable Anchor ground in " + world.getKey()
                    + "; check arena/Anchor geometry and terrain. Retrying in 30 seconds.");
            return;
        }
        dragon = candidate;
        work = new EncounterWork(scheduler, server.getLogger());
        arena = candidateArena;
        originalMaxHealth = dragon.getAttribute(Attribute.MAX_HEALTH).getBaseValue();
        dragon.getPersistentDataContainer().set(ORIGINAL_HEALTH, PersistentDataType.DOUBLE, originalMaxHealth);
        dragon.getAttribute(Attribute.MAX_HEALTH).setBaseValue(config.maxHealth());
        dragon.setHealth(config.maxHealth());
        for (Location ground : grounds) {
            var objective = new Anchor(spawnAnchor(ground), ground);
            anchors.add(objective);
            for (int i = 0; i < config.defenderCount(); i++) {
                Mob defender = spawnDefender(ground);
                defenders.add(defender);
                objective.wave.add(defender);
            }
        }
        announce("shattered-crown.opening");
        showStatus();
        targetCursor = 0;
        Duration cadence = seconds(config.bombardmentCadenceSeconds());
        bombardmentCadence = runRepeating(cadence, cadence, this::warnBombardment);
    }

    private static Duration seconds(double value) {
        return Duration.ofMillis(Math.round(value * 1000));
    }

    private void warnBombardment() {
        if (dragon == null || anchors.isEmpty() || bombardmentIndicator != null) {
            return;
        }
        var targets = dragon.getWorld().getPlayers().stream().filter(arena::participant)
                .sorted(java.util.Comparator.comparing(Player::getUniqueId)).limit(64).toList();
        if (targets.isEmpty()) {
            return;
        }
        Player target = targets.get(Math.floorMod(targetCursor++, targets.size()));
        Location impact = target.getLocation().clone();
        impact.setY(impact.getBlockY());
        if (!arena.bombardmentSafe(impact, config.bombardmentRadius(), targets)) {
            return;
        }
        EnderDragon warnedDragon = dragon;
        bombardmentIndicator = impact.getWorld().spawn(impact.clone().add(0, 1.5, 0), TextDisplay.class);
        // Spawning fires public events: do not resume after a synchronous phase exit/disable.
        if (dragon != warnedDragon || anchors.isEmpty() || !bombardmentIndicator.isValid()) {
            cancelPendingBombardment();
            return;
        }
        own(bombardmentIndicator, "warning");
        battles.faceWarningToViewer(bombardmentIndicator);
        bombardmentIndicator.setShadowed(true);
        bombardmentIndicator.text(Component.text(config.bombardmentWarningSeconds(), NamedTextColor.GOLD));
        targets.forEach(player -> {
            player.sendActionBar(messages.renderFor(player, "shattered-crown.bombardment-warning", "seconds",
                    config.bombardmentWarningSeconds()));
            player.playSound(impact, Sound.ENTITY_ENDER_DRAGON_GROWL, 0.7f, 1.4f);
        });
        drawBombardment(impact);
        final int[] pulses = {0};
        bombardmentWarning = runRepeating(Duration.ofMillis(250), Duration.ofMillis(250), () -> {
            if (!isParticipant(target) || bombardmentIndicator == null || !bombardmentIndicator.isValid()) {
                cancelPendingBombardment();
                return;
            }
            double remaining = Math.max(0, config.bombardmentWarningSeconds() - ++pulses[0] * 0.25);
            bombardmentIndicator.text(Component.text(String.format(java.util.Locale.ROOT, "%.1f", remaining),
                    NamedTextColor.GOLD));
            drawBombardment(impact);
        });
        bombardmentImpact = runLater(seconds(config.bombardmentWarningSeconds()), () -> {
            boolean visible = bombardmentIndicator != null && bombardmentIndicator.isValid();
            cancelPendingBombardment();
            if (!visible || dragon == null || anchors.isEmpty() || !isParticipant(target)) {
                return;
            }
            var participants = dragon.getWorld().getPlayers().stream().filter(arena::participant).toList();
            if (!arena.bombardmentSafe(impact, config.bombardmentRadius(), participants)) {
                return;
            }
            participants.stream().limit(64).forEach(player -> {
                player.spawnParticle(Particle.EXPLOSION, impact.clone().add(0, 0.5, 0), 1);
                player.playSound(impact, Sound.ENTITY_GENERIC_EXPLODE, 0.8f, 1.4f);
            });
            EnderDragon source = dragon;
            for (Player player : participants) {
                // Damage invokes public combat events, which can synchronously end this attempt.
                if (dragon != source || anchors.isEmpty()) {
                    break;
                }
                if (isParticipant(player) && arena.inBombardment(player.getLocation(), impact,
                        config.bombardmentRadius())) {
                    battles.bombardmentDamage(player, source, config.bombardmentDamage());
                }
            }
        });
    }

    private void drawBombardment(Location impact) {
        dragon.getWorld().getPlayers().stream().filter(arena::participant).limit(64).forEach(player -> {
            for (int point = 0; point < 32; point++) {
                double angle = point * Math.PI * 2 / 32;
                player.spawnParticle(Particle.FLAME, impact.clone().add(Math.cos(angle) * config.bombardmentRadius(),
                        0.15, Math.sin(angle) * config.bombardmentRadius()), 1, 0, 0, 0, 0);
            }
        });
    }

    private void cancelPendingBombardment() {
        if (bombardmentWarning != null) {
            bombardmentWarning.cancel();
            bombardmentWarning = null;
        }
        if (bombardmentImpact != null) {
            bombardmentImpact.cancel();
            bombardmentImpact = null;
        }
        if (bombardmentIndicator != null) {
            bombardmentIndicator.remove();
            bombardmentIndicator = null;
        }
    }

    private void stopBombardments() {
        if (bombardmentCadence != null) {
            bombardmentCadence.cancel();
            bombardmentCadence = null;
        }
        cancelPendingBombardment();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onWorldUnload(WorldUnloadEvent event) {
        unavailableWorlds.add(event.getWorld().getUID());
        if (dragon != null && dragon.getWorld().equals(event.getWorld())) {
            cleanup(false);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldLoad(WorldLoadEvent event) {
        unavailableWorlds.remove(event.getWorld().getUID());
        event.getWorld().getEntities().forEach(this::recoverStale);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntitiesLoad(EntitiesLoadEvent event) {
        event.getEntities().forEach(this::recoverStale);
    }

    private void recoverStale(Entity entity) {
        EncounterRecovery.recover(entity, owns(entity));
    }

    boolean owns(Entity entity) {
        return entity.equals(dragon) || entity.equals(bombardmentIndicator)
                || anchors.stream().anyMatch(anchor -> entity.equals(anchor.crystal)) || defenders.contains(entity)
                || work != null && work.owns(entity);
    }

    /** Registers a persistent encounter-owned entity for cleanup and restart discovery. */
    public void own(Entity entity, String kind) {
        requireWork().own(entity, kind);
    }

    /** All delayed warnings, damage and landing effects must use this attempt-scoped scheduler. */
    public Scheduler.Task runLater(Duration delay, Runnable effect) {
        return requireWork().later(delay, effect);
    }

    /** Repeating effects are cancelled on every termination, not just on plugin shutdown. */
    public Scheduler.Task runRepeating(Duration delay, Duration period, Runnable effect) {
        return requireWork().repeating(delay, period, effect);
    }

    /** Restore temporary effects/UI on termination or call the returned action on normal expiry. */
    public Runnable onCleanup(Runnable restore) {
        return requireWork().onCleanup(restore);
    }

    /** Register native phase ownership before changing flight/landing behavior via Paper. */
    public void ownDragonPhase() {
        requireWork();
        EnderDragon owned = dragon;
        if (owned.getPersistentDataContainer().has(EncounterRecovery.ORIGINAL_PHASE)) {
            return;
        }
        EnderDragon.Phase original = owned.getPhase();
        owned.getPersistentDataContainer().set(EncounterRecovery.ORIGINAL_PHASE, PersistentDataType.STRING,
                original.name());
        onCleanup(() -> {
            if (!owned.isDead() && !owned.getUniqueId().equals(completed)) {
                owned.setPhase(original);
            }
            owned.getPersistentDataContainer().remove(EncounterRecovery.ORIGINAL_PHASE);
        });
    }

    private EncounterWork requireWork() {
        if (work == null || closed) {
            throw new IllegalStateException("No active Encounter owns this effect");
        }
        return work;
    }

    private Mob spawnDefender(Location ground) {
        Mob defender = (Mob) ground.getWorld().spawnEntity(ground.clone().add(2, 0, 0),
                EntityType.valueOf(config.defenderType()));
        try {
            if (!defender.isValid()) {
                throw new IllegalStateException("Defender spawn was refused");
            }
            own(defender, "defender");
            defender.setPersistent(true);
            battles.preventDespawn(defender);
            defender.getAttribute(Attribute.MAX_HEALTH).setBaseValue(config.defenderHealth());
            defender.setHealth(config.defenderHealth());
            defender.getAttribute(Attribute.ATTACK_DAMAGE).setBaseValue(config.defenderDamage());
            defender.getAttribute(Attribute.SPAWN_REINFORCEMENTS).setBaseValue(0);
            return defender;
        } catch (RuntimeException exception) {
            defender.remove();
            throw exception;
        }
    }

    private EnderCrystal spawnAnchor(Location ground) {
        EnderCrystal crystal = ground.getWorld().spawn(ground, EnderCrystal.class);
        if (!crystal.isValid()) {
            crystal.remove();
            throw new IllegalStateException("Anchor spawn was refused");
        }
        own(crystal, "anchor");
        return crystal;
    }

    private void maintainDefenders() {
        for (Anchor anchor : anchors) {
            Location home = anchor.home;
            if (!anchor.crystal.isValid()) {
                anchor.crystal = spawnAnchor(home);
                anchor.crystal.setGlowing(anchor.wave.isEmpty());
            }
            for (int i = 0; i < anchor.wave.size(); i++) {
                Mob defender = anchor.wave.get(i);
                if (!defender.isValid()) {
                    defenders.remove(defender);
                    defender.remove();
                    defender = spawnDefender(home);
                    defenders.add(defender);
                    anchor.wave.set(i, defender);
                }
                Location location = defender.getLocation();
                if (!location.getWorld().equals(home.getWorld()) || !arena.contains(location)
                        || location.distanceSquared(home) > config.defenderLeash() * config.defenderLeash()) {
                    defender.teleport(home.clone().add(2, 0, 0));
                }
                defender.setTarget(home.getWorld().getPlayers().stream().filter(arena::participant)
                        .min(java.util.Comparator.comparingDouble(player -> player.getLocation().distanceSquared(home)))
                        .orElse(null));
            }
        }
    }

    /** Shared target boundary for encounter attacks and future warnings. */
    public boolean isParticipant(Player player) {
        return arena != null && arena.participant(player);
    }

    /** Shared protection boundary, including arrivals outside the combat arena. */
    public boolean isProtectedArrival(Location location) {
        if (arena != null) {
            return arena.protectedArrival(location);
        }
        World world = location.getWorld();
        if (world == null || !world.getKey().toString().equals(nextConfig.worldKey())
                || world.getEnvironment() != World.Environment.THE_END) {
            return false;
        }
        var battle = battles.battle(world);
        return battle != null && battle.getEnderDragon() != null
                && new EncounterArena(battle.getEnderDragon().getPodium(), nextConfig).protectedArrival(location);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        Entity target = event.getEntity() instanceof ComplexEntityPart part ? part.getParent() : event.getEntity();
        Entity direct = event instanceof EntityDamageByEntityEvent byEntity ? byEntity.getDamager()
                : event.getDamageSource().getDirectEntity();
        Entity source = event.getDamageSource().getCausingEntity();
        if (source == null) {
            source = direct;
        }
        if (source instanceof Projectile projectile && projectile.getShooter() instanceof Entity shooter) {
            source = shooter;
        } else if (source instanceof TNTPrimed tnt && tnt.getSource() != null) {
            source = tnt.getSource();
        }
        if (dragon == null && target instanceof EnderDragon candidate && isManagedDragon(candidate)) {
            if (protectedSource(source) || protectedSource(direct)) {
                event.setCancelled(true);
                return;
            }
            if (retryDelay == 0) {
                pulse();
            }
        }
        if (dragon == null) {
            return;
        }
        Anchor objective = anchors.stream().filter(anchor -> target.equals(anchor.crystal)).findFirst().orElse(null);
        boolean enemy = target.equals(dragon) || objective != null || defenders.contains(target);
        if (enemy && (protectedSource(source) || protectedSource(direct))) {
            event.setCancelled(true);
            return;
        }
        if (target.equals(dragon) && !anchors.isEmpty()) {
            event.setCancelled(true);
        }
        if (objective != null) {
            event.setCancelled(true); // Remove the objective without a terrain-damaging crystal explosion.
            if (objective.wave.isEmpty() && source instanceof Player player && isParticipant(player)) {
                objective.crystal.remove();
                anchors.remove(objective);
                if (anchors.isEmpty()) {
                    stopBombardments();
                    announce("shattered-crown.unshielded");
                }
                showStatus();
            }
        }
        if (target instanceof Player player && isProtectedArrival(player.getLocation()) && defenders.contains(source)) {
            event.setCancelled(true);
        }
    }

    private boolean isManagedDragon(EnderDragon candidate) {
        World world = candidate.getWorld();
        if (!world.getKey().toString().equals(nextConfig.worldKey())
                || world.getEnvironment() != World.Environment.THE_END || unavailableWorlds.contains(world.getUID())) {
            return false;
        }
        var battle = battles.battle(world);
        return battle != null && candidate.equals(battle.getEnderDragon());
    }

    private boolean protectedSource(Entity entity) {
        return entity != null && (isProtectedArrival(entity.getLocation())
                || entity.getPersistentDataContainer().has(PROTECTED_SHOT));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onLaunch(ProjectileLaunchEvent event) {
        Projectile projectile = event.getEntity();
        if (isProtectedArrival(projectile.getLocation())
                || projectile.getShooter() instanceof Entity shooter && protectedSource(shooter)) {
            projectile.getPersistentDataContainer().set(PROTECTED_SHOT, PersistentDataType.BYTE, (byte) 1);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSpawn(EntitySpawnEvent event) {
        if (event.getEntity() instanceof TNTPrimed tnt
                && (isProtectedArrival(tnt.getLocation()) || protectedSource(tnt.getSource()))) {
            tnt.getPersistentDataContainer().set(PROTECTED_SHOT, PersistentDataType.BYTE, (byte) 1);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onHealing(EntityRegainHealthEvent event) {
        if (event.getEntity().equals(dragon)
                && event.getRegainReason() == EntityRegainHealthEvent.RegainReason.ENDER_CRYSTAL) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onIgnite(BlockIgniteEvent event) {
        if (anchors.stream().anyMatch(anchor -> anchor.crystal.equals(event.getIgnitingEntity()))) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTransform(EntityTransformEvent event) {
        if (defenders.contains(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDeath(EntityDeathEvent event) {
        if (event.getEntity() instanceof Player player) {
            updateAbsence(player, false);
        }
        if (defenders.remove(event.getEntity())) {
            for (Anchor anchor : anchors) {
                if (anchor.wave.remove(event.getEntity()) && anchor.wave.isEmpty()) {
                    anchor.crystal.setGlowing(true);
                    announce("shattered-crown.exposed");
                }
            }
        }
        if (event.getEntity().equals(dragon)) {
            completed = dragon.getUniqueId();
            cleanup(false);
        }
    }

    private void announce(String key) {
        if (dragon != null) {
            dragon.getWorld().getPlayers().stream().filter(arena::participant)
                    .forEach(player -> messages.send(player, key));
        }
    }

    private void showStatus() {
        if (dragon == null) {
            return;
        }
        dragon.getWorld().getPlayers().stream().filter(arena::participant)
                .forEach(player -> {
                    if (anchors.isEmpty()) {
                        player.sendActionBar(messages.renderFor(player, "shattered-crown.hunt-status"));
                    } else {
                        player.sendActionBar(messages.renderFor(player, "shattered-crown.shield-status", "anchors",
                                anchors.size(), "exposed",
                                anchors.stream().filter(anchor -> anchor.wave.isEmpty()).count(),
                                "defenders", defenders.size()));
                    }
                });
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (arena != null) {
            updateAbsence(event.getPlayer(),
                    arena.participantAt(event.getPlayer(), event.getTo(), event.getPlayer().getGameMode()));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        onMove(event);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        updateAbsence(event.getPlayer(), false);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onGameMode(PlayerGameModeChangeEvent event) {
        if (arena != null) {
            updateAbsence(event.getPlayer(),
                    arena.participantAt(event.getPlayer(), event.getPlayer().getLocation(), event.getNewGameMode()));
        }
    }

    private void updateAbsence(Player changed, boolean participates) {
        if (dragon == null) {
            return;
        }
        boolean occupied = participates || dragon.getWorld().getPlayers().stream()
                .anyMatch(player -> !player.equals(changed) && arena.participant(player));
        if (occupied) {
            if (absence != null) {
                absence.cancel();
                absence = null;
            }
        } else if (absence == null) {
            absence = runLater(Duration.ofSeconds(60), () -> {
                if (dragon != null && dragon.getWorld().getPlayers().stream().noneMatch(arena::participant)) {
                    cleanup(true);
                }
            });
        }
    }

    private void cleanup(boolean reset) {
        stopBombardments();
        if (work != null) {
            work.close();
            work = null;
        }
        if (absence != null) {
            absence.cancel();
            absence = null;
        }
        defenders.clear();
        anchors.clear();
        if (dragon != null && !dragon.getUniqueId().equals(completed) && !dragon.isDead()) {
            double fraction = dragon.getHealth() / dragon.getAttribute(Attribute.MAX_HEALTH).getValue();
            dragon.getAttribute(Attribute.MAX_HEALTH).setBaseValue(originalMaxHealth);
            dragon.setHealth(reset ? dragon.getAttribute(Attribute.MAX_HEALTH).getValue()
                    : Math.max(1, dragon.getAttribute(Attribute.MAX_HEALTH).getValue() * fraction));
        }
        if (dragon != null) {
            dragon.getPersistentDataContainer().remove(ORIGINAL_HEALTH);
        }
        dragon = null;
        arena = null;
    }

    @Override
    public void close() {
        closed = true;
        if (discovery != null) {
            discovery.cancel();
        }
        cleanup(false);
        HandlerList.unregisterAll(this);
    }
}
