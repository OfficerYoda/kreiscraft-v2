package de.kreiscraft.modules.pluginhider;

import de.kreiscraft.core.config.ConfigService;
import de.kreiscraft.core.module.Module;
import de.kreiscraft.core.module.ModuleContext;
import de.kreiscraft.core.module.ModuleException;
import de.kreiscraft.modules.pluginhider.commands.PluginHiderCommand;
import java.io.IOException;
import java.util.List;
import org.spongepowered.configurate.ConfigurationNode;

public final class PluginHiderModule implements Module {

    private PluginHiderConfig config;
    private DefaultPluginHiderService service;

    @Override
    public String id() {
        return "plugin-hider";
    }

    @Override
    public List<String> dependsOn() {
        return List.of();
    }

    @Override
    public void enable(ModuleContext ctx) {
        try {
            this.config = ctx.moduleConfig().get(PluginHiderConfig.class);
        } catch (org.spongepowered.configurate.serialize.SerializationException e) {
            throw new ModuleException("failed to load config for module " + id(), e);
        }

        PluginHiderData data = new PluginHiderData(ctx.plugin().getDataFolder().toPath());
        try {
            data.load();
        } catch (IOException e) {
            throw new ModuleException("failed to load data for module " + id(), e);
        }

        ctx.services().get(ConfigService.class).register(data);

        this.service = new DefaultPluginHiderService(config, data);
        ctx.services().publish(PluginHiderService.class, service);

        ctx.events().register(new PluginHiderListener(service));
        ctx.commands().register(
                PluginHiderCommand.build(service, ctx.messages()),
                "Manage the plugin-hider allowlist");
    }

    @Override
    public void onConfigReload(ConfigurationNode moduleConfig) {
        try {
            this.config = moduleConfig.get(PluginHiderConfig.class);
        } catch (org.spongepowered.configurate.serialize.SerializationException e) {
            throw new ModuleException("failed to reload config for module " + id(), e);
        }
    }
}
