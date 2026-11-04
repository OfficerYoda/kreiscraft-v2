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
import de.kreiscraft.core.message.ConfigurateMessageService;
import java.nio.file.Path;
import java.util.UUID;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerQuitEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

class TpsBarTest {

    private Server server;
    private MessageService messages;
    private TpsBar service;
    private TpsBarConfig settings;

    @BeforeEach
    void setUp() {
        server = mock(Server.class);
        messages = mock(MessageService.class);
        when(server.getTPS()).thenReturn(new double[] {21.0});
        when(server.getAverageTickTime()).thenReturn(12.345);
        when(messages.renderFor(any(), eq("admin.tps-bar.title"), any(Object[].class)))
                .thenAnswer(invocation -> Component.empty()
                        .append(invocation.getArgument(3, Component.class)).append(Component.text(" / "))
                        .append(invocation.getArgument(5, Component.class)).append(Component.text(" / "))
                        .append(invocation.getArgument(7, Component.class)));
        settings = new TpsBarConfig();
        service = new TpsBar(messages, server, () -> settings);
    }

    @Test
    void toggle_showsLiveMetricsImmediatelyAndHidesSameBar() {
        Player player = player(42);

        assertThat(service.toggle(player)).isTrue();
        BossBar bar = shownBar(player);
        assertThat(plain(bar)).isEqualTo("20.00 / 12.35 / 42");
        assertThat(bar.color()).isEqualTo(BossBar.Color.GREEN);
        assertThat(bar.progress()).isCloseTo(12.345f / 50.0f, org.assertj.core.data.Offset.offset(0.00001f));
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

        assertThat(plain(aliceBar)).isEqualTo("19.88 / 45.68 / 50");
        assertThat(plain(bobBar)).isEqualTo("19.88 / 45.68 / 99");
        assertThat(aliceBar.color()).isEqualTo(BossBar.Color.YELLOW);
        assertThat(aliceBar.progress()).isCloseTo(45.678f / 50.0f, org.assertj.core.data.Offset.offset(0.00001f));
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

    @ParameterizedTest
    @CsvSource({
            "19, 40, 50, GREEN, GREEN, GREEN",
            "18.999, 40.001, 51, YELLOW, YELLOW, YELLOW",
            "15, 50, 120, YELLOW, YELLOW, YELLOW",
            "14.999, 50.001, 121, RED, RED, RED",
            "20, 60, 100, GREEN, RED, YELLOW",
            "16, 35, 150, YELLOW, GREEN, RED"
    })
    void display_colorsEachMetricIndependentlyAtExactRawThresholds(
            double tps, double mspt, int ping, String tpsColor, String msptColor, String pingColor) {
        when(server.getTPS()).thenReturn(new double[] {tps});
        when(server.getAverageTickTime()).thenReturn(mspt);
        Player player = player(ping);
        service.toggle(player);
        BossBar bar = shownBar(player);

        assertThat(bar.name().children().get(0).color())
                .isEqualTo(NamedTextColor.NAMES.value(tpsColor.toLowerCase(java.util.Locale.ROOT)));
        assertThat(bar.name().children().get(2).color())
                .isEqualTo(NamedTextColor.NAMES.value(msptColor.toLowerCase(java.util.Locale.ROOT)));
        assertThat(bar.name().children().get(4).color())
                .isEqualTo(NamedTextColor.NAMES.value(pingColor.toLowerCase(java.util.Locale.ROOT)));
        assertThat(bar.color()).isEqualTo(BossBar.Color.valueOf(msptColor));
    }

    @ParameterizedTest
    @CsvSource({"-1, 0", "0, 0", "25, 0.5", "50, 1", "100, 1"})
    void progress_isProportionalToMsptAndClamped(double mspt, float progress) {
        when(server.getAverageTickTime()).thenReturn(mspt);
        Player player = player(42);
        service.toggle(player);
        assertThat(shownBar(player).progress()).isEqualTo(progress);
    }

    @Test
    void tick_appliesReloadedThresholdsAndFillEndpointsWithoutReplacingBar() {
        when(server.getTPS()).thenReturn(new double[] {18.5});
        Player player = player(42);
        service.toggle(player);
        BossBar bar = shownBar(player);
        assertThat(bar.name().children().get(0).color()).isEqualTo(NamedTextColor.YELLOW);
        settings = new TpsBarConfig(10.0, 20.0, 18.0, 15.0, 40, 90, 10.0, 20.0);

        service.tick();

        assertThat(bar.color()).isEqualTo(BossBar.Color.YELLOW);
        assertThat(bar.progress()).isCloseTo(0.2345f, org.assertj.core.data.Offset.offset(0.00001f));
        assertThat(bar.name().children().get(0).color()).isEqualTo(NamedTextColor.GREEN);
        assertThat(bar.name().children().get(2).color()).isEqualTo(NamedTextColor.YELLOW);
        assertThat(bar.name().children().get(4).color()).isEqualTo(NamedTextColor.YELLOW);
        verify(player).showBossBar(bar);
    }

    @ParameterizedTest
    @CsvSource({"5, 0", "10, 0", "15, 0.5", "20, 1", "25, 1"})
    void progress_usesBothConfiguredEndpoints(double mspt, float progress) {
        settings = new TpsBarConfig(null, null, null, null, null, null, 10.0, 20.0);
        when(server.getAverageTickTime()).thenReturn(mspt);
        Player player = player(42);
        service.toggle(player);
        assertThat(shownBar(player).progress()).isEqualTo(progress);
    }

    @ParameterizedTest
    @ValueSource(strings = {"en", "de"})
    void localizedTitle_preservesIndependentNumberColors(String language, @TempDir Path tmp) throws Exception {
        when(server.getTPS()).thenReturn(new double[] {20.0});
        when(server.getAverageTickTime()).thenReturn(60.0);
        service = new TpsBar(new ConfigurateMessageService(tmp, getClass().getClassLoader(), language, server),
                server, () -> settings);
        Player player = player(100);
        service.toggle(player);
        BossBar bar = shownBar(player);

        assertThat(plain(bar)).isEqualTo("TPS: 20.00 | MSPT: 60.00 ms | Ping: 100 ms");
        assertThat(containsColoredText(bar.name(), "20.00", NamedTextColor.GREEN)).isTrue();
        assertThat(containsColoredText(bar.name(), "60.00", NamedTextColor.RED)).isTrue();
        assertThat(containsColoredText(bar.name(), "100", NamedTextColor.YELLOW)).isTrue();
        assertThat(bar.color()).isEqualTo(BossBar.Color.RED);
        assertThat(bar.progress()).isEqualTo(1.0f);
    }

    private static boolean containsColoredText(Component component, String text, NamedTextColor color) {
        return (component instanceof TextComponent tc && tc.content().equals(text) && color.equals(tc.color()))
                || component.children().stream().anyMatch(child -> containsColoredText(child, text, color));
    }

    private static String plain(BossBar bar) {
        return PlainTextComponentSerializer.plainText().serialize(bar.name());
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
