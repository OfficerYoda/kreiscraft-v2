package de.kreiscraft.modules.settingsgui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verify;

import de.kreiscraft.core.config.ConfigField;
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
import java.util.List;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Particle;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryCloseEvent;
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
    private DefaultServerConfigService editor;
    private RecordingMessageService messages;
    private ServerConfigNavigator navigator;

    private xyz.xenondevs.invui.gui.Gui lastGui;
    private Component lastTitle;
    private Consumer<InventoryCloseEvent.Reason> lastClose;

    @BeforeEach
    void setUp(@TempDir Path tmp) throws IOException {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        player = server.addPlayer("Admin");
        editor =
                new DefaultServerConfigService(
                        new YamlConfigService(tmp, getClass().getClassLoader()));
        messages = new RecordingMessageService();
        SettingsService settings =
                new DefaultSettingsService(
                        new InMemoryPlayerPreferencesService(), PlayerLanguage.DE, Particle.FLAME);
        navigator =
                new ServerConfigNavigator(
                        settings, id -> true, editor, messages, gui, new MockScheduler());
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
                settings, id -> true, editor, localizedMessages, gui, new MockScheduler());

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
        assertThat(itemLabel(SettingsGui.SLOT_GLOW)).isEqualTo("Glow: Off");
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
    void hotReloadEdit_thenClose_reportsReloadPromptOnly() {
        navigator.openAdmin(player);
        captureLastWindow();
        openCategory("combat");

        ConfigField disableElytra = field("combat", "disable-elytra");
        clickField("combat", disableElytra);
        lastClose.accept(InventoryCloseEvent.Reason.PLAYER);

        assertThat(messages.sentKeys).contains("server-config.close.hot-reload");
        assertThat(messages.sentKeys)
                .as("a hot-reload-only session must not warn about a restart")
                .doesNotContain("server-config.close.restart-required");
    }

    @Test
    void moduleEnableEdit_thenClose_warnsRestartAndNeverReload() {
        navigator.openAdmin(player);
        captureLastWindow();
        openCategory("modules");

        ConfigField enabled = field("combat", "enabled");
        clickField("modules", enabled);
        lastClose.accept(InventoryCloseEvent.Reason.PLAYER);

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

        assertThat(messages.sentKeys).isEmpty();
    }

    @Test
    void backToSettings_reportsAndReopensPlayerSettings() {
        navigator.openAdmin(player);
        captureLastWindow();
        openCategory("combat");
        clickField("combat", field("combat", "disable-elytra"));

        navigator.openSettingsRoot(player);
        captureLastWindow();

        assertThat(messages.sentKeys).contains("server-config.close.hot-reload");
        assertThat(lastGui.getItem(SettingsGui.SLOT_LANGUAGE))
                .as("Player Settings root was reopened")
                .isNotNull();
    }

    // ------------------------------------------------------------------
    // Chat-based text entry
    // ------------------------------------------------------------------

    @Test
    void chatInput_appliesStringFieldAndReopensPage() throws Exception {
        navigator.openAdmin(player);
        captureLastWindow();
        openCategory("chat-format");

        ConfigField format = field("chat-format", "format");
        clickField("chat-format", format);
        assertThat(messages.sentKeys).contains("server-config.input.prompt");

        player.chat("NEW FORMAT");
        server.getScheduler().waitAsyncEventsFinished();

        assertThat(editor.current(format)).isEqualTo("NEW FORMAT");
        verify(gui, atLeast(1)).open(any(), any(), any(), any());
    }

    @Test
    void chatInput_cancel_doesNotEdit() throws Exception {
        navigator.openAdmin(player);
        captureLastWindow();
        openCategory("chat-format");

        ConfigField format = field("chat-format", "format");
        String before = (String) editor.current(format);
        clickField("chat-format", format);

        player.chat("cancel");
        server.getScheduler().waitAsyncEventsFinished();

        assertThat(editor.current(format)).isEqualTo(before);
        assertThat(messages.sentKeys).contains("server-config.input.cancelled");
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
        int index = ServerConfigGui.fieldsIn(editor.schema(), category).indexOf(field);
        assertThat(index).isNotNegative();
        lastGui.handleClick(ServerConfigGui.entrySlot(index), new Click(player, ClickType.LEFT));
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

    private String itemLabel(int slot) {
        return plain(lastGui.getItem(slot).getItemProvider(player).get().getItemMeta().displayName());
    }

    private static String plain(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }
}
