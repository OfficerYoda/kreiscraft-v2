package de.kreiscraft.modules.clickthrough;

import de.kreiscraft.core.module.Module;
import de.kreiscraft.core.module.ModuleContext;
import de.kreiscraft.core.module.ModuleException;
import de.kreiscraft.modules.settingsgui.SettingsService;
import java.util.List;
import org.spongepowered.configurate.ConfigurationNode;

public final class ClickthroughModule implements Module {

    @Override
    public String id() {
        return "clickthrough";
    }

    @Override
    public List<String> dependsOn() {
        return List.of("settings-gui");
    }

    @Override
    public void enable(ModuleContext ctx) {
        try {
            ctx.moduleConfig().get(ClickthroughConfig.class);
        } catch (org.spongepowered.configurate.serialize.SerializationException e) {
            throw new ModuleException("failed to load config for module " + id(), e);
        }

        SettingsService settingsService = ctx.services().get(SettingsService.class);
        ctx.events().register(new ClickthroughListener(settingsService, ctx.scheduler()));
    }

    @Override
    public void onConfigReload(ConfigurationNode moduleConfig) {
        try {
            moduleConfig.get(ClickthroughConfig.class);
        } catch (org.spongepowered.configurate.serialize.SerializationException e) {
            throw new ModuleException("failed to reload config for module " + id(), e);
        }
    }
}
