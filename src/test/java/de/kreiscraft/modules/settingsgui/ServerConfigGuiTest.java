package de.kreiscraft.modules.settingsgui;

import static org.assertj.core.api.Assertions.assertThat;

import de.kreiscraft.core.config.ConfigEditResult;
import de.kreiscraft.core.config.ConfigField;
import de.kreiscraft.core.config.ConfigFieldType;
import de.kreiscraft.core.config.ConfigLifecycle;
import de.kreiscraft.core.config.ConfigValidators;
import de.kreiscraft.core.config.ServerConfigSchema;
import de.kreiscraft.core.config.YamlConfigService;
import de.kreiscraft.core.message.ConfigurateMessageService;
import de.kreiscraft.testsupport.RecordingMessageService;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
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
        return ServerConfigGui.fieldSlot(editor.schema(), category, field);
    }

    private ConfigField field(String module, String key) {
        return editor.schema().find(List.of("modules", module, key)).orElseThrow();
    }

    private ConfigField coreField(String key) {
        return editor.schema().find(List.of("core", key)).orElseThrow();
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
        assertThat(categories).contains("combat", "effects", "spawn", "chat-format", "wither");
        assertThat(categories).doesNotContain("modules");
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
        assertThat(ServerConfigGui.fieldsIn(editor.schema(), "api"))
                .as("the HTTP API section exposes only its module toggle")
                .allMatch(field -> field.path().get(2).equals("enabled"));
    }

    @Test
    void root_listsTheCompleteCategorySet() {
        List<String> categories = ServerConfigGui.categories(editor.schema());

        assertThat(categories)
                .contains(
                        "core",
                        "admin",
                        "api",
                        "anti-cheat",
                        "bedrock",
                        "chat-format",
                        "clickthrough",
                        "combat",
                        "effects",
                        "firework",
                        "glow",
                        "invsee",
                        "jail",
                        "positions",
                        "reboot",
                        "settings-gui",
                        "sign-color",
                        "spawn",
                        "start-sequence",
                        "status",
                        "tablist",
                        "update-mode",
                        "wither",
                        "world-mgmt");
    }

    @Test
    void moduleEntries_useRepresentativeIcons() {
        Gui gui = ServerConfigGui.buildRoot(editor, messages, player, navigator);
        List<String> categories = ServerConfigGui.categories(editor.schema());

        assertThat(stackAt(gui, ServerConfigGui.entrySlot(categories.indexOf("wither"))).getType())
                .isEqualTo(org.bukkit.Material.WITHER_SKELETON_SKULL);
        for (int index = 0; index < categories.size(); index++) {
            assertThat(stackAt(gui, ServerConfigGui.entrySlot(index)).getType())
                    .isNotIn(org.bukkit.Material.CHEST, org.bukkit.Material.PAPER);
        }
    }

    @Test
    void categoryEntry_marksDisabledModule() {
        Gui gui = ServerConfigGui.buildRoot(editor, messages, player, navigator);
        int firework = ServerConfigGui.categories(editor.schema()).indexOf("firework");

        assertThat(plain(stackAt(gui, ServerConfigGui.entrySlot(firework))))
                .contains("server-config.category-disabled");
    }

    @Test
    void categoryEntry_omitsDisabledMarkerForEnabledModule() {
        Gui gui = ServerConfigGui.buildRoot(editor, messages, player, navigator);
        int combat = ServerConfigGui.categories(editor.schema()).indexOf("combat");

        assertThat(plain(stackAt(gui, ServerConfigGui.entrySlot(combat))))
                .doesNotContain("server-config.category-disabled");
    }

    @Test
    void categoryEntry_coreHasNoEnableFlagAndIsNeverMarkedDisabled() {
        Gui gui = ServerConfigGui.buildRoot(editor, messages, player, navigator);
        int core = ServerConfigGui.categories(editor.schema()).indexOf("core");

        assertThat(plain(stackAt(gui, ServerConfigGui.entrySlot(core))))
                .doesNotContain("server-config.category-disabled");
    }

    @Test
    void categoryEntry_countExcludesTheModuleToggleOnlyWhenOneExists() {
        Gui gui = ServerConfigGui.buildRoot(editor, messages, player, navigator);
        List<String> categories = ServerConfigGui.categories(editor.schema());

        int combat = categories.indexOf("combat");
        assertThat(plain(stackAt(gui, ServerConfigGui.entrySlot(combat))))
                .contains("(" + (ServerConfigGui.fieldsIn(editor.schema(), "combat").size() - 1) + ")");

        int core = categories.indexOf("core");
        assertThat(plain(stackAt(gui, ServerConfigGui.entrySlot(core))))
                .contains("(" + ServerConfigGui.fieldsIn(editor.schema(), "core").size() + ")");
    }

    @Test
    void categoryDisabledMarker_rendersTheLocalizedSuffix(@TempDir Path tmp) throws IOException {
        ConfigurateMessageService english =
                new ConfigurateMessageService(tmp, getClass().getClassLoader(), "en", server);
        assertThat(
                PlainTextComponentSerializer.plainText()
                        .serialize(english.render("server-config.category-disabled")))
                                .isEqualTo(" | disabled");

        ConfigurateMessageService german =
                new ConfigurateMessageService(tmp, getClass().getClassLoader(), "de", server);
        assertThat(
                PlainTextComponentSerializer.plainText()
                        .serialize(german.render("server-config.category-disabled")))
                                .isEqualTo(" | deaktiviert");
    }

    @Test
    void everyModuleEnableToggle_isGlassPaneInFarLeftBottomSlotWithRestartLore() {
        List<ConfigField> toggles = editor.schema().guiExposed().stream()
                .filter(field -> field.path().size() == 3 && field.path().get(2).equals("enabled"))
                .toList();

        assertThat(toggles).isNotEmpty();
        for (ConfigField toggleField : toggles) {
            Gui gui = category(String.valueOf(toggleField.path().get(1)));
            ItemStack toggle = stackAt(gui, ServerConfigGui.SLOT_MODULE_ENABLED);
            assertThat(toggle.getType())
                    .isIn(
                            org.bukkit.Material.LIME_STAINED_GLASS_PANE,
                            org.bukkit.Material.RED_STAINED_GLASS_PANE);
            assertThat(toggle.getItemMeta().lore())
                    .isNotEmpty()
                    .allSatisfy(line -> assertThat(
                            PlainTextComponentSerializer.plainText().serialize(line))
                                    .containsIgnoringCase("restart"));
        }
    }

    @Test
    void witherExplosionPhases_eachOccupyTheirOwnRow() {
        ConfigField spawnPower = field("wither", "spawn-explosion-power");
        ConfigField halfHealthPower = field("wither", "half-health-explosion-power");
        ConfigField deathPower = field("wither", "death-explosion-power");
        int spawn = slotOf("wither", spawnPower);
        int spawnFire = slotOf("wither", field("wither", "spawn-explosion-fire"));
        int halfHealth = slotOf("wither", halfHealthPower);
        int death = slotOf("wither", deathPower);

        assertThat(spawn / 9).isEqualTo(spawnFire / 9);
        assertThat(List.of(spawn / 9, halfHealth / 9, death / 9)).doesNotHaveDuplicates();

        Gui gui = category("wither");
        double spawnBefore = (Double) editor.current(spawnPower);
        double halfHealthBefore = (Double) editor.current(halfHealthPower);
        double deathBefore = (Double) editor.current(deathPower);
        click(gui, spawn);
        click(gui, halfHealth);
        click(gui, death);
        assertThat(editor.current(spawnPower)).isEqualTo(spawnBefore + 0.1);
        assertThat(editor.current(halfHealthPower)).isEqualTo(halfHealthBefore + 0.1);
        assertThat(editor.current(deathPower)).isEqualTo(deathBefore + 0.1);
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
    void particleEnumField_selectionPersistsThroughEditFlow() {
        ConfigField particle = field("effects", "default-particle");
        Gui gui = category("effects");

        click(gui, slotOf("effects", particle));
        assertThat(editor.current(particle)).isEqualTo("HAPPY_VILLAGER");

        navigator.select(Particle.HEART);

        assertThat(editor.current(particle)).isEqualTo("HEART");
        assertThat(session.hotReloadCount()).isEqualTo(1);
    }

    @Test
    void particleEnumField_cancellationLeavesValueUnchanged() {
        ConfigField particle = field("effects", "default-particle");
        Gui gui = category("effects");

        click(gui, slotOf("effects", particle));
        navigator.cancel();

        assertThat(editor.current(particle)).isEqualTo("HAPPY_VILLAGER");
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
        Gui gui = category("combat");

        click(gui, ServerConfigGui.SLOT_MODULE_ENABLED);

        assertThat(editor.current(enabled)).isEqualTo(false);
        assertThat(session.restartCount()).isEqualTo(1);
        assertThat(session.hotReloadCount()).isZero();
    }

    @Test
    void fieldNames_useDescriptorAndValueColorRoles() {
        ConfigField count = field("effects", "count");
        ConfigField enabled = field("combat", "disable-elytra");

        assertThat(textRunColors(stackAt(category("effects"), slotOf("effects", count))))
                .containsExactly(NamedTextColor.GRAY, NamedTextColor.AQUA);
        assertThat(textRunColors(stackAt(category("combat"), slotOf("combat", enabled))))
                .containsExactly(NamedTextColor.GRAY, NamedTextColor.GREEN);
    }

    @Test
    void fieldLore_isShownOnlyForRestartRequiredFields() {
        ConfigField hotReload = field("effects", "count");
        ConfigField restart = field("tablist", "update-interval");

        assertThat(stackAt(category("effects"), slotOf("effects", hotReload)).getItemMeta().lore())
                .isNull();
        assertThat(loreColor(stackAt(category("tablist"), slotOf("tablist", restart))))
                .isEqualTo(NamedTextColor.RED);
    }

    @Test
    void coreLanguage_usesGermanAndUkHeadsAndKeepsRestartLifecycle() {
        ConfigField language = coreField("language");
        Gui gui = category("core");
        int slot = slotOf("core", language);

        ItemStack german = stackAt(gui, slot);
        assertThat(german.getType()).isEqualTo(Material.PLAYER_HEAD);
        assertThat(texture(german)).isEqualTo(SettingsGui.TEXTURE_DE);

        click(gui, slot);

        ItemStack english = stackAt(gui, slot);
        assertThat(editor.current(language)).isEqualTo("en");
        assertThat(english.getType()).isEqualTo(Material.PLAYER_HEAD);
        assertThat(texture(english)).isEqualTo(SettingsGui.TEXTURE_EN);
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
    void textField_clickRequestsAnvilEditor() {
        ConfigField format = field("chat-format", "format");
        Gui gui = category("chat-format");

        click(gui, slotOf("chat-format", format));

        assertThat(navigator.stringEditors).containsExactly(ServerConfigPage.of("chat-format"));
    }

    @Test
    void listField_clickRequestsAnvilEditor() {
        ConfigField worlds = field("combat", "excluded-worlds");
        Gui gui = category("combat");

        click(gui, slotOf("combat", worlds));

        assertThat(navigator.listEditors).containsExactly(ServerConfigPage.of("combat"));
        assertThat(navigator.stringEditors).isEmpty();
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

    private static List<TextColor> textRunColors(ItemStack stack) {
        List<TextColor> colors = new ArrayList<>();
        collectTextRunColors(stack.getItemMeta().displayName(), Style.empty(), colors);
        return colors;
    }

    private static void collectTextRunColors(
            Component component, Style inherited, List<TextColor> colors) {
        Style effective = component.style().merge(inherited, Style.Merge.Strategy.IF_ABSENT_ON_TARGET);
        if (component instanceof TextComponent text && !text.content().isEmpty()) {
            TextColor color = effective.color();
            if (colors.isEmpty() || !colors.getLast().equals(color)) {
                colors.add(color);
            }
        }
        for (Component child : component.children()) {
            collectTextRunColors(child, effective, colors);
        }
    }

    private static TextColor loreColor(ItemStack stack) {
        List<Component> lore = stack.getItemMeta().lore();
        assertThat(lore).isNotNull().hasSize(1);
        List<TextColor> colors = new ArrayList<>();
        collectTextRunColors(lore.getFirst(), Style.empty(), colors);
        return colors.getFirst();
    }

    private static String texture(ItemStack stack) {
        SkullMeta meta = (SkullMeta) stack.getItemMeta();
        return meta.getPlayerProfile().getProperties().stream()
                .filter(property -> property.getName().equals("textures"))
                .findFirst()
                .orElseThrow()
                .getValue();
    }

    // ------------------------------------------------------------------
    // Test doubles
    // ------------------------------------------------------------------

    private static final class NavigatorRecorder implements ServerConfigGui.Navigator {
        private final List<ServerConfigPage> serverConfigPages = new ArrayList<>();
        private final List<ServerConfigPage> stringEditors = new ArrayList<>();
        private final List<ServerConfigPage> listEditors = new ArrayList<>();
        private Consumer<Particle> particleSelection;
        private Runnable particleCancellation;
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
        public void openStringEditor(Player player, ConfigField field, ServerConfigPage page) {
            stringEditors.add(page);
        }

        @Override
        public void openListEditor(Player player, ConfigField field, ServerConfigPage page) {
            listEditors.add(page);
        }

        @Override
        public void pickParticle(
                Player player, Consumer<Particle> selection, Runnable cancellation) {
            particleSelection = selection;
            particleCancellation = cancellation;
        }

        private void select(Particle particle) {
            particleSelection.accept(particle);
        }

        private void cancel() {
            particleCancellation.run();
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
