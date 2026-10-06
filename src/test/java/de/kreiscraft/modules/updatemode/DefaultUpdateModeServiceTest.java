package de.kreiscraft.modules.updatemode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.kreiscraft.core.message.MessageService;
import de.kreiscraft.platform.PaperPlatform;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.logging.Logger;
import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.plugin.PluginMock;

class DefaultUpdateModeServiceTest {

    private static final Logger LOG = Logger.getLogger("test");
    private static final String BYPASS_PERMISSION = "kreiscraft.updatemode.bypass";

    @TempDir
    Path tmp;

    private ServerMock server;
    private PluginMock plugin;
    private MessageService messages;
    private DefaultUpdateModeService service;

    private static UpdateModeConfig defaultConfig() {
        return new UpdateModeConfig(BYPASS_PERMISSION, List.of());
    }

    @BeforeEach
    void setUp() throws IOException {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        messages = mock(MessageService.class);
        when(messages.render("update-mode.kick-message")).thenReturn(Component.text("kick"));

        UpdateModeData data = new UpdateModeData(tmp);
        data.load();
        service = new DefaultUpdateModeService(
                data, defaultConfig(), new PaperPlatform(plugin, server), messages, LOG);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    // --- persistence -------------------------------------------------------

    @Test
    void setActive_true_persists() throws IOException {
        service.setActive(true);

        // Verify in-memory state
        assertThat(service.isActive()).isTrue();

        // Verify on-disk persistence
        assertThat(Files.exists(tmp.resolve("data/update-mode.yml"))).isTrue();
        assertThat(Files.readString(tmp.resolve("data/update-mode.yml"))).contains("active: true");
    }

    @Test
    void setActive_false_persists() throws IOException {
        service.setActive(true);
        service.setActive(false);

        assertThat(service.isActive()).isFalse();
        assertThat(Files.readString(tmp.resolve("data/update-mode.yml"))).contains("active: false");
    }

    @Test
    void stateLoaded_fromDisk() throws IOException {
        // First service writes active=true
        UpdateModeData data1 = new UpdateModeData(tmp);
        data1.load();
        DefaultUpdateModeService svc1 = new DefaultUpdateModeService(
                data1, defaultConfig(), new PaperPlatform(plugin, server), messages, LOG);
        svc1.setActive(true);

        // Second service loads from same directory
        UpdateModeData data2 = new UpdateModeData(tmp);
        data2.load();
        DefaultUpdateModeService svc2 = new DefaultUpdateModeService(
                data2, defaultConfig(), new PaperPlatform(plugin, server), messages, LOG);

        assertThat(svc2.isActive()).isTrue();
    }

    @Test
    void initialState_isFalse() {
        assertThat(service.isActive()).isFalse();
    }

    @Test
    void updateConfig_replacesConfig() {
        UpdateModeConfig newCfg = new UpdateModeConfig("kreiscraft.other.bypass", List.of("alice"));
        service.updateConfig(newCfg);

        assertThat(service.config().bypassPermission()).isEqualTo("kreiscraft.other.bypass");
        assertThat(service.config().bypassNames()).containsExactly("alice");
    }

    // --- activation effects ------------------------------------------------

    @Test
    void setActive_true_kicksNonBypassedOnlinePlayers() {
        PlayerMock alice = server.addPlayer("Alice");

        service.setActive(true);

        assertThat(alice.isOnline()).isFalse();
        verify(messages).render("update-mode.kick-message");
    }

    @Test
    void setActive_true_notifiesPermissionBypassedPlayers() {
        PlayerMock alice = server.addPlayer("Alice");
        alice.addAttachment(plugin, BYPASS_PERMISSION, true);

        service.setActive(true);

        assertThat(alice.isOnline()).isTrue();
        verify(messages).send(alice, "update-mode.bypass-notice");
    }

    @Test
    void setActive_true_notifiesNameBypassedPlayers() {
        service.updateConfig(new UpdateModeConfig(BYPASS_PERMISSION, List.of("Bob")));
        PlayerMock bob = server.addPlayer("Bob");

        service.setActive(true);

        assertThat(bob.isOnline()).isTrue();
        verify(messages).send(bob, "update-mode.bypass-notice");
    }

    @Test
    void setActive_true_notifiesOpBypassedPlayers() {
        PlayerMock op = server.addPlayer("OpPlayer");
        op.setOp(true);

        service.setActive(true);

        assertThat(op.isOnline()).isTrue();
        verify(messages).send(op, "update-mode.bypass-notice");
    }

    @Test
    void setActive_false_doesNotKickOnlinePlayers() {
        PlayerMock alice = server.addPlayer("Alice");

        service.setActive(false);

        assertThat(alice.isOnline()).isTrue();
    }

    @Test
    void setActive_true_mixedPlayers_kicksOnlyNonBypassed() {
        PlayerMock staff = server.addPlayer("Staff");
        staff.addAttachment(plugin, BYPASS_PERMISSION, true);
        PlayerMock regular = server.addPlayer("Regular");

        service.setActive(true);

        assertThat(staff.isOnline()).isTrue();
        assertThat(regular.isOnline()).isFalse();
        verify(messages).send(staff, "update-mode.bypass-notice");
    }

    // --- bypass resolution -------------------------------------------------

    @Test
    void isBypassed_usesConfiguredPermission() {
        service.updateConfig(new UpdateModeConfig("kreiscraft.custom.bypass", List.of()));
        PlayerMock alice = server.addPlayer("Alice");
        alice.addAttachment(plugin, "kreiscraft.custom.bypass", true);

        assertThat(service.isBypassed(alice)).isTrue();
    }

    @Test
    void isBypassed_falseWhenNoPermissionNameOrOp() {
        PlayerMock alice = server.addPlayer("Alice");

        assertThat(service.isBypassed(alice)).isFalse();
    }
}
