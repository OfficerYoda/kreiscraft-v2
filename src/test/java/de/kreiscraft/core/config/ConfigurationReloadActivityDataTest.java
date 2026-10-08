package de.kreiscraft.core.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.serialize.SerializationException;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

/**
 * Operator-owned state lives in {@code config.yml}, while player and gameplay records stay in
 * {@code data/}. A {@code /kreiscraft reload} re-reads only {@code config.yml}, so it must never
 * re-read or overwrite Activity Data held in memory or on disk.
 */
class ConfigurationReloadActivityDataTest {

    /** Minimal Activity Data file: an append-only counter that stands in for a real record type. */
    static final class CounterData extends DataFile<int[]> {

        CounterData(Path dataFolder) {
            super(dataFolder, "data/counter.yml");
        }

        @Override
        protected int[] load(ConfigurationNode node) {
            return new int[] {node.node("value").getInt(0)};
        }

        @Override
        protected void save(int[] state, ConfigurationNode node) throws SerializationException {
            node.node("value").set(state[0]);
        }

        @Override
        protected int[] empty() {
            return new int[] {0};
        }
    }

    @Test
    void reload_doesNotOverwriteInMemoryActivityData(@TempDir Path tmp) throws IOException {
        YamlConfigService config = new YamlConfigService(tmp, getClass().getClassLoader());
        CounterData activity = new CounterData(tmp);
        activity.load();
        config.register(activity);
        activity.update(state -> state[0] = 7);

        config.reload();

        assertThat(activity.get()[0])
                .as("config reload must not reset unsaved Activity Data")
                .isEqualTo(7);
    }

    @Test
    void reload_doesNotRewriteOrDeleteActivityDataFile(@TempDir Path tmp) throws IOException {
        CounterData writer = new CounterData(tmp);
        writer.load();
        writer.update(state -> state[0] = 3);
        writer.flushIfDirty();
        Path activityFile = tmp.resolve("data/counter.yml");
        String before = Files.readString(activityFile);

        YamlConfigService config = new YamlConfigService(tmp, getClass().getClassLoader());
        config.reload();

        assertThat(Files.readString(activityFile)).isEqualTo(before);
    }

    @Test
    void operatorStateEdits_doNotCreateRetiredActivityDataFiles(@TempDir Path tmp)
            throws IOException {
        YamlConfigService config = new YamlConfigService(tmp, getClass().getClassLoader());

        config.applyEdit(ServerConfigSchema.updateModeActive(), true);
        config.applyEdit(ServerConfigSchema.startSequenceStarted(), true);

        assertThat(Files.exists(tmp.resolve("data/update-mode.yml"))).isFalse();
        assertThat(Files.exists(tmp.resolve("data/start-sequence.yml"))).isFalse();
        assertThat(Files.exists(tmp.resolve("data/spawn.yml"))).isFalse();
    }

    @Test
    void operatorStateEdits_surviveAReloadFromDisk(@TempDir Path tmp) throws IOException {
        YamlConfigService config = new YamlConfigService(tmp, getClass().getClassLoader());
        config.applyEdit(ServerConfigSchema.updateModeActive(), true);
        config.applyEdit(ServerConfigSchema.startSequenceStarted(), true);

        YamlConfigService reopened = new YamlConfigService(tmp, getClass().getClassLoader());

        assertThat(reopened.module("update-mode").node("active").getBoolean()).isTrue();
        assertThat(reopened.module("start-sequence").node("has-started").getBoolean()).isTrue();
        assertThat(YamlConfigurationLoader.builder()
                .path(tmp.resolve("config.yml"))
                .build()
                .load()
                .node("modules", "update-mode", "active")
                .getBoolean())
                .isTrue();
    }

    @Test
    void retiredOperatorStateDataFiles_areGone() {
        for (String className : new String[] {
                "de.kreiscraft.modules.spawn.SpawnData",
                "de.kreiscraft.modules.updatemode.UpdateModeData",
                "de.kreiscraft.modules.startsequence.StartSequenceData"
        }) {
            assertThatThrownBy(() -> Class.forName(className))
                    .as(className + " should have been retired")
                    .isInstanceOf(ClassNotFoundException.class);
        }
    }

    @Test
    void retainedPlayerAndGameplayRecords_remainActivityData() {
        assertThat(DataFile.class).isAssignableFrom(de.kreiscraft.modules.jail.JailData.class);
        assertThat(DataFile.class).isAssignableFrom(de.kreiscraft.modules.bedrock.BedrockData.class);
        assertThat(DataFile.class)
                .isAssignableFrom(
                        de.kreiscraft.modules.bedrock.BedrockAuthorizationData.class);
        assertThat(DataFile.class)
                .isAssignableFrom(de.kreiscraft.modules.anticheat.HackLogData.class);
        assertThat(DataFile.class).isAssignableFrom(de.kreiscraft.modules.status.StatusData.class);
        assertThat(DataFile.class)
                .isAssignableFrom(de.kreiscraft.modules.positions.PositionsData.class);
        assertThat(DataFile.class)
                .isAssignableFrom(de.kreiscraft.shared.preferences.PlayerPreferencesData.class);
    }
}
