package de.kreiscraft.modules.anticheat.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import de.kreiscraft.core.command.SuggestionProviders;
import de.kreiscraft.core.message.MessageService;
import de.kreiscraft.modules.anticheat.HackEntry;
import de.kreiscraft.modules.anticheat.HackLogService;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Server;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class HackerLogsCommand {

    private static final DateTimeFormatter WHEN_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneOffset.UTC);

    private HackerLogsCommand() {}

    public static LiteralCommandNode<CommandSourceStack> build(
            HackLogService service, MessageService messages, Server server) {
        return Commands.literal("hackerlogs")
                .requires(src -> src.getSender().hasPermission("kreiscraft.anticheat.view"))
                .then(Commands.argument("target", ArgumentTypes.players())
                        .suggests(
                                SuggestionProviders.onlineAndKnownOfflineProfiles(
                                        server, "kreiscraft.anticheat.view"))
                        .executes(ctx -> {
                            List<Player> targets = ctx.getArgument(
                                    "target", PlayerSelectorArgumentResolver.class)
                                    .resolve(ctx.getSource());
                            CommandSender sender = ctx.getSource().getSender();
                            for (Player target : targets) {
                                showLogs(sender, target.getUniqueId(), target.getName(),
                                        null, service, messages);
                            }
                            return Command.SINGLE_SUCCESS;
                        })
                        .then(Commands.argument("kind", StringArgumentType.word())
                                .suggests((ctx, builder) -> {
                                    builder.suggest("cheats");
                                    builder.suggest("xray");
                                    return builder.buildFuture();
                                })
                                .executes(ctx -> {
                                    List<Player> targets = ctx.getArgument(
                                            "target", PlayerSelectorArgumentResolver.class)
                                            .resolve(ctx.getSource());
                                    String kindArg = StringArgumentType.getString(ctx, "kind");
                                    Optional<HackEntry.Kind> kind = parseKind(kindArg);
                                    if (kind.isEmpty()) {
                                        messages.send(ctx.getSource().getSender(),
                                                "anti-cheat.invalid-kind", "kind", kindArg);
                                        return Command.SINGLE_SUCCESS;
                                    }
                                    CommandSender sender = ctx.getSource().getSender();
                                    for (Player target : targets) {
                                        showLogs(sender, target.getUniqueId(), target.getName(),
                                                kind.get(), service, messages);
                                    }
                                    return Command.SINGLE_SUCCESS;
                                })))
                .build();
    }

    /** Package-private for tests. Returns empty if the argument is not a recognised kind. */
    static Optional<HackEntry.Kind> parseKind(String arg) {
        if ("cheats".equalsIgnoreCase(arg)) {
            return Optional.of(HackEntry.Kind.CHEAT);
        }
        if ("xray".equalsIgnoreCase(arg)) {
            return Optional.of(HackEntry.Kind.XRAY);
        }
        return Optional.empty();
    }

    /** Package-private for tests: renders one target's logs to the requesting sender. */
    static void showLogs(
            CommandSender sender,
            UUID targetId,
            String targetName,
            HackEntry.Kind kind,
            HackLogService service,
            MessageService messages) {
        if (kind == null) {
            // show both kinds
            List<HackEntry> cheats = service.forPlayer(targetId, HackEntry.Kind.CHEAT);
            List<HackEntry> xray = service.forPlayer(targetId, HackEntry.Kind.XRAY);
            if (cheats.isEmpty() && xray.isEmpty()) {
                messages.send(sender, "anti-cheat.no-logs", "player", targetName);
                return;
            }
            if (!cheats.isEmpty()) {
                sendEntries(sender, targetName, kindLabel(messages, sender, HackEntry.Kind.CHEAT),
                        cheats, messages);
            }
            if (!xray.isEmpty()) {
                sendEntries(sender, targetName, kindLabel(messages, sender, HackEntry.Kind.XRAY),
                        xray, messages);
            }
        } else {
            List<HackEntry> entries = service.forPlayer(targetId, kind);
            if (entries.isEmpty()) {
                messages.send(sender, "anti-cheat.no-logs", "player", targetName);
                return;
            }
            sendEntries(sender, targetName, kindLabel(messages, sender, kind), entries, messages);
        }
    }

    /** The localized display label for a hack-log kind, rendered for this recipient. */
    private static String kindLabel(
            MessageService messages, CommandSender sender, HackEntry.Kind kind) {
        String key = kind == HackEntry.Kind.CHEAT
                ? "anti-cheat.kind-cheats"
                : "anti-cheat.kind-xray";
        return PlainTextComponentSerializer.plainText().serialize(messages.renderFor(sender, key));
    }

    private static void sendEntries(
            CommandSender sender,
            String targetName,
            String kindLabel,
            List<HackEntry> entries,
            MessageService messages) {
        messages.send(sender, "anti-cheat.log-header",
                "player", targetName, "kind", kindLabel);
        for (HackEntry entry : entries) {
            messages.send(sender, "anti-cheat.log-entry",
                    "when", WHEN_FMT.format(entry.when()),
                    "detail", entry.detail());
        }
    }
}
