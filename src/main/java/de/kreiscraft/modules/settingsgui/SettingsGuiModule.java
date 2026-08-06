package de.kreiscraft.modules.settingsgui;

import de.kreiscraft.core.config.ConfigService;
import de.kreiscraft.core.module.Module;
import de.kreiscraft.core.module.ModuleContext;
import de.kreiscraft.core.module.ModuleException;
import de.kreiscraft.modules.settingsgui.commands.SettingsCommand;
import de.kreiscraft.shared.gui.Gui;
import java.io.IOException;
import java.util.List;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.serialize.SerializationException;

public final class SettingsGuiModule implements Module {

    private SettingsGuiConfig config;
    private SettingsService service; // published interface type

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

        ConfigService configService = ctx.services().get(ConfigService.class);
        SettingsData data = new SettingsData(ctx.plugin().getDataFolder().toPath());
        try {
            data.load();
        } catch (IOException e) {
            throw new ModuleException("failed to load settings data for module " + id(), e);
        }
        configService.register(data);

        Gui gui = ctx.services().get(Gui.class);
        // Use concrete type locally so SettingsCommand can call toggleClickThrough
        DefaultSettingsService impl = new DefaultSettingsService(data);
        this.service = impl;
        ctx.services().publish(SettingsService.class, impl);

        ctx.commands().register(
                SettingsCommand.build(impl, ctx.messages(), gui),
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
