package de.kreiscraft.modules.startsequence;

import de.kreiscraft.core.config.ConfigService;
import de.kreiscraft.core.module.Module;
import de.kreiscraft.core.module.ModuleContext;
import de.kreiscraft.core.module.ModuleException;
import de.kreiscraft.modules.startsequence.commands.StartCommand;
import de.kreiscraft.platform.PaperPlatform;
import java.io.IOException;
import java.util.List;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.serialize.SerializationException;

public final class StartSequenceModule implements Module {

    private StartSequenceConfig config;
    private DefaultStartSequenceService service;

    @Override
    public String id() {
        return "start-sequence";
    }

    @Override
    public List<String> dependsOn() {
        return List.of();
    }

    @Override
    public void enable(ModuleContext ctx) {
        try {
            this.config = ctx.moduleConfig().get(StartSequenceConfig.class);
        } catch (SerializationException e) {
            throw new ModuleException("Failed to load config for module " + id(), e);
        }
        if (this.config == null) {
            this.config = new StartSequenceConfig(null, null, null, null, null, null, null);
        }

        StartSequenceData data = new StartSequenceData(ctx.plugin().getDataFolder().toPath());
        try {
            data.load();
        } catch (IOException e) {
            throw new ModuleException("Failed to load data for module " + id(), e);
        }

        ctx.services().get(ConfigService.class).register(data);

        PaperPlatform platform = ctx.services().get(PaperPlatform.class);
        this.service = new DefaultStartSequenceService(
                ctx.scheduler(), config, ctx.messages(), platform, data);

        ctx.services().publish(StartSequenceService.class, service);

        ctx.commands().register(
                StartCommand.build(service, ctx.messages()),
                "Trigger the season start sequence");
    }

    @Override
    public void disable() {
        if (service != null) {
            service.shutdown();
        }
    }

    @Override
    public void onConfigReload(ConfigurationNode moduleConfig) {
        try {
            StartSequenceConfig reloaded = moduleConfig.get(StartSequenceConfig.class);
            if (reloaded != null && service != null) {
                service.updateConfig(reloaded);
            }
        } catch (SerializationException e) {
            throw new ModuleException("Failed to reload config for module " + id(), e);
        }
    }
}
