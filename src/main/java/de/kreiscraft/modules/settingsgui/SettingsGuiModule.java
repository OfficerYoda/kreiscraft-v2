package de.kreiscraft.modules.settingsgui;

import de.kreiscraft.core.config.ConfigService;
import de.kreiscraft.core.module.Module;
import de.kreiscraft.core.module.ModuleContext;
import de.kreiscraft.core.module.ModuleEnablement;
import de.kreiscraft.core.module.ModuleException;
import de.kreiscraft.modules.settingsgui.commands.SettingsCommand;
import de.kreiscraft.shared.gui.Gui;
import de.kreiscraft.shared.preferences.PlayerLanguage;
import de.kreiscraft.shared.preferences.PlayerPreferencesService;
import java.util.List;
import org.bukkit.Particle;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.serialize.SerializationException;

public final class SettingsGuiModule implements Module {

    private static final Particle DEFAULT_EFFECTS_PARTICLE = Particle.HAPPY_VILLAGER;

    private SettingsGuiConfig config;

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

        PlayerPreferencesService preferences = ctx.services().get(PlayerPreferencesService.class);
        Gui gui = ctx.services().get(Gui.class);
        ModuleEnablement modules = ctx.services().get(ModuleEnablement.class);

        // The GUI only ever reads and mutates preferences through the shared boundary. The language
        // and particle fallbacks mirror what the owning features would use so the GUI shows a
        // concrete current value even before the player chooses.
        PlayerLanguage defaultLanguage = serverLanguage(ctx);
        Particle defaultParticle = defaultEffectsParticle(ctx);
        SettingsService settings =
                new DefaultSettingsService(preferences, defaultLanguage, defaultParticle);

        ctx.commands()
                .register(
                        SettingsCommand.build(settings, modules, ctx.messages(), gui),
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

    private static PlayerLanguage serverLanguage(ModuleContext ctx) {
        return ctx.services()
                .find(ConfigService.class)
                .map(config -> config.root().node("core", "language").getString("de"))
                .flatMap(PlayerLanguage::fromCode)
                .orElse(PlayerLanguage.DE);
    }

    private static Particle defaultEffectsParticle(ModuleContext ctx) {
        return ctx.services()
                .find(ConfigService.class)
                .map(config -> {
                    try {
                        Particle particle = config.root()
                                .node("modules", "effects", "default-particle")
                                .get(Particle.class);
                        return particle != null ? particle : DEFAULT_EFFECTS_PARTICLE;
                    } catch (SerializationException e) {
                        return DEFAULT_EFFECTS_PARTICLE;
                    }
                })
                .orElse(DEFAULT_EFFECTS_PARTICLE);
    }
}
