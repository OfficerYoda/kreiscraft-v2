package de.kreiscraft.modules.updatemode;

import de.kreiscraft.core.config.ConfigService;
import de.kreiscraft.core.module.Module;
import de.kreiscraft.core.module.ModuleContext;
import de.kreiscraft.core.module.ModuleException;
import de.kreiscraft.modules.updatemode.commands.UpdateModeCommand;
import de.kreiscraft.platform.PaperPlatform;
import java.io.IOException;
import java.util.List;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.serialize.SerializationException;

public final class UpdateModeModule implements Module {

    private UpdateModeConfig config;
    private DefaultUpdateModeService service;

    @Override
    public String id() {
        return "update-mode";
    }

    @Override
    public List<String> dependsOn() {
        return List.of();
    }

    @Override
    public void enable(ModuleContext ctx) {
        try {
            this.config = ctx.moduleConfig().get(UpdateModeConfig.class);
        } catch (SerializationException e) {
            throw new ModuleException("failed to load config for module " + id(), e);
        }

        UpdateModeData data = new UpdateModeData(ctx.plugin().getDataFolder().toPath());
        try {
            data.load();
        } catch (IOException e) {
            throw new ModuleException("failed to load data for module " + id(), e);
        }

        this.service = new DefaultUpdateModeService(data, config, ctx.plugin().getLogger());
        ctx.services().publish(UpdateModeService.class, service);

        PaperPlatform platform = ctx.services().get(PaperPlatform.class);
        ctx.events().register(new UpdateModeListener(service, platform, ctx.messages()));

        ctx.commands().register(
                UpdateModeCommand.build(service, ctx.messages()),
                "Toggle maintenance mode");

        ctx.services().get(ConfigService.class).register(data);
    }

    @Override
    public void onConfigReload(ConfigurationNode moduleConfig) {
        try {
            this.config = moduleConfig.get(UpdateModeConfig.class);
        } catch (SerializationException e) {
            throw new ModuleException("failed to reload config for module " + id(), e);
        }
        if (service != null) {
            service.updateConfig(config);
        }
    }
}
