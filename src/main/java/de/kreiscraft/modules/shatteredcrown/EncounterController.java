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
        Duration cadence = seconds(config.bombardmentCadenceSeconds());
        bombardmentCadence = attackWork.repeating(cadence, cadence, this::warnBombardment);
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
        endLanding();
        if (dragon != source || closed) {
            return;
        }
        stopBombardments();
        attackWork.close();
        attackWork = new EncounterWork(scheduler, server.getLogger());
        lanes.clear();
        marks.clear();
        areas.clear();
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
        Duration cadence = seconds(config.bombardmentCadenceSeconds());
        bombardmentCadence = attackWork.repeating(cadence, cadence, this::warnBombardment);
        updateAbsence(null, false);
    }

    private void startHunt() {
        if (hunting) {
            return;
        }
        hunting = true;
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
        if (!hunting || dragon == null || !anchors.isEmpty() || landingRequested || !lanes.isEmpty()
                || !marks.isEmpty() || !areas.isEmpty()
                || dragon.getWorld().getPlayers().stream().noneMatch(arena::participant) || !arena.landingSafe()
                || dragon.getPhase() == EnderDragon.Phase.DYING) {
            return;
        }
        ownDragonPhase();
        landingDue = false;
        if (landingDeferral != null) {
            landingDeferral.cancel();
            landingDeferral = null;
        }
        landingRequested = true;
        announce("shattered-crown.landing-approach");
        dragon.setPhase(EnderDragon.Phase.FLY_TO_PORTAL);
        if (dragon == null || !hunting) {
            return;
        }
        landingDeadline = attackWork.later(seconds(config.landingApproachSeconds()), this::endLanding);
        landingMonitor = attackWork.repeating(Duration.ofMillis(250), Duration.ofMillis(250), () -> {
            if (!arena.landingSafe() || dragon.getWorld().getPlayers().stream().noneMatch(arena::participant)) {
                endLanding();
            } else if (!landingWindow && sitting(dragon.getPhase()) && arena.atPodium(dragon.getLocation())) {
                landingWindow = true;
                landingDeadline.cancel();
                dragon.setPhase(EnderDragon.Phase.SEARCH_FOR_BREATH_ATTACK_TARGET);
                if (dragon == null || !hunting) {
                    return;
                }
                announce("shattered-crown.landing-window");
                landingEnd = attackWork.later(seconds(config.landingWindowSeconds()), this::endLanding);
            }
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDragonPhase(org.bukkit.event.entity.EnderDragonChangePhaseEvent event) {
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
        if (dragon != null && hunting && dragon.getPhase() != EnderDragon.Phase.DYING) {
            dragon.setPhase(wasSitting ? EnderDragon.Phase.LEAVE_PORTAL : EnderDragon.Phase.CIRCLING);
        }
    }

    private void warnLane() {
        if (!hunting || landingRequested || landingDue || dragon == null || !anchors.isEmpty() || lanes.size() >= 2
                || !marks.isEmpty() || !areas.isEmpty()
                || lanes.stream().anyMatch(lane -> !lane.active)) {
            return;
        }
        var targets = dragon.getWorld().getPlayers().stream().filter(arena::participant)
                .sorted(java.util.Comparator.comparing(Player::getUniqueId)).toList();
        if (targets.isEmpty()) {
            return;
        }
        Player target = targets.get(Math.floorMod(targetCursor++, targets.size()));
        var candidate =
                new BreathLane(target.getLocation(), config.laneLength(), config.laneWidth(), targetCursor % 2 == 1);
        if (!arena.laneSafe(candidate, lanes, targets)) {
            candidate =
                    new BreathLane(target.getLocation(), config.laneLength(), config.laneWidth(), !candidate.eastWest);
            if (!arena.laneSafe(candidate, lanes, targets)) {
                return;
            }
        }
        final BreathLane lane = candidate;
        EnderDragon source = dragon;
        lane.indicator = lane.center.getWorld().spawn(lane.center.clone().add(0, 1.5, 0), TextDisplay.class);
        if (dragon != source || !hunting || !lane.indicator.isValid()) {
            lane.indicator.remove();
            return;
        }
        attackWork.own(lane.indicator, "lane");
        battles.faceWarningToViewer(lane.indicator);
        lanes.add(lane);
        targets.forEach(player -> {
            player.sendActionBar(messages.renderFor(player, "shattered-crown.lane-warning", "seconds",
                    config.laneWarningSeconds()));
            player.playSound(lane.center, Sound.ENTITY_ENDER_DRAGON_GROWL, 0.7f, 1.4f);
        });
        drawLane(lane);
        final Scheduler.Task[] refresh = new Scheduler.Task[1];
        refresh[0] = attackWork.repeating(Duration.ofMillis(250), Duration.ofMillis(250), () -> {
            lane.age += 0.25;
            if (!lane.indicator.isValid() || !lane.active && !isParticipant(target)) {
                lane.indicator.remove();
                lanes.remove(lane);
                refresh[0].cancel();
                return;
            }
            drawLane(lane);
        });
        attackWork.later(seconds(config.laneWarningSeconds()), () -> {
            if (!lane.indicator.isValid() || !lanes.contains(lane) || !isParticipant(target)) {
                return;
            }
            if (!arena.laneSafe(lane, lanes.stream().filter(other -> other != lane).toList(),
                    source.getWorld().getPlayers().stream().filter(arena::participant).toList())) {
                lane.indicator.remove();
                lanes.remove(lane);
                refresh[0].cancel();
                return;
            }
            lane.active = true;
            lane.age = 0;
            drawLane(lane);
            final Scheduler.Task[] damage = new Scheduler.Task[1];
            // Expiry wins over a cadence pulse on the same server tick: the lifetime is [start, end).
            attackWork.later(seconds(config.laneLifetimeSeconds()), () -> {
                damage[0].cancel();
                refresh[0].cancel();
                lane.indicator.remove();
                lanes.remove(lane);
            });
            damage[0] = attackWork.repeating(Duration.ZERO, seconds(config.laneDamageCadenceSeconds()), () -> {
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
        });
    }

    private void markPrey() {
        // ponytail: serialize attack families; add concurrent combinations only with route-planning evidence.
        if (!hunting || landingRequested || landingDue || !marks.isEmpty() || !areas.isEmpty() || !lanes.isEmpty()) {
            return;
        }
        var targets = dragon.getWorld().getPlayers().stream().filter(arena::participant)
                .sorted(java.util.Comparator.comparing(Player::getUniqueId)).toList();
        EnderDragon source = dragon;
        for (int index = 0; index < Math.min(config.preyCount(), targets.size()); index++) {
            Player target = targets.get(Math.floorMod(targetCursor++, targets.size()));
            TextDisplay mark = source.getWorld().spawn(target.getLocation().add(0, 2.5, 0), TextDisplay.class);
            if (dragon != source || !hunting || !mark.isValid()) {
                mark.remove();
                break;
            }
            attackWork.own(mark, "prey");
            battles.faceWarningToViewer(mark);
            marks.put(target.getUniqueId(), mark);
            final double[] age = {0};
            mark.text(Component.text(target.getName() + " · " + config.preyCountdownSeconds() + "s",
                    NamedTextColor.GOLD));
            target.sendActionBar(messages.renderFor(target, "shattered-crown.prey-warning", "seconds",
                    config.preyCountdownSeconds()));
            target.playSound(target.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 0.7f, 1.4f);
            final Scheduler.Task[] refresh = new Scheduler.Task[1];
            refresh[0] = attackWork.repeating(Duration.ofMillis(250), Duration.ofMillis(250), () -> {
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
                var footprint = new HuntArea(target.getLocation(), config.poolRadius());
                drawAreaRing(footprint, Particle.FLAME);
            });
            attackWork.later(seconds(config.preyCountdownSeconds()), () -> {
                boolean valid = isParticipant(target) && mark.isValid() && marks.containsValue(mark);
                mark.remove();
                marks.remove(target.getUniqueId(), mark);
                refresh[0].cancel();
                if (!valid || landingRequested || areas.size() >= config.preyCount()) {
                    return;
                }
                var pool = new HuntArea(target.getLocation(), config.poolRadius());
                var participants = source.getWorld().getPlayers().stream().filter(arena::participant).toList();
                var combined = new ArrayList<>(areas);
                combined.add(pool);
                if (combined.stream().allMatch(candidate -> arena.areaSafe(candidate,
                        combined.stream().filter(other -> other != candidate).toList(), lanes, participants,
                        config.preyCountdownSeconds()))) {
                    activatePool(pool, source);
                }
            });
        }
    }

    private void activatePool(HuntArea pool, EnderDragon source) {
        pool.indicator = source.getWorld().spawn(pool.center.clone().add(0, 1.5, 0), TextDisplay.class);
        if (dragon != source || !hunting || !pool.indicator.isValid()) {
            pool.indicator.remove();
            return;
        }
        attackWork.own(pool.indicator, "pool");
        battles.faceWarningToViewer(pool.indicator);
        areas.add(pool);
        pool.indicator.text(Component.text(poolLifetime() + "s", NamedTextColor.LIGHT_PURPLE));
        drawAreaRing(pool, Particle.DRAGON_BREATH);
        final Scheduler.Task[] refresh = new Scheduler.Task[1];
        final Scheduler.Task[] damage = new Scheduler.Task[1];
        Runnable expire = () -> {
            pool.indicator.remove();
            areas.remove(pool);
            refresh[0].cancel();
            damage[0].cancel();
        };
        attackWork.later(seconds(poolLifetime()), expire);
        refresh[0] = attackWork.repeating(Duration.ofMillis(250), Duration.ofMillis(250), () -> {
            if (!pool.indicator.isValid()) {
                expire.run();
                return;
            }
            pool.age += 0.25;
            pool.indicator.text(Component.text(String.format(java.util.Locale.ROOT, "%.1fs",
                    Math.max(0, poolLifetime() - pool.age)), NamedTextColor.LIGHT_PURPLE));
            drawAreaRing(pool, Particle.DRAGON_BREATH);
        });
        damage[0] = attackWork.repeating(Duration.ZERO, seconds(config.poolDamageCadenceSeconds()), () -> {
            if (!pool.indicator.isValid()) {
                expire.run();
                return;
            }
            for (Player recipient : source.getWorld().getPlayers()) {
                if (dragon != source || !hunting) {
                    break;
                }
                if (isParticipant(recipient) && pool.contains(recipient.getLocation(), 0)) {
                    battles.bombardmentDamage(recipient, source, config.poolDamage());
                }
            }
        });
    }

    private void drawAreaRing(HuntArea area, Particle particle) {
        dragon.getWorld().getPlayers().stream().filter(arena::participant).limit(64).forEach(player -> {
            for (int point = 0; point < 32; point++) {
                double angle = area.direction - area.halfAngle + point * area.halfAngle * 2 / 31;
                player.spawnParticle(particle, area.point(angle, area.radius).add(0, 0.15, 0),
                        1, 0, 0, 0, 0);
            }
            if (area.halfAngle != Math.PI) {
                for (int point = 0; point <= 8; point++) {
                    for (double side : new double[] {-area.halfAngle, area.halfAngle}) {
                        player.spawnParticle(particle, area.point(area.direction + side, area.radius * point / 8)
                                .add(0, 0.15, 0), 1, 0, 0, 0, 0);
                    }
                }
            }
        });
    }

    private void warnSweep() {
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
        if (dragon != source || !hunting || !sweep.indicator.isValid()) {
            sweep.indicator.remove();
            return;
        }
        attackWork.own(sweep.indicator, "sweep");
        battles.faceWarningToViewer(sweep.indicator);
        areas.add(sweep);
        participants.forEach(player -> {
            player.sendActionBar(messages.renderFor(player, "shattered-crown.sweep-warning", "seconds",
                    config.sweepWarningSeconds()));
            player.playSound(sweep.center, Sound.ENTITY_ENDER_DRAGON_GROWL, 0.7f, 1.4f);
        });
        sweep.indicator.text(Component.text(config.sweepWarningSeconds() + "s", NamedTextColor.GOLD));
        drawAreaRing(sweep, Particle.FLAME);
        final Scheduler.Task[] refresh = new Scheduler.Task[1];
        refresh[0] = attackWork.repeating(Duration.ofMillis(250), Duration.ofMillis(250), () -> {
            if (!sweep.indicator.isValid()) {
                areas.remove(sweep);
                refresh[0].cancel();
                return;
            }
            sweep.age += 0.25;
            sweep.indicator.text(Component.text(String.format(java.util.Locale.ROOT, "%.1fs",
                    Math.max(0, config.sweepWarningSeconds() - sweep.age)), NamedTextColor.GOLD));
            drawAreaRing(sweep, Particle.FLAME);
        });
        attackWork.later(seconds(config.sweepWarningSeconds()), () -> {
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
                    player.spawnParticle(lane.active ? Particle.DRAGON_BREATH : Particle.FLAME,
                            lane.point(along, side).add(0, 0.15, 0), 1, 0, 0, 0, 0);
                }
            }
            for (int point = 0; point <= 4; point++) {
                double across = -lane.width / 2 + lane.width * point / 4;
                for (double end : new double[] {-lane.length / 2, lane.length / 2}) {
                    player.spawnParticle(lane.active ? Particle.DRAGON_BREATH : Particle.FLAME,
                            lane.point(end, across).add(0, 0.15, 0), 1, 0, 0, 0, 0);
                }
            }
        });
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
        attackWork.own(bombardmentIndicator, "warning");
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
        bombardmentWarning = attackWork.repeating(Duration.ofMillis(250), Duration.ofMillis(250), () -> {
            if (!isParticipant(target) || bombardmentIndicator == null || !bombardmentIndicator.isValid()) {
                cancelPendingBombardment();
                return;
            }
            double remaining = Math.max(0, config.bombardmentWarningSeconds() - ++pulses[0] * 0.25);
            bombardmentIndicator.text(Component.text(String.format(java.util.Locale.ROOT, "%.1f", remaining),
                    NamedTextColor.GOLD));
            drawBombardment(impact);
        });
        bombardmentImpact = attackWork.later(seconds(config.bombardmentWarningSeconds()), () -> {
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
        hunting = false;
        endLanding();
        lanes.clear();
        marks.clear();
        areas.clear();
        stopBombardments();
        if (attackWork != null) {
            attackWork.close();
            attackWork = null;
        }
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
