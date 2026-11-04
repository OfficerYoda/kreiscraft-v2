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
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

/**
 * Verifies the GUI-facing read/write boundary against the real validated {@link YamlConfigService}:
 * reads return the live values and every accepted edit is immediately durable.
 */
class ServerConfigServiceTest {

    private Path configFile;
    private DefaultServerConfigService service;

    @BeforeEach
    void setUp(@TempDir Path tmp) throws IOException {
        this.configFile = tmp.resolve("config.yml");
        this.service =
                new DefaultServerConfigService(new YamlConfigService(tmp, getClass().getClassLoader()));
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

    private void assertApplied(ConfigField field, Object value) {
        assertThat(service.edit(field, value)).isInstanceOf(ConfigEditResult.Applied.class);
    }
}
