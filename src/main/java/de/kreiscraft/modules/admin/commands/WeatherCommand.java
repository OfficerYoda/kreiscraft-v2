package de.kreiscraft.modules.admin.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import de.kreiscraft.core.message.MessageService;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import org.bukkit.World;
import org.bukkit.entity.Player;

public final class WeatherCommand {

    private WeatherCommand() {}

    public static LiteralCommandNode<CommandSourceStack> build(MessageService messages) {
        return Commands.literal("kreiscraftweather")
                .requires(
                        src -> src.getSender() instanceof Player
                                && src.getSender()
                                        .hasPermission("kreiscraft.admin.weather"))
                .then(Commands.argument("weather", StringArgumentType.word())
                        .executes(ctx -> {
                            Player player = (Player) ctx.getSource().getSender();
                            String weatherArg = StringArgumentType.getString(ctx, "weather");
                            World world = player.getWorld();
                            switch (weatherArg.toLowerCase()) {
                                case "clear" -> {
                                    world.setStorm(false);
                                    world.setThundering(false);
                                }
                                case "rain" -> {
                                    world.setStorm(true);
                                    world.setThundering(false);
                                }
                                case "thunder" -> {
                                    world.setStorm(true);
                                    world.setThundering(true);
                                }
                                default -> {
                                    messages.send(
                                            player,
                                            "admin.usage",
                                            "usage",
                                            "/kreiscraftweather <clear|rain|thunder>");
                                    return Command.SINGLE_SUCCESS;
                                }
                            }
                            messages.send(
                                    player,
                                    "admin.weather-set",
                                    "weather",
                                    weatherArg.toLowerCase());
                            return Command.SINGLE_SUCCESS;
                        }))
                .build();
    }
}
