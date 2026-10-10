package de.kreiscraft.modules.reboot;

import de.kreiscraft.core.module.Module;
import de.kreiscraft.core.module.ModuleContext;
import de.kreiscraft.platform.PaperPlatform;
import de.kreiscraft.modules.reboot.commands.RebootCommand;
import java.util.List;
import org.spongepowered.configurate.ConfigurationNode;

public final class RebootModule implements Module {

    private RebootConfig config;
    private DefaultRebootService service;

    @Override
    public String id() {
        return "reboot";
    }

    @Override
    public List<String> dependsOn() {
        return List.of();
    }

    @Override
    public void enable(ModuleContext ctx) {
        try {
            this.config = ctx.moduleConfig().get(RebootConfig.class);
        } catch (org.spongepowered.configurate.serialize.SerializationException e) {
            throw new de.kreiscraft.core.module.ModuleException("failed to load config for module " + id(), e);
        }
        PaperPlatform platform = ctx.services().get(PaperPlatform.class);
        this.service = new DefaultRebootService(ctx.scheduler(), ctx.messages(), platform, config);
        ctx.services().publish(RebootService.class, service);
        ctx.events().register(new RebootListener());
        ctx.commands().register(
                RebootCommand.build(service, ctx.messages(), config),
                "Trigger or cancel a server restart",
                "restart");
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
            this.config = moduleConfig.get(RebootConfig.class);
        } catch (org.spongepowered.configurate.serialize.SerializationException e) {
            throw new de.kreiscraft.core.module.ModuleException("failed to reload config for module " + id(), e);
        }
        if (service != null) {
            service.updateConfig(config);
        }
    }
}
