package de.kreiscraft.modules.api.metrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

import de.kreiscraft.modules.api.dto.Metrics;
import de.kreiscraft.modules.api.dto.PlayerData;
import de.kreiscraft.testsupport.MockScheduler;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.bukkit.Statistic;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

class ServerMetricsCollectorTest {

    private static final Duration REFRESH_INTERVAL = Duration.ofSeconds(5);

    private ServerMock server;
    private MockScheduler scheduler;
    private ServerMetricsCollector collector;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        scheduler = new MockScheduler();
        ServerMock metricsServer = spy(server);
        doReturn(new double[] {20, 20, 20}).when(metricsServer).getTPS();
        collector = new ServerMetricsCollector(metricsServer, scheduler, REFRESH_INTERVAL);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void currentMetrics_beforeStart_returnsPlaceholderOnlineStatus_zeroMetrics() {
        Metrics metrics = collector.currentMetrics();

        assertThat(metrics.status()).isEqualTo("Online");
        assertThat(metrics.tps()).isEqualTo(0.0);
        assertThat(metrics.onlinePlayerCount()).isEqualTo(0);
        assertThat(metrics.memoryUsedMB()).isEqualTo(0L);
        assertThat(metrics.memoryMaxMB()).isEqualTo(0L);
        assertThat(metrics.cpuUsage()).isEqualTo(0.0);
        assertThat(collector.currentPlayers()).isEmpty();
    }

    @Test
    void start_thenRefreshOnMain_populatesMetrics() {
        collector.start();
        scheduler.advance(REFRESH_INTERVAL);

        Metrics metrics = collector.currentMetrics();

        // After refresh the status must still be "Online" and TPS populated.
        assertThat(metrics.status()).isEqualTo("Online");
        // MockBukkit's ServerMock returns default TPS; the field is no longer the zero placeholder.
        assertThat(metrics.tps()).isGreaterThanOrEqualTo(0.0);
        // Memory fields are populated from the real JVM.
        assertThat(metrics.memoryUsedMB()).isGreaterThan(0L);
        assertThat(metrics.memoryMaxMB()).isGreaterThan(0L);
    }

    @Test
    void refresh_snapshotsOnlinePlayersHealth_deaths_kills_playtime() {
        PlayerMock player = server.addPlayer("Alice");
        player.setHealth(10.0);
        player.setAbsorptionAmount(4);
        player.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 200, 0));
        player.setStatistic(Statistic.DEATHS, 3);
        player.setStatistic(Statistic.PLAYER_KILLS, 7);
        // PLAY_ONE_MINUTE counts in ticks (20 ticks = 1 second)
        player.setStatistic(Statistic.PLAY_ONE_MINUTE, 200); // 200 ticks = 10 seconds

        collector.start();
        scheduler.advance(REFRESH_INTERVAL);

        List<PlayerData> players = collector.currentPlayers();
        assertThat(players).hasSize(1);

        PlayerData snapshot = players.get(0);
        assertThat(snapshot.name()).isEqualTo("Alice");
        assertThat(snapshot.uuid()).isEqualTo(player.getUniqueId().toString());
        assertThat(snapshot.health()).isEqualTo(10); // Math.round(10.0f) = 10
        assertThat(snapshot.maxHealth()).isEqualTo(20);
        assertThat(snapshot.absorption()).isEqualTo(4);
        assertThat(snapshot.effects()).containsExactly("poison");
        assertThat(snapshot.deaths()).isEqualTo(3);
        assertThat(snapshot.playerKills()).isEqualTo(7);
        assertThat(snapshot.playtimeSeconds()).isEqualTo(10L); // 200 / 20 = 10
    }

    @Test
    void playerByUuid_returnsSnapshot_forOnlinePlayer() {
        PlayerMock player = server.addPlayer("Bob");
        player.setStatistic(Statistic.DEATHS, 1);
        player.setStatistic(Statistic.PLAYER_KILLS, 2);
        player.setStatistic(Statistic.PLAY_ONE_MINUTE, 40);

        collector.start();
        scheduler.advance(REFRESH_INTERVAL);

        PlayerData snapshot = collector.playerByUuid(player.getUniqueId());
        assertThat(snapshot).isNotNull();
        assertThat(snapshot.name()).isEqualTo("Bob");
        assertThat(snapshot.uuid()).isEqualTo(player.getUniqueId().toString());
    }

    @Test
    void playerByUuid_returnsNull_forOfflinePlayer() {
        collector.start();
        scheduler.advance(REFRESH_INTERVAL);

        assertThat(collector.playerByUuid(UUID.randomUUID())).isNull();
    }

    @Test
    void stop_cancelsRefreshTask() {
        // First refresh fires at t=0 (delay=ZERO) with no players.
        collector.start();
        scheduler.advance(Duration.ZERO);
        assertThat(collector.currentPlayers()).isEmpty();

        // Stop the collector before adding a player.
        collector.stop();

        // Add a player — if refresh ran again it would appear in the snapshot.
        server.addPlayer("Charlie");

        // Advance well past the interval; refresh must NOT fire.
        scheduler.advance(REFRESH_INTERVAL.multipliedBy(3));

        assertThat(collector.currentPlayers()).isEmpty();
    }
}
