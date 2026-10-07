package de.kreiscraft.modules.settingsgui;

import static org.assertj.core.api.Assertions.assertThat;

import de.kreiscraft.core.config.ConfigEditResult;
import de.kreiscraft.core.config.ConfigField;
import de.kreiscraft.core.config.ConfigFieldType;
import de.kreiscraft.core.config.ConfigLifecycle;
import de.kreiscraft.core.config.ConfigValidators;
import de.kreiscraft.core.config.ServerConfigSchema;
import de.kreiscraft.core.config.YamlConfigService;
import de.kreiscraft.testsupport.RecordingMessageService;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import xyz.xenondevs.invui.Click;
import xyz.xenondevs.invui.gui.Gui;

class ServerConfigGuiTest {

    private ServerMock server;
    private PlayerMock player;
    private RecordingMessageService messages;
    private ServerConfigService editor;
    private NavigatorRecorder navigator;
    private ServerConfigSession session;

    @BeforeEach
    void setUp(@TempDir Path tmp) throws IOException {
        server = MockBukkit.mock();
        player = server.addPlayer("Admin");
        messages = new RecordingMessageService();
        editor =
                new DefaultServerConfigService(
                        new YamlConfigService(tmp, getClass().getClassLoader()));
        navigator = new NavigatorRecorder();
        session = new ServerConfigSession();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private static String plain(ItemStack stack) {
        return PlainTextComponentSerializer.plainText().serialize(stack.getItemMeta().displayName());
    }

    private int slotOf(String category, ConfigField field) {
        List<ConfigField> fields = ServerConfigGui.fieldsIn(editor.schema(), category);
        int index = fields.indexOf(field);
        assertThat(index).as("field is present in category " + category).isNotNegative();
        return ServerConfigGui.entrySlot(index);
    }

    private ConfigField field(String module, String key) {
        return editor.schema().find(List.of("modules", module, key)).orElseThrow();
    }

    private Gui category(String name) {
        return ServerConfigGui.buildCategory(editor, messages, player, navigator, session, name);
    }

    private ItemStack stackAt(Gui gui, int slot) {
        return gui.getItem(slot).getItemProvider(player).get();
    }

    private void click(Gui gui, int slot) {
        click(gui, slot, ClickType.LEFT);
    }

    private void click(Gui gui, int slot, ClickType type) {
        gui.handleClick(slot, new Click(player, type));
    }

    // ------------------------------------------------------------------
    // Layout and categories
    // ------------------------------------------------------------------

    @Test
    void root_listsEveryDeclaredCategory() {
        Gui gui = ServerConfigGui.buildRoot(editor, messages, player, navigator);

        List<String> categories = ServerConfigGui.categories(editor.schema());
        assertThat(categories).contains("modules", "combat", "effects", "spawn", "chat-format");
        for (int index = 0; index < categories.size(); index++) {
            assertThat(gui.getItem(ServerConfigGui.entrySlot(index))).isNotNull();
        }
        assertThat(gui.getItem(ServerConfigGui.SLOT_BACK)).isNotNull();
    }

    @Test
    void hiddenFields_areNeverExposed() {
        List<List<Object>> exposedPaths =
                editor.schema().guiExposed().stream().map(ConfigField::path).toList();

        assertThat(exposedPaths)
                .as("credentials, network settings, and tab-list header/footer stay YAML-only")
                .noneMatch(
                        path -> path.contains("admin-api-key")
                                || path.contains("host")
                                || path.contains("port")
                                || path.contains("cors-origins")
                                || path.contains("session-cookie")
                                || path.contains("channel")
                                || path.contains("header")
                                || path.contains("footer")
                                || path.contains("timezone")
                                || path.contains("debug")
                                || path.contains("active")
                                || path.contains("has-started"));
        assertThat(ServerConfigGui.categories(editor.schema()))
                .as("the HTTP API stays entirely YAML-only")
                .doesNotContain("api");
    }

    @Test
    void root_listsTheCompleteCategorySet() {
        List<String> categories = ServerConfigGui.categories(editor.schema());

        assertThat(categories)
                .contains(
                        "core",
                        "modules",
                        "admin",
                        "anti-cheat",
                        "bedrock",
                        "chat-format",
                        "combat",
                        "effects",
                        "firework",
                        "jail",
                        "positions",
                        "reboot",
                        "spawn",
                        "start-sequence",
                        "tablist",
                        "update-mode",
                        "wither");
    }

    // ------------------------------------------------------------------
    // Typed controls persist immediately
    // ------------------------------------------------------------------

    @Test
    void booleanField_clickTogglesAndPersists() {
        ConfigField disableElytra = field("combat", "disable-elytra");
        Gui gui = category("combat");

        click(gui, slotOf("combat", disableElytra));

        assertThat(editor.current(disableElytra)).isEqualTo(false);
        assertThat(stackAt(gui, slotOf("combat", disableElytra))).isNotNull();
    }

    @Test
    void integerField_clickStepsAndPersists() {
        ConfigField count = field("effects", "count");
        Gui gui = category("effects");

        click(gui, slotOf("effects", count));

        assertThat(editor.current(count)).isEqualTo(26);
    }

    @Test
    void integerField_rightClickStepsDown() {
        ConfigField count = field("effects", "count");
        Gui gui = category("effects");

        click(gui, slotOf("effects", count), ClickType.RIGHT);

        assertThat(editor.current(count)).isEqualTo(24);
    }

    @Test
    void durationField_clickCyclesAndPersists() {
        ConfigField duration = field("combat", "tag-duration");
        Gui gui = category("combat");

        click(gui, slotOf("combat", duration));

        assertThat(editor.current(duration)).isEqualTo("10s");
    }

    @Test
    void enumField_clickCyclesAndPersists() {
        ConfigField display = field("combat", "default-display");
        Gui gui = category("combat");

        click(gui, slotOf("combat", display));

        assertThat(editor.current(display)).isEqualTo("TITLE");
    }

    @Test
    void locationField_clickSetsToCurrentPosition() {
        ConfigField location = field("spawn", "location");
        player.teleport(new Location(player.getWorld(), 5.0, 70.0, 9.0));
        Gui gui = category("spawn");

        click(gui, slotOf("spawn", location));

        Object stored = editor.current(location);
        assertThat(stored).isInstanceOf(Location.class);
        assertThat(((Location) stored).getBlockX()).isEqualTo(5);
        assertThat(((Location) stored).getBlockZ()).isEqualTo(9);
    }

    @Test
    void particleEnumField_clickCyclesAndPersists() {
        ConfigField particle = field("effects", "default-particle");
        Gui gui = category("effects");

        click(gui, slotOf("effects", particle));

        assertThat(editor.current(particle)).isEqualTo("FLAME");
    }

    @Test
    void witherBooleanField_clickTogglesAndPersists() {
        ConfigField fire = field("wither", "spawn-explosion-fire");
        Gui gui = category("wither");

        click(gui, slotOf("wither", fire));

        assertThat(editor.current(fire)).isEqualTo(false);
    }

    @Test
    void restartOnlyDurationField_recordsRestartSession() {
        ConfigField interval = field("tablist", "update-interval");
        Gui gui = category("tablist");

        click(gui, slotOf("tablist", interval));

        assertThat(editor.current(interval)).isEqualTo("1s");
        assertThat(session.restartCount()).isEqualTo(1);
        assertThat(session.hotReloadCount()).isZero();
    }

    @Test
    void moduleEnabledField_clickRecordsRestartOnlySession() {
        ConfigField enabled = field("combat", "enabled");
        Gui gui = category("modules");

        click(gui, slotOf("modules", enabled));

        assertThat(editor.current(enabled)).isEqualTo(false);
        assertThat(session.restartCount()).isEqualTo(1);
        assertThat(session.hotReloadCount()).isZero();
    }

    @Test
    void rejectedEdit_reportsReasonAndRecordsNothing() {
        ConfigField field =
                new ConfigField(
                        List.of("modules", "test", "value"),
                        ConfigFieldType.INTEGER,
                        ConfigValidators.integerRange(1, 1),
                        true,
                        ConfigLifecycle.HOT_RELOAD);
        ServerConfigService rejecting =
                new SingleFieldService(field, new ConfigEditResult.Rejected("out of range"));
        Gui gui =
                ServerConfigGui.buildCategory(
                        rejecting, messages, player, navigator, session, "test");

        click(gui, ServerConfigGui.entrySlot(0));

        assertThat(messages.sentKeys).contains("server-config.edit-rejected");
        assertThat(messages.sent).anyMatch(text -> text.contains("out of range"));
        assertThat(session.isEmpty()).isTrue();
    }

    // ------------------------------------------------------------------
    // Text entry and navigation
    // ------------------------------------------------------------------

    @Test
    void textField_clickRequestsChatInput() {
        ConfigField format = field("chat-format", "format");
        Gui gui = category("chat-format");

        click(gui, slotOf("chat-format", format));

        assertThat(navigator.textInputs).containsExactly(ServerConfigPage.of("chat-format"));
    }

    @Test
    void categoryEntry_opensCategoryPage() {
        Gui gui = ServerConfigGui.buildRoot(editor, messages, player, navigator);
        List<String> categories = ServerConfigGui.categories(editor.schema());
        int index = categories.indexOf("combat");

        click(gui, ServerConfigGui.entrySlot(index));

        assertThat(navigator.serverConfigPages).containsExactly(ServerConfigPage.of("combat"));
    }

    @Test
    void categoryBack_returnsToCategories() {
        Gui gui = category("combat");

        click(gui, ServerConfigGui.SLOT_BACK);

        assertThat(navigator.serverConfigPages).containsExactly(ServerConfigPage.root());
    }

    @Test
    void rootBack_returnsToSettings() {
        Gui gui = ServerConfigGui.buildRoot(editor, messages, player, navigator);

        click(gui, ServerConfigGui.SLOT_BACK);

        assertThat(navigator.openedSettingsRoot).isTrue();
    }

    // ------------------------------------------------------------------
    // Test doubles
    // ------------------------------------------------------------------

    private static final class NavigatorRecorder implements ServerConfigGui.Navigator {
        private final List<ServerConfigPage> serverConfigPages = new ArrayList<>();
        private final List<ServerConfigPage> textInputs = new ArrayList<>();
        private boolean openedSettingsRoot;

        @Override
        public void openServerConfig(Player player, ServerConfigPage page) {
            serverConfigPages.add(page);
        }

        @Override
        public void openSettingsRoot(Player player) {
            openedSettingsRoot = true;
        }

        @Override
        public void requestTextInput(Player player, ConfigField field, ServerConfigPage page) {
            textInputs.add(page);
        }
    }

    private record SingleFieldService(ConfigField field, ConfigEditResult result)
            implements
                ServerConfigService {

        @Override
        public ServerConfigSchema schema() {
            return new ServerConfigSchema(List.of(field));
        }

        @Override
        public Object current(ConfigField ignored) {
            return 1;
        }

        @Override
        public ConfigEditResult edit(ConfigField ignored, Object value) {
            return result;
        }
    }
}
