package de.kreiscraft;

import de.kreiscraft.core.command.CommandRegistrar;
import de.kreiscraft.core.command.PaperCommandRegistrar;
import de.kreiscraft.core.config.AutoSaveTask;
import de.kreiscraft.core.config.ConfigService;
import de.kreiscraft.core.config.YamlConfigService;
import de.kreiscraft.core.event.EventBus;
import de.kreiscraft.core.message.ConfigurateMessageService;
import de.kreiscraft.core.message.MessageService;
import de.kreiscraft.core.message.PlaceholderResolver;
import de.kreiscraft.core.module.ModuleContext;
import de.kreiscraft.core.module.ModuleRegistry;
import de.kreiscraft.core.module.StartupSummary;
import de.kreiscraft.core.scheduler.BukkitScheduler;
import de.kreiscraft.core.scheduler.Scheduler;
import de.kreiscraft.core.service.ServiceRegistry;
import de.kreiscraft.core.util.Durations;
import java.time.Clock;
import java.time.Duration;
import java.time.ZoneId;
import org.bukkit.plugin.java.JavaPlugin;

public final class KreiscraftBootstrap {

    private final JavaPlugin plugin;

    private ConfigService config;
    private MessageService messages;
    private ServiceRegistry services;
    private Scheduler scheduler;
    private EventBus events;
    private CommandRegistrar commands;
    private ModuleRegistry modules;
    private AutoSaveTask autoSave;

    public KreiscraftBootstrap(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() throws Exception {
        this.config = new YamlConfigService(plugin.getDataFolder().toPath(),
                plugin.getClass().getClassLoader());
        String lang = config.root().node("core", "language").getString("de");
        this.messages = new ConfigurateMessageService(plugin.getDataFolder().toPath(),
                plugin.getClass().getClassLoader(),
                lang, plugin.getServer());

        this.scheduler = new BukkitScheduler(plugin);
        this.events = new EventBus(plugin);
        this.commands = new PaperCommandRegistrar(plugin);
        this.services = new ServiceRegistry();
        services.publish(MessageService.class, messages);
        services.publish(ConfigService.class, config);

        ZoneId zone = ZoneId.of(config.root().node("core", "timezone").getString("Europe/Berlin"));
        PlaceholderResolver placeholders = new PlaceholderResolver(plugin.getServer(), zone, Clock.systemDefaultZone());
        services.publish(PlaceholderResolver.class, placeholders);

        this.modules = new ModuleRegistry(
                m -> new ModuleContext(plugin, config.module(m.id()), messages, services, scheduler, commands, events),
                config.root(),
                plugin.getLogger());
        // Plan 1: no modules registered yet.

        StartupSummary summary = modules.enableAll();
        messages.broadcast("core.startup.summary",
                "enabled", summary.enabled(),
                "disabled", summary.disabled(),
                "failed", summary.failed());

        Duration autoSaveInterval = Durations.parse(
                config.root().node("core", "auto-save-interval").getString("5m"));
        this.autoSave = new AutoSaveTask(scheduler, autoSaveInterval, config::registeredDataFiles);
        autoSave.start();

        commands.register(ReloadCommand.build(config, modules, messages),
                "Kreiscraft admin commands");
    }

    public void stop() {
        if (autoSave != null)
            autoSave.stop();
        if (modules != null)
            modules.disableAll();
        if (events != null)
            events.unregisterAll();
    }
}
