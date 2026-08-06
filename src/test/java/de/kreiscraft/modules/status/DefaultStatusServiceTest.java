package de.kreiscraft.modules.status;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.io.IOException;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.bukkit.plugin.PluginManager;

@ExtendWith(MockitoExtension.class)
class DefaultStatusServiceTest {

    @Mock
    private PluginManager pluginManager;

    @TempDir
    Path tmp;

    private StatusData data;
    private DefaultStatusService service;

    @BeforeEach
    void setUp() throws IOException {
        data = new StatusData(tmp);
        data.load();
        service = new DefaultStatusService(pluginManager, data);
    }

    @Test
    void get_returnsDefaults_whenNotSet() {
        UUID id = UUID.randomUUID();
        Status status = service.get(id);
        assertThat(status).isEqualTo(Status.defaults());
    }

    @Test
    void setPrefix_updatesStatus_andFiresEvent() {
        UUID id = UUID.randomUUID();
        service.setPrefix(id, "<bold>Admin</bold>");
        assertThat(service.get(id).prefix()).isEqualTo("<bold>Admin</bold>");
        verify(pluginManager).callEvent(any(StatusChangedEvent.class));
    }

    @Test
    void setPrefix_whenIdentical_doesNotFireEvent() {
        UUID id = UUID.randomUUID();
        String defaultPrefix = Status.defaults().prefix();
        service.setPrefix(id, defaultPrefix);
        verify(pluginManager, never()).callEvent(any(StatusChangedEvent.class));
    }

    @Test
    void setColor_whenIdentical_doesNotFireEvent() {
        UUID id = UUID.randomUUID();
        int defaultColor = Status.defaults().color();
        service.setColor(id, defaultColor);
        verify(pluginManager, never()).callEvent(any(StatusChangedEvent.class));
    }

    @Test
    void setColor_withDifferentValue_updatesAndFiresEvent() {
        UUID id = UUID.randomUUID();
        service.setColor(id, 0xFF0000);
        assertThat(service.get(id).color()).isEqualTo(0xFF0000);
        verify(pluginManager).callEvent(any(StatusChangedEvent.class));
    }

    @Test
    void setSuffix_updatesStatus_andFiresEvent() {
        UUID id = UUID.randomUUID();
        service.setSuffix(id, " [VIP]");
        assertThat(service.get(id).suffix()).isEqualTo(" [VIP]");
        verify(pluginManager).callEvent(any(StatusChangedEvent.class));
    }

    @Test
    void defaults_returnsDefaultStatus() {
        assertThat(service.defaults()).isEqualTo(Status.defaults());
    }
}
