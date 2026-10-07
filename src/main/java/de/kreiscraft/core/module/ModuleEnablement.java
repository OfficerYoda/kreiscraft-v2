package de.kreiscraft.core.module;

/**
 * Read-only view of which modules are currently enabled.
 *
 * <p>Published by the composition root (backed by {@link ModuleRegistry}) so client modules such as
 * the Player Settings GUI can omit controls whose owning module is not running, without taking a
 * hard dependency on the module they present.
 */
@FunctionalInterface
public interface ModuleEnablement {

    /** Returns {@code true} when a module with this id is currently enabled. */
    boolean isEnabled(String moduleId);
}
