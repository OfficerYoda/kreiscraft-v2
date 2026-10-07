package de.kreiscraft.modules.settingsgui;

import de.kreiscraft.core.config.ConfigField;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * One player's Server Configuration editing session.
 *
 * <p>The session records which declared fields the player successfully changed, classified by the
 * field's lifecycle. Closing the GUI uses this to report exactly what the player did: which edits
 * are hot-reloadable and which require a restart. A module enable flag is always restart-only, so it
 * is never counted as a hot-applied change.
 *
 * <p>Rejected and failed edits are not recorded: they changed nothing.
 */
public final class ServerConfigSession {

    private final Set<String> hotReload = new LinkedHashSet<>();
    private final Set<String> restartRequired = new LinkedHashSet<>();

    /** Records a successfully applied edit, classified by the field's lifecycle. */
    public void recordApplied(ConfigField field) {
        String key = String.join(".", field.path().stream().map(String::valueOf).toList());
        if (field.restartRequired()) {
            restartRequired.add(key);
        } else {
            hotReload.add(key);
        }
    }

    /** How many distinct hot-reloadable fields were changed. */
    public int hotReloadCount() {
        return hotReload.size();
    }

    /** How many distinct restart-only fields were changed. */
    public int restartCount() {
        return restartRequired.size();
    }

    /** Whether any hot-reloadable field was changed. */
    public boolean hasHotReload() {
        return !hotReload.isEmpty();
    }

    /** Whether any restart-only field was changed. */
    public boolean hasRestartRequired() {
        return !restartRequired.isEmpty();
    }

    /** Whether no successful edit happened. */
    public boolean isEmpty() {
        return hotReload.isEmpty() && restartRequired.isEmpty();
    }
}
