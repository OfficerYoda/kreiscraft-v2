package de.kreiscraft.modules.settingsgui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;

import de.kreiscraft.core.config.ConfigField;
import de.kreiscraft.core.config.ConfigEditResult;
import de.kreiscraft.core.config.ServerConfigSchema;
import de.kreiscraft.core.config.YamlConfigService;
import de.kreiscraft.core.message.ConfigurateMessageService;
import de.kreiscraft.core.message.MessageService;
import de.kreiscraft.shared.gui.Gui;
import de.kreiscraft.shared.preferences.PlayerLanguage;
import de.kreiscraft.testsupport.InMemoryPlayerPreferencesService;
import de.kreiscraft.testsupport.MockScheduler;
import de.kreiscraft.testsupport.RecordingMessageService;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.stream.IntStream;
import java.util.function.Consumer;
import java.util.concurrent.atomic.AtomicInteger;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Particle;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.plugin.PluginMock;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import xyz.xenondevs.invui.Click;

@ExtendWith(MockitoExtension.class)
class ServerConfigNavigatorTest {

    @Mock
    private Gui gui;

    private ServerMock server;
    private PluginMock plugin;
    private PlayerMock player;
    private ServerConfigService editor;
    private RecordingMessageService messages;
    private ServerConfigNavigator navigator;
    private MockScheduler scheduler;
    private AtomicInteger reloads;

    private xyz.xenondevs.invui.gui.Gui lastGui;
    private Component lastTitle;
    private Consumer<InventoryCloseEvent.Reason> lastClose;
    private Consumer<String> lastRename;
    private xyz.xenondevs.invui.gui.Gui lastAnvilUpper;
    private xyz.xenondevs.invui.gui.Gui lastAnvilLower;

    @BeforeEach
    void setUp(@TempDir Path tmp) throws IOException {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        player = server.addPlayer("Admin");
        editor =
                new DefaultServerConfigService(
                        new YamlConfigService(tmp, getClass().getClassLoader()));
        messages = new RecordingMessageService();
        reloads = new AtomicInteger();
        SettingsService settings =
                new DefaultSettingsService(
                        new InMemoryPlayerPreferencesService(), PlayerLanguage.DE, Particle.FLAME);
        scheduler = new MockScheduler();
        navigator =
                new ServerConfigNavigator(
                        settings,
                        id -> true,
                        editor,
                        messages,
                        gui,
                        scheduler,
                        sender -> {
                            reloads.incrementAndGet();
                            return true;
                        });
        server.getPluginManager().registerEvents(navigator, plugin);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    // ------------------------------------------------------------------
    // Authorization
    // ------------------------------------------------------------------

    @Test
    void root_hidesServerConfigEntry_withoutAdminPermission() {
        navigator.open(player, SettingsGui.Page.ROOT);
        captureLastWindow();

        assertThat(lastGui.getItem(SettingsGui.SLOT_SERVER_CONFIG)).isNull();
    }

    @Test
    void root_showsServerConfigEntry_withAdminPermission() {
        grantAdmin();
        navigator.open(player, SettingsGui.Page.ROOT);
        captureLastWindow();

        assertThat(lastGui.getItem(SettingsGui.SLOT_SERVER_CONFIG)).isNotNull();
    }

    @Test
    void languageClick_reopensTitleAndAllItemsInTheSelectedLanguage(@TempDir Path tmp)
            throws IOException {
        InMemoryPlayerPreferencesService preferences = new InMemoryPlayerPreferencesService();
        SettingsService settings =
                new DefaultSettingsService(preferences, PlayerLanguage.DE, Particle.FLAME);
        MessageService localizedMessages = new ConfigurateMessageService(
                tmp,
                getClass().getClassLoader(),
                "de",
                server,
                recipient -> preferences
                        .preferences(((PlayerMock) recipient).getUniqueId())
                        .language()
                        .map(PlayerLanguage::code));
        navigator = new ServerConfigNavigator(
                settings,
                id -> true,
                editor,
                localizedMessages,
                gui,
                new MockScheduler(),
                sender -> true);

        navigator.open(player, SettingsGui.Page.ROOT);
        captureLastWindow();
        lastGui.handleClick(
                SettingsGui.SLOT_LANGUAGE, new Click(player, ClickType.LEFT));
        captureLastWindow();

        assertThat(plain(lastTitle)).isEqualTo("Settings");
        assertThat(itemLabel(SettingsGui.SLOT_LANGUAGE)).isEqualTo("Language: English");
        assertThat(itemLabel(SettingsGui.SLOT_CLICK_THROUGH)).isEqualTo("Click-Through: On");
        assertThat(itemLabel(SettingsGui.SLOT_COMBAT_DISPLAY)).isEqualTo("Combat Display: Action Bar");
        assertThat(itemLabel(SettingsGui.SLOT_EFFECTS)).isEqualTo("Effects (click to open)");
        assertThat(lastGui.getItem(22)).isNull();
    }

    @Test
    void openAdmin_opensServerConfigurationRoot() {
        navigator.openAdmin(player);
        captureLastWindow();

        assertThat(lastGui.getItem(ServerConfigGui.entrySlot(0))).isNotNull();
        assertThat(lastGui.getItem(ServerConfigGui.SLOT_BACK)).isNotNull();
    }

    // ------------------------------------------------------------------
    // Close reporting
    // ------------------------------------------------------------------

    @Test
    void hotReloadEdit_thenClose_reloadsAutomatically() {
        navigator.openAdmin(player);
        captureLastWindow();
        openCategory("combat");

        ConfigField disableElytra = field("combat", "disable-elytra");
        clickField("combat", disableElytra);
        lastClose.accept(InventoryCloseEvent.Reason.PLAYER);

        assertThat(reloads).hasValue(1);
        assertThat(messages.sentKeys).contains("server-config.close.hot-reload");
        assertThat(messages.sentKeys)
                .as("a hot-reload-only session must not warn about a restart")
                .doesNotContain("server-config.close.restart-required");
    }

    @Test
    void moduleEnableEdit_thenClose_warnsRestartAndNeverReload() {
        navigator.openAdmin(player);
        captureLastWindow();
        openCategory("combat");

        ConfigField enabled = field("combat", "enabled");
        clickField("combat", enabled);
        lastClose.accept(InventoryCloseEvent.Reason.PLAYER);

        assertThat(reloads).hasValue(0);
        assertThat(messages.sentKeys).contains("server-config.close.restart-required");
        assertThat(messages.sentKeys)
                .as("module lifecycle changes are never described as hot-applied")
                .doesNotContain("server-config.close.hot-reload");
    }

    @Test
    void closeWithoutEdits_reportsNothing() {
        navigator.openAdmin(player);
        captureLastWindow();

        lastClose.accept(InventoryCloseEvent.Reason.PLAYER);

        assertThat(reloads).hasValue(0);
        assertThat(messages.sentKeys).isEmpty();
    }

    @Test
    void mixedSession_reloadsAndSeparatelyWarnsAboutRestart() {
        navigator.openAdmin(player);
        captureLastWindow();
        openCategory("combat");
        clickField("combat", field("combat", "disable-elytra"));
        navigator.openServerConfig(player, ServerConfigPage.root());
        captureLastWindow();
        openCategory("combat");
        clickField("combat", field("combat", "enabled"));

        lastClose.accept(InventoryCloseEvent.Reason.PLAYER);

        assertThat(reloads).hasValue(1);
        assertThat(messages.sentKeys)
                .contains("server-config.close.hot-reload", "server-config.close.restart-required");
    }

    @Test
    void pageNavigation_doesNotReloadBeforeGenuineClose() {
        navigator.openAdmin(player);
        captureLastWindow();
        openCategory("combat");
        clickField("combat", field("combat", "disable-elytra"));

        Consumer<InventoryCloseEvent.Reason> navigationClose = lastClose;
        doAnswer(invocation -> {
            navigationClose.accept(InventoryCloseEvent.Reason.OPEN_NEW);
            return null;
        })
                .when(gui)
                .open(any(), any(), any(), any());
        navigator.openServerConfig(player, ServerConfigPage.root());
        captureLastWindow();

        assertThat(reloads).hasValue(0);
        assertThat(messages.sentKeys).isEmpty();

        lastClose.accept(InventoryCloseEvent.Reason.PLAYER);
        assertThat(reloads).hasValue(1);
    }

    @Test
    void rejectedAndFailedEdits_doNotReload() {
        ConfigField field = field("combat", "disable-elytra");
        ConfigEditResult[] result = {new ConfigEditResult.Rejected("no")};
        ServerConfigService unsuccessfulEditor = new ServerConfigService() {
            @Override
            public ServerConfigSchema schema() {
                return editor.schema();
            }

            @Override
            public Object current(ConfigField requested) {
                return editor.current(requested);
            }

            @Override
            public ConfigEditResult edit(ConfigField requested, Object value) {
                return result[0];
            }
        };
        SettingsService settings = new DefaultSettingsService(
                new InMemoryPlayerPreferencesService(), PlayerLanguage.DE, Particle.FLAME);
        navigator = new ServerConfigNavigator(
                settings,
                id -> true,
                unsuccessfulEditor,
                messages,
                gui,
                new MockScheduler(),
                sender -> {
                    reloads.incrementAndGet();
                    return true;
                });

        for (ConfigEditResult unsuccessful : List.of(
                new ConfigEditResult.Rejected("no"),
                new ConfigEditResult.Failed(new IOException("disk")))) {
            result[0] = unsuccessful;
            navigator.openAdmin(player);
            captureLastWindow();
            openCategory("combat");
            clickField("combat", field);
            lastClose.accept(InventoryCloseEvent.Reason.PLAYER);
        }

        assertThat(reloads).hasValue(0);
    }

    @Test
    void backToSettings_reportsAndReopensPlayerSettings() {
        navigator.openAdmin(player);
        captureLastWindow();
        openCategory("combat");
        clickField("combat", field("combat", "disable-elytra"));

        navigator.openSettingsRoot(player);
        captureLastWindow();

        assertThat(reloads).hasValue(1);
        assertThat(messages.sentKeys).contains("server-config.close.hot-reload");
        assertThat(lastGui.getItem(SettingsGui.SLOT_LANGUAGE))
                .as("Player Settings root was reopened")
                .isNotNull();
    }

    // ------------------------------------------------------------------
    // Text entry
    // ------------------------------------------------------------------

    @Test
    void stringField_opensAnvilAndSavesValue() {
        navigator.openAdmin(player);
        captureLastWindow();
        openCategory("chat-format");

        ConfigField format = field("chat-format", "format");
        clickField("chat-format", format);
        captureLastAnvil();

        lastRename.accept("NEW FORMAT");
        lastAnvilUpper.handleClick(2, new Click(player, ClickType.LEFT));
        captureLastWindow();

        assertThat(editor.current(format)).isEqualTo("NEW FORMAT");
    }

    @Test
    void stringField_trimsWhitespaceBeforeSaving() {
        navigator.openAdmin(player);
        captureLastWindow();
        openCategory("chat-format");

        ConfigField format = field("chat-format", "format");
        clickField("chat-format", format);
        captureLastAnvil();

        lastRename.accept("  NEW FORMAT  ");
        lastAnvilUpper.handleClick(2, new Click(player, ClickType.LEFT));

        assertThat(editor.current(format)).isEqualTo("NEW FORMAT");
    }

    @Test
    void listField_opensAnvilInsteadOfRequestingChatInput() {
        navigator.openAdmin(player);
        captureLastWindow();
        openCategory("combat");

        clickField("combat", field("combat", "excluded-worlds"));
        captureLastAnvil();

        assertThat(lastAnvilUpper.getSize()).isEqualTo(3);
        assertThat(lastAnvilLower.getSize()).isEqualTo(36);
    }

    @Test
    void anvilOutput_addsTrimmedValuesRepeatedlyAndStaysOpen() {
        ConfigField field = openListEditor();

        lastRename.accept("  world  ");
        lastAnvilUpper.handleClick(2, new Click(player, ClickType.LEFT));
        captureLastAnvil();
        lastRename.accept("world_nether");
        lastAnvilUpper.handleClick(2, new Click(player, ClickType.LEFT));
        captureLastAnvil();

        assertThat(currentList(field)).endsWith("world", "world_nether");
    }

    @Test
    void blankAndDuplicateSubmissionsLeaveListUnchangedAndExplainWhy() {
        ConfigField field = openListEditor();
        List<String> before = currentList(field);

        lastRename.accept("   ");
        lastAnvilUpper.handleClick(2, new Click(player, ClickType.LEFT));
        assertThat(currentList(field)).isEqualTo(before);
        assertThat(messages.sentKeys).contains("server-config.list.blank");

        lastRename.accept("world");
        lastAnvilUpper.handleClick(2, new Click(player, ClickType.LEFT));
        captureLastAnvil();
        lastRename.accept(" world ");
        lastAnvilUpper.handleClick(2, new Click(player, ClickType.LEFT));

        assertThat(currentList(field)).endsWith("world");
        assertThat(messages.sentKeys).contains("server-config.list.duplicate");
    }

    @Test
    void existingValuesArePaperItemsAndRightClickDeletesOne() {
        ConfigField field = field("combat", "excluded-worlds");
        editor.edit(field, List.of("world", "world_nether"));
        openListEditor();

        ItemStack first = lastAnvilLower.getItem(0).getItemProvider(player).get();
        assertThat(first.getType()).isEqualTo(org.bukkit.Material.PAPER);
        assertThat(plain(first.getItemMeta().displayName())).isEqualTo("world");
        assertThat(first.getItemMeta().lore()).singleElement().satisfies(
                line -> assertThat(plain(line)).isEqualTo("Right click to delete"));

        lastAnvilLower.handleClick(0, new Click(player, ClickType.RIGHT));
        captureLastAnvil();

        assertThat(currentList(field)).containsExactly("world_nether");
    }

    @Test
    void editorDisplaysOnlyTheThirtySixEntriesThatFit() {
        ConfigField field = field("combat", "excluded-worlds");
        List<String> values = IntStream.range(0, 40).mapToObj(i -> "world-" + i).toList();
        editor.edit(field, values);

        openListEditor();

        assertThat(IntStream.range(0, 36)
                .filter(slot -> lastAnvilLower.getItem(slot) != null)
                .count())
                        .isEqualTo(36);
        assertThat(lastAnvilLower.getSize()).isEqualTo(36);
    }

    @Test
    void closingEditorReturnsToCategoryWithoutLosingSuccessfulEdits() {
        ConfigField field = openListEditor();
        lastRename.accept("world");
        lastAnvilUpper.handleClick(2, new Click(player, ClickType.LEFT));
        captureLastAnvil();

        lastClose.accept(InventoryCloseEvent.Reason.PLAYER);
        scheduler.advance(Duration.ZERO);
        captureLastWindow();

        assertThat(currentList(field)).contains("world");
        assertThat(lastGui.getItem(ServerConfigGui.entrySlot(0))).isNotNull();
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private void grantAdmin() {
        player.addAttachment(plugin, SettingsGui.SERVER_CONFIG_PERMISSION, true);
    }

    private ConfigField field(String module, String key) {
        return editor.schema().find(List.of("modules", module, key)).orElseThrow();
    }

    private void openCategory(String category) {
        List<String> categories = ServerConfigGui.categories(editor.schema());
        int index = categories.indexOf(category);
        assertThat(index).isNotNegative();
        lastGui.handleClick(
                ServerConfigGui.entrySlot(index), new Click(player, ClickType.LEFT));
        captureLastWindow();
    }

    private void clickField(String category, ConfigField field) {
        lastGui.handleClick(
                ServerConfigGui.fieldSlot(editor.schema(), category, field),
                new Click(player, ClickType.LEFT));
    }

    private ConfigField openListEditor() {
        navigator.openAdmin(player);
        captureLastWindow();
        openCategory("combat");
        ConfigField field = field("combat", "excluded-worlds");
        clickField("combat", field);
        captureLastAnvil();
        return field;
    }

    @SuppressWarnings("unchecked")
    private List<String> currentList(ConfigField field) {
        return (List<String>) editor.current(field);
    }

    private void captureLastWindow() {
        ArgumentCaptor<xyz.xenondevs.invui.gui.Gui> guiCaptor =
                ArgumentCaptor.forClass(xyz.xenondevs.invui.gui.Gui.class);
        ArgumentCaptor<Component> titleCaptor = ArgumentCaptor.forClass(Component.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Consumer<InventoryCloseEvent.Reason>> closeCaptor =
                ArgumentCaptor.forClass((Class) Consumer.class);
        verify(gui, atLeast(1))
                .open(any(), guiCaptor.capture(), titleCaptor.capture(), closeCaptor.capture());
        lastGui = guiCaptor.getValue();
        lastTitle = titleCaptor.getValue();
        lastClose = closeCaptor.getValue();
        clearInvocations(gui);
    }

    private void captureLastAnvil() {
        ArgumentCaptor<xyz.xenondevs.invui.gui.Gui> upperCaptor =
                ArgumentCaptor.forClass(xyz.xenondevs.invui.gui.Gui.class);
        ArgumentCaptor<xyz.xenondevs.invui.gui.Gui> lowerCaptor =
                ArgumentCaptor.forClass(xyz.xenondevs.invui.gui.Gui.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Consumer<String>> renameCaptor =
                ArgumentCaptor.forClass((Class) Consumer.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Consumer<InventoryCloseEvent.Reason>> closeCaptor =
                ArgumentCaptor.forClass((Class) Consumer.class);
        verify(gui, atLeast(1))
                .openAnvil(
                        any(),
                        upperCaptor.capture(),
                        lowerCaptor.capture(),
                        any(),
                        renameCaptor.capture(),
                        closeCaptor.capture());
        lastAnvilUpper = upperCaptor.getValue();
        lastAnvilLower = lowerCaptor.getValue();
        lastRename = renameCaptor.getValue();
        lastClose = closeCaptor.getValue();
        clearInvocations(gui);
    }

    private String itemLabel(int slot) {
        return plain(lastGui.getItem(slot).getItemProvider(player).get().getItemMeta().displayName());
    }

    private static String plain(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }
}
