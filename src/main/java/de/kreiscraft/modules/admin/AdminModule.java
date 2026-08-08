package de.kreiscraft.modules.admin;

import de.kreiscraft.core.message.MessageService;
import de.kreiscraft.core.message.PlaceholderResolver;
import de.kreiscraft.core.module.Module;
import de.kreiscraft.core.module.ModuleContext;
import de.kreiscraft.core.module.ModuleException;
import de.kreiscraft.modules.admin.commands.HeadCommand;
import de.kreiscraft.modules.admin.commands.IpCommand;
import de.kreiscraft.modules.admin.commands.ItemCommand;
import de.kreiscraft.modules.admin.commands.KHelpCommand;
import de.kreiscraft.modules.admin.commands.KreiscraftGamemodeCommand;
import de.kreiscraft.modules.admin.commands.LastOnlineCommand;
import de.kreiscraft.modules.admin.commands.PingCommand;
import de.kreiscraft.modules.admin.commands.SkullCommand;
import de.kreiscraft.modules.admin.commands.SpawnMobCommand;
import de.kreiscraft.modules.admin.commands.StatisticCommand;
import de.kreiscraft.modules.admin.commands.SudoCommand;
import de.kreiscraft.modules.admin.commands.TpsCommand;
import de.kreiscraft.modules.admin.commands.UuidCommand;
import de.kreiscraft.modules.admin.commands.WeatherCommand;
import java.util.List;
import org.bukkit.Server;
import org.spongepowered.configurate.ConfigurationNode;

public final class AdminModule implements Module {

    private AdminConfig config;

    @Override
    public String id() {
        return "admin";
    }

    @Override
    public List<String> dependsOn() {
        return List.of();
    }

    @Override
    public void enable(ModuleContext ctx) {
        try {
            this.config = ctx.moduleConfig().get(AdminConfig.class);
        } catch (org.spongepowered.configurate.serialize.SerializationException e) {
            throw new ModuleException("failed to load config for module " + id(), e);
        }
        if (config == null) {
            config = new AdminConfig(null, null);
        }

        MessageService messages = ctx.messages();
        Server server = ctx.plugin().getServer();
        PlaceholderResolver resolver = ctx.services().get(PlaceholderResolver.class);

        ctx.commands()
                .register(
                        KreiscraftGamemodeCommand.build(messages, server),
                        "Change a player's gamemode",
                        "kreiscraftgm", "kcgamemode", "kcgm");
        ctx.commands().register(ItemCommand.build(messages), "Spawn items", "i");
        ctx.commands()
                .register(SkullCommand.build(messages, server), "Get a player head", "kopf");
        ctx.commands().register(HeadCommand.build(messages), "Swap held item and helmet");
        ctx.commands()
                .register(WeatherCommand.build(messages), "Change weather in your world", "kcweather");
        ctx.commands()
                .register(
                        SpawnMobCommand.build(messages),
                        "Spawn mobs at your location",
                        "sm");
        ctx.commands().register(IpCommand.build(messages, server), "Show a player's IP");
        ctx.commands().register(UuidCommand.build(messages, server), "Show a player's UUID");
        ctx.commands()
                .register(
                        LastOnlineCommand.build(messages, server, () -> this.config),
                        "Show when a player was last online");
        ctx.commands()
                .register(PingCommand.build(messages, server), "Show a player's ping");
        ctx.commands().register(TpsCommand.build(messages, resolver), "Show server TPS");
        ctx.commands()
                .register(
                        StatisticCommand.build(messages, server),
                        "Show a player's statistic");
        ctx.commands().register(SudoCommand.build(messages, server), "Force a player to chat");
        ctx.commands()
                .register(
                        KHelpCommand.build(messages, () -> this.config),
                        "Show Kreiscraft help links",
                        "?", "kchelp");
    }

    @Override
    public void onConfigReload(ConfigurationNode moduleConfig) {
        try {
            AdminConfig reloaded = moduleConfig.get(AdminConfig.class);
            this.config = reloaded != null ? reloaded : new AdminConfig(null, null);
        } catch (org.spongepowered.configurate.serialize.SerializationException e) {
            throw new ModuleException("failed to reload config for module " + id(), e);
        }
    }
}
