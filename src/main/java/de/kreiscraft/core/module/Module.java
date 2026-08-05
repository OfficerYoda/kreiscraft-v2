package de.kreiscraft.core.module;

import java.util.List;
import org.spongepowered.configurate.ConfigurationNode;

public interface Module {

    String id();

    default List<String> dependsOn() {
        return List.of();
    }

    void enable(ModuleContext ctx);

    default void disable() {}

    default void onConfigReload(ConfigurationNode moduleConfig) {}
}
