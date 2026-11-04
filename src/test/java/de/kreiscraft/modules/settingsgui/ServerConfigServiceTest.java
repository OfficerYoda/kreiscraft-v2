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
