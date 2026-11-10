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
    private double attemptMaxHealth;
    private int retryDelay;
    private EncounterWork work;
    private EncounterWork attackWork;
    private EncounterWork mainWork;
    private DebugAttack mainAttack;
    private final Set<EncounterWork> hazards = new HashSet<>();
    private boolean closed;
    private Scheduler.Task bombardmentCadence;
    private Scheduler.Task bombardmentWarning;
    private Scheduler.Task bombardmentImpact;
    private TextDisplay bombardmentIndicator;
    private int targetCursor;
    private final List<BreathLane> lanes = new ArrayList<>();
    private final List<HuntArea> areas = new ArrayList<>();
    private final java.util.Map<UUID, TextDisplay> marks = new java.util.HashMap<>();
    private boolean hunting;
    private boolean finalEntered;
    private Scheduler.Task finalAcceptance;
    private final List<Location> openingGround = new ArrayList<>();
    private double[] explosionBuckets;
    private int explosionCursor;
    private final java.util.Map<EntityDamageEvent, Double> explosionReservations = new java.util.IdentityHashMap<>();
    private final java.util.Map<EntityDamageEvent, Double> damageReservations = new java.util.IdentityHashMap<>();
    private boolean landingRequested;
    private boolean landingDue;
    private Scheduler.Task landingDeferral;
    private boolean landingWindow;
    private Scheduler.Task landingMonitor;
    private Scheduler.Task landingDeadline;
    private Scheduler.Task landingEnd;
    private DragonFlight flight;
    private boolean flightDamage;
    private boolean manualMode;
    private DebugAttack debugAttack;
    private Player debugTarget;
    private HuntRotation rotation;

    enum DebugAttack {
        DIVE, SMASH, PREY, BOMBARDMENT, LANDING
    }

    private boolean attacking() {
        return hunting || debugAttack != null;
    }

    private List<Player> attackTargets() {
        if (debugTarget != null) {
            return isParticipant(debugTarget) ? List.of(debugTarget) : List.of();
        }
        return attackParticipants();
    }

    private List<Player> attackParticipants() {
        return dragon.getWorld().getPlayers().stream().filter(arena::participant)
                .sorted(java.util.Comparator.comparing(Player::getUniqueId)).toList();
    }

    private boolean busy() {
        return mainWork != null;
    }

    String debugStatus() {
        if (dragon == null) {
            return "No active encounter; enter the configured End arena in survival/adventure.";
        }
        return "mode=" + (manualMode ? "manual" : "automatic") + ", phase="
                + (hunting ? "hunt" : "anchors") + ", final=" + finalEntered
                + ", main=" + (busy() ? "executing" : "idle")
                + ", attack=" + activeAttack() + ", hazards=" + hazards.size()
                + ", queued=[" + (rotation == null ? "" : rotation.queued()) + "]"
                + ", lanes=" + lanes.size() + ", marks=" + marks.size() + ", areas=" + areas.size()
                + ", landing=" + landingRequested + ", flight=" + (flight != null)
                + ", native=" + dragon.getPhase();
    }

    private String activeAttack() {
        return busy() ? mainAttack.name() : "none";
    }

    void debugPause() {
        manualMode = true;
        resetAttacks();
    }

    void debugCancel() {
        debugPause();
    }

    void debugResume() {
        resetAttacks();
        manualMode = false;
        if (dragon == null) {
            return;
        }
        if (hunting) {
            hunting = false;
            startHunt();
        } else {
            scheduleBombardments();
        }
    }

    String debugTrigger(DebugAttack attack, Player target) {
        if (closed) {
            return "Encounter module is disabled.";
        }
        if (dragon == null) {
            pulse();
        }
        if (dragon == null) {
            return "No active encounter; enter the configured End arena in survival/adventure.";
        }
        if (!isParticipant(target)) {
            return "Target must be alive in survival/adventure inside the combat arena, outside protected arrival.";
        }
        debugPause();
        debugAttack = attack;
        debugTarget = target;
        arena.resetRejection();
        try {
            switch (attack) {
                case DIVE -> warnLane();
                case SMASH -> smash();
                case PREY -> markPrey();
                case BOMBARDMENT -> warnBombardment();
                case LANDING -> requestLanding();
            }
        } catch (RuntimeException error) {
            resetAttacks();
            server.getLogger().log(java.util.logging.Level.WARNING, "[Shattered Crown] Debug attack failed", error);
            return "Attack failed: " + error.getMessage();
        }
        if (!busy()) {
            String reason = arena.rejection();
            resetAttacks();
            return "Not started: " + reason;
        }
        return "Started " + attack + " targeting " + target.getName() + "; automatic attacks paused.";
    }

    private void resetAttacks() {
        if (attackWork != null) {
            attackWork.close();
            attackWork = new EncounterWork(scheduler, server.getLogger());
        }
        endLanding();
        stopBombardments();
        lanes.clear();
        marks.clear();
        areas.clear();
        rotation = null;
        debugAttack = null;
        debugTarget = null;
    }

    private void scheduleBombardments() {
        if (!manualMode) {
            Duration cadence = seconds(config.bombardmentCadenceSeconds());
            bombardmentCadence = attackWork.repeating(cadence, cadence, this::warnBombardment);
        }
    }

    private static final class Anchor {
        private EnderCrystal crystal;
        private final Location home;
        private final List<Mob> wave = new ArrayList<>();
        private boolean waveInstalled;

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
                || candidate.getPhase() == EnderDragon.Phase.DYING
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
        attackWork = new EncounterWork(scheduler, server.getLogger());
        // Extra tick prevents a hit between clock pulses expiring before a full rolling window.
        explosionBuckets = new double[(int) Math.ceil(config.explosionWindowSeconds() * 20) + 1];
        startExplosionClock();
        arena = candidateArena;
        originalMaxHealth = dragon.getAttribute(Attribute.MAX_HEALTH).getBaseValue();
        dragon.getPersistentDataContainer().set(ORIGINAL_HEALTH, PersistentDataType.DOUBLE, originalMaxHealth);
        dragon.getAttribute(Attribute.MAX_HEALTH).setBaseValue(config.maxHealth());
        attemptMaxHealth = dragon.getAttribute(Attribute.MAX_HEALTH).getValue();
        dragon.setHealth(attemptMaxHealth);
        openingGround.addAll(grounds);
        for (Location ground : grounds) {
            installAnchor(ground);
        }
        announce("shattered-crown.opening");
        showStatus();
        targetCursor = 0;
        scheduleBombardments();
    }

    private static Duration seconds(double value) {
        return Duration.ofMillis(Math.round(value * 1000));
    }

    private void enterFinalGate() {
        if (finalEntered || dragon == null) {
            return;
        }
        finalEntered = true; // Set before spawning: public spawn callbacks may reenter combat.
        EnderDragon source = dragon;
        hunting = false;
        resetAttacks();
        if (dragon != source || closed) {
            return;
        }
        try {
            if (openingGround.subList(0, 2).stream().anyMatch(ground -> !arena.usable(ground))) {
                throw new IllegalStateException("Final Anchor ground is no longer usable");
            }
            for (Location ground : openingGround.subList(0, 2)) {
                installAnchor(ground);
            }
        } catch (RuntimeException exception) {
            cleanup(true);
            retryDelay = 30;
            server.getLogger().log(java.util.logging.Level.WARNING,
                    "[Shattered Crown] Final Anchor setup aborted; retry in 30 seconds", exception);
            return;
        }
        announce("shattered-crown.final-anchors");
        showStatus();
        scheduleBombardments();
        updateAbsence(null, false);
    }

    private void startHunt() {
        if (hunting) {
            return;
        }
        hunting = true;
        if (manualMode) {
            return;
        }
        if (config.movementAttacks()) {
            rotation = new HuntRotation(
                    attackCadence(config.laneCadenceSeconds(), config.laneWarningSeconds() + 0.5),
                    attackCadence(config.preyCadenceSeconds(), config.preyCountdownSeconds() + poolLifetime() + 0.5),
                    attackCadence(config.sweepCadenceSeconds(), config.sweepWarningSeconds() + 0.5),
                    attackCadence(config.landingCadenceSeconds(), 10),
                    this::busy,
                    attack -> {
                        arena.resetRejection();
                        switch (attack) {
                            case DIVE -> warnLane();
                            case PREY -> markPrey();
                            case SMASH -> warnSweep();
                            case LANDING -> requestLanding();
                        }
                        return busy();
                    }, (attack, reason) -> server.getLogger()
                            .info("[Shattered Crown] " + attack + " deferred: "
                                    + (reason.startsWith("another") ? reason : arena.rejection() + "; retry in 1s")));
            attackWork.repeating(Duration.ofMillis(250), Duration.ofMillis(250), rotation::tick);
            return;
        }
        Duration cadence = seconds(attackCadence(config.laneCadenceSeconds(), config.laneWarningSeconds() + 0.5));
        Duration landingCadence = seconds(attackCadence(config.landingCadenceSeconds(), 10));
        attackWork.repeating(landingCadence, landingCadence, () -> {
            if (!landingRequested && !landingDue) {
                landingDue = true;
                double pressure = Math.max(config.laneWarningSeconds() + config.laneLifetimeSeconds(),
                        Math.max(config.preyCountdownSeconds() + poolLifetime(),
                                config.sweepWarningSeconds()));
                landingDeferral =
                        attackWork.later(seconds(pressure + config.landingApproachSeconds()), () -> landingDue = false);
                requestLanding();
            }
        });
        attackWork.repeating(Duration.ofMillis(250), Duration.ofMillis(250), () -> {
            if (landingDue && !landingRequested) {
                requestLanding();
            }
        });
        Duration preyCadence = seconds(attackCadence(config.preyCadenceSeconds(),
                config.preyCountdownSeconds() + poolLifetime() + 0.5));
        attackWork.repeating(preyCadence, preyCadence, this::markPrey);
        Duration sweepCadence =
                seconds(attackCadence(config.sweepCadenceSeconds(), config.sweepWarningSeconds() + 0.5));
        attackWork.repeating(sweepCadence, sweepCadence, this::warnSweep);
        attackWork.repeating(cadence, cadence, this::warnLane);
    }

    private double attackCadence(double ordinary, double minimum) {
        return finalEntered ? Math.max(minimum, ordinary * config.finalCadenceFactor()) : ordinary;
    }

    private double poolLifetime() {
        return config.poolLifetimeSeconds() * (finalEntered ? config.finalPoolLifetimeFactor() : 1);
    }

    private void startExplosionClock() {
        // One fixed-size tick ring, not one expiry task per hit: even fractional bed hits stay bounded.
        runRepeating(Duration.ofMillis(50), Duration.ofMillis(50), () -> {
            explosionCursor = (explosionCursor + 1) % explosionBuckets.length;
            explosionBuckets[explosionCursor] = 0;
        });
    }

    private static boolean explosive(EntityDamageEvent event) {
        return event.getCause() == EntityDamageEvent.DamageCause.BLOCK_EXPLOSION
                || event.getCause() == EntityDamageEvent.DamageCause.ENTITY_EXPLOSION;
    }

    private static void capDamage(EntityDamageEvent event, double maximum) {
        if (maximum <= 0) {
            event.setCancelled(true);
        } else if (event.getFinalDamage() > maximum) {
            event.setDamage(Math.max(0, event.getDamage() - (event.getFinalDamage() - maximum)));
            // Unexpected positive/nonlinear modifiers must never defeat the safety cap.
            if (event.getFinalDamage() > maximum) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDamageAccepted(EntityDamageEvent event) {
        damageReservations.remove(event);
        Double reserved = explosionReservations.remove(event);
        if (reserved != null && !event.isCancelled() && explosionBuckets != null) {
            explosionBuckets[explosionCursor] += Math.min(reserved, event.getFinalDamage());
        }
        Entity target = event.getEntity() instanceof ComplexEntityPart part ? part.getParent() : event.getEntity();
        if (!event.isCancelled() && target.equals(dragon) && !finalEntered && finalAcceptance == null
                && Double.isFinite(event.getFinalDamage()) && event.getFinalDamage() > 0
                && dragon.getHealth() - event.getFinalDamage() <= attemptMaxHealth * config.finalHealthFraction()) {
            // MONITOR only observes acceptance. Native damage applies before this owned callback;
            // the HIGHEST health floor protects subsequent hits until the Anchors are installed.
            finalAcceptance = runLater(Duration.ZERO, () -> {
                finalAcceptance = null;
                if (dragon != null && dragon.isValid() && !finalEntered
                        && dragon.getHealth() <= attemptMaxHealth * config.finalHealthFraction()) {
                    enterFinalGate();
                }
            });
        }
    }

    private static boolean sitting(EnderDragon.Phase phase) {
        return phase == EnderDragon.Phase.SEARCH_FOR_BREATH_ATTACK_TARGET
                || phase == EnderDragon.Phase.ROAR_BEFORE_ATTACK || phase == EnderDragon.Phase.BREATH_ATTACK;
    }

    private void requestLanding() {
        startAction(DebugAttack.LANDING, this::requestLanding);
    }

    private void requestLanding(EncounterWork owner) {
        if (flight != null || !attacking() || dragon == null || debugAttack == null && !anchors.isEmpty()
                || landingRequested || !lanes.isEmpty()
                || !marks.isEmpty() || !areas.isEmpty()
                || dragon.getWorld().getPlayers().stream().noneMatch(arena::participant) || !arena.landingSafe()
                || dragon.getPhase() == EnderDragon.Phase.DYING) {
            return;
        }
        ownDragonPhase();
        owner.onCleanup(this::releaseLanding);
        landingDue = false;
        if (landingDeferral != null) {
            landingDeferral.cancel();
            landingDeferral = null;
        }
        landingRequested = true;
        announce("shattered-crown.landing-approach");
        dragon.setPhase(EnderDragon.Phase.FLY_TO_PORTAL);
        if (dragon == null || !attacking()) {
            return;
        }
        landingDeadline = owner.later(seconds(config.landingApproachSeconds()), this::endLanding);
        landingMonitor = owner.repeating(Duration.ofMillis(250), Duration.ofMillis(250), () -> {
            if (!arena.landingSafe() || dragon.getWorld().getPlayers().stream().noneMatch(arena::participant)) {
                endLanding();
            } else if (!landingWindow && sitting(dragon.getPhase()) && arena.atPodium(dragon.getLocation())) {
                landingWindow = true;
                landingDeadline.cancel();
                dragon.setPhase(EnderDragon.Phase.SEARCH_FOR_BREATH_ATTACK_TARGET);
                if (dragon == null || !attacking()) {
                    return;
                }
                announce("shattered-crown.landing-window");
                landingEnd = owner.later(seconds(config.landingWindowSeconds()), this::endLanding);
            }
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDragonPhase(org.bukkit.event.entity.EnderDragonChangePhaseEvent event) {
        if (event.getEntity().equals(dragon) && flight != null && !flight.isClosed()
                && event.getNewPhase() != EnderDragon.Phase.CHARGE_PLAYER
                && event.getNewPhase() != EnderDragon.Phase.DYING) {
            event.setCancelled(true);
            return;
        }
        if (event.getEntity().equals(dragon) && event.getNewPhase() == EnderDragon.Phase.DYING
                && (!finalEntered || !anchors.isEmpty() || !hunting)) {
            event.setCancelled(true);
            if (anchors.isEmpty()) {
                enterFinalGate();
            }
            return;
        }
        if (!event.getEntity().equals(dragon) || !landingRequested || event.getNewPhase() == EnderDragon.Phase.DYING) {
            return;
        }
        if (sitting(event.getNewPhase()) || landingWindow) {
            event.setNewPhase(EnderDragon.Phase.SEARCH_FOR_BREATH_ATTACK_TARGET);
        } else if (event.getNewPhase() != EnderDragon.Phase.FLY_TO_PORTAL
                && event.getNewPhase() != EnderDragon.Phase.LAND_ON_PORTAL) {
            event.setCancelled(true);
        }
    }

    private void endLanding() {
        if (mainAttack == DebugAttack.LANDING && mainWork != null) {
            mainWork.close();
        } else {
            releaseLanding();
        }
    }

    private void releaseLanding() {
        boolean wasSitting = dragon != null && sitting(dragon.getPhase());
        landingRequested = false;
        landingDue = false;
        if (landingDeferral != null) {
            landingDeferral.cancel();
            landingDeferral = null;
        }
        landingWindow = false;
        if (landingMonitor != null) {
            landingMonitor.cancel();
            landingMonitor = null;
        }
        if (landingDeadline != null) {
            landingDeadline.cancel();
            landingDeadline = null;
        }
        if (landingEnd != null) {
            landingEnd.cancel();
            landingEnd = null;
        }
        if (dragon != null && attacking() && dragon.getPhase() != EnderDragon.Phase.DYING) {
            dragon.setPhase(wasSitting ? EnderDragon.Phase.LEAVE_PORTAL : EnderDragon.Phase.CIRCLING);
        }
    }

    private void warnLane() {
        startAction(DebugAttack.DIVE, this::warnLane);
    }

    private void warnLane(EncounterWork owner) {
        if (flight != null) {
            return;
        }
        if (!attacking() || landingRequested || landingDue || dragon == null
                || debugAttack == null && !anchors.isEmpty()
                || lanes.size() >= 2
                || !marks.isEmpty() || !areas.isEmpty()
                || lanes.stream().anyMatch(lane -> !lane.active)) {
            return;
        }
        var targets = attackTargets();
        if (targets.isEmpty()) {
            return;
        }
        Player target = targets.get(Math.floorMod(targetCursor++, targets.size()));
        Location center = config.movementAttacks() ? arena.groundBelow(target.getLocation()) : target.getLocation();
        if (center == null) {
            return;
        }
        var candidate =
                new BreathLane(center, config.laneLength(), config.laneWidth(), targetCursor % 2 == 1);
        var participants = attackParticipants();
        if (config.movementAttacks() && !arena.surface(candidate) || !arena.laneSafe(candidate, lanes, participants)) {
            candidate =
                    new BreathLane(center, config.laneLength(), config.laneWidth(), !candidate.eastWest);
            if (config.movementAttacks() && !arena.surface(candidate)
                    || !arena.laneSafe(candidate, lanes, participants)) {
                return;
            }
        }
        final BreathLane lane = candidate;
        if (config.movementAttacks() || debugAttack == DebugAttack.DIVE) {
            dive(lane, target, owner);
            return;
        }
        EnderDragon source = dragon;
        lane.indicator = lane.center.getWorld().spawn(lane.center.clone().add(0, 1.5, 0), TextDisplay.class);
        if (owner.isClosed() || dragon != source || !hunting || !lane.indicator.isValid()) {
            lane.indicator.remove();
            return;
        }
        EncounterWork laneWork = attackWork.child(() -> lanes.remove(lane));
        hazards.add(laneWork);
        laneWork.onCleanup(() -> hazards.remove(laneWork));
        laneWork.onCleanup(owner::close);
        owner.onCleanup(() -> {
            if (!lane.active) {
                laneWork.close();
            }
        });
        laneWork.own(lane.indicator, "lane");
        battles.faceWarningToViewer(lane.indicator);
        if (owner.isClosed()) {
            return;
        }
        lanes.add(lane);
        targets.forEach(player -> {
            player.sendActionBar(messages.renderFor(player, "shattered-crown.lane-warning", "seconds",
                    config.laneWarningSeconds()));
            player.playSound(lane.center, Sound.ENTITY_ENDER_DRAGON_GROWL, 0.7f, 1.4f);
        });
        drawLane(lane);
        final Scheduler.Task[] refresh = new Scheduler.Task[1];
        refresh[0] = laneWork.repeating(Duration.ofMillis(250), Duration.ofMillis(250), () -> {
            lane.age += 0.25;
            if (!lane.indicator.isValid() || !lane.active && !isParticipant(target)) {
                lane.indicator.remove();
                lanes.remove(lane);
                refresh[0].cancel();
                laneWork.close();
                return;
            }
            drawLane(lane);
        });
        owner.later(seconds(config.laneWarningSeconds()), () -> laneWork.run(() -> {
            if (!lane.indicator.isValid() || !lanes.contains(lane) || !isParticipant(target)) {
                owner.close();
                return;
            }
            if (!arena.laneSafe(lane, lanes.stream().filter(other -> other != lane).toList(),
                    source.getWorld().getPlayers().stream().filter(arena::participant).toList())) {
                lane.indicator.remove();
                lanes.remove(lane);
                refresh[0].cancel();
                owner.close();
                return;
            }
            lane.active = true;
            lane.age = 0;
            drawLane(lane);
            final Scheduler.Task[] damage = new Scheduler.Task[1];
            // Expiry wins over a cadence pulse on the same server tick: the lifetime is [start, end).
            laneWork.later(seconds(config.laneLifetimeSeconds()), () -> {
                damage[0].cancel();
                refresh[0].cancel();
                lane.indicator.remove();
                lanes.remove(lane);
                laneWork.close();
            });
            damage[0] = laneWork.repeating(Duration.ZERO, seconds(config.laneDamageCadenceSeconds()), () -> {
                if (!lane.indicator.isValid() || !lanes.contains(lane)) {
                    damage[0].cancel();
                    return;
                }
                for (Player player : source.getWorld().getPlayers()) {
                    if (dragon != source || !hunting) {
                        break;
                    }
                    if (isParticipant(player) && lane.contains(player.getLocation(), 0)) {
                        battles.bombardmentDamage(player, source, config.laneDamage());
                    }
                }
            });
            owner.close();
        }));
    }

    private void markPrey() {
        startAction(DebugAttack.PREY, this::markPrey);
    }

    /** Main completion closes only this child; deposited hazards are sibling Encounter work. */
    private void startAction(DebugAttack attack, java.util.function.Consumer<EncounterWork> setup) {
        if (mainWork != null || attackWork == null) {
            return;
        }
        EncounterWork owner = attackWork.child(() -> {
        });
        owner.onCleanup(() -> {
            if (mainWork == owner) {
                mainWork = null;
                mainAttack = null;
            }
        });
        mainWork = owner;
        mainAttack = attack;
        owner.run(() -> {
            setup.accept(owner);
            if (!owner.hasWork()) {
                owner.close();
            }
        });
    }

    private void markPrey(EncounterWork owner) {
        if (flight != null) {
            return;
        }
        // ponytail: serialize attack families; add concurrent combinations only with route-planning evidence.
        if (!attacking() || landingRequested || landingDue || !marks.isEmpty() || !areas.isEmpty()
                || !lanes.isEmpty()) {
            return;
        }
        var targets = attackTargets();
        EnderDragon source = dragon;
        for (int index = 0; index < Math.min(config.preyCount(), targets.size()); index++) {
            Player target = targets.get(Math.floorMod(targetCursor++, targets.size()));
            TextDisplay mark = source.getWorld().spawn(target.getLocation().add(0, 2.5, 0), TextDisplay.class);
            if (owner.isClosed() || dragon != source || !attacking() || !mark.isValid()) {
                mark.remove();
                break;
            }
            owner.own(mark, "prey");
            owner.onCleanup(() -> marks.remove(target.getUniqueId(), mark));
            battles.faceWarningToViewer(mark);
            if (owner.isClosed()) {
                return;
            }
            marks.put(target.getUniqueId(), mark);
            final double[] age = {0};
            mark.text(Component.text(target.getName() + " · " + config.preyCountdownSeconds() + "s",
                    NamedTextColor.GOLD));
            target.sendActionBar(messages.renderFor(target, "shattered-crown.prey-warning", "seconds",
                    config.preyCountdownSeconds()));
            target.playSound(target.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 0.7f, 1.4f);
            final Scheduler.Task[] refresh = new Scheduler.Task[1];
            refresh[0] = owner.repeating(Duration.ofMillis(250), Duration.ofMillis(250), () -> {
                if (!isParticipant(target) || !mark.isValid()) {
                    mark.remove();
                    marks.remove(target.getUniqueId(), mark);
                    refresh[0].cancel();
                    return;
                }
                age[0] += 0.25;
                double remaining = Math.max(0, config.preyCountdownSeconds() - age[0]);
                mark.teleport(target.getLocation().add(0, 2.5, 0));
                mark.text(Component.text(
                        target.getName() + " · " + String.format(java.util.Locale.ROOT, "%.1fs", remaining),
                        NamedTextColor.GOLD));
                target.sendActionBar(messages.renderFor(target, "shattered-crown.prey-warning", "seconds", remaining));
                Location ground = arena.groundBelow(target.getLocation());
                if (ground != null) {
                    drawAreaRing(new HuntArea(ground, config.poolRadius()), Particle.FLAME);
                }
            });
            owner.later(seconds(config.preyCountdownSeconds()), () -> {
                boolean valid = isParticipant(target) && mark.isValid() && marks.containsValue(mark);
                mark.remove();
                marks.remove(target.getUniqueId(), mark);
                refresh[0].cancel();
                if (!valid || landingRequested || areas.size() >= config.preyCount()) {
                    return;
                }
                Location ground = arena.groundBelow(target.getLocation());
                if (ground == null) {
                    server.getLogger().fine("[Shattered Crown] Prey pool skipped: no nearby footing");
                    return;
                }
                var pool = new HuntArea(ground, config.poolRadius());
                if (config.movementAttacks() && !arena.surface(pool)) {
                    return;
                }
                var participants = source.getWorld().getPlayers().stream().filter(arena::participant).toList();
                var combined = new ArrayList<>(areas);
                combined.add(pool);
                if (combined.stream().allMatch(candidate -> arena.areaSafe(candidate,
                        combined.stream().filter(other -> other != candidate).toList(), lanes, participants,
                        config.preyCountdownSeconds()))) {
                    activatePool(pool, source);
                } else {
                    server.getLogger().fine("[Shattered Crown] Prey pool skipped: terrain, overlap or escape route");
                }
            });
        }
        owner.later(seconds(config.preyCountdownSeconds()), owner::close);
    }

    private void activatePool(HuntArea pool, EnderDragon source) {
        EncounterWork poolWork = attackWork.child(() -> {
            areas.remove(pool);
            if (pool.indicator != null) {
                pool.indicator.remove();
            }
        });
        hazards.add(poolWork);
        poolWork.onCleanup(() -> hazards.remove(poolWork));
        poolWork.run(() -> {
            pool.indicator = source.getWorld().spawn(pool.center.clone().add(0, 1.5, 0), TextDisplay.class);
            if (poolWork.isClosed() || dragon != source || !attacking() || !pool.indicator.isValid()) {
                pool.indicator.remove();
                poolWork.close();
                return;
            }
            poolWork.own(pool.indicator, "pool");
            battles.faceWarningToViewer(pool.indicator);
            if (poolWork.isClosed()) {
                return;
            }
            areas.add(pool);
            pool.indicator.text(Component.text(poolLifetime() + "s", NamedTextColor.LIGHT_PURPLE));
            drawAreaRing(pool, Particle.DRAGON_BREATH);
            drawPool(pool);
            source.getWorld().playSound(pool.center, Sound.ENTITY_DRAGON_FIREBALL_EXPLODE, 1.0f, 0.7f);
            poolWork.later(seconds(poolLifetime()), poolWork::close);
            poolWork.repeating(Duration.ofMillis(250), Duration.ofMillis(250), () -> {
                if (!pool.indicator.isValid()) {
                    poolWork.close();
                    return;
                }
                pool.age += 0.25;
                pool.indicator.text(Component.text(String.format(java.util.Locale.ROOT, "%.1fs",
                        Math.max(0, poolLifetime() - pool.age)), NamedTextColor.LIGHT_PURPLE));
                drawAreaRing(pool, Particle.DRAGON_BREATH);
                drawPool(pool);
            });
            poolWork.repeating(Duration.ZERO, seconds(config.poolDamageCadenceSeconds()), () -> {
                if (!pool.indicator.isValid()) {
                    poolWork.close();
                    return;
                }
                for (Player recipient : source.getWorld().getPlayers()) {
                    if (dragon != source || poolWork.isClosed()) {
                        break;
                    }
                    if (isParticipant(recipient) && pool.contains(recipient.getLocation(), 0)) {
                        battles.bombardmentDamage(recipient, source, config.poolDamage());
                    }
                }
            });
        });
    }

    private void drawAreaRing(HuntArea area, Particle particle) {
        dragon.getWorld().getPlayers().stream().filter(arena::participant).limit(64).forEach(player -> {
            for (int point = 0; point < 32; point++) {
                double angle = area.direction - area.halfAngle + point * area.halfAngle * 2 / 31;
                CrownParticles.spawn(player, particle, area.point(angle, area.radius).add(0, 0.15, 0),
                        1, 0, 0, 0, 0);
            }
            if (area.halfAngle != Math.PI) {
                for (int point = 0; point <= 8; point++) {
                    for (double side : new double[] {-area.halfAngle, area.halfAngle}) {
                        CrownParticles.spawn(player, particle,
                                area.point(area.direction + side, area.radius * point / 8)
                                        .add(0, 0.15, 0),
                                1, 0, 0, 0, 0);
                    }
                }
            }
        });
    }

    private void dive(BreathLane lane, Player target, EncounterWork owner) {
        Location start = lane.point(-lane.length / 2, 0).add(0, 14, 0);
        Location end = lane.point(lane.length / 2, 0).add(0, 2, 0);
        if (!arena.contains(start) || !arena.contains(end)) {
            return;
        }
        EnderDragon source = dragon;
        lane.indicator = source.getWorld().spawn(lane.center.clone().add(0, 1.5, 0), TextDisplay.class);
        if (owner.isClosed() || !lane.indicator.isValid() || dragon != source || !attacking()) {
            lane.indicator.remove();
            return;
        }
        owner.own(lane.indicator, "lane");
        owner.onCleanup(() -> lanes.remove(lane));
        battles.faceWarningToViewer(lane.indicator);
        if (owner.isClosed()) {
            return;
        }
        lanes.add(lane);
        ownDragonPhase();
        flight = new DragonFlight(source, battles, owner,
                () -> dragon == source && attacking() && isParticipant(target) && lane.indicator.isValid(), () -> {
                    flight = null;
                    lanes.remove(lane);
                    owner.close();
                });
        DragonFlight movement = flight;
        announce("shattered-crown.dive-warning");
        Location origin = source.getLocation();
        movement.start(t -> DragonFlight.between(origin, start, t), config.laneWarningSeconds(), position -> {
            lane.age += 0.05;
            if (Math.round(lane.age * 20) % 5 != 0) {
                return;
            }
            drawLane(lane);
            // A moving arrow points down the fixed strip during the warning.
            source.getWorld().getPlayers().stream().filter(arena::participant).limit(64).forEach(player -> {
                double along = -lane.length / 2 + (lane.age % 1) * lane.length;
                for (int side : new int[] {-1, 1}) {
                    for (int point = 0; point < 5; point++) {
                        player.spawnParticle(Particle.END_ROD, lane.point(along - point * 0.4, side * point * 0.25)
                                .add(0, 0.3, 0), 1, 0, 0, 0, 0);
                    }
                }
            });
        }, () -> {
            if (!arena.laneSafe(lane, List.of(), source.getWorld().getPlayers().stream()
                    .filter(arena::participant).toList())) {
                movement.close();
                return;
            }
            lane.active = true;
            lane.age = config.laneLifetimeSeconds() - 3;
            Set<UUID> hit = new HashSet<>();
            movement.start(t -> {
                double along = -lane.length / 2 + lane.length * t;
                Location position = lane.point(along, 0);
                double groundY = position.getY();
                for (double across = -lane.width / 2; across <= lane.width / 2; across += 1) {
                    groundY = Math.max(groundY, lane.point(along, across).getY());
                }
                position.setY(groundY + 2 + 12 * Math.pow(Math.max(0, 1 - t * 3), 2));
                return position;
            }, 3, position -> {
                lane.age += 0.05;
                if (Math.round(lane.age * 20) % 5 == 0) {
                    drawLane(lane);
                }
                Location ground = lane.surface == null ? lane.center : lane.surface.project(position);
                if (ground == null || position.getY() > ground.getY() + 5) {
                    return;
                }
                for (Player player : source.getWorld().getPlayers()) {
                    if (dragon != source || flight != movement || !attacking()) {
                        break;
                    }
                    if (isParticipant(player) && lane.contains(player.getLocation(), 0)
                            && Math.abs((lane.eastWest ? player.getX() - position.getX()
                                    : player.getZ() - position.getZ())) <= 2
                            && hit.add(player.getUniqueId())) {
                        flightHit(player, source, config.laneDamage(), position);
                    }
                }
            }, () -> climb(movement, source));
        });
    }

    private void smash() {
        startAction(DebugAttack.SMASH, this::smash);
    }

    private void smash(EncounterWork owner) {
        var targets = attackTargets();
        if (targets.isEmpty()) {
            return;
        }
        Player target = targets.get(Math.floorMod(targetCursor++, targets.size()));
        Location ground = arena.groundBelow(target.getLocation());
        double radius = Math.min(config.sweepRadius(), 8);
        double warning = Math.max(config.sweepWarningSeconds(), (radius + 2) / 3);
        if (ground == null) {
            return;
        }
        HuntArea area = new HuntArea(ground, radius);
        if (!arena.surface(area)) {
            return;
        }
        Location above = ground.clone().add(0, 18, 0);
        if (!arena.contains(above) || !arena.areaSafe(area, areas, lanes, attackParticipants(), warning)) {
            return;
        }
        EnderDragon source = dragon;
        area.indicator = source.getWorld().spawn(ground.clone().add(0, 1.5, 0), TextDisplay.class);
        if (owner.isClosed() || !area.indicator.isValid() || dragon != source || !attacking()) {
            area.indicator.remove();
            return;
        }
        owner.own(area.indicator, "sweep");
        owner.onCleanup(() -> areas.remove(area));
        battles.faceWarningToViewer(area.indicator);
        if (owner.isClosed()) {
            return;
        }
        areas.add(area);
        ownDragonPhase();
        flight = new DragonFlight(source, battles, owner,
                () -> dragon == source && attacking() && isParticipant(target) && area.indicator.isValid(), () -> {
                    flight = null;
                    areas.remove(area);
                    owner.close();
                });
        DragonFlight movement = flight;
        announce("shattered-crown.smash-warning");
        Location origin = source.getLocation();
        movement.start(t -> DragonFlight.between(origin, above, t), warning, position -> {
            area.age += 0.05;
            if (Math.round(area.age * 20) % 5 != 0) {
                return;
            }
            area.indicator.text(Component.text(String.format(java.util.Locale.ROOT, "%.1fs",
                    Math.max(0, warning - area.age)), NamedTextColor.GOLD));
            drawAreaRing(area, Particle.FLAME);
        }, () -> {
            if (!arena.areaSafe(area, List.of(), List.of(), source.getWorld().getPlayers().stream()
                    .filter(arena::participant).toList(), warning)) {
                movement.close();
                return;
            }
            Location impact = ground.clone().add(0, 2, 0);
            movement.start(t -> DragonFlight.between(above, impact, t * t), 1,
                    position -> drawAreaRing(area, Particle.FLAME), () -> {
                        if (!arena.areaSafe(area, List.of(), List.of(), source.getWorld().getPlayers().stream()
                                .filter(arena::participant).toList(), warning)) {
                            movement.close();
                            return;
                        }
                        drawAreaRing(area, Particle.EXPLOSION);
                        source.getWorld().getPlayers().stream().filter(arena::participant).limit(64).forEach(player -> {
                            player.spawnParticle(Particle.EXPLOSION_EMITTER, ground, 1, 0, 0, 0, 0);
                            player.spawnParticle(Particle.BLOCK, ground.clone().add(0, 0.5, 0), 80,
                                    radius / 3, 0.5, radius / 3, 0.1,
                                    ground.getBlock().getRelative(0, -1, 0).getBlockData());
                        });
                        source.getWorld().playSound(ground, Sound.ENTITY_GENERIC_EXPLODE, 2, 0.6f);
                        for (Player player : source.getWorld().getPlayers()) {
                            if (dragon != source || flight != movement || !attacking()) {
                                break;
                            }
                            if (isParticipant(player) && area.contains(player.getLocation(), 0)) {
                                flightHit(player, source, config.sweepDamage(), ground);
                            }
                        }
                        area.indicator.text(Component.text("↓", NamedTextColor.GREEN));
                        movement.start(t -> impact.clone(), 3, position -> {
                        }, () -> climb(movement, source));
                    });
        });
    }

    private void climb(DragonFlight movement, EnderDragon source) {
        Location origin = source.getLocation();
        Location end = origin.clone().add(0, 16, 0);
        if (!arena.contains(end)) {
            movement.close();
            return;
        }
        movement.start(t -> DragonFlight.between(origin, end, t), 2, position -> {
        }, movement::close);
    }

    private void flightHit(Player player, EnderDragon source, double amount, Location center) {
        double health = player.getHealth();
        flightDamage = true;
        try {
            battles.bombardmentDamage(player, source, amount);
        } finally {
            flightDamage = false;
        }
        if (dragon == source && flight != null && attacking() && isParticipant(player) && player.getHealth() < health) {
            var direction = player.getLocation().toVector().subtract(center.toVector()).setY(0);
            if (direction.lengthSquared() < 0.001) {
                direction.setX(1);
            }
            player.setVelocity(direction.normalize().multiply(config.sweepKnockback()).setY(0.4));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFlightContact(EntityDamageByEntityEvent event) {
        if (flight != null && !flightDamage && (event.getDamager().equals(dragon)
                || event.getDamager() instanceof ComplexEntityPart part && part.getParent().equals(dragon))) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFlightTerrain(org.bukkit.event.entity.EntityExplodeEvent event) {
        if (flight != null && event.getEntity().equals(dragon)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFlightPush(io.papermc.paper.event.entity.EntityKnockbackEvent event) {
        if (flight != null && event instanceof io.papermc.paper.event.entity.EntityPushedByEntityAttackEvent pushed
                && pushed.getPushedBy().equals(dragon)) {
            event.setCancelled(true);
        }
    }

    private void drawPool(HuntArea pool) {
        // Deterministic filled disk: particles never imply danger beyond the actual radius.
        List<Location> points = new ArrayList<>();
        for (int point = 0; point < 64; point++) {
            double radius = pool.radius * Math.sqrt((point + 0.5) / 64);
            Location spot = arena.groundBelow(pool.point(point * 2.399963229728653, radius));
            if (spot != null) {
                points.add(spot.add(0, 0.35, 0));
            }
        }
        dragon.getWorld().getPlayers().stream().filter(arena::participant).limit(64).forEach(player -> {
            for (Location spot : points) {
                CrownParticles.spawn(player, Particle.DRAGON_BREATH, spot, 1, 0, 0.15, 0, 0);
            }
        });
    }

    private void warnSweep() {
        if (config.movementAttacks()) {
            smash();
        } else {
            startAction(DebugAttack.SMASH, this::warnSweep);
        }
    }

    private void warnSweep(EncounterWork owner) {
        if (flight != null) {
            return;
        }
        if (!hunting || landingRequested || landingDue || !marks.isEmpty() || !areas.isEmpty() || !lanes.isEmpty()) {
            return;
        }
        EnderDragon source = dragon;
        Location ground = source.getLocation();
        ground.setY(ground.getWorld().getHighestBlockYAt(ground.getBlockX(), ground.getBlockZ()) + 1);
        if (Math.abs(source.getLocation().getY() - ground.getY()) > 24) {
            return;
        }
        var sweep = new HuntArea(ground, config.sweepRadius(), ground.getYaw(), config.sweepAngle());
        var participants = source.getWorld().getPlayers().stream().filter(arena::participant).toList();
        if (participants.stream().noneMatch(player -> sweep.contains(player.getLocation(), 1))
                || !arena.areaSafe(sweep, areas, lanes, participants, config.sweepWarningSeconds())) {
            return;
        }
        sweep.indicator = source.getWorld().spawn(sweep.center.clone().add(0, 1.5, 0), TextDisplay.class);
        if (owner.isClosed() || dragon != source || !hunting || !sweep.indicator.isValid()) {
            sweep.indicator.remove();
            return;
        }
        owner.own(sweep.indicator, "sweep");
        owner.onCleanup(() -> areas.remove(sweep));
        battles.faceWarningToViewer(sweep.indicator);
        if (owner.isClosed()) {
            return;
        }
        areas.add(sweep);
        participants.forEach(player -> {
            player.sendActionBar(messages.renderFor(player, "shattered-crown.sweep-warning", "seconds",
                    config.sweepWarningSeconds()));
            player.playSound(sweep.center, Sound.ENTITY_ENDER_DRAGON_GROWL, 0.7f, 1.4f);
        });
        sweep.indicator.text(Component.text(config.sweepWarningSeconds() + "s", NamedTextColor.GOLD));
        drawAreaRing(sweep, Particle.FLAME);
        final Scheduler.Task[] refresh = new Scheduler.Task[1];
        refresh[0] = owner.repeating(Duration.ofMillis(250), Duration.ofMillis(250), () -> {
            if (!sweep.indicator.isValid()) {
                areas.remove(sweep);
                refresh[0].cancel();
                owner.close();
                return;
            }
            sweep.age += 0.25;
            sweep.indicator.text(Component.text(String.format(java.util.Locale.ROOT, "%.1fs",
                    Math.max(0, config.sweepWarningSeconds() - sweep.age)), NamedTextColor.GOLD));
            drawAreaRing(sweep, Particle.FLAME);
        });
        owner.later(seconds(config.sweepWarningSeconds()), () -> {
            boolean visible = sweep.indicator.isValid() && areas.contains(sweep);
            sweep.indicator.remove();
            areas.remove(sweep);
            refresh[0].cancel();
            if (!visible || !arena.areaSafe(sweep, areas, lanes,
                    source.getWorld().getPlayers().stream().filter(arena::participant).toList(),
                    config.sweepWarningSeconds())) {
                return;
            }
            drawAreaRing(sweep, Particle.SWEEP_ATTACK);
            for (Player recipient : source.getWorld().getPlayers()) {
                if (dragon != source || !hunting) {
                    break;
                }
                if (isParticipant(recipient) && sweep.contains(recipient.getLocation(), 0)) {
                    double before = recipient.getHealth();
                    battles.bombardmentDamage(recipient, source, config.sweepDamage());
                    // Native cancellation/armor/invulnerability is authoritative; no successful damage, no shove.
                    if (dragon == source && hunting && isParticipant(recipient) && recipient.getHealth() < before) {
                        var velocity = recipient.getLocation().toVector().subtract(sweep.center.toVector()).setY(0);
                        if (velocity.lengthSquared() < 1e-9) {
                            velocity =
                                    new org.bukkit.util.Vector(Math.cos(sweep.direction), 0, Math.sin(sweep.direction));
                        }
                        recipient.setVelocity(velocity.normalize().multiply(config.sweepKnockback())
                                .setY(config.sweepKnockback() / 3));
                    }
                }
            }
        });
        owner.later(seconds(config.sweepWarningSeconds()), owner::close);
    }

    private void drawLane(BreathLane lane) {
        double remaining =
                Math.max(0, (lane.active ? config.laneLifetimeSeconds() : config.laneWarningSeconds()) - lane.age);
        lane.indicator.text(Component.text(String.format(java.util.Locale.ROOT, "%.1fs", remaining),
                lane.active ? NamedTextColor.LIGHT_PURPLE : NamedTextColor.GOLD));
        dragon.getWorld().getPlayers().stream().filter(arena::participant).limit(64).forEach(player -> {
            for (int point = 0; point <= 16; point++) {
                double along = -lane.length / 2 + lane.length * point / 16;
                for (double side : new double[] {-lane.width / 2, lane.width / 2}) {
                    CrownParticles.spawn(player, lane.active ? Particle.DRAGON_BREATH : Particle.FLAME,
                            lane.point(along, side).add(0, 0.15, 0), 1, 0, 0, 0, 0);
                }
            }
            for (int point = 0; point <= 4; point++) {
                double across = -lane.width / 2 + lane.width * point / 4;
                for (double end : new double[] {-lane.length / 2, lane.length / 2}) {
                    CrownParticles.spawn(player, lane.active ? Particle.DRAGON_BREATH : Particle.FLAME,
                            lane.point(end, across).add(0, 0.15, 0), 1, 0, 0, 0, 0);
                }
            }
        });
    }

    private void warnBombardment() {
        startAction(DebugAttack.BOMBARDMENT, this::warnBombardment);
    }

    private void warnBombardment(EncounterWork owner) {
        if (dragon == null || debugAttack == null && anchors.isEmpty() || bombardmentIndicator != null) {
            return;
        }
        var targets = attackTargets().stream().limit(64).toList();
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
        if (owner.isClosed() || dragon != warnedDragon || debugAttack == null && anchors.isEmpty()
                || !bombardmentIndicator.isValid()) {
            cancelPendingBombardment();
            return;
        }
        owner.own(bombardmentIndicator, "warning");
        owner.onCleanup(this::cancelPendingBombardment);
        battles.faceWarningToViewer(bombardmentIndicator);
        if (owner.isClosed()) {
            return;
        }
        bombardmentIndicator.setShadowed(true);
        bombardmentIndicator.text(Component.text(config.bombardmentWarningSeconds(), NamedTextColor.GOLD));
        targets.forEach(player -> {
            player.sendActionBar(messages.renderFor(player, "shattered-crown.bombardment-warning", "seconds",
                    config.bombardmentWarningSeconds()));
            player.playSound(impact, Sound.ENTITY_ENDER_DRAGON_GROWL, 0.7f, 1.4f);
        });
        drawBombardment(impact);
        final int[] pulses = {0};
        bombardmentWarning = owner.repeating(Duration.ofMillis(250), Duration.ofMillis(250), () -> {
            if (!isParticipant(target) || bombardmentIndicator == null || !bombardmentIndicator.isValid()) {
                cancelPendingBombardment();
                owner.close();
                return;
            }
            double remaining = Math.max(0, config.bombardmentWarningSeconds() - ++pulses[0] * 0.25);
            bombardmentIndicator.text(Component.text(String.format(java.util.Locale.ROOT, "%.1f", remaining),
                    NamedTextColor.GOLD));
            drawBombardment(impact);
        });
        bombardmentImpact = owner.later(seconds(config.bombardmentWarningSeconds()), () -> {
            boolean visible = bombardmentIndicator != null && bombardmentIndicator.isValid();
            cancelPendingBombardment();
            if (!visible || dragon == null || debugAttack == null && anchors.isEmpty() || !isParticipant(target)) {
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
                if (dragon != source || debugAttack == null && anchors.isEmpty()) {
                    break;
                }
                if (isParticipant(player) && arena.inBombardment(player.getLocation(), impact,
                        config.bombardmentRadius())) {
                    battles.bombardmentDamage(player, source, config.bombardmentDamage());
                }
            }
        });
        owner.later(seconds(config.bombardmentWarningSeconds()), owner::close);
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
            TextDisplay indicator = bombardmentIndicator;
            bombardmentIndicator = null;
            indicator.remove();
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
                || work != null && work.owns(entity) || attackWork != null && attackWork.owns(entity);
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
        EncounterWork owner = requireWork();
        Mob defender = (Mob) ground.getWorld().spawnEntity(ground.clone().add(2, 0, 0),
                EntityType.valueOf(config.defenderType()));
        try {
            if (!defender.isValid() || work != owner || dragon == null) {
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
        EncounterWork owner = requireWork();
        EnderCrystal crystal = ground.getWorld().spawn(ground, EnderCrystal.class);
        if (!crystal.isValid() || work != owner || dragon == null) {
            crystal.remove();
            throw new IllegalStateException("Anchor spawn was refused");
        }
        own(crystal, "anchor");
        return crystal;
    }

    private void installAnchor(Location ground) {
        var objective = new Anchor(spawnAnchor(ground), ground);
        anchors.add(objective);
        for (int i = 0; i < config.defenderCount(); i++) {
            Mob defender = spawnDefender(ground);
            defenders.add(defender);
            objective.wave.add(defender);
        }
        objective.waveInstalled = true;
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
        } else if (source instanceof org.bukkit.entity.AreaEffectCloud cloud
                && cloud.getSource() instanceof Entity owner) {
            source = owner;
        }
        if (dragon == null && target instanceof EnderDragon candidate && isManagedDragon(candidate)) {
            if (protectedSource(source) || protectedSource(direct)
                    || protectedSource(event.getDamageSource().getDirectEntity())) {
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
        if (enemy && (protectedSource(source) || protectedSource(direct)
                || protectedSource(event.getDamageSource().getDirectEntity()))) {
            event.setCancelled(true);
            return;
        }
        if (target.equals(dragon) && (!anchors.isEmpty() || finalEntered && !hunting)) {
            event.setCancelled(true);
        } else if (target.equals(dragon)) {
            if (!Double.isFinite(event.getFinalDamage()) || event.getFinalDamage() < 0) {
                event.setCancelled(true);
                return;
            }
            if (explosive(event)) {
                double spent = java.util.Arrays.stream(explosionBuckets).sum()
                        + explosionReservations.values().stream().mapToDouble(Double::doubleValue).sum();
                capDamage(event, config.explosionWindowBudget() - spent);
            }
            if (!event.isCancelled() && !finalEntered) {
                double pending = damageReservations.values().stream().mapToDouble(Double::doubleValue).sum();
                capDamage(event, Math.max(0,
                        dragon.getHealth() - attemptMaxHealth * config.finalHealthFraction() - pending));
            }
            if (!event.isCancelled()) {
                damageReservations.put(event, event.getFinalDamage());
            }
            if (!event.isCancelled() && explosive(event)) {
                explosionReservations.put(event, event.getFinalDamage());
            }
        }
        if (objective != null) {
            event.setCancelled(true); // Remove the objective without a terrain-damaging crystal explosion.
            if (objective.waveInstalled && objective.wave.isEmpty() && source instanceof Player player
                    && isParticipant(player)) {
                objective.crystal.remove();
                anchors.remove(objective);
                if (anchors.isEmpty()) {
                    stopBombardments();
                    announce(finalEntered ? "shattered-crown.final-hunt" : "shattered-crown.unshielded");
                    startHunt();
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
        if (entity == null) {
            return false;
        }
        Entity attribution = null;
        if (entity instanceof Projectile projectile && projectile.getShooter() instanceof Entity shooter) {
            attribution = shooter;
        } else if (entity instanceof TNTPrimed tnt) {
            attribution = tnt.getSource();
        } else if (entity instanceof org.bukkit.entity.AreaEffectCloud cloud
                && cloud.getSource() instanceof Entity owner) {
            attribution = owner;
        }
        return isProtectedArrival(entity.getLocation()) || entity.getPersistentDataContainer().has(PROTECTED_SHOT)
                || attribution != null && (isProtectedArrival(attribution.getLocation())
                        || attribution.getPersistentDataContainer().has(PROTECTED_SHOT));
    }

    private boolean encounterEnemy(Entity entity) {
        Entity target = entity instanceof ComplexEntityPart part ? part.getParent() : entity;
        return target.equals(dragon) || target instanceof EnderDragon candidate && isManagedDragon(candidate)
                || defenders.contains(target) || anchors.stream().anyMatch(anchor -> target.equals(anchor.crystal));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onLingering(org.bukkit.event.entity.LingeringPotionSplashEvent event) {
        if (protectedSource(event.getEntity())) {
            event.getAreaEffectCloud().getPersistentDataContainer().set(PROTECTED_SHOT, PersistentDataType.BYTE,
                    (byte) 1);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPotion(org.bukkit.event.entity.PotionSplashEvent event) {
        if (protectedSource(event.getEntity())) {
            event.getAffectedEntities().stream().filter(this::encounterEnemy)
                    .forEach(entity -> event.setIntensity(entity, 0));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCloud(org.bukkit.event.entity.AreaEffectCloudApplyEvent event) {
        if (protectedSource(event.getEntity())) {
            event.getAffectedEntities().removeIf(this::encounterEnemy);
        }
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
        if ((event.getEntity() instanceof TNTPrimed || event.getEntity() instanceof org.bukkit.entity.AreaEffectCloud)
                && protectedSource(event.getEntity())) {
            event.getEntity().getPersistentDataContainer().set(PROTECTED_SHOT, PersistentDataType.BYTE, (byte) 1);
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
            cancelMark(player);
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

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPrematureDeath(EntityDeathEvent event) {
        if (event.getEntity().equals(dragon) && (!finalEntered || !anchors.isEmpty() || !hunting)) {
            event.setCancelled(true);
            event.setReviveHealth(attemptMaxHealth * config.finalHealthFraction());
            if (anchors.isEmpty()) {
                enterFinalGate();
            }
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
                        player.sendActionBar(messages.renderFor(player,
                                finalEntered ? "shattered-crown.final-status" : "shattered-crown.hunt-status"));
                    } else {
                        player.sendActionBar(messages.renderFor(player,
                                finalEntered ? "shattered-crown.final-shield-status" : "shattered-crown.shield-status",
                                "anchors",
                                anchors.size(), "exposed",
                                anchors.stream().filter(anchor -> anchor.wave.isEmpty()).count(),
                                "defenders", defenders.size()));
                    }
                });
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (arena != null) {
            if (!arena.participantAt(event.getPlayer(), event.getTo(), event.getPlayer().getGameMode())) {
                cancelMark(event.getPlayer());
            }
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
        cancelMark(event.getPlayer());
        updateAbsence(event.getPlayer(), false);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onGameMode(PlayerGameModeChangeEvent event) {
        if (arena != null) {
            if (!arena.participantAt(event.getPlayer(), event.getPlayer().getLocation(), event.getNewGameMode())) {
                cancelMark(event.getPlayer());
            }
            updateAbsence(event.getPlayer(),
                    arena.participantAt(event.getPlayer(), event.getPlayer().getLocation(), event.getNewGameMode()));
        }
    }

    private void cancelMark(Player player) {
        var mark = marks.remove(player.getUniqueId());
        if (mark != null) {
            mark.remove();
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
        if (attackWork != null) {
            attackWork.close();
            attackWork = null;
        }
        hunting = false;
        endLanding();
        lanes.clear();
        marks.clear();
        areas.clear();
        debugAttack = null;
        debugTarget = null;
        manualMode = false;
        rotation = null;
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
        openingGround.clear();
        finalEntered = false;
        finalAcceptance = null;
        explosionBuckets = null;
        explosionCursor = 0;
        explosionReservations.clear();
        // Abort may run inside a later damage listener, before native damage applies.
        damageReservations.keySet().forEach(event -> event.setCancelled(true));
        damageReservations.clear();
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
        attemptMaxHealth = 0;
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
