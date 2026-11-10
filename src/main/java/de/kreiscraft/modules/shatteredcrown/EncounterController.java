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
import org.bukkit.command.CommandSender;
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

/**
 * Owns one managed battle's attempt, objectives and cancellable work on the server thread.
 * Communication policy: automatic cues stay in-world (displays, sounds, visuals, native boss bars),
 * never chat, action bars or replacement titles. Only explicitly requested debug replies use chat.
 */
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
    private final java.util.Map<UUID, Anchor> combatOwners = new java.util.HashMap<>();
    private final java.util.Map<UUID, java.util.function.Consumer<Mob>> defenderInitializers =
            new java.util.HashMap<>();
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
    private EncounterWork clockWork;
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
    private final Set<HuntArea> pools = new HashSet<>();
    private final java.util.Map<UUID, Long> poolCooldowns = new java.util.HashMap<>();
    private long encounterTicks;
    private final java.util.Map<UUID, TextDisplay> marks = new java.util.HashMap<>();
    private boolean hunting;
    private boolean finalEntered;
    private int waveNumber;
    private int clearedWaves;
    private int wavePopulation;
    private int rosterUnits;
    private boolean installingWave;
    private EncounterWork waveWork;

    public enum AttackFamily {
        BOMBARDMENT, PREY, DIVE, RAIN, SMASH
    }

    /** Permanent unlocks, independent of the current wave's temporary shield. */
    public Set<AttackFamily> unlockedAttacks() {
        if (dragon == null) {
            return Set.of();
        }
        var unlocked = java.util.EnumSet.of(AttackFamily.BOMBARDMENT, AttackFamily.PREY);
        if (clearedWaves >= 1) {
            unlocked.add(AttackFamily.DIVE);
        }
        if (clearedWaves >= 2) {
            unlocked.add(AttackFamily.RAIN);
        }
        if (clearedWaves >= 3) {
            unlocked.add(AttackFamily.SMASH);
        }
        return Set.copyOf(unlocked);
    }

    public int waveNumber() {
        return waveNumber;
    }

    public int wavePopulation() {
        return wavePopulation;
    }

    public int rosterUnits() {
        return rosterUnits;
    }

    private double nextGateFraction() {
        return waveNumber < 2 ? 0.65 : 0.30;
    }

    private boolean shielded() {
        return installingWave || !anchors.isEmpty();
    }

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
    private final java.util.random.RandomGenerator random;
    private Scheduler.Task rotationLoop;
    private long nextLanding;
    private boolean landingNeedsRangedTurn;
    private UUID populationWorld;
    private int entryPopulation;
    private long graceTicks;
    private Scheduler.Task entryDiscovery;

    private boolean openingGrace() {
        return graceTicks > 0 && debugAttack == null;
    }

    private void discoverSoon() {
        if (!closed && dragon == null && entryDiscovery == null) {
            entryDiscovery = scheduler.runLater(Duration.ZERO, () -> {
                entryDiscovery = null;
                pulse();
            });
        }
    }

    /** Frozen server-wide population at first entry; occupied-End recovery snapshots discovery. */
    public int firstEntryPopulation() {
        return entryPopulation;
    }

    /** Remaining preparation time; manual attack debugging does not replenish this clock. */
    public double remainingGraceSeconds() {
        return graceTicks / 20.0;
    }

    /** Everyone connected in the managed End, regardless of mode, health or arena position. */
    public int scalingPopulation() {
        if (closed) {
            return 0;
        }
        World world = dragon == null ? server.getWorld(NamespacedKey.fromString(nextConfig.worldKey()))
                : dragon.getWorld();
        return world == null || unavailableWorlds.contains(world.getUID()) ? 0
                : (int) world.getPlayers().stream().filter(Player::isOnline).count();
    }

    public int combatPopulation() {
        return arena == null ? 0 : (int) dragon.getWorld().getPlayers().stream().filter(arena::participant).count();
    }

    private void recordEntry(World world) {
        if (!closed && dragon == null && entryPopulation == 0 && world != null
                && world.getKey().toString().equals(nextConfig.worldKey())
                && world.getEnvironment() == World.Environment.THE_END
                && !unavailableWorlds.contains(world.getUID())) {
            populationWorld = world.getUID();
            entryPopulation = server.getOnlinePlayers().size();
        }
    }

    enum DebugAttack {
        DIVE, SMASH, PREY, BOMBARDMENT, LANDING
    }

    private boolean attacking() {
        return hunting || debugAttack != null || config.movementAttacks() && !manualMode && !openingGrace();
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

    private boolean pendingArea() {
        return areas.stream().anyMatch(area -> !pools.contains(area));
    }

    void debugStatus(CommandSender sender) {
        if (dragon == null) {
            messages.send(sender, "shattered-crown.debug-no-encounter");
            messages.send(sender, "shattered-crown.debug-status", "status",
                    "combat-population=0, scaling-population=" + scalingPopulation()
                            + ", entry-snapshot=" + entryPopulation + ", grace-seconds=0.0");
            return;
        }
        messages.send(sender, "shattered-crown.debug-status", "status",
                "mode=" + (manualMode ? "manual" : "automatic") + ", phase="
                        + (hunting ? "hunt" : "anchors") + ", final=" + finalEntered
                        + ", main=" + (busy() ? "executing" : "idle")
                        + ", attack=" + activeAttack() + ", hazards=" + hazards.size()
                        + ", queued=[" + (rotation == null ? "" : rotation.queued()) + "]"
                        + ", next-warning-seconds="
                        + (rotation == null ? (manualMode ? "paused" : openingGrace() ? "grace" : "idle")
                                : rotation.remainingTicks(encounterTicks) / 20.0)
                        + ", available=" + availableAttacks()
                        + ", lanes=" + lanes.size() + ", marks=" + marks.size() + ", areas=" + areas.size()
                        + ", landing=" + landingRequested + ", flight=" + (flight != null)
                        + ", landing-window=" + landingWindow
                        + ", native=" + dragon.getPhase()
                        + ", combat-population=" + combatPopulation() + ", scaling-population=" + scalingPopulation()
                        + ", entry-snapshot=" + entryPopulation + ", grace-seconds=" + remainingGraceSeconds()
                        + ", wave=" + waveNumber + ", shield=" + shielded() + ", objectives=" + (4 - anchors.size())
                        + "/4"
                        + ", defenders=" + defenders.size() + ", wave-population=" + wavePopulation
                        + ", roster-units=" + rosterUnits + ", unlocked=" + unlockedAttacks());
    }

    void debugWave(int selected, CommandSender sender) {
        if (closed) {
            messages.send(sender, "shattered-crown.debug-disabled");
            return;
        }
        if (dragon == null) {
            pulse();
        }
        if (dragon == null) {
            messages.send(sender, "shattered-crown.debug-no-encounter");
            return;
        }
        if (selected < 1 || selected > 3) {
            throw new IllegalArgumentException("Wave must be 1, 2 or 3");
        }
        debugPause();
        if (finalAcceptance != null) {
            finalAcceptance.cancel();
            finalAcceptance = null;
        }
        damageReservations.keySet().forEach(event -> event.setCancelled(true));
        damageReservations.clear();
        explosionReservations.clear();
        poolCooldowns.clear();
        java.util.Arrays.fill(explosionBuckets, 0);
        EnderDragon source = dragon;
        EncounterWork replaced = work;
        work.close(); // Includes generic extension-owned entities/tasks, not just current attacks/Anchors.
        if (closed || dragon != source || work != replaced) {
            return;
        }
        work = new EncounterWork(scheduler, server.getLogger());
        waveWork = null;
        combatOwners.clear();
        defenderInitializers.clear();
        clearedWaves = selected - 1;
        entryPopulation = server.getOnlinePlayers().size();
        dragon.setHealth(attemptMaxHealth * (selected == 1 ? 1 : selected == 2 ? 0.65 : 0.30));
        enterWave(selected, selected == 1 ? entryPopulation : scalingPopulation());
        if (dragon != null) {
            messages.send(sender, "shattered-crown.debug-wave", "wave", selected);
            debugStatus(sender);
        }
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

    void debugTrigger(DebugAttack attack, Player target, CommandSender sender) {
        if (closed) {
            messages.send(sender, "shattered-crown.debug-disabled");
            return;
        }
        if (dragon == null) {
            pulse();
        }
        if (dragon == null) {
            messages.send(sender, "shattered-crown.debug-no-encounter");
            return;
        }
        if (!isParticipant(target)) {
            messages.send(sender, "shattered-crown.debug-target-ineligible");
            return;
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
            messages.send(sender, "shattered-crown.debug-failed", "reason", error.getMessage());
            return;
        }
        if (!busy()) {
            String reason = arena.rejection();
            resetAttacks();
            messages.send(sender, "shattered-crown.debug-not-started", "reason", reason);
            return;
        }
        messages.send(sender, "shattered-crown.debug-started", "attack", attack, "player", target.getName());
    }

    private void resetAttacks() {
        resetAttacks(true);
    }

    private void resetAttacks(boolean discardRound) {
        if (rotationLoop != null) {
            rotationLoop.cancel();
            rotationLoop = null;
        }
        if (attackWork != null) {
            attackWork.close();
            attackWork = new EncounterWork(scheduler, server.getLogger());
        }
        endLanding();
        stopBombardments();
        lanes.clear();
        marks.clear();
        areas.clear();
        pools.clear();
        poolCooldowns.clear();
        if (discardRound) {
            rotation = null;
            landingNeedsRangedTurn = false;
        }
        debugAttack = null;
        debugTarget = null;
    }

    private void scheduleBombardments() {
        if (config.movementAttacks()) {
            startRotation();
            return;
        }
        if (!manualMode && !openingGrace()) {
            Duration cadence = seconds(config.bombardmentCadenceSeconds());
            bombardmentCadence = attackWork.repeating(cadence, cadence, this::warnBombardment);
        }
    }

    private static final class Anchor {
        private EnderCrystal crystal;
        private final UUID id;
        private final Location home;
        private final List<Mob> wave = new ArrayList<>();
        private boolean waveInstalled;
        private int recoveryPasses;

        private Anchor(EnderCrystal crystal, Location home) {
            this.crystal = crystal;
            this.id = crystal.getUniqueId();
            this.home = home;
        }
    }

    public EncounterController(Server server, DragonBattleAccess battles,
            ShatteredCrownConfig config, Scheduler scheduler, MessageService messages) {
        this(server, battles, config, scheduler, messages, java.util.random.RandomGenerator.getDefault());
    }

    /** Seedable randomness at the Encounter boundary for reproducible scheduling checks. */
    public EncounterController(Server server, DragonBattleAccess battles,
            ShatteredCrownConfig config, Scheduler scheduler, MessageService messages,
            java.util.random.RandomGenerator random) {
        this.random = java.util.Objects.requireNonNull(random);
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
        World occupied = server.getWorld(NamespacedKey.fromString(nextConfig.worldKey()));
        if (occupied != null && !occupied.getPlayers().isEmpty()) {
            recordEntry(occupied);
        }
        if (discovery == null) {
            discovery = scheduler.runRepeating(Duration.ZERO, Duration.ofSeconds(1), this::pulse);
        }
    }

    /** Validated Server Configuration takes effect only when a fresh attempt starts. */
    public void configure(ShatteredCrownConfig next) {
        if (dragon == null && !next.worldKey().equals(nextConfig.worldKey())) {
            populationWorld = null;
            entryPopulation = 0;
        }
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
            return;
        }
        config = nextConfig;
        World world = server.getWorld(NamespacedKey.fromString(config.worldKey()));
        if (world == null || world.getPlayers().isEmpty()) {
            populationWorld = null;
            entryPopulation = 0;
        }
        if (world == null || unavailableWorlds.contains(world.getUID())
                || world.getEnvironment() != World.Environment.THE_END || world.getPlayers().isEmpty()) {
            return;
        }
        recordEntry(world);
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
        clockWork = new EncounterWork(scheduler, server.getLogger());
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
        graceTicks = Math.round(config.openingGraceSeconds() * 20);
        enterWave(1, entryPopulation);
        if (dragon != candidate || closed) {
            return;
        }
        targetCursor = 0;
        if (graceTicks > 0) {
            startGraceClock();
        }
    }

    private void startGraceClock() {
        ownDragonPhase();
        dragon.setPhase(EnderDragon.Phase.CIRCLING);
        final Scheduler.Task[] clock = new Scheduler.Task[1];
        clock[0] = clockWork.repeating(Duration.ofMillis(50), Duration.ofMillis(50), () -> {
            if (--graceTicks == 0) {
                clock[0].cancel();
                if (!manualMode) {
                    if (config.movementAttacks()) {
                        startRotation();
                        return;
                    }
                    if (hunting) {
                        hunting = false;
                        startHunt();
                        warnLane();
                    } else {
                        warnBombardment();
                        scheduleBombardments();
                    }
                }
            }
        });
    }

    private static Duration seconds(double value) {
        return Duration.ofMillis(Math.round(value * 1000));
    }

    private void enterFinalGate() {
        if (waveNumber >= 3 || dragon == null || shielded()) {
            return;
        }
        enterWave(waveNumber + 1, scalingPopulation());
    }

    private void enterWave(int selected, int population) {
        waveNumber = selected; // Install the shield before any public spawn callback can reenter damage.
        finalEntered = selected == 3;
        wavePopulation = population;
        rosterUnits = (int) Math.ceil(population / 4.0);
        installingWave = true;
        EnderDragon source = dragon;
        hunting = false;
        resetAttacks(!config.movementAttacks());
        if (dragon != source || closed) {
            return;
        }
        try {
            if (waveWork != null) {
                waveWork.close();
            }
            defenders.clear();
            anchors.clear();
            defenderInitializers.clear();
            waveWork = requireWork().child(() -> {
            });
            for (int index = 0; index < openingGround.size(); index++) {
                Location ground = arena.anchorSite(openingGround.get(index));
                openingGround.set(index, ground);
                installAnchor(ground);
            }
            installingWave = false;
        } catch (RuntimeException exception) {
            cleanup(true);
            retryDelay = 30;
            server.getLogger().log(java.util.logging.Level.WARNING,
                    "[Shattered Crown] Anchor wave setup aborted; retry in 30 seconds", exception);
            return;
        }
        scheduleBombardments();
        updateAbsence(null, false);
    }

    private void startHunt() {
        if (hunting) {
            return;
        }
        hunting = true;
        nextLanding = encounterTicks + Math.round(config.landingCadenceSeconds() * 20);
        if (manualMode || openingGrace()) {
            return;
        }
        if (config.movementAttacks()) {
            startRotation();
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
                config.preyCountdownSeconds() + 0.5));
        attackWork.repeating(preyCadence, preyCadence, this::markPrey);
        Duration sweepCadence =
                seconds(attackCadence(config.sweepCadenceSeconds(), config.sweepWarningSeconds() + 0.5));
        attackWork.repeating(sweepCadence, sweepCadence, this::warnSweep);
        attackWork.repeating(cadence, cadence, this::warnLane);
    }

    /** Rain is a permanent unlock but cannot enter a round until #162 supplies its main action. */
    public Set<AttackFamily> availableAttacks() {
        var available = java.util.EnumSet.noneOf(AttackFamily.class);
        available.addAll(unlockedAttacks());
        available.remove(AttackFamily.RAIN);
        return Set.copyOf(available);
    }

    private void startRotation() {
        if (manualMode || openingGrace() || rotationLoop != null || dragon == null) {
            return;
        }
        if (rotation == null) {
            rotation = new HuntRotation(random);
            landingNeedsRangedTurn = true; // Even an overdue landing cannot replace the first grace-expiry warning.
        }
        rotationLoop = attackWork.repeating(Duration.ofMillis(50), Duration.ofMillis(50), this::rotationTick);
        rotationTick(); // The first warning starts at grace expiry, not one pulse later.
    }

    private void rotationTick() {
        if (manualMode || openingGrace() || busy() || dragon == null) {
            return;
        }
        if (hunting && encounterTicks >= nextLanding && !landingNeedsRangedTurn) {
            nextLanding = encounterTicks + Math.round(config.landingCadenceSeconds() * 20);
            requestLanding();
            if (busy()) {
                return;
            }
        }
        if (!rotation.ready(encounterTicks)) {
            return;
        }
        arena.resetRejection();
        landingNeedsRangedTurn = false;
        switch (rotation.next(availableAttacks())) {
            case BOMBARDMENT -> warnBombardment();
            case PREY -> markPrey();
            case DIVE -> warnLane();
            case SMASH -> smash();
            case RAIN -> throw new IllegalStateException("Rain content is not installed");
        }
    }

    private double attackCadence(double ordinary, double minimum) {
        return finalEntered ? Math.max(minimum, ordinary * config.finalCadenceFactor()) : ordinary;
    }

    private double poolLifetime() {
        return config.poolLifetimeSeconds() * (finalEntered ? config.finalPoolLifetimeFactor() : 1);
    }

    private void startExplosionClock() {
        // One fixed-size tick ring, not one expiry task per hit: even fractional bed hits stay bounded.
        clockWork.repeating(Duration.ofMillis(50), Duration.ofMillis(50), () -> {
            encounterTicks++;
            poolCooldowns.values().removeIf(expiry -> expiry <= encounterTicks);
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
        if (!event.isCancelled() && target.equals(dragon) && waveNumber < 3 && !shielded() && finalAcceptance == null
                && Double.isFinite(event.getFinalDamage()) && event.getFinalDamage() > 0
                && dragon.getHealth() - event.getFinalDamage() <= attemptMaxHealth * nextGateFraction()) {
            // MONITOR only observes acceptance. Native damage applies before this owned callback;
            // the HIGHEST health floor protects subsequent hits until the Anchors are installed.
            finalAcceptance = runLater(Duration.ZERO, () -> {
                finalAcceptance = null;
                if (dragon != null && dragon.isValid() && waveNumber < 3 && !shielded()
                        && dragon.getHealth() <= attemptMaxHealth * nextGateFraction()) {
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
                || !marks.isEmpty() || pendingArea()
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
        dragon.setPhase(EnderDragon.Phase.FLY_TO_PORTAL);
        if (dragon == null || !attacking()) {
            return;
        }
        landingDeadline = owner.later(seconds(config.landingApproachSeconds()), this::endLanding);
        landingMonitor = owner.repeating(Duration.ofMillis(50), Duration.ofMillis(50), () -> {
            if (!arena.landingSafe() || dragon.getWorld().getPlayers().stream().noneMatch(arena::participant)) {
                endLanding();
            } else if (!landingWindow && sitting(dragon.getPhase()) && arena.atPodium(dragon.getLocation())) {
                beginLandingWindow(owner);
                dragon.setPhase(EnderDragon.Phase.SEARCH_FOR_BREATH_ATTACK_TARGET);
            }
        });
    }

    private void beginLandingWindow(EncounterWork owner) {
        if (landingWindow || owner == null || owner.isClosed()) {
            return;
        }
        landingWindow = true;
        if (landingDeadline != null) {
            landingDeadline.cancel();
        }
        landingEnd = owner.later(seconds(config.landingWindowSeconds()), this::endLanding);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDragonPhase(org.bukkit.event.entity.EnderDragonChangePhaseEvent event) {
        if (event.getEntity().equals(dragon) && openingGrace()
                && event.getNewPhase() != EnderDragon.Phase.CIRCLING) {
            event.setCancelled(true);
            return;
        }
        if (event.getEntity().equals(dragon) && flight != null && !flight.isClosed()
                && event.getNewPhase() != EnderDragon.Phase.CHARGE_PLAYER
                && event.getNewPhase() != EnderDragon.Phase.DYING) {
            event.setCancelled(true);
            return;
        }
        if (event.getEntity().equals(dragon) && event.getNewPhase() == EnderDragon.Phase.DYING
                && (clearedWaves < 3 || shielded())) {
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
            if (arena.atPodium(dragon.getLocation())) {
                beginLandingWindow(mainWork);
            }
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
        if (debugAttack == null && !unlockedAttacks().contains(AttackFamily.DIVE)) {
            return;
        }
        startAction(DebugAttack.DIVE, this::warnLane);
    }

    private void warnLane(EncounterWork owner) {
        if (flight != null) {
            return;
        }
        if (!attacking() || landingRequested || landingDue || dragon == null
                || lanes.size() >= 2
                || !marks.isEmpty() || pendingArea()
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
                if (mainAttack == DebugAttack.LANDING) {
                    nextLanding = encounterTicks + Math.round(config.landingCadenceSeconds() * 20);
                    landingNeedsRangedTurn = true;
                }
                mainWork = null;
                mainAttack = null;
                if (rotation != null) {
                    rotation.completed(encounterTicks, (int) Math.round(config.attackGapMinSeconds() * 20),
                            (int) Math.round(config.attackGapMaxSeconds() * 20));
                }
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
        // Main actions serialize; sibling pools do not occupy the main-action slot.
        if (!attacking() || landingRequested || landingDue || !marks.isEmpty() || pendingArea()
                || !lanes.isEmpty()) {
            return;
        }
        var targets = attackTargets();
        if (targets.isEmpty()) {
            return;
        }
        EnderDragon source = dragon;
        int count = (int) Math.ceil(scalingPopulation() * config.preyPercentage() / 100);
        for (int index = 0; index < Math.min(count, targets.size()); index++) {
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
            target.playSound(target.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 0.7f, 1.4f);
            final Scheduler.Task[] refresh = new Scheduler.Task[1];
            refresh[0] = owner.repeating(Duration.ofMillis(250), Duration.ofMillis(250), () -> {
                if (!isParticipant(target) || !mark.isValid()) {
                    cancelMark(target);
                    refresh[0].cancel();
                    return;
                }
                age[0] += 0.25;
                double remaining = Math.max(0, config.preyCountdownSeconds() - age[0]);
                mark.teleport(target.getLocation().add(0, 2.5, 0));
                mark.text(Component.text(
                        target.getName() + " · " + String.format(java.util.Locale.ROOT, "%.1fs", remaining),
                        NamedTextColor.GOLD));
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
                if (!valid || landingRequested) {
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
                if (arena.poolsSafe(combined, lanes, participants, config.preyCountdownSeconds())) {
                    activatePool(pool, source, poolLifetime());
                } else {
                    server.getLogger().fine("[Shattered Crown] Prey pool skipped: terrain, overlap or escape route");
                }
            });
        }
        owner.later(seconds(config.preyCountdownSeconds()), owner::close);
    }

    /** Shared Prey/Rain hazard, with effect-specific geometry and lifetime but unchanged raw pool damage.
     * Returns false when there is no live owner, footing or a startup failure. Call on the server thread.
     */
    public boolean depositBreathPool(Location ground, double radius, double lifetimeSeconds) {
        if (!Double.isFinite(radius) || radius <= 0 || radius > 32
                || !Double.isFinite(lifetimeSeconds) || lifetimeSeconds <= 0 || lifetimeSeconds > 120) {
            throw new IllegalArgumentException("Breath pools require radius (0,32] and lifetime (0,120] seconds");
        }
        if (closed || dragon == null || attackWork == null || attackWork.isClosed() || !arena.contains(ground)
                || arena.protectedArrival(ground)) {
            return false;
        }
        var pool = new HuntArea(ground, radius);
        if (config.movementAttacks() && !arena.surface(pool)) {
            return false;
        }
        return activatePool(pool, dragon, lifetimeSeconds);
    }

    private boolean activatePool(HuntArea pool, EnderDragon source, double lifetimeSeconds) {
        EncounterWork poolWork = attackWork.child(() -> {
            areas.remove(pool);
            pools.remove(pool);
            if (pool.indicator != null) {
                pool.indicator.remove();
            }
        });
        hazards.add(poolWork);
        poolWork.onCleanup(() -> hazards.remove(poolWork));
        poolWork.run(() -> {
            pool.indicator = source.getWorld().spawn(pool.center.clone().add(0, 1.5, 0), TextDisplay.class);
            if (poolWork.isClosed() || dragon != source || !pool.indicator.isValid()) {
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
            pools.add(pool);
            pool.indicator.text(Component.text(lifetimeSeconds + "s", NamedTextColor.LIGHT_PURPLE));
            drawAreaRing(pool, Particle.DRAGON_BREATH);
            drawPool(pool);
            source.getWorld().playSound(pool.center, Sound.ENTITY_DRAGON_FIREBALL_EXPLODE, 1.0f, 0.7f);
            poolWork.later(seconds(lifetimeSeconds), poolWork::close);
            poolWork.repeating(Duration.ofMillis(250), Duration.ofMillis(250), () -> {
                if (!pool.indicator.isValid()) {
                    poolWork.close();
                    return;
                }
                pool.age += 0.25;
                pool.indicator.text(Component.text(String.format(java.util.Locale.ROOT, "%.1fs",
                        Math.max(0, lifetimeSeconds - pool.age)), NamedTextColor.LIGHT_PURPLE));
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
                    if (isParticipant(recipient) && pool.contains(recipient.getLocation(), 0)
                            && !poolCooldowns.containsKey(recipient.getUniqueId())) {
                        UUID id = recipient.getUniqueId();
                        // One rolling cooldown across sibling hazards, not one independent clock per pool.
                        poolCooldowns.put(id, encounterTicks
                                + (long) Math.ceil(Math.max(1, config.poolDamageCadenceSeconds()) * 20));
                        battles.bombardmentDamage(recipient, source, config.poolDamage());
                    }
                }
            });
        });
        return !poolWork.isClosed();
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
        owner.onCleanup(() -> lanes.remove(lane));
        lanes.add(lane);
        ownDragonPhase();
        flight = new DragonFlight(source, battles, owner,
                () -> dragon == source && attacking() && isParticipant(target)
                        && (lane.indicator == null || lane.indicator.isValid())
                        && (lane.surface == null || lane.surface.unchanged()),
                () -> {
                    flight = null;
                    lanes.remove(lane);
                    owner.close();
                });
        DragonFlight movement = flight;
        Location origin = source.getLocation();
        int warningTicks = (int) Math.ceil(config.laneWarningSeconds() * 20);
        if (origin.distance(start) > warningTicks * 8) {
            // ponytail: stage only out-of-range approaches; the visible warning still lasts exactly its configured time.
            double approachSeconds = Math.ceil(origin.distance(start) / 7.5) / 20;
            movement.start(t -> DragonFlight.between(origin, start, t), approachSeconds, position -> {
            }, () -> beginDiveWarning(lane, owner, start, source, movement));
        } else {
            beginDiveWarning(lane, owner, start, source, movement);
        }
    }

    private void beginDiveWarning(BreathLane lane, EncounterWork owner, Location start, EnderDragon source,
            DragonFlight movement) {
        lane.indicator = source.getWorld().spawn(lane.center.clone().add(0, 1.5, 0), TextDisplay.class);
        if (owner.isClosed() || !lane.indicator.isValid() || dragon != source || !attacking()) {
            lane.indicator.remove();
            movement.close();
            return;
        }
        owner.own(lane.indicator, "lane");
        battles.faceWarningToViewer(lane.indicator);
        if (owner.isClosed()) {
            return;
        }
        drawLane(lane);
        attackTargets().forEach(player -> player.playSound(lane.center, Sound.ENTITY_ENDER_DRAGON_GROWL, 0.7f, 1.4f));
        Location origin = source.getLocation();
        movement.start(t -> DragonFlight.between(origin, start, t), config.laneWarningSeconds(), position -> {
            lane.age += 0.05;
            if (Math.round(lane.age * 20) % 5 != 0) {
                return;
            }
            drawLane(lane);
        }, () -> {
            if (!arena.laneSafe(lane, List.of(), source.getWorld().getPlayers().stream()
                    .filter(arena::participant).toList())) {
                movement.close();
                return;
            }
            lane.active = true;
            lane.age = config.laneLifetimeSeconds() - config.divePassSeconds();
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
            }, config.divePassSeconds(), position -> {
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
                        flightHit(player, source, config.laneDamage(), position, true);
                    }
                }
            }, () -> climb(movement, source, config.diveClimbSeconds()));
        });
    }

    private void smash() {
        if (debugAttack == null && !unlockedAttacks().contains(AttackFamily.SMASH)) {
            return;
        }
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
        climb(movement, source, 2);
    }

    private void climb(DragonFlight movement, EnderDragon source, double duration) {
        Location origin = source.getLocation();
        Location end = origin.clone().add(0, 16, 0);
        if (!arena.contains(end)) {
            movement.close();
            return;
        }
        movement.start(t -> DragonFlight.between(origin, end, t), duration, position -> {
        }, movement::close);
    }

    private void flightHit(Player player, EnderDragon source, double amount, Location center) {
        flightHit(player, source, amount, center, false);
    }

    private void flightHit(Player player, EnderDragon source, double amount, Location center, boolean reference) {
        double health = player.getHealth();
        flightDamage = true;
        try {
            if (reference) {
                battles.referenceDamage(player, source, amount);
            } else {
                battles.bombardmentDamage(player, source, amount);
            }
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
        if (event instanceof io.papermc.paper.event.entity.EntityPushedByEntityAttackEvent pushed) {
            Entity source = pushed.getPushedBy() instanceof ComplexEntityPart part ? part.getParent()
                    : pushed.getPushedBy();
            if ((flight != null || openingGrace()) && source.equals(dragon)
                    || encounterEnemy(source) && event.getEntity() instanceof Player player
                            && (isProtectedArrival(player.getLocation()) || dragon != null && !isParticipant(player))) {
                event.setCancelled(true);
            }
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
        if (!hunting || landingRequested || landingDue || !marks.isEmpty() || pendingArea() || !lanes.isEmpty()) {
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
    }

    private void warnBombardment() {
        startAction(DebugAttack.BOMBARDMENT, this::warnBombardment);
    }

    private void warnBombardment(EncounterWork owner) {
        if (dragon == null || landingRequested || bombardmentIndicator != null) {
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
        if (owner.isClosed() || dragon != warnedDragon
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
            if (!visible || dragon == null || !isParticipant(target)) {
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
                if (dragon != source) {
                    break;
                }
                if (isParticipant(player) && arena.inBombardment(player.getLocation(), impact,
                        config.bombardmentRadius())) {
                    battles.referenceDamage(player, source, config.bombardmentDamage());
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
        if (event.getWorld().getUID().equals(populationWorld)) {
            populationWorld = null;
            entryPopulation = 0;
        }
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

    /** Combat attribution survives parent death, but never crosses an attempt boundary. */
    public boolean ownsCombatant(Entity entity) {
        return entity != null && combatOwners.containsKey(entity.getUniqueId());
    }

    /** Original objective identity; roaming, riding and parent death do not reassign it. */
    public UUID anchorId(Entity entity) {
        Anchor anchor = entity == null ? null : combatOwners.get(entity.getUniqueId());
        return anchor == null ? null : anchor.id;
    }

    /** Call in Paper's pre-spawn consumer, not a post-spawn listener. Mobs also gate their parent's Anchor. */
    public boolean inheritOwnership(Entity entity, Entity parent) {
        Anchor anchor = parent == null ? null : combatOwners.get(parent.getUniqueId());
        if (anchor == null || work == null || work.isClosed() || !anchors.contains(anchor)) {
            return false;
        }
        registerCombatant(entity, anchor);
        return true;
    }

    private void registerCombatant(Entity entity, Anchor anchor) {
        Anchor previous = combatOwners.get(entity.getUniqueId());
        if (previous != null && previous != anchor) {
            throw new IllegalArgumentException("Combatants cannot change their original Anchor");
        }
        waveWork.own(entity, entity instanceof Mob ? "defender" : "defender-effect");
        if (combatOwners.putIfAbsent(entity.getUniqueId(), anchor) == null && entity instanceof Mob mob) {
            defenders.add(mob);
            anchor.wave.add(mob);
            mob.setPersistent(true);
            setCombatRange(mob);
            anchor.crystal.setGlowing(false);
        }
    }

    private void setCombatRange(Mob mob) {
        var range = mob.getAttribute(Attribute.FOLLOW_RANGE);
        if (range != null) {
            // Native zombie randomization must not shrink pursuit below the whole arena diameter.
            range.getModifiers().stream().filter(modifier -> modifier.getAmount() < 0).forEach(range::removeModifier);
            range.setBaseValue(config.arenaRadius() * 2);
        }
    }

    /** Recoverable descendant spawn. Initialization and ownership run before public spawn callbacks. */
    public Mob spawnCombatant(Location location, EntityType type, Entity parent,
            java.util.function.Consumer<Mob> initialize) {
        Anchor anchor = parent == null ? null : combatOwners.get(parent.getUniqueId());
        if (anchor == null || !anchors.contains(anchor)) {
            throw new IllegalArgumentException("Parent is not an active Encounter combatant");
        }
        return spawnCombatant(location, type, anchor, initialize);
    }

    private Mob spawnCombatant(Location location, EntityType type, Anchor anchor,
            java.util.function.Consumer<Mob> initialize) {
        EncounterWork owner = waveWork;
        Entity entity = location.getWorld().spawnEntity(location, type,
                org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.CUSTOM, spawned -> {
                    if (!(spawned instanceof Mob mob)) {
                        throw new IllegalArgumentException("Defenders must be mobs");
                    }
                    registerCombatant(mob, anchor);
                    defenderInitializers.put(mob.getUniqueId(), initialize);
                    initialize.accept(mob);
                    setCombatRange(mob);
                });
        if (!entity.isValid() || entity.isDead() || waveWork != owner || owner.isClosed() || dragon == null) {
            entity.remove();
            throw new IllegalStateException("Defender spawn was refused");
        }
        return (Mob) entity;
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

    private Mob spawnDefender(Anchor anchor) {
        return spawnCombatant(arena.defenderSite(anchor.home), EntityType.valueOf(config.defenderType()), anchor,
                defender -> {
                    battles.preventDespawn(defender);
                    defender.getAttribute(Attribute.MAX_HEALTH).setBaseValue(config.defenderHealth());
                    defender.setHealth(config.defenderHealth());
                    defender.getAttribute(Attribute.ATTACK_DAMAGE).setBaseValue(config.defenderDamage());
                    defender.getAttribute(Attribute.SPAWN_REINFORCEMENTS).setBaseValue(0);
                });
    }

    private EnderCrystal spawnAnchor(Location ground) {
        EncounterWork owner = waveWork;
        EnderCrystal crystal = ground.getWorld().spawn(ground, EnderCrystal.class);
        if (!crystal.isValid() || waveWork != owner || owner.isClosed() || dragon == null) {
            crystal.remove();
            throw new IllegalStateException("Anchor spawn was refused");
        }
        owner.own(crystal, "anchor");
        return crystal;
    }

    private void installAnchor(Location ground) {
        var objective = new Anchor(spawnAnchor(ground), ground);
        anchors.add(objective);
        for (int unit = 0; unit < rosterUnits; unit++) {
            spawnRosterUnit(objective, waveNumber, unit);
        }
        objective.waveInstalled = true;
        objective.crystal.setGlowing(objective.wave.isEmpty());
    }

    /** #153–155 replace this unit's content, not wave scaling or objective completion. */
    private void spawnRosterUnit(Anchor objective, int wave, int unit) {
        // ponytail: existing configured roster is the playable placeholder until #153–155 supply each wave.
        for (int index = 0; index < config.defenderCount(); index++) {
            spawnDefender(objective);
        }
    }

    private void maintainDefenders() {
        for (Anchor anchor : anchors) {
            Location home = anchor.home;
            if ((!anchor.crystal.isValid() || anchor.wave.stream().anyMatch(mob -> !mob.isValid()))
                    && ++anchor.recoveryPasses > 3) {
                throw new IllegalStateException("Anchor recovery exhausted after three replacement passes");
            }
            if (!anchor.crystal.isValid()) {
                waveWork.forget(anchor.crystal);
                anchor.crystal.remove();
                anchor.crystal = spawnAnchor(home);
                anchor.crystal.setGlowing(anchor.wave.isEmpty());
            }
            for (int i = 0; i < anchor.wave.size(); i++) {
                Mob defender = anchor.wave.get(i);
                if (!defender.isValid()) {
                    var initialize = defenderInitializers.remove(defender.getUniqueId());
                    defenders.remove(defender);
                    combatOwners.remove(defender.getUniqueId());
                    waveWork.forget(defender);
                    anchor.wave.remove(i--);
                    defender.remove();
                    spawnCombatant(arena.defenderSite(home), defender.getType(), anchor,
                            initialize == null ? battles::preventDespawn : initialize);
                    continue;
                }
                Location location = defender.getLocation();
                if (!location.getWorld().equals(home.getWorld()) || !arena.contains(location)
                        || arena.protectedArrival(location)) {
                    defender.teleport(arena.defenderSite(home));
                }
                Location targetOrigin = defender.getLocation();
                defender.setTarget(home.getWorld().getPlayers().stream().filter(arena::participant)
                        .min(java.util.Comparator.comparingDouble(player -> player.getLocation()
                                .distanceSquared(targetOrigin)))
                        .orElse(null));
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTarget(org.bukkit.event.entity.EntityTargetLivingEntityEvent event) {
        if (ownsCombatant(event.getEntity()) && event.getTarget() != null
                && (!(event.getTarget() instanceof Player player) || !isParticipant(player))) {
            event.setCancelled(true);
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
        source = source instanceof ComplexEntityPart part ? part.getParent() : source;
        direct = direct instanceof ComplexEntityPart part ? part.getParent() : direct;
        if ((ownsCombatant(source) || ownsCombatant(direct)) && combatAlly(target)) {
            event.setCancelled(true);
            return;
        }
        if (target instanceof Player player
                && (source != null && encounterEnemy(source) || direct != null && encounterEnemy(direct))
                && (isProtectedArrival(player.getLocation()) || dragon != null && !isParticipant(player)
                        || openingGrace() && (dragon.equals(source) || dragon.equals(direct)))) {
            event.setCancelled(true);
            return;
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
        boolean enemy = target.equals(dragon) || objective != null || ownsCombatant(target);
        if (enemy && (protectedSource(source) || protectedSource(direct)
                || protectedSource(event.getDamageSource().getDirectEntity()))) {
            event.setCancelled(true);
            return;
        }
        if (target.equals(dragon) && shielded()) {
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
            if (!event.isCancelled() && waveNumber < 3) {
                double pending = damageReservations.values().stream().mapToDouble(Double::doubleValue).sum();
                capDamage(event, Math.max(0,
                        dragon.getHealth() - attemptMaxHealth * nextGateFraction() - pending));
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
                    && isParticipant(player) && !explosive(event) && !installingWave) {
                objective.crystal.remove();
                anchors.remove(objective);
                if (anchors.isEmpty()) {
                    clearedWaves = waveNumber;
                    if (!config.movementAttacks()) {
                        resetAttacks();
                    }
                    startHunt();
                }
            }
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
                || ownsCombatant(target) || anchors.stream().anyMatch(anchor -> target.equals(anchor.crystal));
    }

    private boolean combatAlly(Entity entity) {
        Entity target = entity instanceof ComplexEntityPart part ? part.getParent() : entity;
        return target instanceof org.bukkit.entity.Enderman || ownsCombatant(target) || target.equals(dragon);
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
        if (ownsCombatant(event.getEntity())
                || event.getEntity().getShooter() instanceof Entity source && ownsCombatant(source)) {
            event.getAffectedEntities().stream().filter(this::combatAlly)
                    .forEach(entity -> event.setIntensity(entity, 0));
        }
        if (protectedSource(event.getEntity())) {
            event.getAffectedEntities().stream().filter(this::encounterEnemy)
                    .forEach(entity -> event.setIntensity(entity, 0));
        }
        Entity source = event.getEntity().getShooter() instanceof Entity owner ? owner : null;
        if (ownsCombatant(event.getEntity()) || source != null && encounterEnemy(source)) {
            event.getAffectedEntities().stream().filter(entity -> entity instanceof Player player
                    && (isProtectedArrival(player.getLocation()) || dragon != null && !isParticipant(player)
                            || openingGrace() && dragon.equals(source)))
                    .forEach(entity -> event.setIntensity(entity, 0));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCloud(org.bukkit.event.entity.AreaEffectCloudApplyEvent event) {
        if (ownsCombatant(event.getEntity())
                || event.getEntity().getSource() instanceof Entity source && ownsCombatant(source)) {
            event.getAffectedEntities().removeIf(this::combatAlly);
        }
        if (protectedSource(event.getEntity())) {
            event.getAffectedEntities().removeIf(this::encounterEnemy);
        }
        Entity source = event.getEntity().getSource() instanceof Entity owner ? owner : null;
        if (ownsCombatant(event.getEntity()) || source != null && encounterEnemy(source)) {
            event.getAffectedEntities().removeIf(entity -> entity instanceof Player player
                    && (isProtectedArrival(player.getLocation()) || dragon != null && !isParticipant(player)
                            || openingGrace() && dragon.equals(source)));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onLaunch(ProjectileLaunchEvent event) {
        Projectile projectile = event.getEntity();
        if (openingGrace() && dragon.equals(projectile.getShooter())) {
            event.setCancelled(true);
            return;
        }
        if (isProtectedArrival(projectile.getLocation())
                || projectile.getShooter() instanceof Entity shooter && protectedSource(shooter)) {
            projectile.getPersistentDataContainer().set(PROTECTED_SHOT, PersistentDataType.BYTE, (byte) 1);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onCombatLaunch(ProjectileLaunchEvent event) {
        if (event.getEntity().getShooter() instanceof Entity source) {
            inheritOwnership(event.getEntity(), source);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onCombatLingering(org.bukkit.event.entity.LingeringPotionSplashEvent event) {
        inheritOwnership(event.getAreaEffectCloud(), event.getEntity());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onCombatSpawn(EntitySpawnEvent event) {
        Entity entity = event.getEntity();
        if (entity instanceof Projectile projectile && projectile.getShooter() instanceof Entity source) {
            inheritOwnership(entity, source);
        } else if (entity instanceof TNTPrimed tnt && tnt.getSource() != null) {
            inheritOwnership(entity, tnt.getSource());
        } else if (entity instanceof org.bukkit.entity.AreaEffectCloud cloud
                && cloud.getSource() instanceof Entity source) {
            inheritOwnership(entity, source);
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
            clearDefenderTargets(player);
            updateAbsence(player, false);
        }
        if (defenders.remove(event.getEntity())) {
            defenderInitializers.remove(event.getEntity().getUniqueId());
            for (Anchor anchor : anchors) {
                if (anchor.wave.remove(event.getEntity()) && anchor.waveInstalled && anchor.wave.isEmpty()) {
                    anchor.crystal.setGlowing(true);
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
        if (event.getEntity().equals(dragon) && (clearedWaves < 3 || shielded())) {
            event.setCancelled(true);
            double floor = shielded() ? (waveNumber == 1 ? 1 : waveNumber == 2 ? 0.65 : 0.30) : nextGateFraction();
            event.setReviveHealth(attemptMaxHealth * floor);
            if (anchors.isEmpty()) {
                enterFinalGate();
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (!event.getFrom().getWorld().equals(event.getTo().getWorld())) {
            recordEntry(event.getTo().getWorld());
        }
        if (dragon == null && event.getTo().getWorld().getKey().toString().equals(nextConfig.worldKey())) {
            discoverSoon();
        }
        if (arena != null) {
            if (!arena.participantAt(event.getPlayer(), event.getTo(), event.getPlayer().getGameMode())) {
                cancelMark(event.getPlayer());
                clearDefenderTargets(event.getPlayer());
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
        clearDefenderTargets(event.getPlayer());
        updateAbsence(event.getPlayer(), false);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(org.bukkit.event.player.PlayerJoinEvent event) {
        recordEntry(event.getPlayer().getWorld());
        discoverSoon();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onGameMode(PlayerGameModeChangeEvent event) {
        discoverSoon();
        if (arena != null) {
            if (!arena.participantAt(event.getPlayer(), event.getPlayer().getLocation(), event.getNewGameMode())) {
                cancelMark(event.getPlayer());
                clearDefenderTargets(event.getPlayer());
            }
            updateAbsence(event.getPlayer(),
                    arena.participantAt(event.getPlayer(), event.getPlayer().getLocation(), event.getNewGameMode()));
        }
    }

    private void cancelMark(Player player) {
        var mark = marks.remove(player.getUniqueId());
        if (mark != null) {
            mark.remove();
            if (marks.isEmpty() && mainAttack == DebugAttack.PREY && mainWork != null) {
                mainWork.close();
            }
        }
    }

    private void clearDefenderTargets(Player player) {
        for (Mob mob : List.copyOf(defenders)) {
            if (!mob.isValid()) {
                continue;
            }
            if (player.equals(mob.getTarget())) {
                mob.setTarget(null);
            }
            if (mob instanceof org.bukkit.entity.Wither wither) {
                for (var head : org.bukkit.entity.Wither.Head.values()) {
                    if (player.equals(wither.getTarget(head))) {
                        wither.setTarget(head, null);
                    }
                }
            }
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
            absence = clockWork.later(Duration.ofSeconds(60), () -> {
                if (dragon != null && dragon.getWorld().getPlayers().stream().noneMatch(arena::participant)) {
                    cleanup(true);
                }
            });
        }
    }

    private void cleanup(boolean reset) {
        graceTicks = 0;
        if (clockWork != null) {
            clockWork.close();
            clockWork = null;
        }
        if (entryDiscovery != null) {
            entryDiscovery.cancel();
            entryDiscovery = null;
        }
        if (attackWork != null) {
            attackWork.close();
            attackWork = null;
        }
        hunting = false;
        endLanding();
        lanes.clear();
        marks.clear();
        areas.clear();
        pools.clear();
        poolCooldowns.clear();
        encounterTicks = 0;
        debugAttack = null;
        debugTarget = null;
        manualMode = false;
        rotation = null;
        rotationLoop = null;
        nextLanding = 0;
        landingNeedsRangedTurn = false;
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
        combatOwners.clear();
        defenderInitializers.clear();
        anchors.clear();
        openingGround.clear();
        finalEntered = false;
        waveNumber = 0;
        clearedWaves = 0;
        wavePopulation = 0;
        rosterUnits = 0;
        installingWave = false;
        waveWork = null;
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
        populationWorld = null;
        entryPopulation = 0;
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
