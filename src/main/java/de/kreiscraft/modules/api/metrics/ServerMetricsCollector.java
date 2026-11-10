package de.kreiscraft.modules.api.metrics;

import de.kreiscraft.core.scheduler.Scheduler;
import de.kreiscraft.modules.api.dto.Metrics;
import de.kreiscraft.modules.api.dto.PlayerData;
import de.kreiscraft.modules.api.dto.PlayerState;
import java.lang.management.ManagementFactory;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Server;
import org.bukkit.Statistic;
import org.bukkit.entity.Player;

public final class ServerMetricsCollector {

    private static final Metrics PLACEHOLDER_METRICS =
            new Metrics("Online", 0.0, 0, 0L, 0L, 0.0);

    private final Server server;
    private final Scheduler scheduler;
    private final Duration refreshInterval;

    private volatile Metrics currentMetrics = PLACEHOLDER_METRICS;
    private volatile List<PlayerData> currentPlayers = List.of();
    private volatile Map<UUID, PlayerData> playersByUuid = Map.of();

    private Scheduler.Task refreshTask = null;

    public ServerMetricsCollector(Server server, Scheduler scheduler, Duration refreshInterval) {
        this.server = server;
        this.scheduler = scheduler;
        this.refreshInterval = refreshInterval;
    }

    /** Starts the main-thread refresh task. Idempotent. */
    public void start() {
        if (refreshTask != null && !refreshTask.isCancelled()) {
            return;
        }
        refreshTask = scheduler.runRepeating(Duration.ZERO, refreshInterval, this::refresh);
    }

    /** Cancels the refresh task. Idempotent. */
    public void stop() {
        if (refreshTask != null) {
            refreshTask.cancel();
            refreshTask = null;
        }
    }

    /** Latest snapshot. Never null. Safe to call from any thread. */
    public Metrics currentMetrics() {
        return currentMetrics;
    }

    /** Latest per-player snapshot. Safe to call from any thread. */
    public List<PlayerData> currentPlayers() {
        return currentPlayers;
    }

    /** Direct read for a specific player if online. Null if not. Safe from any thread. */
    public PlayerData playerByUuid(UUID uuid) {
        return playersByUuid.get(uuid);
    }

    // Called on the main thread by the Scheduler.
    private void refresh() {
        double tps = server.getTPS()[0];

        var memBean = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
        long memoryUsedMB = memBean.getUsed() / 1024 / 1024;
        long memoryMaxMB = memBean.getMax() / 1024 / 1024;

        double cpuUsage;
        try {
            var osMBean =
                    (com.sun.management.OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
            cpuUsage = osMBean.getCpuLoad() * 100;
        } catch (ClassCastException e) {
            cpuUsage = -1.0;
        }

        List<PlayerData> players =
                server.getOnlinePlayers().stream().map(this::snapshot).toList();

        Map<UUID, PlayerData> byUuid = new HashMap<>();
        for (PlayerData pd : players) {
            byUuid.put(UUID.fromString(pd.uuid()), pd);
        }

        Metrics metrics =
                new Metrics(
                        "Online",
                        tps,
                        players.size(),
                        memoryUsedMB,
                        memoryMaxMB,
                        cpuUsage);

        // Assign volatile fields after fully populating (safe publication).
        this.currentPlayers = List.copyOf(players);
        this.playersByUuid = Map.copyOf(byUuid);
        this.currentMetrics = metrics;
    }

    private PlayerData snapshot(Player p) {
        PlayerState state = PlayerState.snapshot(p);
        int health = (int) Math.round(state.health());
        int deaths = p.getStatistic(Statistic.DEATHS);
        int playerKills = p.getStatistic(Statistic.PLAYER_KILLS);
        long playtimeSeconds = p.getStatistic(Statistic.PLAY_ONE_MINUTE) / 20L;
        return new PlayerData(
                p.getName(), p.getUniqueId().toString(), health, (int) Math.round(state.maxHealth()),
                state.absorption(), state.effects(), deaths, playerKills, playtimeSeconds);
    }
}
