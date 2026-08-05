package de.kreiscraft.core.module;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;
import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.ConfigurationNode;

class ModuleRegistryTest {

    static class Recording implements Module {
        final String id;
        final List<String> deps;
        final List<String> log;
        boolean throwOnEnable;

        Recording(String id, List<String> deps, List<String> log) {
            this.id = id;
            this.deps = deps;
            this.log = log;
        }

        @Override
        public String id() {
            return id;
        }

        @Override
        public List<String> dependsOn() {
            return deps;
        }

        @Override
        public void enable(ModuleContext ctx) {
            if (throwOnEnable)
                throw new RuntimeException("boom");
            log.add("enable:" + id);
        }

        @Override
        public void disable() {
            log.add("disable:" + id);
        }
    }

    private ConfigurationNode configWithEnabled(String... ids) {
        ConfigurationNode root = CommentedConfigurationNode.root();
        try {
            for (String id : ids) {
                root.node("modules", id, "enabled").set(true);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return root;
    }

    @Test
    void enableAll_respectsDependencyOrder() throws Exception {
        List<String> log = new ArrayList<>();
        Recording a = new Recording("a", List.of("b"), log);
        Recording b = new Recording("b", List.of(), log);

        ConfigurationNode cfg = configWithEnabled("a", "b");
        ModuleRegistry reg = new ModuleRegistry(m -> null, cfg, Logger.getAnonymousLogger());
        reg.register(a);
        reg.register(b);

        StartupSummary summary = reg.enableAll();

        assertThat(log).containsExactly("enable:b", "enable:a");
        assertThat(summary.enabled()).isEqualTo(2);
        assertThat(summary.failed()).isEqualTo(0);
    }

    @Test
    void disabledModule_isSkipped() throws Exception {
        List<String> log = new ArrayList<>();
        Recording a = new Recording("a", List.of(), log);

        ConfigurationNode cfg = CommentedConfigurationNode.root(); // nothing enabled
        ModuleRegistry reg = new ModuleRegistry(m -> null, cfg, Logger.getAnonymousLogger());
        reg.register(a);

        StartupSummary summary = reg.enableAll();

        assertThat(log).isEmpty();
        assertThat(summary.enabled()).isEqualTo(0);
        assertThat(summary.disabled()).isEqualTo(1);
    }

    @Test
    void failingModule_doesntAbortOthers() throws Exception {
        List<String> log = new ArrayList<>();
        Recording a = new Recording("a", List.of(), log);
        Recording b = new Recording("b", List.of(), log);
        a.throwOnEnable = true;

        ConfigurationNode cfg = configWithEnabled("a", "b");
        ModuleRegistry reg = new ModuleRegistry(m -> null, cfg, Logger.getAnonymousLogger());
        reg.register(a);
        reg.register(b);

        StartupSummary summary = reg.enableAll();

        assertThat(summary.enabled()).isEqualTo(1);
        assertThat(summary.failed()).isEqualTo(1);
        assertThat(summary.failedIds()).containsExactly("a");
        assertThat(log).contains("enable:b");
    }

    @Test
    void cycle_throws() {
        Recording a = new Recording("a", List.of("b"), new ArrayList<>());
        Recording b = new Recording("b", List.of("a"), new ArrayList<>());

        ConfigurationNode cfg = configWithEnabled("a", "b");
        ModuleRegistry reg = new ModuleRegistry(m -> null, cfg, Logger.getAnonymousLogger());
        reg.register(a);
        reg.register(b);

        assertThatThrownBy(reg::enableAll).isInstanceOf(ModuleException.class);
    }

    @Test
    void unknownDependency_throws() {
        Recording a = new Recording("a", List.of("ghost"), new ArrayList<>());

        ConfigurationNode cfg = configWithEnabled("a");
        ModuleRegistry reg = new ModuleRegistry(m -> null, cfg, Logger.getAnonymousLogger());
        reg.register(a);

        assertThatThrownBy(reg::enableAll).isInstanceOf(ModuleException.class);
    }

    @Test
    void disableAll_reverseOrder() throws Exception {
        List<String> log = new ArrayList<>();
        Recording a = new Recording("a", List.of("b"), log);
        Recording b = new Recording("b", List.of(), log);

        ConfigurationNode cfg = configWithEnabled("a", "b");
        ModuleRegistry reg = new ModuleRegistry(m -> null, cfg, Logger.getAnonymousLogger());
        reg.register(a);
        reg.register(b);
        reg.enableAll();
        log.clear();
        reg.disableAll();

        assertThat(log).containsExactly("disable:a", "disable:b");
    }
}
