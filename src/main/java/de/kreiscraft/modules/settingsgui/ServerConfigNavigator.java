package de.kreiscraft.modules.settingsgui;

import de.kreiscraft.core.config.ConfigEditResult;
import de.kreiscraft.core.config.ConfigField;
import de.kreiscraft.core.config.ConfigFieldType;
import de.kreiscraft.core.message.MessageService;
import de.kreiscraft.core.module.ModuleEnablement;
import de.kreiscraft.core.scheduler.Scheduler;
import de.kreiscraft.shared.gui.Gui;
import de.kreiscraft.shared.gui.GuiRegistry;
import io.papermc.paper.event.player.AsyncChatEvent;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Predicate;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Particle;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import xyz.xenondevs.invui.Click;
import xyz.xenondevs.invui.item.AbstractItem;
import xyz.xenondevs.invui.item.ItemProvider;
import xyz.xenondevs.invui.item.Item;
import xyz.xenondevs.invui.item.ItemWrapper;

/**
 * Opens every Player Settings and Server Configuration page and owns their sessions.
 *
 * <p>A Server Configuration session begins when the editor is opened (from the Player Settings root
 * or {@code /settings admin}) and ends when the player genuinely leaves the GUI. Navigation between
 * pages keeps the same session; a programmatic reopen does not count as a close. On close, the
 * {@link ServerConfigCloseReporter} tells the operator which changes need a reload and which need a
 * restart.
 *
 * <p>Free-form strings are entered through chat. String lists use an anvil editor whose lower
 * inventory shows removable values. All chat-triggered GUI work is scheduled on the main thread
 * because the chat event is asynchronous.
 */
public final class ServerConfigNavigator
        implements SettingsNavigator, ServerConfigGui.Navigator, Listener {

    private static final String CANCEL = "cancel";

    private final SettingsService settings;
    private final ModuleEnablement modules;
    private final ServerConfigService editor;
    private final MessageService messages;
    private final Gui gui;
    private final Scheduler scheduler;
    private final Predicate<CommandSender> reload;

    private final Map<UUID, ServerConfigSession> sessions = new HashMap<>();
    private final Map<UUID, PendingInput> pendingInputs = new ConcurrentHashMap<>();
    private final Map<UUID, GuiRegistry.Handle> windows = new HashMap<>();
    private final Set<UUID> suppressReport = new HashSet<>();
    private final Set<UUID> particlePickers = new HashSet<>();

    public ServerConfigNavigator(
            SettingsService settings,
            ModuleEnablement modules,
            ServerConfigService editor,
            MessageService messages,
            Gui gui,
            Scheduler scheduler,
            Predicate<CommandSender> reload) {
        this.settings = settings;
        this.modules = modules;
        this.editor = editor;
        this.messages = messages;
        this.gui = gui;
        this.scheduler = scheduler;
        this.reload = reload;
    }

    @Override
    public void openAdmin(Player player) {
        sessions.put(player.getUniqueId(), new ServerConfigSession());
        openServerConfig(player, ServerConfigPage.root());
    }

    @Override
    public void open(Player player, SettingsGui.Page page) {
        switch (page) {
            case SERVER_CONFIG -> openServerConfig(player, ServerConfigPage.root());
            case ROOT -> openWindow(
                    player,
                    SettingsGui.buildRoot(
                            settings, modules, messages, player, this, canConfigure(player)),
                    messages.renderFor(player, page.titleKey()));
            case EFFECTS -> openWindow(
                    player,
                    SettingsGui.buildEffects(settings, messages, this),
                    messages.renderFor(player, page.titleKey()));
        }
    }

    @Override
    public void pickParticle(
            Player player, Consumer<Particle> selection, Runnable cancellation) {
        UUID id = player.getUniqueId();
        particlePickers.add(id);
        ParticlePicker.open(
                player,
                particle -> scheduler.runLater(Duration.ZERO, () -> {
                    particlePickers.remove(id);
                    selection.accept(particle);
                }),
                () -> scheduler.runLater(Duration.ZERO, () -> {
                    particlePickers.remove(id);
                    cancellation.run();
                }));
    }

    @Override
    public void openServerConfig(Player player, ServerConfigPage page) {
        ServerConfigSession session =
                sessions.computeIfAbsent(player.getUniqueId(), id -> new ServerConfigSession());
        xyz.xenondevs.invui.gui.Gui invui =
                page.isRoot()
                        ? ServerConfigGui.buildRoot(editor, messages, player, this)
                        : ServerConfigGui.buildCategory(
                                editor, messages, player, this, session, page.category());
        Component title =
                page.isRoot()
                        ? messages.renderFor(player, "server-config.title")
                        : messages.renderFor(
                                player,
                                "server-config.category-title",
                                "category",
                                messages.renderFor(
                                        player, ServerConfigGui.categoryKey(page.category())));
        openWindow(player, invui, title);
    }

    @Override
    public void openSettingsRoot(Player player) {
        reportAndClear(player);
        open(player, SettingsGui.Page.ROOT);
    }

    private boolean canConfigure(Player player) {
        return player.hasPermission(SettingsGui.SERVER_CONFIG_PERMISSION);
    }

    private void openWindow(Player player, xyz.xenondevs.invui.gui.Gui invui, Component title) {
        UUID id = player.getUniqueId();
        // Opening a new window closes the previous one as a side effect; that is navigation, not a
        // session end, so suppress the close report for the duration of the open call.
        suppressReport.add(id);
        try {
            GuiRegistry.Handle handle = gui.open(player, invui, title, reason -> onClose(player));
            windows.put(id, handle);
        } finally {
            suppressReport.remove(id);
        }
    }

    private void onClose(Player player) {
        UUID id = player.getUniqueId();
        windows.remove(id);
        if (suppressReport.contains(id)
                || pendingInputs.containsKey(id)
                || particlePickers.contains(id)) {
            return;
        }
        ServerConfigSession session = sessions.remove(id);
        if (session != null && player.isOnline()) {
            report(player, session);
        }
    }

    private void reportAndClear(Player player) {
        ServerConfigSession session = sessions.remove(player.getUniqueId());
        if (session != null && player.isOnline()) {
            report(player, session);
        }
    }

    private void report(Player player, ServerConfigSession session) {
        boolean hotReloadApplied = session.hasHotReload() && reload.test(player);
        ServerConfigCloseReporter.report(messages, player, session, hotReloadApplied);
    }

    // ------------------------------------------------------------------
    // Chat-based text entry
    // ------------------------------------------------------------------

    @Override
    public void requestTextInput(Player player, ConfigField field, ServerConfigPage page) {
        UUID id = player.getUniqueId();
        pendingInputs.put(id, new PendingInput(field, page));
        GuiRegistry.Handle handle = windows.get(id);
        if (handle != null) {
            handle.close();
        }
        String fieldName =
                PlainTextComponentSerializer.plainText()
                        .serialize(ServerConfigGui.label(field, messages, player));
        messages.send(
                player,
                "server-config.input.prompt",
                "field",
                fieldName,
                "kind",
                "text");
    }

    @Override
    public void openListEditor(Player player, ConfigField field, ServerConfigPage page) {
        String[] draft = {""};
        xyz.xenondevs.invui.gui.Gui upper = xyz.xenondevs.invui.gui.Gui.empty(3, 1);
        upper.setItem(0, Item.simple(named(Material.PAPER, Component.text(" "), List.of())));
        upper.setItem(2, new AbstractItem() {
            @Override
            public ItemProvider getItemProvider(Player viewer) {
                return new ItemWrapper(named(Material.LIME_DYE, Component.text("Add"), List.of()));
            }

            @Override
            public void handleClick(
                    org.bukkit.event.inventory.ClickType clickType, Player viewer, Click click) {
                addListValue(viewer, field, page, draft[0]);
            }
        });
        xyz.xenondevs.invui.gui.Gui lower = xyz.xenondevs.invui.gui.Gui.empty(9, 4);
        List<String> values = listValue(field);
        for (int slot = 0; slot < Math.min(lower.getSize(), values.size()); slot++) {
            String value = values.get(slot);
            lower.setItem(slot, listEntry(player, field, page, value));
        }
        openAnvilWindow(
                player,
                upper,
                lower,
                ServerConfigGui.label(field, messages, player),
                text -> draft[0] = text,
                () -> openServerConfig(player, page));
    }

    private AbstractItem listEntry(
            Player player, ConfigField field, ServerConfigPage page, String value) {
        return new AbstractItem() {
            @Override
            public ItemProvider getItemProvider(Player viewer) {
                return new ItemWrapper(named(
                        Material.PAPER,
                        Component.text(value),
                        List.of(Component.text("Right click to delete"))));
            }

            @Override
            public void handleClick(
                    org.bukkit.event.inventory.ClickType clickType, Player viewer, Click click) {
                if (!clickType.isRightClick()) {
                    return;
                }
                List<String> updated = new ArrayList<>(listValue(field));
                updated.remove(value);
                applyListEdit(player, field, page, updated);
            }
        };
    }

    private void addListValue(
            Player player, ConfigField field, ServerConfigPage page, String input) {
        String value = input.trim();
        if (value.isEmpty()) {
            messages.send(player, "server-config.list.blank");
            return;
        }
        if (field.type() == ConfigFieldType.INT_LIST) {
            try {
                Integer.parseInt(value);
            } catch (NumberFormatException e) {
                messages.send(player, "server-config.list.not-an-integer");
                return;
            }
        }
        List<String> updated = new ArrayList<>(listValue(field));
        if (updated.contains(value)) {
            messages.send(player, "server-config.list.duplicate", "value", value);
            return;
        }
        updated.add(value);
        applyListEdit(player, field, page, updated);
    }

    private void applyListEdit(
            Player player, ConfigField field, ServerConfigPage page, List<String> updated) {
        Object value = field.type() == ConfigFieldType.INT_LIST
                ? updated.stream().map(Integer::parseInt).toList()
                : updated;
        ConfigEditResult result = editor.edit(field, value);
        if (result instanceof ConfigEditResult.Applied) {
            ServerConfigSession session = sessions.get(player.getUniqueId());
            if (session != null) {
                session.recordApplied(field);
            }
            openListEditor(player, field, page);
        } else if (result instanceof ConfigEditResult.Rejected rejected) {
            messages.send(player, "server-config.edit-rejected", "reason", rejected.reason());
        } else if (result instanceof ConfigEditResult.Failed) {
            messages.send(player, "server-config.edit-failed");
        }
    }

    private List<String> listValue(ConfigField field) {
        Object current = editor.current(field);
        if (!(current instanceof List<?> values)) {
            return List.of();
        }
        return values.stream().map(String::valueOf).toList();
    }

    private static ItemStack named(Material material, Component name, List<Component> lore) {
        ItemStack stack = new ItemStack(material);
        stack.editMeta(meta -> {
            meta.displayName(name);
            meta.lore(lore);
        });
        return stack;
    }

    private void openAnvilWindow(
            Player player,
            xyz.xenondevs.invui.gui.Gui upper,
            xyz.xenondevs.invui.gui.Gui lower,
            Component title,
            java.util.function.Consumer<String> onRename,
            Runnable onClose) {
        UUID id = player.getUniqueId();
        suppressReport.add(id);
        try {
            GuiRegistry.Handle handle = gui.openAnvil(
                    player,
                    upper,
                    lower,
                    title,
                    onRename,
                    reason -> {
                        windows.remove(id);
                        if (!suppressReport.contains(id)) {
                            // InvUI rejects opening another window from inside a close callback.
                            scheduler.runLater(Duration.ZERO, onClose);
                        }
                    });
            windows.put(id, handle);
        } finally {
            suppressReport.remove(id);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onChat(AsyncChatEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        PendingInput input = pendingInputs.remove(id);
        if (input == null) {
            return;
        }
        // Swallow the message: it was a setting value, not chat.
        event.setCancelled(true);
        String text =
                PlainTextComponentSerializer.plainText()
                        .serialize(event.originalMessage())
                        .trim();
        Player player = event.getPlayer();
        scheduler.runOnMain(() -> completeInput(player, input, text));
    }

    private void completeInput(Player player, PendingInput input, String text) {
        if (!player.isOnline()) {
            return;
        }
        if (CANCEL.equalsIgnoreCase(text)) {
            messages.send(player, "server-config.input.cancelled");
            openServerConfig(player, input.page());
            return;
        }
        ConfigEditResult result = editor.edit(input.field(), text);
        ServerConfigSession session = sessions.get(player.getUniqueId());
        if (result instanceof ConfigEditResult.Applied && session != null) {
            session.recordApplied(input.field());
        } else if (result instanceof ConfigEditResult.Rejected rejected) {
            messages.send(player, "server-config.edit-rejected", "reason", rejected.reason());
        } else if (result instanceof ConfigEditResult.Failed) {
            messages.send(player, "server-config.edit-failed");
        }
        openServerConfig(player, input.page());
    }

    private record PendingInput(ConfigField field, ServerConfigPage page) {
    }
}
