package de.kreiscraft.modules.status;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StatusDataTest {

    @Test
    void loadEmpty_returnsEmptyMap(@TempDir Path tmp) throws IOException {
        StatusData data = new StatusData(tmp);
        data.load();
        assertThat(data.get()).isEmpty();
    }

    @Test
    void updateAndFlush_persistsData(@TempDir Path tmp) throws IOException {
        UUID id = UUID.randomUUID();
        Status status = new Status("<red>Test</red>", "[Test]", 0xFF0000);

        StatusData data = new StatusData(tmp);
        data.load();
        data.update(m -> m.put(id, status));
        data.flushIfDirty();

        String content = Files.readString(tmp.resolve("data/statuses.yml"));
        assertThat(content).contains(id.toString());
        assertThat(content).contains("prefix:");
    }

    @Test
    void reload_restoresPersistedData(@TempDir Path tmp) throws IOException {
        UUID id = UUID.randomUUID();
        Status status = new Status("<red>Test</red>", "[Suffix]", 0xFF0000);

        StatusData data = new StatusData(tmp);
        data.load();
        data.update(m -> m.put(id, status));
        data.flushIfDirty();

        StatusData data2 = new StatusData(tmp);
        data2.load();
        Status loaded = data2.get().get(id);

        assertThat(loaded).isNotNull();
        assertThat(loaded.prefix()).isEqualTo(status.prefix());
        assertThat(loaded.suffix()).isEqualTo(status.suffix());
        assertThat(loaded.color()).isEqualTo(status.color());
    }

    @Test
    void flushIfDirty_isNoop_whenClean(@TempDir Path tmp) throws IOException {
        StatusData data = new StatusData(tmp);
        data.load();
        data.flushIfDirty();
        assertThat(Files.exists(tmp.resolve("data/statuses.yml"))).isFalse();
    }
}
