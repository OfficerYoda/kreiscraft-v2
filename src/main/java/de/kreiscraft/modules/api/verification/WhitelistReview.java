package de.kreiscraft.modules.api.verification;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import de.kreiscraft.core.message.MessageService;
import de.kreiscraft.core.scheduler.Scheduler;
import de.kreiscraft.modules.api.verification.WhitelistRequests.Entry;
import de.kreiscraft.modules.api.verification.WhitelistRequests.Status;
import de.kreiscraft.shared.gui.Gui;
import de.kreiscraft.shared.whitelist.WhitelistService;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.TooltipDisplay;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import xyz.xenondevs.invui.Click;
import xyz.xenondevs.invui.item.AbstractItem;
import xyz.xenondevs.invui.item.Item;
import xyz.xenondevs.invui.item.ItemProvider;
import xyz.xenondevs.invui.item.ItemWrapper;

/** Permission-gated chat actions and paginated review menus for public Java submissions. */
public final class WhitelistReview implements Listener {
    public static final String PERMISSION = "kreiscraft.api.whitelist-review";
    private final WhitelistRequests requests;
    private final WhitelistService whitelist;
    private final Gui gui;
    private final MessageService messages;
    private final Scheduler scheduler;
    private final Server server;
    private final Logger logger;
    private boolean active = true;
    // ponytail: serialize rare admin decisions; per-name queues if review throughput matters.
    private boolean busy;

    public WhitelistReview(WhitelistRequests requests, WhitelistService whitelist, Gui gui,
            MessageService messages, Scheduler scheduler, Server server, Logger logger) {
        this.requests = requests;
        this.whitelist = whitelist;
        this.gui = gui;
        this.messages = messages;
        this.scheduler = scheduler;
        this.server = server;
        this.logger = logger;
    }

    public LiteralCommandNode<CommandSourceStack> command() {
        var root = Commands.literal("whitelist-requests")
                .requires(source -> source.getSender().hasPermission(PERMISSION))
                .executes(context -> {
                    if (context.getSource().getSender() instanceof Player player) {
                        open(player, Status.PENDING, 0);
                    } else {
                        messages.send(context.getSource().getSender(), "core.player-only");
                    }
                    return Command.SINGLE_SUCCESS;
                });
        for (Status status : List.of(Status.APPROVED, Status.DECLINED)) {
            String action = status == Status.APPROVED ? "approve" : "deny";
            root.then(Commands.literal(action).then(Commands.argument("name", StringArgumentType.word())
                    .then(Commands.argument("submission", StringArgumentType.word()).executes(context -> {
                        if (context.getSource().getSender() instanceof Player player) {
                            String name = StringArgumentType.getString(context, "name");
                            String submission = StringArgumentType.getString(context, "submission");
                            load(player, entries -> {
                                Set<String> names = whitelistedNames();
                                Entry entry = entries.stream().filter(e -> e.playerName().equalsIgnoreCase(name)
                                        && e.submissionId().equals(submission) && statusOf(e, names) == Status.PENDING)
                                        .findFirst().orElse(null);
                                if (entry == null) {
                                    messages.send(player, "api.whitelist-stale");
                                } else {
                                    decide(player, entry, status,
                                            () -> messages.send(player, "api.whitelist-resolved", "player",
                                                    entry.playerName()));
                                }
                            });
                        }
                        return Command.SINGLE_SUCCESS;
                    }))));
        }
        return root.build();
    }

    public void submitted(Entry entry) {
        if (!active) {
            return;
        }
        for (Player player : server.getOnlinePlayers()) {
            if (authorized(player)) {
                player.sendMessage(messages.renderFor(player, "api.whitelist-new", "player", entry.playerName())
                        .append(Component.space()).append(action(player, entry, true))
                        .append(Component.space()).append(action(player, entry, false)));
            }
        }
    }

    private Component action(Player player, Entry entry, boolean approve) {
        String action = approve ? "approve" : "deny";
        return messages.renderFor(player, approve ? "api.whitelist-approve" : "api.whitelist-deny")
                .clickEvent(ClickEvent.runCommand("/whitelist-requests " + action + " "
                        + entry.playerName() + " " + entry.submissionId()));
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        load(player, entries -> {
            Set<String> names = whitelistedNames();
            long count = entries.stream().filter(entry -> statusOf(entry, names) == Status.PENDING).count();
            if (count > 0) {
                player.sendMessage(messages.renderFor(player, "api.whitelist-pending", "count", count)
                        .clickEvent(ClickEvent.runCommand("/whitelist-requests")));
            }
        });
    }

    public void shutdown() {
        active = false;
    }

    private boolean authorized(Player player) {
        return active && player.isOnline() && player.hasPermission(PERMISSION);
    }

    private void load(Player player, Consumer<List<Entry>> loaded) {
        if (!authorized(player)) {
            return;
        }
        scheduler.runAsync(() -> {
            try {
                List<Entry> entries = requests.list();
                scheduler.runOnMain(() -> {
                    if (authorized(player)) {
                        loaded.accept(entries);
                    }
                });
            } catch (IOException | RuntimeException e) {
                scheduler.runOnMain(() -> failure(player, e));
            }
        });
    }

    private Set<String> whitelistedNames() {
        return whitelist.allNames().stream().map(name -> name.toLowerCase(Locale.ROOT)).collect(Collectors.toSet());
    }

    private static Status statusOf(Entry entry, Set<String> names) {
        if (names.contains(entry.playerName().toLowerCase(Locale.ROOT))) {
            return Status.APPROVED;
        }
        return entry.status() == Status.DECLINED ? Status.DECLINED : Status.PENDING;
    }

    public void open(Player player, Status status, int page) {
        load(player, entries -> gui.open(player, build(player, entries, status, page),
                messages.renderFor(player, "api.whitelist-title")));
    }

    xyz.xenondevs.invui.gui.Gui build(Player player, List<Entry> entries, Status status, int requestedPage) {
        Set<String> names = whitelistedNames();
        List<Entry> visible = entries.stream().filter(entry -> statusOf(entry, names) == status).toList();
        int page = Math.max(0, Math.min(requestedPage, Math.max(0, (visible.size() - 1) / 45)));
        var menu = xyz.xenondevs.invui.gui.Gui.builder().setStructure(
                ". . . . . . . . .", ". . . . . . . . .", ". . . . . . . . .",
                ". . . . . . . . .", ". . . . . . . . .", ". . . . . . . . .").build();
        for (int index = page * 45; index < Math.min(visible.size(), (page + 1) * 45); index++) {
            Entry entry = visible.get(index);
            ItemStack head = stack(Material.PLAYER_HEAD, Component.text(entry.playerName(), NamedTextColor.GOLD));
            head.editMeta(SkullMeta.class, meta -> {
                meta.setPlayerProfile(Bukkit.createProfile(entry.playerName()));
                meta.lore(List.of(
                        messages.renderFor(player, "api.whitelist-approve-hint").decoration(TextDecoration.ITALIC,
                                false),
                        messages.renderFor(player, "api.whitelist-deny-hint").decoration(TextDecoration.ITALIC,
                                false)));
            });
            head.setData(DataComponentTypes.TOOLTIP_DISPLAY,
                    TooltipDisplay.tooltipDisplay().addHiddenComponents(DataComponentTypes.PROFILE).build());
            menu.setItem(index % 45, item(head, (viewer, click) -> {
                if (click == ClickType.LEFT || click == ClickType.RIGHT) {
                    load(viewer, current -> {
                        if (!current.contains(entry)) {
                            messages.send(viewer, "api.whitelist-stale");
                            open(viewer, status, page);
                            return;
                        }
                        decide(viewer, entry, click == ClickType.LEFT ? Status.APPROVED : Status.DECLINED,
                                () -> open(viewer, status, page));
                    });
                }
            }));
        }
        if (page > 0) {
            menu.setItem(45, navigation(player, Material.ARROW, "server-config.previous-page",
                    () -> open(player, status, page - 1)));
        }
        menu.setItem(47, navigation(player, Material.PAPER, "api.whitelist-pending-tab",
                () -> open(player, Status.PENDING, 0)));
        menu.setItem(48, navigation(player, Material.LIME_WOOL, "api.whitelist-approved-tab",
                () -> open(player, Status.APPROVED, 0)));
        menu.setItem(49, navigation(player, Material.RED_WOOL, "api.whitelist-declined-tab",
                () -> open(player, Status.DECLINED, 0)));
        menu.setItem(51, navigation(player, Material.SUNFLOWER, "api.whitelist-refresh",
                () -> open(player, status, page)));
        if ((page + 1) * 45 < visible.size()) {
            menu.setItem(53, navigation(player, Material.ARROW, "server-config.next-page",
                    () -> open(player, status, page + 1)));
        }
        return menu;
    }

    private Item navigation(Player player, Material material, String key, Runnable action) {
        return item(stack(material, messages.renderFor(player, key)), (viewer, click) -> action.run());
    }

    private Item item(ItemStack stack, java.util.function.BiConsumer<Player, ClickType> action) {
        return new AbstractItem() {
            @Override
            public ItemProvider getItemProvider(Player viewer) {
                return new ItemWrapper(stack);
            }

            @Override
            public void handleClick(ClickType clickType, Player player, Click click) {
                if (authorized(player)) {
                    action.accept(player, clickType);
                }
            }
        };
    }

    private static ItemStack stack(Material material, Component name) {
        ItemStack stack = new ItemStack(material);
        stack.editMeta(meta -> meta.displayName(name.decoration(TextDecoration.ITALIC, false)));
        return stack;
    }

    private void decide(Player player, Entry entry, Status status, Runnable onSuccess) {
        if (!authorized(player)) {
            return;
        }
        if (busy) {
            messages.send(player, "api.whitelist-busy");
            return;
        }
        boolean before = whitelist.allNames().stream().anyMatch(entry.playerName()::equalsIgnoreCase);
        busy = true;
        try {
            apply(entry.playerName(), status == Status.APPROVED);
        } catch (RuntimeException e) {
            busy = false;
            try {
                apply(entry.playerName(), before);
            } catch (RuntimeException rollback) {
                e.addSuppressed(rollback);
            }
            failure(player, e);
            return;
        }
        scheduler.runAsync(() -> {
            Exception error = null;
            try {
                requests.resolve(entry, status);
            } catch (IOException | RuntimeException e) {
                error = e;
            }
            Exception result = error;
            scheduler.runOnMain(() -> {
                busy = false;
                if (result != null) {
                    // Do not leave the live whitelist changed when persistence or the snapshot check fails.
                    try {
                        apply(entry.playerName(), before);
                    } catch (RuntimeException rollback) {
                        result.addSuppressed(rollback);
                    }
                    failure(player, result);
                } else if (authorized(player)) {
                    onSuccess.run();
                }
            });
        });
    }

    private void apply(String name, boolean approved) {
        if (approved) {
            whitelist.addJavaPlayer(name);
        } else {
            whitelist.remove(name);
        }
        if (whitelist.allNames().stream().anyMatch(name::equalsIgnoreCase) != approved) {
            throw new IllegalStateException("Whitelist membership did not change for " + name);
        }
    }

    private void failure(Player player, Exception error) {
        logger.log(Level.WARNING, "Whitelist review failed", error);
        if (authorized(player)) {
            messages.send(player, "api.whitelist-error");
        }
    }
}
