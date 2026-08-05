package de.kreiscraft.core.config;

import static org.assertj.core.api.Assertions.assertThat;

import de.kreiscraft.testsupport.MockScheduler;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.serialize.SerializationException;

class DataFileTest {

    static final class CounterData extends DataFile<Map<String, Integer>> {
        CounterData(Path folder) {
            super(folder, "counters.yml");
        }

        @Override
        protected Map<String, Integer> load(ConfigurationNode node) throws SerializationException {
            Map<String, Integer> m = new LinkedHashMap<>();
            node.childrenMap().forEach((k, v) -> m.put(String.valueOf(k), v.getInt(0)));
            return m;
        }

        @Override
        protected void save(Map<String, Integer> state, ConfigurationNode node) throws SerializationException {
            node.set(null);
            for (var e : state.entrySet())
                node.node(e.getKey()).set(e.getValue());
        }

        @Override
        protected Map<String, Integer> empty() {
            return new LinkedHashMap<>();
        }
    }

    @Test
    void update_thenFlush_writesToDisk(@TempDir Path tmp) throws IOException {
        CounterData data = new CounterData(tmp);
        data.load();
        data.update(m -> m.put("apples", 3));
        data.flushIfDirty();
        assertThat(Files.readString(tmp.resolve("counters.yml"))).contains("apples: 3");
    }

    @Test
    void flush_isNoop_whenClean(@TempDir Path tmp) throws IOException {
        CounterData data = new CounterData(tmp);
        data.load();
        data.flushIfDirty();
        assertThat(Files.exists(tmp.resolve("counters.yml"))).isFalse();
    }

    @Test
    void autoSaveTask_flushesDirtyFiles(@TempDir Path tmp) throws IOException {
        CounterData data = new CounterData(tmp);
        data.load();
        data.update(m -> m.put("bananas", 7));

        MockScheduler sched = new MockScheduler();
        AutoSaveTask task = new AutoSaveTask(sched, Duration.ofMinutes(5),
                () -> java.util.List.of(data));
        task.start();
        sched.advance(Duration.ofMinutes(5));

        assertThat(Files.readString(tmp.resolve("counters.yml"))).contains("bananas: 7");
    }
}
