package de.kreiscraft.modules.admin.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import de.kreiscraft.core.message.MessageService;
import de.kreiscraft.modules.admin.AdminConfig;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.function.Supplier;
import org.bukkit.OfflinePlayer;
import org.bukkit.Server;
import org.bukkit.command.CommandSender;

public final class LastOnlineCommand {

    private static final DateTimeFormatter ISO_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX");

    private LastOnlineCommand() {}

    public static LiteralCommandNode<CommandSourceStack> build(
            MessageService messages, Server server, Supplier<AdminConfig> configSupplier) {
        return Commands.literal("lastonline")
                .requires(src -> src.getSender().hasPermission("kreiscraft.admin.lastonline"))
                .then(Commands.argument("player", StringArgumentType.word())
                        .executes(ctx -> {
                            CommandSender sender = ctx.getSource().getSender();
                            String targetName = StringArgumentType.getString(ctx, "player");
                            @SuppressWarnings("deprecation")
                            OfflinePlayer offlinePlayer = server.getOfflinePlayer(targetName);
                            long lastSeen = offlinePlayer.getLastSeen();
                            ZoneId zone = ZoneId.of(configSupplier.get().timezone());
                            String when =
                                    Instant.ofEpochMilli(lastSeen)
                                            .atZone(zone)
                                            .format(ISO_FMT);
                            messages.send(
                                    sender,
                                    "admin.lastonline",
                                    "player",
                                    targetName,
                                    "when",
                                    when);
                            return Command.SINGLE_SUCCESS;
                        }))
                .build();
    }
}
