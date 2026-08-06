package de.kreiscraft.modules.pluginhider;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DefaultPluginHiderServiceTest {

    private static PluginHiderData emptyData(Path dir) throws IOException {
        PluginHiderData data = new PluginHiderData(dir);
        data.load();
        return data;
    }

    private static PluginHiderData dataWith(Path dir, List<String> entries) throws IOException {
        PluginHiderData data = new PluginHiderData(dir);
        data.load();
        data.update(list -> list.addAll(entries));
        data.flushIfDirty();
        // reload so state is round-tripped from disk
        data.load();
        return data;
    }

    // ── seed-from-config tests ─────────────────────────────────────────────

    @Test
    void constructor_seedsFromConfig_whenDataEmpty(@TempDir Path tmp) throws IOException {
        PluginHiderConfig config = new PluginHiderConfig(List.of("help", "spawn"));
        PluginHiderData data = emptyData(tmp);

        DefaultPluginHiderService svc = new DefaultPluginHiderService(config, data);

        assertThat(svc.allowedCommands()).containsExactlyInAnyOrder("help", "spawn");
    }

    @Test
    void constructor_doesNotSeed_whenDataAlreadyPopulated(@TempDir Path tmp) throws IOException {
        PluginHiderConfig config = new PluginHiderConfig(List.of("help", "spawn"));
        PluginHiderData data = dataWith(tmp, List.of("custom"));

        DefaultPluginHiderService svc = new DefaultPluginHiderService(config, data);

        assertThat(svc.allowedCommands()).containsExactly("custom");
        assertThat(svc.allowedCommands()).doesNotContain("help", "spawn");
    }

    // ── isAllowed ─────────────────────────────────────────────────────────

    @Test
    void isAllowed_returnsTrueForAllowedCommand(@TempDir Path tmp) throws IOException {
        PluginHiderData data = dataWith(tmp, List.of("spawn"));
        DefaultPluginHiderService svc = new DefaultPluginHiderService(
                new PluginHiderConfig(List.of()), data);

        assertThat(svc.isAllowed("spawn")).isTrue();
    }

    @Test
    void isAllowed_returnsFalseForUnknownCommand(@TempDir Path tmp) throws IOException {
        PluginHiderData data = emptyData(tmp);
        DefaultPluginHiderService svc = new DefaultPluginHiderService(
                new PluginHiderConfig(List.of()), data);

        assertThat(svc.isAllowed("ban")).isFalse();
    }

    // ── add ───────────────────────────────────────────────────────────────

    @Test
    void add_returnsTrueAndAddsCommand(@TempDir Path tmp) throws IOException {
        PluginHiderData data = emptyData(tmp);
        DefaultPluginHiderService svc = new DefaultPluginHiderService(
                new PluginHiderConfig(List.of()), data);

        boolean result = svc.add("tps");

        assertThat(result).isTrue();
        assertThat(svc.allowedCommands()).contains("tps");
    }

    @Test
    void add_returnsFalseWhenAlreadyPresent(@TempDir Path tmp) throws IOException {
        PluginHiderData data = dataWith(tmp, List.of("tps"));
        DefaultPluginHiderService svc = new DefaultPluginHiderService(
                new PluginHiderConfig(List.of()), data);

        boolean result = svc.add("tps");

        assertThat(result).isFalse();
        // collection should still contain exactly one entry
        assertThat(svc.allowedCommands()).containsOnlyOnce("tps");
    }

    // ── remove ────────────────────────────────────────────────────────────

    @Test
    void remove_returnsTrueAndRemovesCommand(@TempDir Path tmp) throws IOException {
        PluginHiderData data = dataWith(tmp, List.of("spawn"));
        DefaultPluginHiderService svc = new DefaultPluginHiderService(
                new PluginHiderConfig(List.of()), data);

        boolean result = svc.remove("spawn");

        assertThat(result).isTrue();
        assertThat(svc.allowedCommands()).doesNotContain("spawn");
    }

    @Test
    void remove_returnsFalseWhenNotPresent(@TempDir Path tmp) throws IOException {
        PluginHiderData data = emptyData(tmp);
        DefaultPluginHiderService svc = new DefaultPluginHiderService(
                new PluginHiderConfig(List.of()), data);

        boolean result = svc.remove("nonexistent");

        assertThat(result).isFalse();
    }
}
