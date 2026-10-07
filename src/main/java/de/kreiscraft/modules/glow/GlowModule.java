package de.kreiscraft.modules.glow;

import de.kreiscraft.core.module.Module;
import de.kreiscraft.core.module.ModuleContext;
import de.kreiscraft.core.module.ModuleException;
import de.kreiscraft.modules.glow.commands.GlowCommand;
import de.kreiscraft.platform.PaperPlatform;
import de.kreiscraft.shared.preferences.PlayerPreferencesService;
import java.util.List;
import org.spongepowered.configurate.ConfigurationNode;

public final class GlowModule implements Module {

    private DefaultGlowService service;

    @Override
    public String id() {
        return "glow";
    }

    @Override
    public List<String> dependsOn() {
        return List.of();
    }

    @Override
    public void enable(ModuleContext ctx) {
        try {
            ctx.moduleConfig().get(GlowConfig.class);
        } catch (org.spongepowered.configurate.serialize.SerializationException e) {
            throw new ModuleException("failed to load config for module " + id(), e);
        }
        PaperPlatform platform = ctx.services().get(PaperPlatform.class);
        PlayerPreferencesService preferences =
                ctx.services().get(PlayerPreferencesService.class);
        this.service = new DefaultGlowService(platform.server(), preferences);
        ctx.services().publish(GlowService.class, service);
        ctx.events().register(new GlowListener(service));
        ctx.commands()
                .register(
                        GlowCommand.build(service, ctx.messages(), platform.server()),
                        "Toggle your glow effect");
    }

    @Override
    public void disable() {
        if (service != null) {
            service.disableAll();
        }
    }

    @Override
    public void onConfigReload(ConfigurationNode moduleConfig) {
        try {
            moduleConfig.get(GlowConfig.class);
        } catch (org.spongepowered.configurate.serialize.SerializationException e) {
            throw new ModuleException("failed to reload config for module " + id(), e);
        }
    }
}
