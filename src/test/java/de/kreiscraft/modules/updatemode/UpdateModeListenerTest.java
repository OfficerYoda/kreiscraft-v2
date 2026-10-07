package de.kreiscraft.modules.updatemode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.kreiscraft.core.config.YamlConfigService;
import de.kreiscraft.core.message.MessageService;
import de.kreiscraft.platform.PaperPlatform;
import java.io.IOException;
import java.net.InetAddress;
import java.nio.file.Path;
import java.util.List;
import java.util.logging.Logger;
import net.kyori.adventure.text.Component;
import org.bukkit.event.player.PlayerLoginEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.plugin.PluginMock;

class UpdateModeListenerTest {

    private static final Logger LOG = Logger.getLogger("test");
    private static final String BYPASS_PERMISSION = "kreiscraft.updatemode.bypass";
    private static final Component KICK_COMPONENT = Component.text("Server in maintenance");

    @TempDir
    Path tmp;

    private ServerMock server;
    private PluginMock plugin;
    private MessageService messages;
    private YamlConfigService config;
    private DefaultUpdateModeService service;
    private UpdateModeListener listener;

    @BeforeEach
    void setUp() throws IOException {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        messages = mock(MessageService.class);
        when(messages.renderFor(any(), any())).thenReturn(KICK_COMPONENT);
        when(messages.renderFor(any(), any(), any())).thenReturn(KICK_COMPONENT);

        config = new YamlConfigService(tmp, getClass().getClassLoader());
        service = new DefaultUpdateModeService(
                config,
                new UpdateModeConfig(BYPASS_PERMISSION, List.of()),
                new PaperPlatform(plugin, server),
                messages,
                LOG);
        listener = new UpdateModeListener(service, messages);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private PlayerLoginEvent loginEvent(PlayerMock player) throws IOException {
        return new PlayerLoginEvent(player, "localhost", InetAddress.getByName("127.0.0.1"));
    }

    @Test
    void onLogin_whenInactive_allowsLogin() throws IOException {
        PlayerMock alice = server.addPlayer("Alice");

        PlayerLoginEvent event = loginEvent(alice);
        listener.onLogin(event);

        assertThat(event.getResult()).isEqualTo(PlayerLoginEvent.Result.ALLOWED);
    }

    @Test
    void onLogin_whenActiveAndNoBypass_blocksLogin() throws IOException {
        service.setActive(true);
        PlayerMock alice = server.addPlayer("Alice");

        PlayerLoginEvent event = loginEvent(alice);
        listener.onLogin(event);

        assertThat(event.getResult()).isEqualTo(PlayerLoginEvent.Result.KICK_WHITELIST);
        assertThat(event.kickMessage()).isEqualTo(KICK_COMPONENT);
    }

    @Test
    void onLogin_whenActiveAndPermissionBypass_allowsLogin() throws IOException {
        service.setActive(true);
        PlayerMock alice = server.addPlayer("Alice");
        alice.addAttachment(plugin, BYPASS_PERMISSION, true);

        PlayerLoginEvent event = loginEvent(alice);
        listener.onLogin(event);

        assertThat(event.getResult()).isEqualTo(PlayerLoginEvent.Result.ALLOWED);
    }

    @Test
    void onLogin_whenActiveAndNameBypass_allowsLogin() throws IOException {
        service.updateConfig(new UpdateModeConfig(BYPASS_PERMISSION, List.of("Bob")));
        service.setActive(true);
        PlayerMock bob = server.addPlayer("Bob");

        PlayerLoginEvent event = loginEvent(bob);
        listener.onLogin(event);

        assertThat(event.getResult()).isEqualTo(PlayerLoginEvent.Result.ALLOWED);
    }

    @Test
    void onLogin_whenActiveAndPlayerIsOp_allowsLogin() throws IOException {
        service.setActive(true);
        PlayerMock op = server.addPlayer("OpPlayer");
        op.setOp(true);

        PlayerLoginEvent event = loginEvent(op);
        listener.onLogin(event);

        assertThat(event.getResult()).isEqualTo(PlayerLoginEvent.Result.ALLOWED);
    }

    @Test
    void onLogin_kickMessage_comesFromMessageService() throws IOException {
        service.setActive(true);
        PlayerMock alice = server.addPlayer("Alice");

        PlayerLoginEvent event = loginEvent(alice);
        listener.onLogin(event);

        assertThat(event.kickMessage()).isEqualTo(KICK_COMPONENT);
    }

    @Test
    void onLogin_afterConfigReload_honorsUpdatedBypassPermission() throws IOException {
        service.setActive(true);
        PlayerMock alice = server.addPlayer("Alice");
        alice.addAttachment(plugin, "kreiscraft.custom.bypass", true);

        // Before reload the custom permission is not configured, so Alice is blocked.
        PlayerLoginEvent before = loginEvent(alice);
        listener.onLogin(before);
        assertThat(before.getResult()).isEqualTo(PlayerLoginEvent.Result.KICK_WHITELIST);

        // Reload changes the configured bypass permission to the one Alice holds.
        service.updateConfig(new UpdateModeConfig("kreiscraft.custom.bypass", List.of()));

        PlayerLoginEvent after = loginEvent(alice);
        listener.onLogin(after);
        assertThat(after.getResult()).isEqualTo(PlayerLoginEvent.Result.ALLOWED);
    }

    @Test
    void onLogin_afterRestart_stillBlocksNonBypassedPlayers() throws IOException {
        // First run: activation persists the active state to config.yml.
        service.setActive(true);

        // Second run: a fresh service loads the persisted state from the same config file.
        YamlConfigService reopened = new YamlConfigService(tmp, getClass().getClassLoader());
        DefaultUpdateModeService restarted = new DefaultUpdateModeService(
                reopened,
                new UpdateModeConfig(BYPASS_PERMISSION, List.of()),
                new PaperPlatform(plugin, server),
                messages,
                LOG);
        UpdateModeListener restartedListener = new UpdateModeListener(restarted, messages);

        assertThat(restarted.isActive()).isTrue();

        PlayerMock alice = server.addPlayer("Alice");
        PlayerLoginEvent event = loginEvent(alice);
        restartedListener.onLogin(event);

        assertThat(event.getResult()).isEqualTo(PlayerLoginEvent.Result.KICK_WHITELIST);
    }
}
