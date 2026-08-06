package de.kreiscraft.modules.updatemode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.kreiscraft.platform.PaperPlatform;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;
import org.bukkit.OfflinePlayer;
import org.bukkit.Server;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;

class UpdateModeListenerTest {

    private static final Logger LOG = Logger.getLogger("test");

    @TempDir
    Path tmp;

    private DefaultUpdateModeService service;
    private Server mockServer;
    private PaperPlatform platform;
    private UpdateModeListener listener;

    @BeforeEach
    void setUp() throws IOException {
        // MockBukkit must be initialised so that AsyncPlayerPreLoginEvent constructor
        // can call Bukkit.createProfile() without NPE.
        MockBukkit.mock();

        UpdateModeData data = new UpdateModeData(tmp);
        data.load();
        service = new DefaultUpdateModeService(
                data, new UpdateModeConfig("kreiscraft.updatemode.bypass", List.of(), null), LOG);

        // Use a separate Mockito mock for the platform server so we can control
        // getOfflinePlayer() / isOp() independently of the MockBukkit ServerMock.
        mockServer = mock(Server.class);
        platform = new PaperPlatform(null, mockServer);
        listener = new UpdateModeListener(service, platform);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @SuppressWarnings("removal")
    private AsyncPlayerPreLoginEvent preLoginEvent(String name, UUID uuid) throws UnknownHostException {
        InetAddress addr = InetAddress.getByName("127.0.0.1");
        return new AsyncPlayerPreLoginEvent(name, addr, uuid);
    }

    private void mockIsOp(UUID uuid, boolean op) {
        OfflinePlayer offlinePlayer = mock(OfflinePlayer.class);
        when(offlinePlayer.isOp()).thenReturn(op);
        when(mockServer.getOfflinePlayer(uuid)).thenReturn(offlinePlayer);
    }

    @Test
    void onPreLogin_whenInactive_allowsLogin() throws UnknownHostException {
        service.setActive(false);
        UUID uuid = UUID.randomUUID();
        mockIsOp(uuid, false);

        AsyncPlayerPreLoginEvent event = preLoginEvent("Player", uuid);
        listener.onPreLogin(event);

        assertThat(event.getLoginResult()).isEqualTo(AsyncPlayerPreLoginEvent.Result.ALLOWED);
    }

    @Test
    void onPreLogin_whenActiveAndNoBypass_kicksPlayer() throws UnknownHostException {
        service.setActive(true);
        UUID uuid = UUID.randomUUID();
        mockIsOp(uuid, false);

        AsyncPlayerPreLoginEvent event = preLoginEvent("Player", uuid);
        listener.onPreLogin(event);

        assertThat(event.getLoginResult()).isEqualTo(AsyncPlayerPreLoginEvent.Result.KICK_WHITELIST);
    }

    @Test
    void onPreLogin_whenActiveAndPlayerInBypassNames_allowsLogin() throws UnknownHostException {
        service.updateConfig(
                new UpdateModeConfig("kreiscraft.updatemode.bypass", List.of("Alice"), null));
        service.setActive(true);
        UUID uuid = UUID.randomUUID();
        mockIsOp(uuid, false);

        AsyncPlayerPreLoginEvent event = preLoginEvent("Alice", uuid);
        listener.onPreLogin(event);

        assertThat(event.getLoginResult()).isEqualTo(AsyncPlayerPreLoginEvent.Result.ALLOWED);
    }

    @Test
    void onPreLogin_whenActiveAndPlayerIsOp_allowsLogin() throws UnknownHostException {
        service.setActive(true);
        UUID uuid = UUID.randomUUID();
        mockIsOp(uuid, true);

        AsyncPlayerPreLoginEvent event = preLoginEvent("OpPlayer", uuid);
        listener.onPreLogin(event);

        assertThat(event.getLoginResult()).isEqualTo(AsyncPlayerPreLoginEvent.Result.ALLOWED);
    }

    @Test
    void onPreLogin_kickMessage_matchesConfig() throws UnknownHostException {
        service.setActive(true);
        UUID uuid = UUID.randomUUID();
        mockIsOp(uuid, false);

        AsyncPlayerPreLoginEvent event = preLoginEvent("Player", uuid);
        listener.onPreLogin(event);

        assertThat(event.kickMessage()).isEqualTo(service.config().kickMessage());
    }
}
