package de.kreiscraft.modules.status;

import de.kreiscraft.core.config.ConfigService;
import de.kreiscraft.core.module.Module;
import de.kreiscraft.core.module.ModuleContext;
import de.kreiscraft.core.module.ModuleException;
import de.kreiscraft.modules.status.commands.StatusCommand;
import java.io.IOException;
import java.util.List;
import org.spongepowered.configurate.ConfigurationNode;

public final class StatusModule implements Module {

    private StatusConfig config;
    private DefaultStatusService service;

    @Override
    public String id() {
        return "status";
    }

    @Override
    public List<String> dependsOn() {
        return List.of();
    }

    @Override
    public void enable(ModuleContext ctx) {
        try {
            this.config = ctx.moduleConfig().get(StatusConfig.class);
        } catch (org.spongepowered.configurate.serialize.SerializationException e) {
            throw new ModuleException("failed to load config for module " + id(), e);
        }

        StatusData data = new StatusData(ctx.plugin().getDataFolder().toPath());
        try {
            data.load();
        } catch (IOException e) {
            throw new ModuleException("failed to load status data", e);
        }
        ctx.services().get(ConfigService.class).register(data);

        this.service = new DefaultStatusService(
                ctx.plugin().getServer().getPluginManager(), data);
        ctx.services().publish(StatusService.class, service);

        ctx.events().register(new StatusListener(service, ctx.plugin().getServer()));

        ctx.commands().register(
                StatusCommand.build(service, ctx.messages()),
                "Set your in-game status prefix or suffix");
    }

    @Override
    public void onConfigReload(ConfigurationNode moduleConfig) {
        // StatusConfig is empty — nothing to reload
    }
}
