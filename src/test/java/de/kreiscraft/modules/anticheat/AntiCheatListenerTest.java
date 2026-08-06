package de.kreiscraft.modules.anticheat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.kreiscraft.core.message.MessageService;
import de.kreiscraft.platform.PlayerBrandProvider;
import de.kreiscraft.testsupport.MockScheduler;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;
import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.plugin.PluginMock;

class AntiCheatListenerTest {

    @TempDir
    Path tmp;

    private ServerMock server;
    private PluginMock plugin;
    private MockScheduler scheduler;
    private DefaultHackLogService service;
    private MessageService messages;
    private PlayerBrandProvider brandProvider;
    private AntiCheatConfig config;

    @BeforeEach
    void setUp() throws IOException {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        scheduler = new MockScheduler();

        HackLogData data = new HackLogData(tmp);
        data.load();
        service = new DefaultHackLogService(data);
        messages = mock(MessageService.class);
        brandProvider = mock(PlayerBrandProvider.class);
        when(messages.render(any())).thenReturn(Component.empty());
        when(messages.render(any(), any())).thenReturn(Component.empty());

        config = new AntiCheatConfig("kreiscraft:mod_checker", true, Duration.ofSeconds(5));
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private void registerListener() {
        AntiCheatListener listener =
                new AntiCheatListener(config, service, scheduler, messages, brandProvider);
        server.getPluginManager().registerEvents(listener, plugin);
    }

    @Test
    void fabricBrandedPlayer_kicked_afterGracePeriod() {
        when(brandProvider.brandOf(any())).thenReturn(Optional.of("fabric"));
        when(messages.render("anti-cheat.fabric-forge-kick")).thenReturn(Component.text("kicked"));
        registerListener();

        PlayerMock player = server.addPlayer("Alice");
        assertThat(player.isOnline()).isTrue();

        scheduler.advance(Duration.ofSeconds(5));

        assertThat(player.isOnline()).isFalse();
    }

    @Test
    void forgeBrandedPlayer_kicked_afterGracePeriod() {
        when(brandProvider.brandOf(any())).thenReturn(Optional.of("forge"));
        when(messages.render("anti-cheat.fabric-forge-kick")).thenReturn(Component.text("kicked"));
        registerListener();

        PlayerMock player = server.addPlayer("Bob");
        scheduler.advance(Duration.ofSeconds(5));

        assertThat(player.isOnline()).isFalse();
    }

    @Test
    void vanillaPlayer_notKicked() {
        when(brandProvider.brandOf(any())).thenReturn(Optional.of("vanilla"));
        registerListener();

        PlayerMock player = server.addPlayer("Charlie");
        scheduler.advance(Duration.ofSeconds(10));

        assertThat(player.isOnline()).isTrue();
    }

    @Test
    void fabricPlayer_handshakePassed_notKicked() {
        when(brandProvider.brandOf(any())).thenReturn(Optional.of("fabric"));
        registerListener();

        PlayerMock player = server.addPlayer("Dave");
        // simulate handshake completed before grace period expires
        service.markHandshakePassed(player.getUniqueId());

        scheduler.advance(Duration.ofSeconds(5));

        assertThat(player.isOnline()).isTrue();
    }

    @Test
    void kickDisabled_fabricPlayer_notKicked() {
        config = new AntiCheatConfig("kreiscraft:mod_checker", false, Duration.ofSeconds(5));
        when(brandProvider.brandOf(any())).thenReturn(Optional.of("fabric"));
        registerListener();

        PlayerMock player = server.addPlayer("Eve");
        scheduler.advance(Duration.ofSeconds(5));

        assertThat(player.isOnline()).isTrue();
    }

    @Test
    void onQuit_clearsHandshakeState() {
        when(brandProvider.brandOf(any())).thenReturn(Optional.of("fabric"));
        registerListener();

        PlayerMock player = server.addPlayer("Frank");
        service.markHandshakePassed(player.getUniqueId());
        assertThat(service.hasHandshakePassed(player.getUniqueId())).isTrue();

        player.disconnect();

        assertThat(service.hasHandshakePassed(player.getUniqueId())).isFalse();
    }

    @Test
    void noBrand_playerNotKicked() {
        when(brandProvider.brandOf(any())).thenReturn(Optional.empty());
        registerListener();

        PlayerMock player = server.addPlayer("Grace");
        scheduler.advance(Duration.ofSeconds(10));

        assertThat(player.isOnline()).isTrue();
    }
}
