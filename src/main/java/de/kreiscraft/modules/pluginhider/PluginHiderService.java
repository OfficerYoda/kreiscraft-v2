package de.kreiscraft.modules.pluginhider;

import java.util.Collection;

public interface PluginHiderService {

    /** Returns the current allowlist (config defaults + runtime additions). */
    Collection<String> allowedCommands();

    /**
     * Adds a command to the runtime allowlist.
     *
     * @return {@code true} if the command was added, {@code false} if it was already present
     */
    boolean add(String command);

    /**
     * Removes a command from the runtime allowlist.
     *
     * @return {@code true} if the command was removed, {@code false} if it was not present
     */
    boolean remove(String command);

    /**
     * Returns {@code true} if the given command is in the allowlist.
     */
    boolean isAllowed(String command);
}
