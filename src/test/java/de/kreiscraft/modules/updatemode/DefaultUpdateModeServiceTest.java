package de.kreiscraft.modules.updatemode;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DefaultUpdateModeServiceTest {

    private static final Logger LOG = Logger.getLogger("test");

    private static UpdateModeConfig defaultConfig() {
        return new UpdateModeConfig("kreiscraft.updatemode.bypass", List.of());
    }

    @Test
    void setActive_true_persists(@TempDir Path tmp) throws IOException {
        UpdateModeData data = new UpdateModeData(tmp);
        data.load();
        DefaultUpdateModeService svc = new DefaultUpdateModeService(data, defaultConfig(), LOG);

        svc.setActive(true);

        // Verify in-memory state
        assertThat(svc.isActive()).isTrue();

        // Verify on-disk persistence
        assertThat(Files.exists(tmp.resolve("data/update-mode.yml"))).isTrue();
        assertThat(Files.readString(tmp.resolve("data/update-mode.yml"))).contains("active: true");
    }

    @Test
    void setActive_false_persists(@TempDir Path tmp) throws IOException {
        UpdateModeData data = new UpdateModeData(tmp);
        data.load();
        DefaultUpdateModeService svc = new DefaultUpdateModeService(data, defaultConfig(), LOG);

        svc.setActive(true);
        svc.setActive(false);

        assertThat(svc.isActive()).isFalse();
        assertThat(Files.readString(tmp.resolve("data/update-mode.yml"))).contains("active: false");
    }

    @Test
    void stateLoaded_fromDisk(@TempDir Path tmp) throws IOException {
        // First service writes active=true
        UpdateModeData data1 = new UpdateModeData(tmp);
        data1.load();
        DefaultUpdateModeService svc1 = new DefaultUpdateModeService(data1, defaultConfig(), LOG);
        svc1.setActive(true);

        // Second service loads from same directory
        UpdateModeData data2 = new UpdateModeData(tmp);
        data2.load();
        DefaultUpdateModeService svc2 = new DefaultUpdateModeService(data2, defaultConfig(), LOG);

        assertThat(svc2.isActive()).isTrue();
    }

    @Test
    void initialState_isFalse(@TempDir Path tmp) throws IOException {
        UpdateModeData data = new UpdateModeData(tmp);
        data.load();
        DefaultUpdateModeService svc = new DefaultUpdateModeService(data, defaultConfig(), LOG);

        assertThat(svc.isActive()).isFalse();
    }

    @Test
    void updateConfig_replacesConfig(@TempDir Path tmp) throws IOException {
        UpdateModeData data = new UpdateModeData(tmp);
        data.load();
        DefaultUpdateModeService svc = new DefaultUpdateModeService(data, defaultConfig(), LOG);

        UpdateModeConfig newCfg = new UpdateModeConfig("kreiscraft.other.bypass", List.of("alice"));
        svc.updateConfig(newCfg);

        assertThat(svc.config().bypassPermission()).isEqualTo("kreiscraft.other.bypass");
        assertThat(svc.config().bypassNames()).containsExactly("alice");
    }
}
