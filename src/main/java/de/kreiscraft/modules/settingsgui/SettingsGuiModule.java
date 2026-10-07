package de.kreiscraft.modules.settingsgui;

import de.kreiscraft.core.module.Module;
import de.kreiscraft.core.module.ModuleContext;
import de.kreiscraft.core.module.ModuleException;
import de.kreiscraft.modules.settingsgui.commands.SettingsCommand;
import de.kreiscraft.shared.gui.Gui;
import de.kreiscraft.shared.preferences.PlayerPreferencesService;
import java.util.List;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.serialize.SerializationException;

public final class SettingsGuiModule implements Module {

    private SettingsGuiConfig config;

    @Override
    public String id() {
        return "settings-gui";
    }

    @Override
    public List<String> dependsOn() {
        return List.of();
    }

    @Override
    public void enable(ModuleContext ctx) {
        try {
            this.config = ctx.moduleConfig().get(SettingsGuiConfig.class);
        } catch (SerializationException e) {
            throw new ModuleException("failed to load config for module " + id(), e);
        }

        PlayerPreferencesService preferences = ctx.services().get(PlayerPreferencesService.class);
        Gui gui = ctx.services().get(Gui.class);
        // The GUI only ever mutates preferences through the shared boundary.
        DefaultSettingsService settings = new DefaultSettingsService(preferences);

        ctx.commands().register(
                SettingsCommand.build(settings, ctx.messages(), gui),
                "Open the per-player settings GUI");
    }

    @Override
    public void disable() {
        // no persistent resources to release
    }

    @Override
    public void onConfigReload(ConfigurationNode moduleConfig) {
        try {
            this.config = moduleConfig.get(SettingsGuiConfig.class);
        } catch (SerializationException e) {
            throw new ModuleException("failed to reload config for module " + id(), e);
        }
    }
}
