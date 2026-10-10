package de.kreiscraft.modules.settingsgui;

import static org.assertj.core.api.Assertions.assertThat;

import de.kreiscraft.core.config.ConfigField;
import de.kreiscraft.core.config.ConfigFieldType;
import de.kreiscraft.core.config.ConfigLifecycle;
import de.kreiscraft.core.config.ConfigValidators;
import de.kreiscraft.core.config.ServerConfigSchema;
import de.kreiscraft.core.message.ConfigurateMessageService;
import de.kreiscraft.core.message.MessageService;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * Verifies the close-time messages use the shipped language content after automatic reload and for
 * restart-only edits (including every module enable flag).
 */
class ServerConfigCloseReporterTest {

    private ServerMock server;
    private PlayerMock player;
    private MessageService messages;

    @BeforeEach
    void setUp(@TempDir Path tmp) throws IOException {
        server = MockBukkit.mock();
        player = server.addPlayer("Admin");
        messages =
                new ConfigurateMessageService(tmp, getClass().getClassLoader(), "en", server);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private static ConfigField hotReloadField() {
        return new ConfigField(
                List.of("modules", "combat", "tag-duration"),
                ConfigFieldType.DURATION,
                ConfigValidators.duration(),
                true,
                ConfigLifecycle.HOT_RELOAD);
    }

    private static String miniMessage(Component component) {
        return MiniMessage.miniMessage().serialize(component);
    }

    @Test
    void hotReloadEdit_confirmsAutomaticApplicationWithoutAction() {
        ServerConfigSession session = new ServerConfigSession();
        session.recordApplied(hotReloadField());

        ServerConfigCloseReporter.report(messages, player, session, true);

        Component received = player.nextComponentMessage();
        assertThat(received).isNotNull();
        assertThat(miniMessage(received)).containsIgnoringCase("automatically").doesNotContain("run_command");
        assertThat(player.nextComponentMessage())
                .as("no restart warning for a hot-reload-only session")
                .isNull();
    }

    @Test
    void moduleEnableEdit_warnsRestartWithoutReloadAction() {
        ServerConfigSession session = new ServerConfigSession();
        session.recordApplied(ServerConfigSchema.moduleEnabled("combat"));

        ServerConfigCloseReporter.report(messages, player, session, false);

        Component received = player.nextComponentMessage();
        assertThat(received).isNotNull();
        String text = miniMessage(received);
        assertThat(text).containsIgnoringCase("restart");
        assertThat(text).as("module lifecycle is never described as hot-applied").doesNotContain("run_command");
        assertThat(player.nextComponentMessage())
                .as("no reload prompt for a restart-only session")
                .isNull();
    }

    @Test
    void mixedSession_receivesReloadAndRestartPrompts() {
        ServerConfigSession session = new ServerConfigSession();
        session.recordApplied(hotReloadField());
        session.recordApplied(ServerConfigSchema.moduleEnabled("combat"));

        ServerConfigCloseReporter.report(messages, player, session, true);

        assertThat(player.nextComponentMessage()).isNotNull();
        assertThat(player.nextComponentMessage()).isNotNull();
    }

    @Test
    void emptySession_sendsNothing() {
        ServerConfigCloseReporter.report(messages, player, new ServerConfigSession(), false);

        assertThat(player.nextComponentMessage()).isNull();
    }
}
