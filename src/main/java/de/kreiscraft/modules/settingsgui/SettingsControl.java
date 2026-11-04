package de.kreiscraft.modules.settingsgui;

import de.kreiscraft.core.module.ModuleEnablement;

/**
 * A control shown in the Player Settings GUI.
 *
 * <p>Each control belongs to the module whose feature it configures. A control is available only
 * when its owning module is enabled, so the GUI never presents a preference for a feature the
 * server does not offer. {@link #LANGUAGE} is owned by no module (language is part of the
 * always-available preferences boundary) and is therefore always available.
 */
public enum SettingsControl {
    /** The player's output language, owned by the always-available preferences boundary. */
    LANGUAGE(null),
    /** Click-through, consumed by the {@code clickthrough} module. */
    CLICK_THROUGH("clickthrough"),
    /** Combat status display, consumed by the {@code combat} module. */
    COMBAT_DISPLAY("combat"),
    /** Particle-trail enablement, consumed by the {@code effects} module. */
    EFFECTS("effects");

    private final String moduleId;

    SettingsControl(String moduleId) {
        this.moduleId = moduleId;
    }

    /** The owning module id, or {@code null} when the control is always available. */
    public String moduleId() {
        return moduleId;
    }

    /** Whether this control should be shown given the set of currently enabled modules. */
    public boolean isAvailable(ModuleEnablement modules) {
        return moduleId == null || modules.isEnabled(moduleId);
    }
}
