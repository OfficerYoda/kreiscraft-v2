package de.kreiscraft.modules.jail;

import de.kreiscraft.core.config.ConfigService;
import de.kreiscraft.core.module.Module;
import de.kreiscraft.core.module.ModuleContext;
import de.kreiscraft.core.module.ModuleException;
import de.kreiscraft.modules.jail.commands.JailCommand;
import de.kreiscraft.modules.jail.commands.UnjailCommand;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.spongepowered.configurate.ConfigurationNode;

/** Module that provides player-jailing functionality via {@link JailService}. */
public final class JailModule implements Module {

    private JailConfig config;
    private AtomicReference<JailConfig> configRef;
    private DefaultJailService service;

    @Override
    public String id() {
        return "jail";
    }

    @Override
    public List<String> dependsOn() {
        return List.of();
    }

    @Override
    public void enable(ModuleContext ctx) {
        try {
            this.config = ctx.moduleConfig().get(JailConfig.class);
        } catch (org.spongepowered.configurate.serialize.SerializationException e) {
            throw new ModuleException("failed to load config for module " + id(), e);
        }

        // Data file
        JailData data = new JailData(ctx.plugin().getDataFolder().toPath());
        try {
            data.load();
        } catch (IOException e) {
            throw new ModuleException("failed to load data/jail.yml for module " + id(), e);
        }
        ctx.services().get(ConfigService.class).register(data);

        // Service
        this.service = new DefaultJailService(data);
        ctx.services().publish(JailService.class, service);

        // Live config reference for listener and commands
        this.configRef = new AtomicReference<>(config);

        // Listener
        ctx.events().register(new JailListener(service, configRef, ctx.messages()));

        // Commands
        ctx.commands().register(JailCommand.build(service, ctx.messages(), configRef), "Jail a player");
        ctx.commands().register(UnjailCommand.build(service, ctx.messages()), "Release a jailed player");
    }

    @Override
    public void onConfigReload(ConfigurationNode moduleConfig) {
        try {
            this.config = moduleConfig.get(JailConfig.class);
            if (configRef != null) {
                configRef.set(config);
            }
        } catch (org.spongepowered.configurate.serialize.SerializationException e) {
            throw new ModuleException("failed to reload config for module " + id(), e);
        }
    }
}
