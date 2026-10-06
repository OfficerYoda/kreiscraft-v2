package de.kreiscraft.modules.bedrock.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import de.kreiscraft.core.message.MessageService;
import de.kreiscraft.modules.bedrock.BedrockRecord;
import de.kreiscraft.modules.bedrock.BedrockService;
import de.kreiscraft.shared.whitelist.WhitelistService;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Implements {@code /bedrocklist add <name>} and {@code /bedrocklist list}.
 *
 * <ul>
 *   <li>{@code add <name>} — looks up the player in the persisted set and, if found,
 *       whitelists them via {@link WhitelistService#addBedrockPlayer}.</li>
 *   <li>{@code list} — prints every known Bedrock player name in a deterministic,
 *       case-insensitive order, preceded by a header whose count matches the entries.</li>
 * </ul>
 *
 * Permission: {@code kreiscraft.bedrock.admin} (default: op).
 */
public final class BedrockListCommand {

    private BedrockListCommand() {}

    public static LiteralCommandNode<CommandSourceStack> build(
            BedrockService service,
            WhitelistService whitelist,
            MessageService messages) {

        return Commands.literal("bedrocklist")
                .requires(src -> src.getSender().hasPermission("kreiscraft.bedrock.admin"))
                .then(Commands.literal("add")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .executes(ctx -> {
                                    String name = ctx.getArgument("name", String.class);
                                    Optional<BedrockRecord> found = service.knownPlayers().stream()
                                            .filter(r -> r.name().equalsIgnoreCase(name))
                                            .findFirst();
                                    if (found.isPresent()) {
                                        BedrockRecord r = found.get();
                                        whitelist.addBedrockPlayer(r.name(), r.uuid());
                                        messages.send(ctx.getSource().getSender(),
                                                "bedrock.added", "player", r.name());
                                    } else {
                                        messages.send(ctx.getSource().getSender(),
                                                "bedrock.unknown", "player", name);
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
