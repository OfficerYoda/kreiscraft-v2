package de.kreiscraft.core.config;

/**
 * How a change to an editable Server Configuration field takes effect.
 *
 * <p>Modeled as a single value rather than two independent booleans so that a field can never be
 * declared both hot-reloadable and restart-required. Exposing both accessors keeps the metadata
 * readable from the point of view of the GUI: see {@link #hotReloadable()} and {@link
 * #restartRequired()}.
 */
public enum ConfigLifecycle {
    /** The value is read again the next time {@code /kreiscraft reload} runs. */
    HOT_RELOAD("server-config.lifecycle.hot-reload"),
    /** The value only takes effect after the server process restarts. */
    RESTART_REQUIRED("server-config.lifecycle.restart-required");

    private final String messageKey;

    ConfigLifecycle(String messageKey) {
        this.messageKey = messageKey;
    }

    /** Whether {@code /kreiscraft reload} applies this field without a restart. */
    public boolean hotReloadable() {
        return this == HOT_RELOAD;
    }

    /** Whether a full server restart is required before the value is applied. */
    public boolean restartRequired() {
        return this == RESTART_REQUIRED;
    }

    /** The language key describing this lifecycle to an operator. */
    public String messageKey() {
        return messageKey;
    }
}
