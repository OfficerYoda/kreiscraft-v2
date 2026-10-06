package de.kreiscraft.modules.tablist;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.kreiscraft.core.message.PlaceholderResolver;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Collection;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Server;
import org.bukkit.Statistic;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * End-to-end check that the shipped default templates, the real {@link PlaceholderResolver} and the
 * updater together reproduce the original Kreiscraft tab-list presentation.
 */
@ExtendWith(MockitoExtension.class)
class TablistDefaultRenderingTest {

    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    @Mock
    private Server server;

    @Mock
    private Player alice;

    @Test
    @SuppressWarnings("unchecked")
    void defaultTemplates_renderBannerPlaytimeClockPingAndTps() {
        // 03:04:05 of playtime, measured in PLAY_ONE_MINUTE ticks.
        int ticks = (int) (((3L * 3600L) + (4L * 60L) + 5L) * 20L);
        when(server.getTPS()).thenReturn(new double[] {20.0});
        when(server.getOnlinePlayers()).thenReturn((Collection) List.of(alice));
        when(alice.getPing()).thenReturn(42);
        when(alice.getStatistic(Statistic.PLAY_ONE_MINUTE)).thenReturn(ticks);

        PlaceholderResolver resolver = new PlaceholderResolver(
                server,
                ZoneId.of("Europe/Berlin"),
                Clock.fixed(Instant.parse("2026-01-01T11:34:56Z"), ZoneId.of("Europe/Berlin")));

        TablistConfig cfg = new TablistConfig(null, null, null);
        TablistUpdater updater =
                new TablistUpdater(server, resolver, cfg.headerTemplate(), cfg.footerTemplate());
        updater.tick();

        ArgumentCaptor<Component> header = ArgumentCaptor.forClass(Component.class);
        ArgumentCaptor<Component> footer = ArgumentCaptor.forClass(Component.class);
        verify(alice).sendPlayerListHeaderAndFooter(header.capture(), footer.capture());

        String headerText = PLAIN.serialize(header.getValue());
        String footerText = PLAIN.serialize(footer.getValue());
        assertThat(headerText).contains("Kreiscraft").contains("Playtime: 03:04:05");
        assertThat(footerText)
                .contains("Uhrzeit: 12:34:56")
                .contains("Ping: 42")
                .contains("Tps: 20.0")
                .contains("Kreiscraft");
    }
}
