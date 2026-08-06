package de.kreiscraft.modules.effects;

import de.kreiscraft.core.module.Module;
import de.kreiscraft.core.module.ModuleContext;
import de.kreiscraft.core.module.ModuleException;
import de.kreiscraft.modules.effects.commands.EffectsCommand;
import java.util.List;
import org.spongepowered.configurate.ConfigurationNode;

public final class EffectsModule implements Module {

    private DefaultEffectsService service;

    @Override
    public String id() {
        return "effects";
    }

    @Override
    public List<String> dependsOn() {
        return List.of();
    }

    @Override
    public void enable(ModuleContext ctx) {
        EffectsConfig config;
        try {
            config = ctx.moduleConfig().get(EffectsConfig.class);
        } catch (org.spongepowered.configurate.serialize.SerializationException e) {
            throw new ModuleException("failed to load config for module " + id(), e);
        }

        this.service = new DefaultEffectsService(config);
        ctx.services().publish(EffectsService.class, service);

        ctx.events().register(new EffectsListener(service));
        ctx.events().register(new EffectsCleanupListener(service));

        ctx.commands()
                .register(
                        EffectsCommand.build(service, ctx.messages()),
                        "Toggle or configure your particle trail");
    }

    @Override
    public void onConfigReload(ConfigurationNode moduleConfig) {
        EffectsConfig config;
        try {
            config = moduleConfig.get(EffectsConfig.class);
        } catch (org.spongepowered.configurate.serialize.SerializationException e) {
            throw new ModuleException("failed to reload config for module " + id(), e);
        }

        if (service != null) {
            service.updateConfig(config);
        }
    }
}
