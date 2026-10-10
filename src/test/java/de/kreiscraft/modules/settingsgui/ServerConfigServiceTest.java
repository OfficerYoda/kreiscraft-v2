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
    void current_readsBundledValues() {
        assertThat(service.current(field("effects", "count"))).isEqualTo(25);
        assertThat(service.current(field("combat", "tag-duration"))).isEqualTo("5s");
        assertThat(service.current(field("combat", "default-display"))).isEqualTo("ACTIONBAR");
        assertThat(service.current(field("combat", "disable-elytra"))).isEqualTo(true);
        assertThat(service.current(field("combat", "excluded-worlds")))
                .isEqualTo(List.of("Lobby"));
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

        ConfigEditResult result = service.edit(count, 100_000);

        assertThat(result).isInstanceOf(ConfigEditResult.Rejected.class);
        assertThat(service.current(count)).isEqualTo(25);
        assertThat(Files.readString(configFile)).contains("count: 25");
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
}
