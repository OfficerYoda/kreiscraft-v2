package de.kreiscraft.modules.pluginhider;

import java.util.Collection;
import java.util.Collections;

public final class DefaultPluginHiderService implements PluginHiderService {

    private final PluginHiderData data;

    /**
     * Constructs the service. The caller must have already called {@link PluginHiderData#load()}
     * before constructing the service. If the data file was empty, the runtime allowlist is
     * seeded from the config's default list.
     */
    public DefaultPluginHiderService(PluginHiderConfig config, PluginHiderData data) {
        this.data = data;
        if (data.get().isEmpty() && !config.defaultAllowedCommands().isEmpty()) {
            data.update(list -> list.addAll(config.defaultAllowedCommands()));
        }
    }

    @Override
    public Collection<String> allowedCommands() {
        return Collections.unmodifiableList(data.get());
    }

    @Override
    public boolean add(String command) {
        if (data.get().contains(command)) {
            return false;
        }
        data.update(list -> list.add(command));
        return true;
    }

    @Override
    public boolean remove(String command) {
        if (!data.get().contains(command)) {
            return false;
        }
        data.update(list -> list.remove(command));
        return true;
    }

    @Override
    public boolean isAllowed(String command) {
        return data.get().contains(command);
    }
}
