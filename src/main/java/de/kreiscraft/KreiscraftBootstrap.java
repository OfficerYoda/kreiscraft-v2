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
import de.kreiscraft.platform.NmsWhitelist;
import de.kreiscraft.platform.OfflineInventoryReader;
import de.kreiscraft.platform.PaperPlatform;
import de.kreiscraft.platform.PlayerBrandProvider;
import de.kreiscraft.shared.chat.ChatBridge;
import de.kreiscraft.shared.gui.Gui;
import de.kreiscraft.shared.gui.GuiRegistry;
import de.kreiscraft.shared.user.UserListener;
import de.kreiscraft.shared.user.UserService;
import de.kreiscraft.shared.whitelist.PaperWhitelistService;
import de.kreiscraft.shared.whitelist.WhitelistService;
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
    private GuiRegistry guiRegistry;
    private UserService userService;

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

        // --- platform layer ---
        PaperPlatform platform = new PaperPlatform(plugin, plugin.getServer());
        PlayerBrandProvider brands = new PlayerBrandProvider(platform);
        NmsWhitelist nmsWhitelist = new NmsWhitelist(platform);
        OfflineInventoryReader offlineInv = new OfflineInventoryReader(platform);

        services.publish(PaperPlatform.class, platform);
        services.publish(PlayerBrandProvider.class, brands);
        services.publish(NmsWhitelist.class, nmsWhitelist);
        services.publish(OfflineInventoryReader.class, offlineInv);

        // --- shared services ---
        this.userService = new UserService(platform, Clock.systemDefaultZone());
        events.register(new UserListener(userService));
        userService.rehydrateOnlinePlayers();

        ChatBridge chatBridge = new ChatBridge();
        WhitelistService whitelist = new PaperWhitelistService(platform, nmsWhitelist);
        this.guiRegistry = new GuiRegistry();
        Gui gui = new Gui(guiRegistry);

        services.publish(UserService.class, userService);
        services.publish(ChatBridge.class, chatBridge);
        services.publish(WhitelistService.class, whitelist);
        services.publish(GuiRegistry.class, guiRegistry);
        services.publish(Gui.class, gui);

        this.modules = new ModuleRegistry(
                m -> new ModuleContext(plugin, config.module(m.id()), messages, services, scheduler, commands, events),
                config.root(),
                plugin.getLogger());
        modules.register(new de.kreiscraft.modules.admin.AdminModule());
        modules.register(new de.kreiscraft.modules.api.ApiModule());
        modules.register(new de.kreiscraft.modules.anticheat.AntiCheatModule());
        modules.register(new de.kreiscraft.modules.bedrock.BedrockModule());
        modules.register(new de.kreiscraft.modules.chatformat.ChatFormatModule());
        modules.register(new de.kreiscraft.modules.clickthrough.ClickthroughModule());
        modules.register(new de.kreiscraft.modules.combat.CombatModule());
        modules.register(new de.kreiscraft.modules.effects.EffectsModule());
        modules.register(new de.kreiscraft.modules.firework.FireworkModule());
        modules.register(new de.kreiscraft.modules.glow.GlowModule());
        modules.register(new de.kreiscraft.modules.invsee.InvseeModule());
        modules.register(new de.kreiscraft.modules.jail.JailModule());
        modules.register(new de.kreiscraft.modules.pluginhider.PluginHiderModule());
        modules.register(new de.kreiscraft.modules.positions.PositionsModule());
        modules.register(new de.kreiscraft.modules.reboot.RebootModule());
        modules.register(new de.kreiscraft.modules.settingsgui.SettingsGuiModule());
        modules.register(new de.kreiscraft.modules.signcolor.SignColorModule());
        modules.register(new de.kreiscraft.modules.spawn.SpawnModule());
        modules.register(new de.kreiscraft.modules.startsequence.StartSequenceModule());
        modules.register(new de.kreiscraft.modules.status.StatusModule());
        modules.register(new de.kreiscraft.modules.tablist.TablistModule());
        modules.register(new de.kreiscraft.modules.updatemode.UpdateModeModule());
        modules.register(new de.kreiscraft.modules.wither.WitherModule());
        modules.register(new de.kreiscraft.modules.worldmgmt.WorldMgmtModule());

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
                "Kreiscraft admin commands", "kc");
    }

    public void stop() {
        if (guiRegistry != null) {
            guiRegistry.closeAll();
        }
        if (autoSave != null) {
            autoSave.stop();
        }
        if (modules != null) {
            modules.disableAll();
        }
        if (events != null) {
            events.unregisterAll();
        }
    }
}
