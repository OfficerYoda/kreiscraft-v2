package de.kreiscraft.core.module;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.spongepowered.configurate.ConfigurationNode;

public final class ModuleRegistry {

    private final Function<Module, ModuleContext> contextFactory;
    private final ConfigurationNode configRoot;
    private final Logger logger;
    private final Map<String, Module> allModules = new HashMap<>();
    private final List<Module> enabledInOrder = new ArrayList<>();

    public ModuleRegistry(Function<Module, ModuleContext> contextFactory,
            ConfigurationNode configRoot, Logger logger) {
        this.contextFactory = contextFactory;
        this.configRoot = configRoot;
        this.logger = logger;
    }

    public void register(Module module) {
        if (allModules.putIfAbsent(module.id(), module) != null) {
            throw new ModuleException("duplicate module id: " + module.id());
        }
    }

    /** Returns an unmodifiable view of all registered module ids (enabled and disabled). */
    public Set<String> registeredIds() {
        return Collections.unmodifiableSet(allModules.keySet());
    }

    public StartupSummary enableAll() {
        List<Module> enabledCandidates = new ArrayList<>();
        int disabledCount = 0;
        for (Module m : allModules.values()) {
            boolean on = configRoot.node("modules", m.id(), "enabled").getBoolean(false);
            if (on) {
                enabledCandidates.add(m);
            } else {
                disabledCount++;
            }
        }

        List<Module> sorted = topologicalSort(enabledCandidates);

        int failedCount = 0;
        List<String> failedIds = new ArrayList<>();
        for (Module m : sorted) {
            try {
                m.enable(contextFactory.apply(m));
                enabledInOrder.add(m);
            } catch (Exception e) {
                failedCount++;
                failedIds.add(m.id());
                logger.log(Level.SEVERE, "failed to enable module " + m.id(), e);
            }
        }
        return new StartupSummary(enabledInOrder.size(), disabledCount, failedCount, failedIds);
    }

    public void disableAll() {
        for (int i = enabledInOrder.size() - 1; i >= 0; i--) {
            Module m = enabledInOrder.get(i);
            try {
                m.disable();
            } catch (Exception e) {
                logger.log(Level.WARNING, "error disabling module " + m.id(), e);
            }
        }
        enabledInOrder.clear();
    }

    public void reloadConfigs(ConfigurationNode newRoot) {
        for (Module m : enabledInOrder) {
            try {
                m.onConfigReload(newRoot.node("modules", m.id()));
            } catch (Exception e) {
                logger.log(Level.WARNING, "onConfigReload failed for " + m.id(), e);
            }
        }
    }

    private List<Module> topologicalSort(List<Module> input) {
        Set<String> enabledIds = new HashSet<>();
        for (Module m : input) {
            enabledIds.add(m.id());
        }
        for (Module m : input) {
            for (String dep : m.dependsOn()) {
                if (!enabledIds.contains(dep)) {
                    throw new ModuleException("module " + m.id()
                            + " depends on '" + dep + "' which is not enabled or does not exist");
                }
            }
        }
        List<Module> sorted = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        Set<String> visiting = new HashSet<>();
        Map<String, Module> byId = new HashMap<>();
        for (Module m : input) {
            byId.put(m.id(), m);
        }
        for (Module m : input) {
            visit(m, byId, visited, visiting, sorted);
        }
        return sorted;
    }

    private void visit(Module m, Map<String, Module> byId,
            Set<String> visited, Set<String> visiting, List<Module> out) {
        if (visited.contains(m.id())) {
            return;
        }
        if (!visiting.add(m.id())) {
            throw new ModuleException("cycle detected involving module " + m.id());
        }
        for (String dep : m.dependsOn()) {
            visit(byId.get(dep), byId, visited, visiting, out);
        }
        visiting.remove(m.id());
        visited.add(m.id());
        out.add(m);
    }
}
