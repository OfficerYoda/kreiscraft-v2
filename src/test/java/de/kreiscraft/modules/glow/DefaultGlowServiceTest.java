package de.kreiscraft.modules.glow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DefaultGlowServiceTest {

    @Mock
    private Server server;
    @Mock
    private Player player;

    private DefaultGlowService service;
    private UUID playerId;

    @BeforeEach
    void setUp() {
        service = new DefaultGlowService(server);
        playerId = UUID.randomUUID();
    }

    @Test
    void isGlowing_beforeToggle_returnsFalse() {
        assertThat(service.isGlowing(playerId)).isFalse();
    }

    @Test
    void toggle_whenPlayerOnline_setsGlowingTrue() {
        when(server.getPlayer(playerId)).thenReturn(player);

        service.toggle(playerId);

        assertThat(service.isGlowing(playerId)).isTrue();
        verify(player).setGlowing(true);
    }

    @Test
    void toggle_twice_setsGlowingFalse() {
        when(server.getPlayer(playerId)).thenReturn(player);

        service.toggle(playerId);
        service.toggle(playerId);

        assertThat(service.isGlowing(playerId)).isFalse();
        verify(player).setGlowing(false);
    }

    @Test
    void toggle_whenPlayerOffline_storesStateWithoutNpe() {
        when(server.getPlayer(playerId)).thenReturn(null);

        service.toggle(playerId);

        assertThat(service.isGlowing(playerId)).isTrue();
    }

    @Test
    void removePlayer_clearsStateFromMap() {
        when(server.getPlayer(playerId)).thenReturn(player);
        service.toggle(playerId);

        service.removePlayer(playerId);

        assertThat(service.isGlowing(playerId)).isFalse();
    }

    @Test
    void disableAll_clearsGlowOnOnlinePlayers() {
        when(server.getPlayer(playerId)).thenReturn(player);
        service.toggle(playerId);

        service.disableAll();

        verify(player).setGlowing(false);
        assertThat(service.isGlowing(playerId)).isFalse();
    }

    @Test
    void disableAll_whenPlayerOffline_doesNotThrow() {
        when(server.getPlayer(playerId)).thenReturn(null);
        service.toggle(playerId);

        service.disableAll();

        assertThat(service.isGlowing(playerId)).isFalse();
    }
}
