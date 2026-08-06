package de.kreiscraft.modules.startsequence;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StartSequenceDataTest {

    @Test
    void load_whenFileAbsent_returnsFalse(@TempDir Path tmp) throws IOException {
        StartSequenceData data = new StartSequenceData(tmp);
        data.load();

        assertThat(data.hasStarted()).isFalse();
    }

    @Test
    void setStarted_andFlush_persistsToFile(@TempDir Path tmp) throws IOException {
        StartSequenceData data = new StartSequenceData(tmp);
        data.load();

        data.setStarted(true);
        data.flushIfDirty();

        assertThat(Files.readString(tmp.resolve("data/start-sequence.yml")))
                .contains("has-started: true");
    }

    @Test
    void roundTrip_truePersistedAndReloaded(@TempDir Path tmp) throws IOException {
        StartSequenceData writer = new StartSequenceData(tmp);
        writer.load();
        writer.setStarted(true);
        writer.flushIfDirty();

        StartSequenceData reader = new StartSequenceData(tmp);
        reader.load();

        assertThat(reader.hasStarted()).isTrue();
    }

    @Test
    void roundTrip_falseAfterReset(@TempDir Path tmp) throws IOException {
        StartSequenceData writer = new StartSequenceData(tmp);
        writer.load();
        writer.setStarted(true);
        writer.flushIfDirty();

        writer.setStarted(false);
        writer.flushIfDirty();

        StartSequenceData reader = new StartSequenceData(tmp);
        reader.load();

        assertThat(reader.hasStarted()).isFalse();
    }
}
