package de.kreiscraft.modules.updatemode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.kreiscraft.core.config.YamlConfigService;
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
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

class DefaultUpdateModeServiceTest {

    private static final Logger LOG = Logger.getLogger("test");
    private static final String BYPASS_PERMISSION = "kreiscraft.updatemode.bypass";

    @TempDir
    Path tmp;

    private ServerMock server;
    private PluginMock plugin;
    private MessageService messages;
    private YamlConfigService config;
    private DefaultUpdateModeService service;

    private static UpdateModeConfig defaultConfig() {
        return new UpdateModeConfig(BYPASS_PERMISSION, List.of());
    }

    private DefaultUpdateModeService serviceWith(YamlConfigService configService) {
        return new DefaultUpdateModeService(
                configService, defaultConfig(), new PaperPlatform(plugin, server), messages, LOG);
    }

    @BeforeEach
    void setUp() throws IOException {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        messages = mock(MessageService.class);
        when(messages.render("update-mode.kick-message")).thenReturn(Component.text("kick"));

        config = new YamlConfigService(tmp, getClass().getClassLoader());
        service = serviceWith(config);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    // --- persistence -------------------------------------------------------

    @Test
    void initialState_isFalse() {
        assertThat(service.isActive()).isFalse();
    }

    @Test
    void setActive_true_persistsToServerConfiguration() throws IOException {
        service.setActive(true);

        assertThat(service.isActive()).isTrue();
        ConfigurationNode onDisk = load(tmp.resolve("config.yml"));
        assertThat(onDisk.node("modules", "update-mode", "active").getBoolean()).isTrue();
        // ADR-10: operator-owned state is not written to the retired data file.
        assertThat(Files.exists(tmp.resolve("data/update-mode.yml"))).isFalse();
    }

    @Test
    void setActive_false_persists() throws IOException {
        service.setActive(true);
        service.setActive(false);

        assertThat(service.isActive()).isFalse();
        ConfigurationNode onDisk = load(tmp.resolve("config.yml"));
        assertThat(onDisk.node("modules", "update-mode", "active").getBoolean()).isFalse();
    }

    @Test
    void stateLoaded_fromServerConfiguration() throws IOException {
        service.setActive(true);

        YamlConfigService reopened =
                new YamlConfigService(tmp, getClass().getClassLoader());
        DefaultUpdateModeService restarted = serviceWith(reopened);

        assertThat(restarted.isActive()).isTrue();
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

    private static ConfigurationNode load(Path file) throws IOException {
        return YamlConfigurationLoader.builder().path(file).build().load();
    }
}
