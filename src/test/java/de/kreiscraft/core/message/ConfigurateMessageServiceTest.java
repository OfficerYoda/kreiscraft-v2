package de.kreiscraft.core.message;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

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

    private static String plain(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
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

    @Test
    void renderFor_usesRecipientLanguageOverServerDefault(@TempDir Path tmp) throws IOException {
        ConfigurateMessageService svc = new ConfigurateMessageService(
                tmp, getClass().getClassLoader(), "de", server, recipient -> Optional.of("en"));
        Player alice = server.addPlayer("Alice");

        assertThat(plain(svc.renderFor(alice, "core.reload.success")))
                .as("recipient preference must take precedence over the server language")
                .contains("Configuration reloaded");
        assertThat(plain(svc.render("core.reload.success")))
                .as("server-language rendering must stay in the server language")
                .contains("Konfiguration neu geladen");
    }

    @Test
    void renderFor_withoutStoredPreference_usesServerLanguage(@TempDir Path tmp) throws IOException {
        ConfigurateMessageService svc = new ConfigurateMessageService(
                tmp, getClass().getClassLoader(), "de", server, recipient -> Optional.empty());
        Player alice = server.addPlayer("Alice");

        assertThat(plain(svc.renderFor(alice, "core.reload.success")))
                .contains("Konfiguration neu geladen");
    }

    @Test
    void renderFor_unknownStoredLocale_fallsBackToServerLanguage(@TempDir Path tmp)
            throws IOException {
        ConfigurateMessageService svc = new ConfigurateMessageService(
                tmp, getClass().getClassLoader(), "de", server, recipient -> Optional.of("fr"));
        Player alice = server.addPlayer("Alice");

        assertThat(plain(svc.renderFor(alice, "core.reload.success")))
                .as("an unknown stored locale must fall back rather than blank the output")
                .contains("Konfiguration neu geladen");
    }

    @Test
    void send_rendersInRecipientLanguage(@TempDir Path tmp) throws IOException {
        ConfigurateMessageService svc = new ConfigurateMessageService(
                tmp, getClass().getClassLoader(), "de", server, recipient -> Optional.of("en"));
        PlayerMock alice = server.addPlayer("Alice");

        svc.send(alice, "core.reload.success");

        assertThat(alice.nextMessage()).contains("Configuration reloaded");
    }

    @Test
    void broadcast_rendersEachRecipientInTheirOwnLanguage(@TempDir Path tmp) throws IOException {
        ConfigurateMessageService svc = new ConfigurateMessageService(
                tmp,
                getClass().getClassLoader(),
                "de",
                server,
                recipient -> "Alice".equals(recipient.getName()) ? Optional.of("en") : Optional.empty());
        PlayerMock alice = server.addPlayer("Alice");
        PlayerMock bob = server.addPlayer("Bob");

        svc.broadcast("core.reload.success");

        assertThat(alice.nextMessage())
                .as("Alice stored English and must receive English")
                .contains("Configuration reloaded");
        assertThat(bob.nextMessage())
                .as("Bob stored no preference and must receive the server language")
                .contains("Konfiguration neu geladen");
    }

    @Test
    void localeFor_usesRecipientLanguageAndFallsBackToServerLanguage(@TempDir Path tmp)
            throws IOException {
        ConfigurateMessageService svc = new ConfigurateMessageService(
                tmp,
                getClass().getClassLoader(),
                "de",
                server,
                recipient -> "Alice".equals(recipient.getName()) ? Optional.of("en") : Optional.empty());
        Player alice = server.addPlayer("Alice");
        Player bob = server.addPlayer("Bob");

        assertThat(svc.localeFor(alice))
                .as("a stored English preference must format dates in English")
                .isEqualTo(java.util.Locale.ENGLISH);
        assertThat(svc.localeFor(bob))
                .as("a recipient without a preference must use the server language")
                .isEqualTo(java.util.Locale.GERMAN);
    }

    @Test
    void render_supportsComponentPlaceholdersWithoutStringifyingThem(@TempDir Path tmp)
            throws IOException {
        ConfigurateMessageService svc =
                new ConfigurateMessageService(tmp, getClass().getClassLoader(), "en", server);

        Component relative = Component.text("2 days ago");
        String plain =
                plain(
                        svc.render(
                                "admin.lastonline",
                                "player",
                                "Bob",
                                "when",
                                "2026-01-15 12:34:56",
                                "relative",
                                relative));

        assertThat(plain).contains("Bob").contains("2026-01-15 12:34:56").contains("2 days ago");
    }

    @Test
    void en_and_de_have_identical_key_sets() throws IOException {
        URL deUrl = getClass().getClassLoader().getResource("lang/de.yml");
        URL enUrl = getClass().getClassLoader().getResource("lang/en.yml");
        assertThat(deUrl).as("lang/de.yml must be on classpath").isNotNull();
        assertThat(enUrl).as("lang/en.yml must be on classpath").isNotNull();
        ConfigurationNode deRoot =
                YamlConfigurationLoader.builder().url(deUrl).build().load();
        ConfigurationNode enRoot =
                YamlConfigurationLoader.builder().url(enUrl).build().load();
        Set<String> deKeys = flattenKeys(deRoot, "");
        Set<String> enKeys = flattenKeys(enRoot, "");
        assertThat(deKeys)
                .as("de.yml and en.yml must have identical leaf-key sets")
                .containsExactlyInAnyOrderElementsOf(enKeys);
    }

    private static Set<String> flattenKeys(ConfigurationNode node, String prefix) {
        Set<String> result = new LinkedHashSet<>();
        if (node.isMap()) {
            for (Map.Entry<Object, ? extends ConfigurationNode> entry : node.childrenMap().entrySet()) {
                String childPath =
                        prefix.isEmpty()
                                ? entry.getKey().toString()
                                : prefix + "." + entry.getKey();
                result.addAll(flattenKeys(entry.getValue(), childPath));
            }
        } else {
            result.add(prefix);
        }
        return result;
    }
}
