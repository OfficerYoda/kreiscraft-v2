package de.kreiscraft.modules.combat;

import de.kreiscraft.core.module.Module;
import de.kreiscraft.core.module.ModuleContext;
import de.kreiscraft.core.module.ModuleException;
import de.kreiscraft.modules.combat.commands.CombatCommand;
import de.kreiscraft.platform.PaperPlatform;
import de.kreiscraft.shared.preferences.PlayerPreferencesService;
import java.util.List;
import org.spongepowered.configurate.ConfigurationNode;

public final class CombatModule implements Module {

    private CombatConfig config;
    private DefaultCombatService service;
    private ElytraDenyListener elytraListener;

    @Override
    public String id() {
        return "combat";
    }

    @Override
    public List<String> dependsOn() {
        return List.of();
    }

    @Override
    public void enable(ModuleContext ctx) {
        try {
            this.config = ctx.moduleConfig().get(CombatConfig.class);
        } catch (org.spongepowered.configurate.serialize.SerializationException e) {
            throw new ModuleException("failed to load config for module " + id(), e);
        }

        PlayerPreferencesService preferences = ctx.services().get(PlayerPreferencesService.class);
        PaperPlatform platform = ctx.services().get(PaperPlatform.class);

        this.service = new DefaultCombatService(
                ctx.scheduler(), ctx.messages(), preferences, platform, () -> this.config);
        ctx.services().publish(CombatService.class, service);

        // Supplier for live config reads — captures the module field, not the value
        ctx.events().register(new CombatListener(service, () -> this.config));
        this.elytraListener = new ElytraDenyListener(
                service, ctx.messages(), () -> this.config, ctx.scheduler(), platform.server());
        ctx.events().register(elytraListener);

        ctx.commands().register(
                CombatCommand.build(service, ctx.messages(), platform.server()),
                "Manually tag a player as in combat");
    }

    @Override
    public void disable() {
        if (elytraListener != null) {
            elytraListener.shutdown();
        }
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
    }
}
