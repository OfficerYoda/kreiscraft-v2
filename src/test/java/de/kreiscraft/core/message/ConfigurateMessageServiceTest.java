package de.kreiscraft.core.message;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

class ConfigurateMessageServiceTest {

    private ServerMock server;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void copiesLangFilesFromJarOnFirstRun(@TempDir Path tmp) throws IOException {
        new ConfigurateMessageService(tmp, getClass().getClassLoader(), "de", server);
        assertThat(Files.exists(tmp.resolve("lang/de.yml"))).isTrue();
        assertThat(Files.exists(tmp.resolve("lang/en.yml"))).isTrue();
    }

    @Test
    void render_resolvesKey(@TempDir Path tmp) throws IOException {
        ConfigurateMessageService svc =
                new ConfigurateMessageService(tmp, getClass().getClassLoader(), "en", server);
        String plain =
                PlainTextComponentSerializer.plainText()
                        .serialize(svc.render("core.reload.success"));
        assertThat(plain).contains("Configuration reloaded");
    }

    @Test
    void render_resolvesPlaceholders(@TempDir Path tmp) throws IOException {
        ConfigurateMessageService svc =
                new ConfigurateMessageService(tmp, getClass().getClassLoader(), "en", server);
        String plain =
                PlainTextComponentSerializer.plainText()
                        .serialize(
                                svc.render(
                                        "core.startup.summary",
                                        "enabled",
                                        5,
                                        "disabled",
                                        2,
                                        "failed",
                                        0));
        assertThat(plain).contains("5 modules enabled").contains("2 disabled").contains("0 failed");
    }

    @Test
    void missingKey_returnsPlaceholder(@TempDir Path tmp) throws IOException {
        ConfigurateMessageService svc =
                new ConfigurateMessageService(tmp, getClass().getClassLoader(), "en", server);
        String plain =
                PlainTextComponentSerializer.plainText()
                        .serialize(svc.render("does.not.exist"));
        assertThat(plain).contains("[?does.not.exist]");
    }
}
