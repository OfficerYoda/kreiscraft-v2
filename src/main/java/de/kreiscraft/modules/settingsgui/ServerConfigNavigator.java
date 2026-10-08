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
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/**
 * Opens every Player Settings and Server Configuration page and owns their sessions.
 *
 * <p>A Server Configuration session begins when the editor is opened (from the Player Settings root
 * or {@code /settings admin}) and ends when the player genuinely leaves the GUI. Navigation between
 * pages keeps the same session; a programmatic reopen does not count as a close. On close, the
 * {@link ServerConfigCloseReporter} tells the operator which changes need a reload and which need a
 * restart.
 *
 * <p>Free-form text and text lists are entered through chat: clicking such a field closes the GUI
 * and prompts for a value, which is applied on the next chat message. All GUI work is scheduled on
 * the main thread because the chat event is asynchronous.
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
                field.type() == ConfigFieldType.STRING_LIST ? "list" : "text");
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
        Object value =
                input.field().type() == ConfigFieldType.STRING_LIST ? parseList(text) : text;
        ConfigEditResult result = editor.edit(input.field(), value);
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

    static List<String> parseList(String text) {
        List<String> values = new ArrayList<>();
        for (String part : text.split(",")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                values.add(trimmed);
            }
        }
        return values;
    }

    private record PendingInput(ConfigField field, ServerConfigPage page) {
    }
}
