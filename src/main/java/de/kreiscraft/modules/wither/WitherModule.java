package de.kreiscraft.modules.wither;

import de.kreiscraft.core.module.Module;
import de.kreiscraft.core.module.ModuleContext;
import de.kreiscraft.core.module.ModuleException;
import java.util.List;
import org.spongepowered.configurate.ConfigurationNode;

public final class WitherModule implements Module {

    private WitherConfig config;
    private WitherListener listener;

    @Override
    public String id() {
        return "wither";
    }

    @Override
    public List<String> dependsOn() {
        return List.of();
    }

    @Override
    public void enable(ModuleContext ctx) {
        try {
            config = ctx.moduleConfig().get(WitherConfig.class);
        } catch (org.spongepowered.configurate.serialize.SerializationException e) {
            throw new ModuleException("failed to load config for module " + id(), e);
        }
        this.listener = new WitherListener(() -> config, ctx.messages(), ctx.scheduler(),
                () -> ctx.services().find(de.kreiscraft.modules.shatteredcrown.EncounterController.class).orElse(null));
        ctx.events().register(listener);
    }

    @Override
    public void disable() {
        if (listener != null) {
            listener.shutdown();
        }
    }

    @Override
    public void onConfigReload(ConfigurationNode moduleConfig) {
        try {
            config = moduleConfig.get(WitherConfig.class);
        } catch (org.spongepowered.configurate.serialize.SerializationException e) {
            throw new ModuleException("failed to reload config for module " + id(), e);
        }
    }
}
