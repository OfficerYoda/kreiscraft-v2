package de.kreiscraft.modules.bedrock.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import de.kreiscraft.core.message.MessageService;
import de.kreiscraft.modules.bedrock.BedrockAuthorizationResult;
import de.kreiscraft.modules.bedrock.BedrockRecord;
import de.kreiscraft.modules.bedrock.BedrockService;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import java.util.Comparator;
import java.util.List;

/**
 * Implements {@code /bedrocklist add <name>}, {@code /bedrocklist remove <name>} and
 * {@code /bedrocklist list}.
 *
 * <ul>
 *   <li>{@code add <name>} — registers a Pending Bedrock Authorization for the name. The first
 *       matching login binds it to the observed UUID and whitelists that identity.</li>
 *   <li>{@code remove <name>} — explicitly removes and revokes an authorization so it can be
 *       re-registered, which is the only way to replace a bound identity.</li>
 *   <li>{@code list} — prints every known Bedrock player name in a deterministic, case-insensitive
 *       order, preceded by a header whose count matches the entries.</li>
 * </ul>
 *
 * Permission: {@code kreiscraft.bedrock.admin} (default: op).
 */
public final class BedrockListCommand {

    private BedrockListCommand() {}

    public static LiteralCommandNode<CommandSourceStack> build(
            BedrockService service, MessageService messages) {

        return Commands.literal("bedrocklist")
                .requires(src -> src.getSender().hasPermission("kreiscraft.bedrock.admin"))
                .then(Commands.literal("add")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .executes(ctx -> {
                                    String name = ctx.getArgument("name", String.class);
                                    BedrockAuthorizationResult result = service.register(name);
                                    if (result == BedrockAuthorizationResult.ALREADY_REGISTERED) {
                                        messages.send(ctx.getSource().getSender(),
                                                "bedrock.already-authorized", "player", name);
                                    } else {
                                        messages.send(ctx.getSource().getSender(),
                                                "bedrock.authorized", "player", name);
                                    }
                                    return Command.SINGLE_SUCCESS;
                                })))
                .then(Commands.literal("remove")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .executes(ctx -> {
                                    String name = ctx.getArgument("name", String.class);
                                    BedrockAuthorizationResult result = service.remove(name);
                                    if (result == BedrockAuthorizationResult.REMOVED) {
                                        messages.send(ctx.getSource().getSender(),
                                                "bedrock.revoked", "player", name);
                                    } else {
                                        messages.send(ctx.getSource().getSender(),
                                                "bedrock.not-authorized", "player", name);
                                    }
                                    return Command.SINGLE_SUCCESS;
                                })))
                .then(Commands.literal("list")
                        .executes(ctx -> {
                            var sender = ctx.getSource().getSender();
                            List<BedrockRecord> players = service.knownPlayers().stream()
                                    .sorted(Comparator.comparing(
                                            BedrockRecord::name,
                                            String.CASE_INSENSITIVE_ORDER)
                                            .thenComparing(BedrockRecord::uuid))
                                    .toList();
                            messages.send(sender,
                                    "bedrock.list-header",
                                    "count", String.valueOf(players.size()));
                            if (players.isEmpty()) {
                                messages.send(sender, "bedrock.list-empty");
                            } else {
                                for (BedrockRecord player : players) {
                                    messages.send(sender,
                                            "bedrock.list-entry",
                                            "player", player.name());
                                }
                            }
                            return Command.SINGLE_SUCCESS;
                        }))
                .build();
    }
}
