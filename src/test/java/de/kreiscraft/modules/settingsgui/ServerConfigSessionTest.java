package de.kreiscraft.modules.settingsgui;

import static org.assertj.core.api.Assertions.assertThat;

import de.kreiscraft.core.config.ConfigField;
import de.kreiscraft.core.config.ConfigFieldType;
import de.kreiscraft.core.config.ConfigLifecycle;
import de.kreiscraft.core.config.ConfigValidators;
import de.kreiscraft.core.config.ServerConfigSchema;
import java.util.List;
import org.junit.jupiter.api.Test;

class ServerConfigSessionTest {

    private static ConfigField hotReloadField() {
        return new ConfigField(
                List.of("modules", "combat", "tag-duration"),
                ConfigFieldType.DURATION,
                ConfigValidators.duration(),
                true,
                ConfigLifecycle.HOT_RELOAD);
    }

    @Test
    void emptySession_reportsNothing() {
        ServerConfigSession session = new ServerConfigSession();

        assertThat(session.isEmpty()).isTrue();
        assertThat(session.hasHotReload()).isFalse();
        assertThat(session.hasRestartRequired()).isFalse();
    }

    @Test
    void hotReloadEdit_isRecordedAsHotReload() {
        ServerConfigSession session = new ServerConfigSession();

        session.recordApplied(hotReloadField());

        assertThat(session.hasHotReload()).isTrue();
        assertThat(session.hotReloadCount()).isEqualTo(1);
        assertThat(session.hasRestartRequired()).isFalse();
    }

    @Test
    void moduleEnableEdit_isRecordedAsRestartOnly() {
        ServerConfigSession session = new ServerConfigSession();

        session.recordApplied(ServerConfigSchema.moduleEnabled("combat"));

        assertThat(session.hasRestartRequired()).isTrue();
        assertThat(session.restartCount()).isEqualTo(1);
        assertThat(session.hasHotReload()).as("module enablement is never hot-applied").isFalse();
    }

    @Test
    void repeatedEditToSameField_countsOnce() {
        ServerConfigSession session = new ServerConfigSession();

        session.recordApplied(hotReloadField());
        session.recordApplied(hotReloadField());

        assertThat(session.hotReloadCount()).isEqualTo(1);
    }
}
