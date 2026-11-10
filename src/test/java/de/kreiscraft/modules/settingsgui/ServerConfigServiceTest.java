package de.kreiscraft.modules.settingsgui;

import static org.assertj.core.api.Assertions.assertThat;

import de.kreiscraft.core.config.ConfigEditResult;
import de.kreiscraft.core.config.ConfigField;
import de.kreiscraft.core.config.ConfigFieldType;
import de.kreiscraft.core.config.YamlConfigService;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

/**
 * Verifies the GUI-facing read/write boundary against the real validated {@link YamlConfigService}:
 * reads return the live values and every accepted edit is immediately durable.
 */
class ServerConfigServiceTest {

    private Path configFile;
    private DefaultServerConfigService service;
    private YamlConfigService config;

    @BeforeEach
    void setUp(@TempDir Path tmp) throws IOException {
        this.configFile = tmp.resolve("config.yml");
        this.config = new YamlConfigService(tmp, getClass().getClassLoader());
        this.service = new DefaultServerConfigService(config);
    }

    private ConfigField field(String module, String key) {
        return service.schema().find(List.of("modules", module, key)).orElseThrow();
    }

    @Test
    void current_readsExplicitlyConfiguredValues() {
        ConfigField count = field("effects", "count");
        ConfigField tagDuration = field("combat", "tag-duration");
        ConfigField defaultDisplay = field("combat", "default-display");
        ConfigField disableElytra = field("combat", "disable-elytra");
        ConfigField excludedWorlds = field("combat", "excluded-worlds");
        assertApplied(count, 23);
        assertApplied(tagDuration, "7s");
        assertApplied(defaultDisplay, "TITLE");
        assertApplied(disableElytra, false);
        assertApplied(excludedWorlds, List.of("TestWorld"));

        assertThat(service.current(count)).isEqualTo(23);
        assertThat(service.current(tagDuration)).isEqualTo("7s");
        assertThat(service.current(defaultDisplay)).isEqualTo("TITLE");
        assertThat(service.current(disableElytra)).isEqualTo(false);
        assertThat(service.current(excludedWorlds)).isEqualTo(List.of("TestWorld"));
    }

    @Test
    void edit_persistsImmediatelyToDisk() throws IOException {
        ConfigField disableElytra = field("combat", "disable-elytra");

        ConfigEditResult result = service.edit(disableElytra, false);

        assertThat(result).isInstanceOf(ConfigEditResult.Applied.class);
        assertThat(service.current(disableElytra)).isEqualTo(false);
        assertThat(Files.readString(configFile)).contains("disable-elytra: false");
        assertThat(YamlConfigurationLoader.builder()
                .path(configFile)
                .build()
                .load()
                .node("modules", "combat", "disable-elytra")
                .getBoolean())
                        .isFalse();
    }

    @Test
    void apiOriginsPersistAndRejectedEditsPreserveLiveAndDurableConfig() throws Exception {
        var origins = field("api", "cors-origins");
        assertApplied(origins, List.of("https://kreiscraft.de", "http://localhost:3000"));
        config.reload();
        assertThat(service.current(origins)).isEqualTo(List.of("https://kreiscraft.de", "http://localhost:3000"));
        String before = Files.readString(configFile);
        for (Object value : List.of(List.of("*"), List.of("https://example.com/path"), List.of())) {
            assertThat(service.edit(origins, value)).isInstanceOf(ConfigEditResult.Rejected.class);
            assertThat(Files.readString(configFile)).isEqualTo(before);
        }
        assertThat(service.edit(field("api", "port"), 65536)).isInstanceOf(ConfigEditResult.Rejected.class);
        assertThat(service.edit(field("api", "heartbeat-interval"), "0s"))
                .isInstanceOf(ConfigEditResult.Rejected.class);
        var cookie = service.schema().find(List.of("modules", "api", "session-cookie", "name")).orElseThrow();
        assertThat(service.edit(cookie, "bad;name")).isInstanceOf(ConfigEditResult.Rejected.class);
        assertThat(Files.readString(configFile)).isEqualTo(before);
        assertThat(service.schema().find(List.of("modules", "api", "admin-api-key"))).isEmpty();
    }

    @Test
    void missingApiValuesDisplayRuntimeDefaultsWithoutWritingThem() throws Exception {
        config.root().node("modules", "api").set(null);
        String before = Files.readString(configFile);
        assertThat(service.current(field("api", "host"))).isEqualTo("0.0.0.0");
        assertThat(service.current(field("api", "port"))).isEqualTo(8080);
        assertThat(service.current(field("api", "cors-origins"))).isEqualTo(List.of("https://kreiscraft.de"));
        assertThat(service
                .current(service.schema().find(List.of("modules", "api", "session-cookie", "name")).orElseThrow()))
                        .isEqualTo("website_user_uuid");
        assertThat(Files.readString(configFile)).isEqualTo(before);
    }

    @Test
    void rejectedEdit_reportsReasonAndLeavesConfiguration() throws IOException {
        ConfigField count = field("effects", "count");
        Object originalValue = service.current(count);
        String originalConfig = Files.readString(configFile);

        ConfigEditResult result = service.edit(count, 100_000);

        assertThat(result).isInstanceOf(ConfigEditResult.Rejected.class);
        assertThat(service.current(count)).isEqualTo(originalValue);
        assertThat(Files.readString(configFile)).isEqualTo(originalConfig);
    }

    @Test
    void schema_exposesRepresentativeTypedFields() {
        assertThat(service.schema().guiExposed())
                .extracting(ConfigField::type)
                .contains(
                        ConfigFieldType.BOOLEAN,
                        ConfigFieldType.INTEGER,
                        ConfigFieldType.DOUBLE,
                        ConfigFieldType.DURATION,
                        ConfigFieldType.ENUM,
                        ConfigFieldType.LOCATION,
                        ConfigFieldType.STRING,
                        ConfigFieldType.STRING_LIST);
    }

    @ParameterizedTest
    @CsvSource({
            "mspt-green-max, 51", "mspt-yellow-max, 39",
            "tps-green-min, 14", "tps-yellow-min, 20",
            "ping-green-max, 121", "ping-yellow-max, 49",
            "mspt-empty, 50", "mspt-full, 0", "mspt-full, -1",
            "tps-green-min, 21", "ping-green-max, 2.5"
    })
    void invalidPerformanceBarEdit_isRejectedWithoutChangingLiveOrDurableConfig(String key, double value)
            throws IOException {
        ConfigField field = service.schema().find(List.of("modules", "admin", "tps-bar", key)).orElseThrow();
        Object before = service.current(field);
        String fileBefore = Files.readString(configFile);

        assertThat(service.edit(field, value)).isInstanceOf(ConfigEditResult.Rejected.class);
        assertThat(service.current(field)).isEqualTo(before);
        assertThat(Files.readString(configFile)).isEqualTo(fileBefore);
    }

    @Test
    void missingWorldMgmtAccessKeys_showEnabledDefaultsWithoutMutatingConfiguration() throws Exception {
        config.root().node("modules", "world-mgmt", "allow-nether").set(null);
        config.root().node("modules", "world-mgmt", "allow-end").set(null);
        String before = Files.readString(configFile);

        assertThat(service.current(field("world-mgmt", "allow-nether"))).isEqualTo(true);
        assertThat(service.current(field("world-mgmt", "allow-end"))).isEqualTo(true);
        assertThat(Files.readString(configFile)).isEqualTo(before);
    }

    @Test
    void missingPerformanceBarSection_showsRuntimeDefaultsWithoutMutatingConfiguration() throws Exception {
        var section = config.root().node("modules", "admin", "tps-bar");
        section.set(null);
        String before = Files.readString(configFile);
        java.util.Map<String, Number> defaults = java.util.Map.of(
                "mspt-green-max", 40.0, "mspt-yellow-max", 50.0,
                "tps-green-min", 19.0, "tps-yellow-min", 15.0,
                "ping-green-max", 50, "ping-yellow-max", 120,
                "mspt-empty", 0.0, "mspt-full", 50.0);
        for (var entry : defaults.entrySet()) {
            ConfigField field =
                    service.schema().find(List.of("modules", "admin", "tps-bar", entry.getKey())).orElseThrow();
            assertThat(service.current(field)).isEqualTo(entry.getValue());
        }
        assertThat(section.virtual()).isTrue();
        assertThat(Files.readString(configFile)).isEqualTo(before);
    }

    private void assertApplied(ConfigField field, Object value) {
        assertThat(service.edit(field, value)).isInstanceOf(ConfigEditResult.Applied.class);
    }
}
