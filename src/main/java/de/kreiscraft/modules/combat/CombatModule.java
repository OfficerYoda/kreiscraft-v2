package de.kreiscraft.modules.combat;

import de.kreiscraft.core.module.Module;
import de.kreiscraft.core.module.ModuleContext;
import de.kreiscraft.core.module.ModuleException;
import de.kreiscraft.modules.combat.commands.CombatCommand;
import de.kreiscraft.modules.settingsgui.SettingsService;
import de.kreiscraft.platform.PaperPlatform;
import java.util.List;
import org.spongepowered.configurate.ConfigurationNode;

public final class CombatModule implements Module {

    private CombatConfig config;
    private DefaultCombatService service;

    @Override
    public String id() {
        return "combat";
    }

    @Override
    public List<String> dependsOn() {
        return List.of("settings-gui");
    }

    @Override
    public void enable(ModuleContext ctx) {
        try {
            this.config = ctx.moduleConfig().get(CombatConfig.class);
        } catch (org.spongepowered.configurate.serialize.SerializationException e) {
            throw new ModuleException("failed to load config for module " + id(), e);
        }

        SettingsService settingsService = ctx.services().get(SettingsService.class);
        PaperPlatform platform = ctx.services().get(PaperPlatform.class);

        this.service = new DefaultCombatService(
                ctx.scheduler(), ctx.messages(), settingsService, platform, config);
        ctx.services().publish(CombatService.class, service);

        // Supplier for live config reads — captures the module field, not the value
        ctx.events().register(new CombatListener(service, () -> this.config));
        ctx.events().register(new ElytraDenyListener(service, ctx.messages(), () -> this.config));

        ctx.commands().register(
                CombatCommand.build(service, ctx.messages()),
                "Manually tag a player as in combat");
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
            this.config = moduleConfig.get(CombatConfig.class);
        } catch (org.spongepowered.configurate.serialize.SerializationException e) {
            throw new ModuleException("failed to reload config for module " + id(), e);
        }
        if (service != null) {
            service.updateConfig(this.config);
        }
    }
}
