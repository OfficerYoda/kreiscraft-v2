package de.kreiscraft.modules.wither;

import de.kreiscraft.core.event.PriorityListener;
import de.kreiscraft.core.message.MessageService;
import de.kreiscraft.core.scheduler.Scheduler;
import de.kreiscraft.modules.shatteredcrown.EncounterController;
import java.time.Duration;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Wither;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.ExplosionPrimeEvent;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

public final class WitherListener implements PriorityListener {

    private static final String TEAM_NAME = "bedrock_wither";
    private static final String HALF_TAG = "Half";
    private static final org.bukkit.NamespacedKey CROWN_ARTIFACT =
            new org.bukkit.NamespacedKey("kreiscraft", "crown_artifact");
    private static final int DEATH_COUNTDOWN_TICKS = 10;

    private final Supplier<WitherConfig> config;
    private final MessageService messages;
    private final Scheduler scheduler;
    private final Supplier<EncounterController> encounters;

    /** Tracks in-flight death-countdown tasks so shutdown() can cancel them. */
    private final Set<Scheduler.Task> activeTasks = new LinkedHashSet<>();
    private final Set<java.util.UUID> ownedDeaths = new LinkedHashSet<>();
    private final Set<Runnable> ownedCleanup = new LinkedHashSet<>();

    public WitherListener(
            Supplier<WitherConfig> config, MessageService messages, Scheduler scheduler) {
        this(config, messages, scheduler, () -> null);
    }

    public WitherListener(Supplier<WitherConfig> config, MessageService messages, Scheduler scheduler,
            Supplier<EncounterController> encounters) {
        this.config = config;
        this.messages = messages;
        this.scheduler = scheduler;
        this.encounters = encounters;
    }

    private EncounterController owner(Entity entity) {
        EncounterController encounter = encounters.get();
        return encounter != null && encounter.ownsCombatant(entity) ? encounter : null;
    }

    private boolean crownArtifact(Entity entity) {
        return entity.getPersistentDataContainer().has(CROWN_ARTIFACT);
    }

    /** Cancels all in-flight death-countdown tasks. Call from {@code WitherModule.disable()}. */
    public void shutdown() {
        Set.copyOf(ownedCleanup).forEach(Runnable::run);
        ownedCleanup.clear();
        ownedDeaths.clear();
        activeTasks.forEach(Scheduler.Task::cancel);
        activeTasks.clear();
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        if (!(event.getEntity() instanceof Wither wither)) {
            return;
        }
        if (crownArtifact(wither) && owner(wither) == null) {
            return; // Removed/persisted old roots must never become ordinary Wither gameplay.
        }
        WitherConfig cfg = config.get();
        AttributeInstance maxHealthAttr = wither.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealthAttr != null) {
            maxHealthAttr.setBaseValue(cfg.maxHealth());
            wither.setHealth(cfg.maxHealth());
        }
        AttributeInstance attackAttr = wither.getAttribute(Attribute.ATTACK_DAMAGE);
        if (attackAttr != null) {
            attackAttr.setBaseValue(cfg.attackDamage());
        }
        AttributeInstance armorAttr = wither.getAttribute(Attribute.ARMOR);
        if (armorAttr != null) {
            armorAttr.setBaseValue(cfg.armor());
        }
        addToWitherTeam(wither.getServer(), wither);
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onExplosionPrime(ExplosionPrimeEvent event) {
        if (!(event.getEntity() instanceof Wither wither)) {
            return;
        }
        if (crownArtifact(wither) && (owner(wither) == null || wither.isDead() || !wither.isValid())) {
            event.setCancelled(true);
            return;
        }
        WitherConfig cfg = config.get();
        // Paper emits ExplosionPrimeEvent once for the vanilla post-charge spawn blast. Cancel it
        // so the configured power/fire/block-damage settings can replace it exactly once.
        // World#createExplosion does not emit another ExplosionPrimeEvent, so this cannot recurse
        // or produce a second immediate blast.
        event.setCancelled(true);
        Location loc = wither.getLocation();
        if (owner(wither) != null) {
            wither.getWorld().createExplosion(loc, (float) cfg.spawnExplosionPower(),
                    cfg.spawnExplosionFire(), cfg.spawnExplosionBlockDamage(), wither);
            return;
        }
        wither.getWorld()
                .createExplosion(
                        loc.getX(),
                        loc.getY(),
                        loc.getZ(),
                        (float) cfg.spawnExplosionPower(),
                        cfg.spawnExplosionFire(),
                        cfg.spawnExplosionBlockDamage());
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onEntityDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Wither wither)) {
            return;
        }
        if (crownArtifact(wither)) {
            return;
        }
        WitherConfig cfg = config.get();
        if (wither.getHealth() <= cfg.halfHealthThreshold()
                && !wither.getScoreboardTags().contains(HALF_TAG)) {
            wither.addScoreboardTag(HALF_TAG);
            Location halfLoc = wither.getLocation();
            wither.getWorld()
                    .createExplosion(
                            halfLoc.getX(),
                            halfLoc.getY(),
                            halfLoc.getZ(),
                            (float) cfg.halfHealthExplosionPower(),
                            cfg.halfHealthExplosionFire(),
                            cfg.halfHealthExplosionBlockDamage());
            Team team = getOrCreateWitherTeam(wither.getServer());
            for (int i = 0; i < cfg.minionCount(); i++) {
                Entity skeleton =
                        wither.getWorld().spawnEntity(wither.getLocation(), EntityType.WITHER_SKELETON);
                team.addEntity(skeleton);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onOwnedDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Wither wither)) {
            return;
        }
        EncounterController encounter = owner(wither);
        WitherConfig cfg = config.get();
        if (encounter == null || wither.isDead() || !wither.isValid()
                || wither.getHealth() - event.getFinalDamage() > cfg.halfHealthThreshold()
                || wither.getScoreboardTags().contains(HALF_TAG)) {
            return;
        }
        wither.addScoreboardTag(HALF_TAG);
        // Membership precedes the blast and any reentrant parent death; all children gate the same Anchor.
        for (int index = 0; index < cfg.minionCount(); index++) {
            encounter.spawnCombatant(wither.getLocation(), EntityType.WITHER_SKELETON, wither, minion -> {
                addToWitherTeam(wither.getServer(), minion);
            });
        }
        wither.getWorld().createExplosion(wither.getLocation(), (float) cfg.halfHealthExplosionPower(),
                cfg.halfHealthExplosionFire(), cfg.halfHealthExplosionBlockDamage(), wither);
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onEntityDeath(EntityDeathEvent event) {
        if (!(event.getEntity() instanceof Wither wither)) {
            return;
        }
        if (crownArtifact(wither)) {
            event.getDrops().removeIf(item -> item.getType() == org.bukkit.Material.NETHER_STAR);
            return;
        }
        WitherConfig cfg = config.get();
        Location loc = wither.getLocation();
        event.getDrops().forEach(itemStack -> {
            Entity item = loc.getWorld().dropItem(loc, itemStack);
            item.setInvulnerable(true);
            item.setGlowing(true);
        });
        event.getDrops().clear();

        Map<Player, BossBar> bars = new HashMap<>();
        int radius = cfg.deathBossBarRadius();
        for (Player player : loc.getWorld().getPlayers()) {
            if (player.getLocation().distance(loc) <= radius) {
                BossBar bar = BossBar.bossBar(
                        messages.renderFor(player, "wither.explosion-title"),
                        0f,
                        BossBar.Color.PURPLE,
                        BossBar.Overlay.PROGRESS);
                bars.put(player, bar);
                player.showBossBar(bar);
            }
        }

        int[] tick = {0};
        Scheduler.Task[] taskRef = {null};
        taskRef[0] = scheduler.runRepeating(Duration.ZERO, Duration.ofSeconds(1), () -> {
            tick[0]++;
            float progress = Math.min(tick[0] / (float) DEATH_COUNTDOWN_TICKS, 1f);
            bars.values().forEach(bar -> bar.progress(progress));
            if (tick[0] >= DEATH_COUNTDOWN_TICKS) {
                loc.getWorld()
                        .createExplosion(
                                loc.getX(),
                                loc.getY(),
                                loc.getZ(),
                                (float) config.get().deathExplosionPower(),
                                config.get().deathExplosionFire(),
                                config.get().deathExplosionBlockDamage());
                bars.forEach(Player::hideBossBar);
                if (taskRef[0] != null) {
                    taskRef[0].cancel();
                    activeTasks.remove(taskRef[0]);
                }
            }
        });
        activeTasks.add(taskRef[0]);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onOwnedDeath(EntityDeathEvent event) {
        if (!(event.getEntity() instanceof Wither wither)) {
            return;
        }
        EncounterController encounter = owner(wither);
        if (encounter == null) {
            if (crownArtifact(wither)) {
                event.getDrops().removeIf(item -> item.getType() == org.bukkit.Material.NETHER_STAR);
            }
            return;
        }
        event.getDrops().removeIf(item -> item.getType() == org.bukkit.Material.NETHER_STAR);
        if (!ownedDeaths.add(wither.getUniqueId())) {
            return;
        }
        encounter.onCleanup(() -> ownedDeaths.remove(wither.getUniqueId()));
        Location loc = wither.getLocation().clone();
        Map<Player, BossBar> bars = new HashMap<>();
        for (Player player : loc.getWorld().getPlayers()) {
            if (player.getLocation().distance(loc) <= config.get().deathBossBarRadius()) {
                BossBar bar = BossBar.bossBar(messages.renderFor(player, "wither.explosion-title"), 0,
                        BossBar.Color.PURPLE, BossBar.Overlay.PROGRESS);
                bars.put(player, bar);
                player.showBossBar(bar);
            }
        }
        Runnable hide = encounter.onCleanup(() -> bars.forEach(Player::hideBossBar));
        int[] tick = {0};
        Scheduler.Task[] task = {null};
        task[0] = encounter.runRepeating(Duration.ZERO, Duration.ofSeconds(1), () -> {
            tick[0]++;
            bars.values().forEach(bar -> bar.progress(Math.min(tick[0] / (float) DEATH_COUNTDOWN_TICKS, 1)));
            if (tick[0] >= DEATH_COUNTDOWN_TICKS) {
                WitherConfig cfg = config.get();
                loc.getWorld().createExplosion(loc, (float) cfg.deathExplosionPower(), cfg.deathExplosionFire(),
                        cfg.deathExplosionBlockDamage(), wither);
                hide.run();
                task[0].cancel();
                activeTasks.remove(task[0]);
            }
        });
        activeTasks.add(task[0]);
        Runnable cleanup = encounter.onCleanup(() -> {
            hide.run();
            task[0].cancel();
            activeTasks.remove(task[0]);
        });
        ownedCleanup.add(cleanup);
        encounter.onCleanup(() -> ownedCleanup.remove(cleanup));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onOwnedDrop(org.bukkit.event.entity.EntityDropItemEvent event) {
        if (event.getEntity() instanceof Wither && crownArtifact(event.getEntity())
                && event.getItemDrop().getItemStack().getType() == org.bukkit.Material.NETHER_STAR) {
            event.setCancelled(true);
            event.getItemDrop().remove();
        }
    }

    private Team getOrCreateWitherTeam(Server server) {
        Scoreboard scoreboard = server.getScoreboardManager().getMainScoreboard();
        Team team = scoreboard.getTeam(TEAM_NAME);
        if (team == null) {
            team = scoreboard.registerNewTeam(TEAM_NAME);
            team.setAllowFriendlyFire(false);
        }
        return team;
    }

    private void addToWitherTeam(Server server, Entity entity) {
        getOrCreateWitherTeam(server).addEntity(entity);
    }
}
