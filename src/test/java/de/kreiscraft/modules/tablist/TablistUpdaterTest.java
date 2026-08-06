package de.kreiscraft.modules.tablist;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.kreiscraft.core.message.PlaceholderResolver;
import java.util.Collection;
import java.util.List;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TablistUpdaterTest {

    @Mock
    private Server server;

    @Mock
    private PlaceholderResolver resolver;

    @Mock
    private Player alice;

    @Mock
    private Player bob;

    private static final String HEADER_TPL = "<gradient:#5555ff:#55ffff>Kreiscraft</gradient>";
    private static final String FOOTER_TPL = "TPS: %tps% | Players: %server_players%";

    @SuppressWarnings("unchecked")
    private void stubTwoPlayers() {
        when(server.getOnlinePlayers()).thenReturn((Collection) List.of(alice, bob));
    }

    private void stubDefaultTemplates() {
        when(resolver.resolveString(eq(HEADER_TPL), eq(alice))).thenReturn("Kreiscraft");
        when(resolver.resolveString(eq(FOOTER_TPL), eq(alice))).thenReturn("TPS: 20.0 | Players: 2");
        when(resolver.resolveString(eq(HEADER_TPL), eq(bob))).thenReturn("Kreiscraft");
        when(resolver.resolveString(eq(FOOTER_TPL), eq(bob))).thenReturn("TPS: 20.0 | Players: 2");
    }

    @Test
    void tick_callsSendPlayerListHeaderAndFooter_onEachOnlinePlayerExactlyOnce() {
        stubTwoPlayers();
        stubDefaultTemplates();
        TablistUpdater updater = new TablistUpdater(server, resolver, HEADER_TPL, FOOTER_TPL);

        updater.tick();

        verify(alice, times(1)).sendPlayerListHeaderAndFooter(any(), any());
        verify(bob, times(1)).sendPlayerListHeaderAndFooter(any(), any());
    }

    @Test
    void tick_callsResolveString_onEachPlayerForBothTemplates() {
        stubTwoPlayers();
        stubDefaultTemplates();
        TablistUpdater updater = new TablistUpdater(server, resolver, HEADER_TPL, FOOTER_TPL);

        updater.tick();

        verify(resolver, times(1)).resolveString(HEADER_TPL, alice);
        verify(resolver, times(1)).resolveString(FOOTER_TPL, alice);
        verify(resolver, times(1)).resolveString(HEADER_TPL, bob);
        verify(resolver, times(1)).resolveString(FOOTER_TPL, bob);
    }

    @Test
    @SuppressWarnings("unchecked")
    void tick_withNoOnlinePlayers_doesNotCallSendPlayerListHeaderAndFooter() {
        when(server.getOnlinePlayers()).thenReturn((Collection) List.of());
        TablistUpdater updater = new TablistUpdater(server, resolver, HEADER_TPL, FOOTER_TPL);

        updater.tick();

        verify(alice, times(0)).sendPlayerListHeaderAndFooter(any(), any());
        verify(bob, times(0)).sendPlayerListHeaderAndFooter(any(), any());
    }

    @Test
    void templates_updatesAreReflectedOnNextTick() {
        stubTwoPlayers();
        String newHeader = "<bold>New Header</bold>";
        String newFooter = "new footer";
        when(resolver.resolveString(eq(newHeader), eq(alice))).thenReturn("New Header");
        when(resolver.resolveString(eq(newFooter), eq(alice))).thenReturn("new footer");
        when(resolver.resolveString(eq(newHeader), eq(bob))).thenReturn("New Header");
        when(resolver.resolveString(eq(newFooter), eq(bob))).thenReturn("new footer");

        TablistUpdater updater = new TablistUpdater(server, resolver, HEADER_TPL, FOOTER_TPL);
        updater.templates(newHeader, newFooter);

        updater.tick();

        verify(resolver, times(1)).resolveString(newHeader, alice);
        verify(resolver, times(1)).resolveString(newFooter, alice);
        verify(resolver, times(0)).resolveString(HEADER_TPL, alice);
        verify(resolver, times(0)).resolveString(FOOTER_TPL, alice);
    }
}
