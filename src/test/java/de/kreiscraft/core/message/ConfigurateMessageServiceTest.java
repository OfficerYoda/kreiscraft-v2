package de.kreiscraft.core.message;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
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
