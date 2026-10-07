package de.kreiscraft.modules.settingsgui;

import org.bukkit.entity.Player;

/**
 * Navigation surface for the settings and Server Configuration GUIs.
 *
 * <p>Extends the Player Settings {@link SettingsGui.Navigator} so one collaborator can build and open
 * every page, and adds the dedicated {@code /settings admin} entry point.
 */
public interface SettingsNavigator extends SettingsGui.Navigator {

    /** Opens the Server Configuration category list, starting a fresh editing session. */
    void openAdmin(Player player);
}
