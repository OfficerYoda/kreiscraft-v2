package de.kreiscraft.modules.effects;

import de.kreiscraft.core.module.Module;
import de.kreiscraft.core.module.ModuleContext;
import de.kreiscraft.core.module.ModuleException;
import de.kreiscraft.modules.effects.commands.EffectsCommand;
import de.kreiscraft.platform.PaperPlatform;
import de.kreiscraft.shared.preferences.PlayerPreferencesService;
import java.util.List;
import org.spongepowered.configurate.ConfigurationNode;

public final class EffectsModule implements Module {

    /**
     * Module default applied when a player has no stored effects preference. Effects are opt-out
     * once the module is enabled, so a brand-new player gets the trail.
     */
    static final boolean EFFECTS_DEFAULT_ENABLED = true;

    private DefaultEffectsService service;
    private EffectsEmitter emitter;

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

        PlayerPreferencesService preferences = ctx.services().get(PlayerPreferencesService.class);
        PaperPlatform platform = ctx.services().get(PaperPlatform.class);

        this.service = new DefaultEffectsService(preferences, config, EFFECTS_DEFAULT_ENABLED);
        ctx.services().publish(EffectsService.class, service);

        this.emitter =
                new EffectsEmitter(
                        service, platform.server(), ctx.scheduler(), config.schedule());

        ctx.commands()
                .register(
                        EffectsCommand.build(service, ctx.messages()),
                        "Report or toggle your particle trail");
    }

    @Override
    public void disable() {
        if (emitter != null) {
            emitter.disable();
            emitter = null;
        }
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
        if (emitter != null) {
            // The schedule is hot-reloadable: replace the running task so the new period applies.
            emitter.reschedule(config.schedule());
        }
    }
}
