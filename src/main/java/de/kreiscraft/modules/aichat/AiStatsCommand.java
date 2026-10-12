package de.kreiscraft.modules.aichat;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import de.kreiscraft.core.message.MessageService;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import java.math.RoundingMode;
import java.util.List;
import org.bukkit.command.CommandSender;

final class AiStatsCommand {

    private static final String PERMISSION = "kreiscraft.ai-chat.stats";
    private static final int PAGE_SIZE = 10;

    private AiStatsCommand() {}

    static LiteralCommandNode<CommandSourceStack> build(AiUsageService usage, MessageService messages) {
        return Commands.literal("aistats")
                .requires(source -> source.getSender().hasPermission(PERMISSION))
                .then(Commands.literal("total").executes(context -> {
                    sendUsage(context.getSource().getSender(), true, usage.total(), messages);
                    return Command.SINGLE_SUCCESS;
                }))
                .then(Commands.literal("top")
                        .executes(context -> showTop(
                                context.getSource().getSender(), "cost", 1, usage, messages))
                        .then(Commands.argument("metric", StringArgumentType.word())
                                .suggests((context, builder) -> {
                                    builder.suggest("cost");
                                    builder.suggest("requests");
                                    builder.suggest("tokens");
                                    return builder.buildFuture();
                                })
                                .executes(context -> showTop(
                                        context.getSource().getSender(),
                                        StringArgumentType.getString(context, "metric"),
                                        1, usage, messages))
                                .then(Commands.argument("page", IntegerArgumentType.integer(1))
                                        .executes(context -> showTop(
                                                context.getSource().getSender(),
                                                StringArgumentType.getString(context, "metric"),
                                                IntegerArgumentType.getInteger(context, "page"), usage, messages)))))
                .then(Commands.argument("player", StringArgumentType.word())
                        .suggests((context, builder) -> {
                            String typed = builder.getRemainingLowerCase();
                            usage.playerNames().stream()
                                    .filter(name -> name.toLowerCase(java.util.Locale.ROOT).startsWith(typed))
                                    .forEach(builder::suggest);
                            return builder.buildFuture();
                        })
                        .executes(context -> {
                            CommandSender sender = context.getSource().getSender();
                            String player = StringArgumentType.getString(context, "player");
                            usage.find(player).ifPresentOrElse(
                                    stats -> sendUsage(sender, false, stats, messages),
                                    () -> messages.send(sender, "ai-chat.stats.not-found", "player", player));
                            return Command.SINGLE_SUCCESS;
                        }))
                .build();
    }

    private static int showTop(
            CommandSender sender, String metricValue, int page, AiUsageService usage, MessageService messages) {
        AiUsageMetric metric = AiUsageMetric.parse(metricValue).orElse(null);
        if (metric == null) {
            messages.send(sender, "ai-chat.stats.invalid-metric", "metric", metricValue);
            return Command.SINGLE_SUCCESS;
        }
        List<AiUsage> entries = usage.top(metric);
        int pages = Math.max(1, (entries.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        if (page > pages) {
            messages.send(sender, "ai-chat.stats.invalid-page", "page", page, "pages", pages);
            return Command.SINGLE_SUCCESS;
        }
        messages.send(sender, "ai-chat.stats.top-header", "metric", metricValue, "page", page, "pages", pages);
        if (entries.isEmpty()) {
            messages.send(sender, "ai-chat.stats.top-empty");
            return Command.SINGLE_SUCCESS;
        }
        int start = (page - 1) * PAGE_SIZE;
        for (int index = start; index < Math.min(start + PAGE_SIZE, entries.size()); index++) {
            AiUsage stats = entries.get(index);
            messages.send(sender, "ai-chat.stats.top-entry", "rank", index + 1, "player", stats.playerName(),
                    "requests", stats.successfulRequests(), "tokens", stats.totalTokens(), "cost", cost(stats));
        }
        return Command.SINGLE_SUCCESS;
    }

    private static void sendUsage(
            CommandSender sender, boolean total, AiUsage usage, MessageService messages) {
        if (total) {
            messages.send(sender, "ai-chat.stats.total", "player", usage.playerName(), "requests",
                    usage.successfulRequests(), "failed", usage.failedRequests(), "prompt-tokens",
                    usage.promptTokens(), "completion-tokens", usage.completionTokens(), "tokens",
                    usage.totalTokens(), "cost", cost(usage));
            return;
        }
        messages.send(sender, "ai-chat.stats.player", "player", usage.playerName(), "requests",
                usage.successfulRequests(), "failed", usage.failedRequests(), "prompt-tokens", usage.promptTokens(),
                "completion-tokens", usage.completionTokens(), "tokens", usage.totalTokens(), "cost", cost(usage));
    }

    private static String cost(AiUsage usage) {
        return usage.cost().setScale(6, RoundingMode.HALF_UP).toPlainString();
    }
}
