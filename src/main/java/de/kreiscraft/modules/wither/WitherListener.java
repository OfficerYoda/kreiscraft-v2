package de.kreiscraft.modules.wither;

import de.kreiscraft.core.event.PriorityListener;
import de.kreiscraft.core.message.MessageService;
import de.kreiscraft.core.scheduler.Scheduler;
import java.time.Duration;
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
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

public final class WitherListener implements PriorityListener {

    private static final String TEAM_NAME = "bedrock_wither";
    private static final String HALF_TAG = "Half";
    private static final int DEATH_COUNTDOWN_TICKS = 10;

    private final WitherConfig config;
    private final MessageService messages;
    private final Scheduler scheduler;

    public WitherListener(WitherConfig config, MessageService messages, Scheduler scheduler) {
        this.config = config;
        this.messages = messages;
        this.scheduler = scheduler;
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        if (!(event.getEntity() instanceof Wither wither)) {
            return;
        }
        AttributeInstance maxHealthAttr = wither.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealthAttr != null) {
            maxHealthAttr.setBaseValue(config.maxHealth());
            wither.setHealth(config.maxHealth());
        }
        AttributeInstance attackAttr = wither.getAttribute(Attribute.ATTACK_DAMAGE);
        if (attackAttr != null) {
            attackAttr.setBaseValue(config.attackDamage());
        }
        AttributeInstance armorAttr = wither.getAttribute(Attribute.ARMOR);
        if (armorAttr != null) {
            armorAttr.setBaseValue(config.armor());
        }
        addToWitherTeam(wither.getServer(), wither);
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onEntityDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Wither wither)) {
            return;
        }
        if (wither.getHealth() <= config.halfHealthThreshold()
                && !wither.getScoreboardTags().contains(HALF_TAG)) {
            wither.addScoreboardTag(HALF_TAG);
            Location halfLoc = wither.getLocation();
            wither.getWorld()
                    .createExplosion(
                            halfLoc.getX(),
                            halfLoc.getY(),
                            halfLoc.getZ(),
                            (float) config.halfHealthExplosionPower(),
                            true,
                            true);
            Team team = getOrCreateWitherTeam(wither.getServer());
            for (int i = 0; i < config.minionCount(); i++) {
                Entity skeleton = wither.getWorld()
                        .spawnEntity(wither.getLocation(), EntityType.WITHER_SKELETON);
                team.addEntity(skeleton);
            }
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onEntityDeath(EntityDeathEvent event) {
        if (!(event.getEntity() instanceof Wither wither)) {
            return;
        }
        Location loc = wither.getLocation();
        event.getDrops().forEach(itemStack -> {
            Entity item = loc.getWorld().dropItem(loc, itemStack);
            item.setInvulnerable(true);
            item.setGlowing(true);
        });
        event.getDrops().clear();

        BossBar bar = BossBar.bossBar(
                messages.render("wither.explosion-title"),
                0f,
                BossBar.Color.PURPLE,
                BossBar.Overlay.PROGRESS);
        int radius = config.deathBossBarRadius();
        for (Player player : loc.getWorld().getPlayers()) {
            if (player.getLocation().distance(loc) <= radius) {
                player.showBossBar(bar);
            }
        }

        int[] tick = {0};
        Scheduler.Task[] taskRef = {null};
        taskRef[0] = scheduler.runRepeating(Duration.ZERO, Duration.ofSeconds(1), () -> {
            tick[0]++;
            bar.progress(Math.min(tick[0] / (float) DEATH_COUNTDOWN_TICKS, 1f));
            if (tick[0] >= DEATH_COUNTDOWN_TICKS) {
                loc.getWorld()
                        .createExplosion(
                                loc.getX(),
                                loc.getY(),
                                loc.getZ(),
                                (float) config.deathExplosionPower());
                for (Player player : loc.getWorld().getPlayers()) {
                    player.hideBossBar(bar);
                }
                if (taskRef[0] != null) {
                    taskRef[0].cancel();
                }
            }
        });
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
