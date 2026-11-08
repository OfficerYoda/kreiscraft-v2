package de.kreiscraft.modules.worldmgmt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

import de.kreiscraft.core.config.ConfigField;
import de.kreiscraft.core.config.ConfigService;
import de.kreiscraft.core.config.YamlConfigService;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Logger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DefaultDimensionAccessServiceTest {

    private Path configFile;
    private YamlConfigService config;
    private DefaultDimensionAccessService service;

    @BeforeEach
    void setUp(@TempDir Path tmp) throws IOException {
        configFile = tmp.resolve("config.yml");
        config = new YamlConfigService(tmp, getClass().getClassLoader());
        service = new DefaultDimensionAccessService(config, Logger.getLogger("test"));
    }

    @Test
    void bundledConfig_allowsBothDimensions() {
        assertThat(service.isAllowed(Dimension.NETHER)).isTrue();
        assertThat(service.isAllowed(Dimension.END)).isTrue();
    }

    @Test
    void missingKeys_defaultToAllowed() throws Exception {
        config.root().node("modules", "world-mgmt", "allow-nether").set(null);
        config.root().node("modules", "world-mgmt", "allow-end").set(null);

        assertThat(service.isAllowed(Dimension.NETHER)).isTrue();
        assertThat(service.isAllowed(Dimension.END)).isTrue();
    }

    @Test
    void setAllowed_persistsAndAppliesImmediately() throws IOException {
        assertThat(service.setAllowed(Dimension.NETHER, false)).isTrue();

        assertThat(service.isAllowed(Dimension.NETHER)).isFalse();
        assertThat(service.isAllowed(Dimension.END)).isTrue();
        assertThat(Files.readString(configFile)).contains("allow-nether: false");
    }

    @Test
    void setAllowed_canReEnableADimension() throws IOException {
        service.setAllowed(Dimension.END, false);

        assertThat(service.setAllowed(Dimension.END, true)).isTrue();
        assertThat(service.isAllowed(Dimension.END)).isTrue();
        assertThat(Files.readString(configFile)).contains("allow-end: true");
    }

    @Test
    void setAllowed_whenPersistenceFails_returnsFalseWithoutChangingTheLiveValue() {
        ConfigService failing = mock(ConfigService.class);
        doThrow(new IllegalStateException("disk full"))
                .when(failing)
                .applyEdit(any(ConfigField.class), any());
        DefaultDimensionAccessService failingService =
                new DefaultDimensionAccessService(failing, Logger.getLogger("test"));

        assertThat(failingService.setAllowed(Dimension.NETHER, false)).isFalse();
    }
}
