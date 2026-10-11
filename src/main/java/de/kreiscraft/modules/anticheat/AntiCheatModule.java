package de.kreiscraft.modules.anticheat;

import de.kreiscraft.core.config.ConfigService;
import de.kreiscraft.core.module.Module;
import de.kreiscraft.core.module.ModuleContext;
import de.kreiscraft.core.module.ModuleException;
import de.kreiscraft.modules.anticheat.commands.HackerLogsCommand;
import de.kreiscraft.platform.PlayerBrandProvider;
import java.io.IOException;
import java.util.List;
import org.bukkit.plugin.java.JavaPlugin;
import org.spongepowered.configurate.ConfigurationNode;

public final class AntiCheatModule implements Module {

    private AntiCheatConfig config;
    private DefaultHackLogService service;
    private JavaPlugin plugin;
    private String registeredChannel;

    @Override
    public String id() {
        return "anti-cheat";
    }

    @Override
    public List<String> dependsOn() {
        return List.of();
    }

    @Override
    public void enable(ModuleContext ctx) {
        try {
            config = ctx.moduleConfig().get(AntiCheatConfig.class);
        } catch (org.spongepowered.configurate.serialize.SerializationException e) {
            throw new ModuleException("failed to load config for module " + id(), e);
        }

        // Set up data file
        HackLogData data = new HackLogData(ctx.plugin().getDataFolder().toPath());
        try {
            data.load();
        } catch (IOException e) {
            throw new ModuleException("failed to load data/hackerlog.yml for module " + id(), e);
        }
        ctx.services().get(ConfigService.class).register(data);

        // Build service
        this.service = new DefaultHackLogService(data);
        ctx.services().publish(HackLogService.class, service);

        // Get PlayerBrandProvider from service registry
        PlayerBrandProvider brandProvider = ctx.services().get(PlayerBrandProvider.class);

        // Store plugin reference for cleanup in disable()
        this.plugin = ctx.plugin();

        // Build and register plugin message channel
        ModCheckerChannel modCheckerChannel = new ModCheckerChannel(service, ctx.messages());
        this.registeredChannel = config.channel();
        this.plugin
                .getServer()
                .getMessenger()
                .registerIncomingPluginChannel(this.plugin, registeredChannel, modCheckerChannel);

        // Register listener (reads the live config via a Supplier so reloads propagate)
        ctx.events()
                .register(new AntiCheatListener(
                        () -> config, service, ctx.scheduler(), ctx.messages(), brandProvider));

        // Register command
        ctx.commands()
                .register(
                        HackerLogsCommand.build(service, ctx.messages(), ctx.plugin().getServer()),
                        "View hack logs for a player");
    }

    @Override
    public void disable() {
        if (plugin != null && registeredChannel != null) {
            plugin.getServer()
                    .getMessenger()
                    .unregisterIncomingPluginChannel(plugin, registeredChannel);
        }
    }

    @Override
    public void onConfigReload(ConfigurationNode moduleConfig) {
        try {
            config = moduleConfig.get(AntiCheatConfig.class);
        } catch (org.spongepowered.configurate.serialize.SerializationException e) {
            throw new ModuleException("failed to reload config for module " + id(), e);
        }
    }
}
