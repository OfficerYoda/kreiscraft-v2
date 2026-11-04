package de.kreiscraft.modules.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import de.kreiscraft.core.message.MessageService;
import de.kreiscraft.core.message.PlaceholderResolver;
import java.time.Clock;
import java.time.ZoneId;
import java.util.UUID;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerQuitEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class TpsBarTest {

    private Server server;
    private MessageService messages;
    private TpsBar service;

    @BeforeEach
    void setUp() {
        server = mock(Server.class);
        messages = mock(MessageService.class);
        when(server.getTPS()).thenReturn(new double[] {21.0});
        when(server.getAverageTickTime()).thenReturn(12.345);
        when(messages.renderFor(any(), eq("admin.tps-bar.title"), any(Object[].class)))
                .thenAnswer(invocation -> Component.text(invocation.getArgument(3, String.class)
                        + " / " + invocation.getArgument(5, String.class)
                        + " / " + invocation.getArgument(7, String.class)));
        service = new TpsBar(messages,
                new PlaceholderResolver(server, ZoneId.of("UTC"), Clock.systemUTC()));
    }

    @Test
    void toggle_showsLiveMetricsImmediatelyAndHidesSameBar() {
        Player player = player(42);

        assertThat(service.toggle(player)).isTrue();
        BossBar bar = shownBar(player);
        assertThat(bar.name()).isEqualTo(Component.text("20.00 / 12.35 / 42"));
        assertThat(service.toggle(player)).isFalse();
        verify(player).hideBossBar(bar);
        clearInvocations(messages, server);
        service.tick();
        verifyNoInteractions(messages, server);
    }

    @Test
    void tick_refreshesEachPlayersOwnPingOnSeparateBars() {
        Player alice = player(42);
        Player bob = player(99);
        service.toggle(alice);
        service.toggle(bob);
        BossBar aliceBar = shownBar(alice);
        BossBar bobBar = shownBar(bob);
        assertThat(aliceBar).isNotSameAs(bobBar);
        when(server.getTPS()).thenReturn(new double[] {19.876});
        when(server.getAverageTickTime()).thenReturn(45.678);
        when(alice.getPing()).thenReturn(50);

        service.tick();

        assertThat(aliceBar.name()).isEqualTo(Component.text("19.88 / 45.68 / 50"));
        assertThat(bobBar.name()).isEqualTo(Component.text("19.88 / 45.68 / 99"));
        service.toggle(alice);
        verify(bob, never()).hideBossBar(any());
    }

    @Test
    void quit_clearsSessionAndNextLoginStartsWithBarOff() {
        Player player = player(42);
        service.toggle(player);
        BossBar bar = shownBar(player);

        PlayerQuitEvent event = mock(PlayerQuitEvent.class);
        when(event.getPlayer()).thenReturn(player);
        service.onQuit(event);
        verify(player).hideBossBar(bar);
        clearInvocations(messages, server);
        service.tick();
        verifyNoInteractions(messages, server);
        assertThat(service.toggle(player)).isTrue();
    }

    @Test
    void tick_removesDisconnectedPlayersEvenIfQuitWasMissed() {
        Player player = player(42);
        service.toggle(player);
        BossBar bar = shownBar(player);
        when(player.isOnline()).thenReturn(false);
        clearInvocations(messages, server);

        service.tick();
        service.tick();

        verify(player).hideBossBar(bar);
        verifyNoInteractions(messages, server);
    }

    @Test
    void tick_removesBarWhenPermissionIsRevoked() {
        Player player = player(42);
        service.toggle(player);
        BossBar bar = shownBar(player);
        when(player.hasPermission("kreiscraft.admin.tps")).thenReturn(false);
        clearInvocations(messages, server);

        service.tick();

        verify(player).hideBossBar(bar);
        verifyNoInteractions(messages, server);
    }

    @Test
    void shutdown_hidesAllBarsAndIsIdempotent() {
        Player alice = player(42);
        Player bob = player(99);
        service.toggle(alice);
        service.toggle(bob);
        BossBar aliceBar = shownBar(alice);
        BossBar bobBar = shownBar(bob);
        clearInvocations(messages, server);

        service.shutdown();
        service.shutdown();
        service.tick();

        verify(alice).hideBossBar(aliceBar);
        verify(bob).hideBossBar(bobBar);
        verifyNoInteractions(messages, server);
    }

    private Player player(int ping) {
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.getPing()).thenReturn(ping);
        when(player.isOnline()).thenReturn(true);
        when(player.hasPermission("kreiscraft.admin.tps")).thenReturn(true);
        return player;
    }

    private BossBar shownBar(Player player) {
        ArgumentCaptor<BossBar> captor = ArgumentCaptor.forClass(BossBar.class);
        verify(player).showBossBar(captor.capture());
        return captor.getValue();
    }
}
