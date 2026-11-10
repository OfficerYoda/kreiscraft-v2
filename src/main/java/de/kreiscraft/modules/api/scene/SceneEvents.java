package de.kreiscraft.modules.api.scene;

import de.kreiscraft.core.scheduler.Scheduler;
import de.kreiscraft.modules.api.dto.PlayerState;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.entity.minecart.ExplosiveMinecart;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityResurrectEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerLevelChangeEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/** Main-thread scene state and qualified presentation events, not a raw event bus. */
public final class SceneEvents implements Listener {
    public record Message(int version, String type, String occurredAt, Object data) {
    }

    private final Server server;
    private final Scheduler scheduler;
    private final Map<UUID, PlayerState> states = new HashMap<>();
    private Consumer<Message> sink;
    private Scheduler.Task stateTask;
    private Scheduler.Task snapshotTask;
    private int generation;

    public SceneEvents(Server server, Scheduler scheduler) {
        this.server = server;
        this.scheduler = scheduler;
    }

    public void start(Consumer<Message> consumer) {
        stop();
        sink = consumer;
        server.getOnlinePlayers().forEach(p -> states.put(p.getUniqueId(), PlayerState.snapshot(p)));
        // ponytail: O(online players) each tick catches plugin-driven state changes too;
        // use dirty-player tracking only if profiling shows this scan matters.
        stateTask = scheduler.runRepeating(Duration.ofMillis(50), Duration.ofMillis(50), this::refresh);
        snapshotTask = scheduler.runRepeating(Duration.ofSeconds(30), Duration.ofSeconds(30),
                () -> sink.accept(snapshot()));
    }

    public void stop() {
        generation++;
        sink = null;
        if (stateTask != null) {
            stateTask.cancel();
        }
        if (snapshotTask != null) {
            snapshotTask.cancel();
        }
        states.clear();
    }

    public Message snapshot() {
        long time = server.getWorlds().stream().filter(w -> w.getEnvironment() == World.Environment.NORMAL)
                .findFirst().map(World::getTime).orElse(0L);
        List<PlayerState> players = server.getOnlinePlayers().stream().map(PlayerState::snapshot).toList();
        return message("scene_snapshot", Map.of("overworldTimeTicks", time, "players", players));
    }

    private void refresh() {
        server.getOnlinePlayers().forEach(this::update);
    }

    private PlayerState update(Player player) {
        PlayerState current = PlayerState.snapshot(player);
        PlayerState previous = states.put(player.getUniqueId(), current);
        if (!current.equals(previous)) {
            emit("player_state_changed", Map.of("player", current));
            if (!current.isInvisible() && previous != null) {
                if (current.isPumpkinWorn() && !previous.isPumpkinWorn()) {
                    emit("pumpkin_equipped", Map.of("player", current));
                }
                if (!current.fireState().equals(previous.fireState())
                        || (previous.isInvisible() && !current.fireState().equals("none"))) {
                    emit("fire_state_changed", Map.of("player", current, "state", current.fireState()));
                }
            }
        }
        return current;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        if (sink == null) {
            return;
        }
        PlayerState player = PlayerState.snapshot(event.getPlayer());
        states.put(event.getPlayer().getUniqueId(), player);
        if (!player.isInvisible()) {
            emit("player_joined", Map.of("player", player));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        PlayerState player = states.remove(event.getPlayer().getUniqueId());
        if (player != null && !PlayerState.snapshot(event.getPlayer()).isInvisible()) {
            emit("player_left", ref(player));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (event.isCancelled() || !(event.getEntity() instanceof Player victim)) {
            return;
        }
        afterOutcome(() -> {
            if (event.isCancelled() || !victim.isOnline()) {
                return;
            }
            PlayerState player = update(victim);
            if (player.isInvisible()) {
                return;
            }
            double damage = event.getFinalDamage();
            var damager = event.getDamager();
            Player attacker = damager instanceof Player p ? p
                    : damager instanceof Projectile projectile && projectile.getShooter() instanceof Player p ? p
                            : null;
            if (attacker != null && damage >= 4) {
                PlayerState attacking = PlayerState.snapshot(attacker);
                if (!attacking.isInvisible()) {
                    emit("damage", Map.of("attacker", ref(attacking), "victim", ref(player), "finalDamage", damage));
                }
            } else if (damage >= 1 && damager instanceof Creeper) {
                emit("creeper_damage", Map.of("player", player, "finalDamage", damage));
            } else if (damage >= 1 && (damager instanceof TNTPrimed || damager instanceof ExplosiveMinecart)) {
                emit("tnt_damage", Map.of("player", player, "finalDamage", damage));
            }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDeath(PlayerDeathEvent event) {
        if (!event.isCancelled()) {
            afterOutcome(() -> {
                if (!event.isCancelled()) {
                    present("death", event.getPlayer());
                }
            });
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onResurrect(EntityResurrectEvent event) {
        if (!event.isCancelled() && event.getEntity() instanceof Player player && event.getHand() != null) {
            afterOutcome(() -> {
                if (!event.isCancelled() && !player.isDead()) {
                    present("totem_used", player);
                }
            });
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent event) {
        if (event.isCancelled()) {
            return;
        }
        afterOutcome(() -> {
            if (event.isCancelled() || !event.getPlayer().isOnline()) {
                return;
            }
            PlayerState player = update(event.getPlayer());
            if (player.isInvisible()) {
                return;
            }
            Material item = event.getItem().getType();
            if (item == Material.POTION) {
                emit("potion_drank", Map.of("player", player));
            } else if (item.isEdible()) {
                String kind = item == Material.GOLDEN_APPLE || item == Material.ENCHANTED_GOLDEN_APPLE
                        ? "golden_apple"
                        : "food";
                emit("food_eaten", Map.of("player", player, "kind", kind));
            }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onLevel(PlayerLevelChangeEvent event) {
        afterOutcome(() -> {
            Player player = event.getPlayer();
            if (!player.isOnline()) {
                return;
            }
            PlayerState current = update(player);
            if (!current.isInvisible()) {
                int end = Math.min(event.getNewLevel(), current.level());
                for (int level = (event.getOldLevel() / 10 + 1) * 10; level <= end; level += 10) {
                    emit("level_milestone_reached", Map.of("player", current, "level", level));
                }
            }
        });
    }

    private void present(String type, Player player) {
        if (player.isOnline()) {
            PlayerState state = update(player);
            if (!state.isInvisible()) {
                emit(type, Map.of("player", state));
            }
        }
    }

    private void afterOutcome(Runnable action) {
        if (sink != null) {
            int scheduledGeneration = generation;
            scheduler.runLater(Duration.ofMillis(50), () -> {
                if (sink != null && generation == scheduledGeneration) {
                    action.run();
                }
            });
        }
    }

    private void emit(String type, Object data) {
        if (sink != null) {
            sink.accept(message(type, data));
        }
    }

    private static Message message(String type, Object data) {
        return new Message(1, type, Instant.now().toString(), data);
    }

    private static Map<String, String> ref(PlayerState player) {
        return Map.of("playerId", player.playerId(), "playerName", player.playerName());
    }
}
