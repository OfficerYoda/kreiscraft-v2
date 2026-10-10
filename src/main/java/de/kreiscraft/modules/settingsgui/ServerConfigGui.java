package de.kreiscraft.modules.settingsgui;

import de.kreiscraft.core.config.ConfigEditResult;
import de.kreiscraft.core.config.ConfigField;
import de.kreiscraft.core.config.ServerConfigSchema;
import de.kreiscraft.core.message.MessageService;
import de.kreiscraft.shared.preferences.PlayerLanguage;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import xyz.xenondevs.invui.Click;
import xyz.xenondevs.invui.gui.Gui;
import xyz.xenondevs.invui.item.AbstractItem;
import xyz.xenondevs.invui.item.ItemProvider;
import xyz.xenondevs.invui.item.ItemWrapper;

/**
 * Builds the InvUI {@link Gui} pages for the authorized Server Configuration editor.
 *
 * <p>The GUI enumerates the GUI-exposed fields declared by the {@link ServerConfigSchema} and groups
 * them by category: one page lists the categories, and each category page lists that category's
 * fields. Every edit is applied through {@link ServerConfigService}, so a value is validated and
 * durably persisted the moment it is clicked; rejected and failed edits change nothing and are
 * reported to the operator.
 *
 * <p>Layout is fixed so tests (and operators) can rely on stable slots:
 *
 * <pre>
 * Root page (6 rows)              Category page (6 rows)
 * slot  0..  Category entries     slot  0..  Field entries
 * slot 53   Back to Settings      slot 53   Back to categories
 * </pre>
 */
public final class ServerConfigGui {

    /** Slot that returns one level up. */
    static final int SLOT_BACK = 53;

    /** Module enablement is always the far-left slot of the bottom row. */
    static final int SLOT_MODULE_ENABLED = 45;

    /** First slot used for a category or field entry. */
    static final int FIRST_ENTRY_SLOT = 0;

    /** Navigation callback used by the Server Configuration pages. */
    public interface Navigator {

        /** Opens a Server Configuration page for the player. */
        void openServerConfig(Player player, ServerConfigPage page);

        /** Leaves Server Configuration and reopens the Player Settings root. */
        void openSettingsRoot(Player player);

        /** Closes the GUI and asks the player to type a free-form value in chat. */
        void requestTextInput(Player player, ConfigField field, ServerConfigPage page);

        /** Opens the anvil editor for a list-valued field. */
        void openListEditor(Player player, ConfigField field, ServerConfigPage page);

        /** Opens the shared searchable particle picker. */
        default void pickParticle(
                Player player, Consumer<Particle> selection, Runnable cancellation) {
            ParticlePicker.open(player, selection, cancellation);
        }
    }

    private ServerConfigGui() {}

    /** The slot used for the {@code index}-th entry on a page. */
    static int entrySlot(int index) {
        return FIRST_ENTRY_SLOT + index;
    }

    /** The category keys declared by the schema, in stable order. */
    static List<String> categories(ServerConfigSchema schema) {
        return schema.guiExposed().stream()
                .map(ServerConfigGui::categoryOf)
                .distinct()
                .sorted()
                .toList();
    }

    /** The category a field belongs to. Module enablement stays with its owning module. */
    static String categoryOf(ConfigField field) {
        return field.category();
    }

    /** The GUI-exposed fields in one category, in stable order. */
    static List<ConfigField> fieldsIn(ServerConfigSchema schema, String category) {
        return schema.guiExposed().stream()
                .filter(field -> categoryOf(field).equals(category))
                .toList();
    }

    /** Stable slot for a field, with each related group occupying one row. */
    static int fieldSlot(ServerConfigSchema schema, String category, ConfigField field) {
        if (isModuleEnabled(field)) {
            return SLOT_MODULE_ENABLED;
        }
        int row = 0;
        for (List<ConfigField> group : fieldGroups(schema, category).values()) {
            int column = group.indexOf(field);
            if (column >= 0) {
                return row * 9 + column;
            }
            row++;
        }
        throw new IllegalArgumentException("Field is not in category " + category);
    }

    /** Builds the category-list page. */
    public static Gui buildRoot(
            ServerConfigService editor,
            MessageService messages,
            Player player,
            Navigator navigator) {
        Gui gui = Gui.empty(9, 6);
        List<String> categories = categories(editor.schema());
        for (int index = 0; index < categories.size(); index++) {
            String category = categories.get(index);
            gui.setItem(entrySlot(index), categoryItem(editor, messages, navigator, category));
        }
        gui.setItem(SLOT_BACK, backItem(messages, navigator, false));
        return gui;
    }

    /** Builds one category's field-editor page. */
    public static Gui buildCategory(
            ServerConfigService editor,
            MessageService messages,
            Player player,
            Navigator navigator,
            ServerConfigSession session,
            String category) {
        Gui gui = Gui.empty(9, 6);
        List<ConfigField> fields = fieldsIn(editor.schema(), category);
        for (ConfigField field : fields) {
            gui.setItem(
                    fieldSlot(editor.schema(), category, field),
                    fieldItem(editor, messages, navigator, session, field, category));
        }
        gui.setItem(SLOT_BACK, backItem(messages, navigator, true));
        return gui;
    }

    // ------------------------------------------------------------------
    // Item factories
    // ------------------------------------------------------------------

    /** The language key for a category label. */
    static String categoryKey(String category) {
        return "server-config.category." + category;
    }

    /** The language key for a field label. */
    static String fieldKey(ConfigField field) {
        return "server-config.field." + labelSlug(field);
    }

    private static AbstractItem categoryItem(
            ServerConfigService editor, MessageService messages, Navigator navigator, String category) {
        return new AbstractItem() {
            @Override
            public ItemProvider getItemProvider(Player viewer) {
                int count = fieldsIn(editor.schema(), category).size() - 1; // -1: don´t count the module toggle
                Component name = messages.renderFor(viewer, categoryKey(category))
                        .append(Component.text(" (" + count + ")").color(NamedTextColor.DARK_GRAY));
                return new ItemWrapper(named(categoryMaterial(category), name));
            }

            @Override
            public void handleClick(ClickType clickType, Player player, Click click) {
                navigator.openServerConfig(player, ServerConfigPage.of(category));
            }
        };
    }

    private static AbstractItem fieldItem(
            ServerConfigService editor,
            MessageService messages,
            Navigator navigator,
            ServerConfigSession session,
            ConfigField field,
            String category) {
        return new AbstractItem() {
            @Override
            public ItemProvider getItemProvider(Player viewer) {
                Object current = editor.current(field);
                return new ItemWrapper(fieldStack(field, current, messages, viewer));
            }

            @Override
            public void handleClick(ClickType clickType, Player player, Click click) {
                if (isParticle(field)) {
                    navigator.pickParticle(
                            player,
                            particle -> {
                                applyResult(
                                        messages,
                                        session,
                                        player,
                                        field,
                                        editor.edit(field, particle.name()));
                                navigator.openServerConfig(player, ServerConfigPage.of(category));
                            },
                            () -> navigator.openServerConfig(
                                    player, ServerConfigPage.of(category)));
                    return;
                }
                if (field.type() == de.kreiscraft.core.config.ConfigFieldType.STRING_LIST) {
                    navigator.openListEditor(player, field, ServerConfigPage.of(category));
                    return;
                }
                if (ServerConfigControls.isTextEntry(field)) {
                    navigator.requestTextInput(player, field, ServerConfigPage.of(category));
                    return;
                }
                Object current = editor.current(field);
                Object target =
                        clickType.isRightClick()
                                ? ServerConfigControls.previous(field, current, player)
                                : ServerConfigControls.next(field, current, player);
                applyResult(messages, session, player, field, editor.edit(field, target));
                notifyWindows();
            }
        };
    }

    private static boolean isParticle(ConfigField field) {
        return field.path().equals(ServerConfigSchema.EFFECTS_DEFAULT_PARTICLE_PATH);
    }

    private static void applyResult(
            MessageService messages,
            ServerConfigSession session,
            Player player,
            ConfigField field,
            ConfigEditResult result) {
        if (result instanceof ConfigEditResult.Applied) {
            session.recordApplied(field);
        } else if (result instanceof ConfigEditResult.Rejected rejected) {
            messages.send(player, "server-config.edit-rejected", "reason", rejected.reason());
        } else if (result instanceof ConfigEditResult.Failed) {
            messages.send(player, "server-config.edit-failed");
        }
    }

    private static AbstractItem backItem(
            MessageService messages, Navigator navigator, boolean toCategories) {
        return new AbstractItem() {
            @Override
            public ItemProvider getItemProvider(Player viewer) {
                return new ItemWrapper(
                        named(Material.ARROW, messages.renderFor(viewer, "settings-gui.back")));
            }

            @Override
            public void handleClick(ClickType clickType, Player player, Click click) {
                if (toCategories) {
                    navigator.openServerConfig(player, ServerConfigPage.root());
                } else {
                    navigator.openSettingsRoot(player);
                }
            }
        };
    }

    // ------------------------------------------------------------------
    // Labels and values (package-private for tests)
    // ------------------------------------------------------------------

    /** The language slug used to look up a field's label. */
    static String labelSlug(ConfigField field) {
        List<Object> path = field.path();
        if (path.size() >= 3 && "enabled".equals(String.valueOf(path.get(2)))) {
            return "module-enabled";
        }
        return path.stream().skip(1).map(String::valueOf).collect(Collectors.joining("-"));
    }

    private static boolean isModuleEnabled(ConfigField field) {
        return field.moduleEnabled();
    }

    private static Map<String, List<ConfigField>> fieldGroups(
            ServerConfigSchema schema, String category) {
        Map<String, List<ConfigField>> groups = new LinkedHashMap<>();
        for (ConfigField field : fieldsIn(schema, category)) {
            if (!isModuleEnabled(field)) {
                groups.computeIfAbsent(groupOf(field), ignored -> new java.util.ArrayList<>())
                        .add(field);
            }
        }
        return groups;
    }

    private static String groupOf(ConfigField field) {
        String key = String.valueOf(field.path().get(field.path().size() - 1));
        if (key.startsWith("spawn-explosion-")) {
            return "spawn";
        }
        if (key.startsWith("half-health-explosion-")) {
            return "half-health";
        }
        if (key.startsWith("death-explosion-")) {
            return "death";
        }
        return field.path().size() > 3 ? String.valueOf(field.path().get(2)) : "general";
    }

    /** Representative icon for each module/core section. */
    static Material categoryMaterial(String category) {
        return switch (category) {
            case "core" -> Material.COMMAND_BLOCK;
            case "admin" -> Material.GOLD_BLOCK;
            case "api" -> Material.REDSTONE_TORCH;
            case "anti-cheat" -> Material.SHIELD;
            case "bedrock" -> Material.BEDROCK;
            case "chat-format" -> Material.WRITABLE_BOOK;
            case "clickthrough" -> Material.ENDER_CHEST;
            case "combat" -> Material.IRON_SWORD;
            case "effects" -> Material.BLAZE_POWDER;
            case "firework" -> Material.FIREWORK_ROCKET;
            case "glow" -> Material.GLOW_INK_SAC;
            case "invsee" -> Material.BARREL;
            case "jail" -> Material.IRON_BARS;
            case "positions" -> Material.COMPASS;
            case "reboot" -> Material.CLOCK;
            case "settings-gui" -> Material.NETHER_STAR;
            case "sign-color" -> Material.OAK_SIGN;
            case "spawn" -> Material.RECOVERY_COMPASS;
            case "start-sequence" -> Material.DRAGON_EGG;
            case "status" -> Material.NAME_TAG;
            case "tablist" -> Material.PLAYER_HEAD;
            case "update-mode" -> Material.REDSTONE_LAMP;
            case "wither" -> Material.WITHER_SKELETON_SKULL;
            case "world-mgmt" -> Material.GRASS_BLOCK;
            default -> Material.PAPER;
        };
    }

    /** The localized label for a field. */
    static Component label(ConfigField field, MessageService messages, Player viewer) {
        if (labelSlug(field).equals("module-enabled")) {
            return messages.renderFor(
                    viewer,
                    "server-config.field.module-enabled",
                    "module",
                    humanize(String.valueOf(field.path().get(1))))
                    .colorIfAbsent(NamedTextColor.GRAY);
        }
        return messages.renderFor(viewer, fieldKey(field)).colorIfAbsent(NamedTextColor.GRAY);
    }

    /** The current value rendered for display. */
    @SuppressWarnings("unchecked")
    static Component value(
            ConfigField field, Object current, MessageService messages, Player viewer) {
        if (current == null) {
            return messages.renderFor(viewer, "server-config.value-none")
                    .colorIfAbsent(NamedTextColor.DARK_GRAY);
        }
        return switch (field.type()) {
            case BOOLEAN -> messages.renderFor(
                    viewer,
                    ((Boolean) current)
                            ? "server-config.value-on"
                            : "server-config.value-off")
                    .colorIfAbsent(Boolean.TRUE.equals(current) ? NamedTextColor.GREEN : NamedTextColor.RED);
            case LOCATION -> Component.text(formatLocation((Location) current), NamedTextColor.AQUA);
            case STRING_LIST -> Component.text(String.join(", ", (List<String>) current), NamedTextColor.AQUA);
            default -> Component.text(String.valueOf(current), NamedTextColor.AQUA);
        };
    }

    private static Component name(
            ConfigField field, Object current, MessageService messages, Player viewer) {
        return label(field, messages, viewer)
                .append(Component.text(": ").color(NamedTextColor.GRAY))
                .append(value(field, current, messages, viewer));
    }

    private static ItemStack fieldStack(
            ConfigField field, Object current, MessageService messages, Player viewer) {
        Component displayName = name(field, current, messages, viewer);
        ItemStack stack;
        if (field.path().equals(List.of("core", "language"))) {
            PlayerLanguage language = PlayerLanguage.fromCode(String.valueOf(current))
                    .orElse(PlayerLanguage.DE);
            stack = SettingsGui.flagHead(language, displayName);
        } else if (isModuleEnabled(field)) {
            stack = named(
                    Boolean.TRUE.equals(current)
                            ? Material.LIME_STAINED_GLASS_PANE
                            : Material.RED_STAINED_GLASS_PANE,
                    displayName);
        } else {
            stack = named(material(field, current), displayName);
        }

        if (field.lifecycle().restartRequired()) {
            Component lifecycle = messages.renderFor(viewer, field.lifecycle().messageKey())
                    .colorIfAbsent(field.hotReloadable() ? NamedTextColor.GREEN : NamedTextColor.RED)
                    .decoration(TextDecoration.ITALIC, false);
            stack.editMeta(meta -> meta.lore(List.of(lifecycle)));
        }
        return stack;
    }

    /** The icon material for a field, reflecting its type and boolean state. */
    static Material material(ConfigField field, Object current) {
        return switch (field.type()) {
            case BOOLEAN -> Boolean.TRUE.equals(current) ? Material.LIME_DYE : Material.RED_DYE;
            case INTEGER -> Material.REPEATER;
            case DOUBLE -> Material.COMPARATOR;
            case DURATION -> Material.CLOCK;
            case ENUM -> Material.BOOK;
            case MATERIAL -> Material.STONE;
            case LOCATION -> Material.COMPASS;
            case STRING -> Material.NAME_TAG;
            case STRING_LIST -> Material.WRITABLE_BOOK;
        };
    }

    static String formatLocation(Location location) {
        String world = location.getWorld() != null ? location.getWorld().getName() : "?";
        return world
                + " "
                + Math.round(location.getX())
                + ", "
                + Math.round(location.getY())
                + ", "
                + Math.round(location.getZ());
    }

    /** Turns a module id such as {@code chat-format} into {@code Chat Format}. */
    static String humanize(String id) {
        String[] words = id.split("[-_]");
        StringBuilder out = new StringBuilder();
        for (String word : words) {
            if (word.isEmpty()) {
                continue;
            }
            if (out.length() > 0) {
                out.append(' ');
            }
            out.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return out.toString();
    }

    private static ItemStack named(Material material, Component displayName) {
        ItemStack stack = new ItemStack(material);
        stack.editMeta(meta -> meta.displayName(displayName.decoration(TextDecoration.ITALIC, false)));
        return stack;
    }
}
