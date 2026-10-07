package de.kreiscraft.modules.settingsgui;

import de.kreiscraft.core.config.ConfigField;
import de.kreiscraft.shared.preferences.CombatDisplay;
import java.util.Arrays;
import java.util.List;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/**
 * Deterministic value transforms for the Server Configuration GUI's typed controls.
 *
 * <p>Each supported {@link de.kreiscraft.core.config.ConfigFieldType} maps to one interaction:
 * booleans toggle, numbers step, durations/enums/materials cycle a bounded option set, and a
 * location is set to the editing player's current position. Text and text lists are not transformed
 * here; they are entered through the chat-input flow so free-form values stay editable.
 */
public final class ServerConfigControls {

    /** Bounded duration choices offered by the duration control. */
    static final List<String> DURATION_OPTIONS = List.of("1s", "3s", "5s", "10s", "30s", "1m");

    /** Bounded material choices offered by the material control. */
    static final List<String> MATERIAL_OPTIONS = List.of(
            Material.STONE.name(),
            Material.DIRT.name(),
            Material.GRASS_BLOCK.name(),
            Material.OAK_LOG.name(),
            Material.GLASS.name(),
            Material.IRON_INGOT.name(),
            Material.DIAMOND.name());

    private ServerConfigControls() {}

    /** Whether the field's value is entered as free text through chat rather than cycled. */
    public static boolean isTextEntry(ConfigField field) {
        return switch (field.type()) {
            case STRING, STRING_LIST -> true;
            default -> false;
        };
    }

    /** The value applied by a left-click (or primary interaction). */
    public static Object next(ConfigField field, Object current, Player player) {
        return adjust(field, current, player, 1);
    }

    /** The value applied by a right-click (or secondary interaction). */
    public static Object previous(ConfigField field, Object current, Player player) {
        return adjust(field, current, player, -1);
    }

    private static Object adjust(ConfigField field, Object current, Player player, int direction) {
        return switch (field.type()) {
            case BOOLEAN -> !asBoolean(current);
            case INTEGER -> asInt(current) + direction;
            case DOUBLE -> round1(asDouble(current) + 0.1 * direction);
            case DURATION -> cycle(DURATION_OPTIONS, current, direction);
            case ENUM -> cycle(combatDisplayOptions(field), current, direction);
            case MATERIAL -> cycle(MATERIAL_OPTIONS, current, direction);
            case LOCATION -> player.getLocation();
            case STRING, STRING_LIST -> null;
        };
    }

    /** The enum options for an enum-typed field, in declaration order. */
    static List<String> combatDisplayOptions(ConfigField field) {
        if (field.path().size() == 3
                && "modules".equals(String.valueOf(field.path().get(0)))
                && "combat".equals(String.valueOf(field.path().get(1)))
                && "default-display".equals(String.valueOf(field.path().get(2)))) {
            return Arrays.stream(CombatDisplay.values()).map(Enum::name).toList();
        }
        return List.of();
    }

    static String cycle(List<String> options, Object current, int direction) {
        if (options.isEmpty()) {
            return null;
        }
        int index = current == null ? -1 : options.indexOf(String.valueOf(current));
        int nextIndex;
        if (index < 0) {
            nextIndex = direction >= 0 ? 0 : options.size() - 1;
        } else {
            nextIndex = Math.floorMod(index + direction, options.size());
        }
        return options.get(nextIndex);
    }

    private static boolean asBoolean(Object value) {
        return value instanceof Boolean bool && bool;
    }

    private static int asInt(Object value) {
        return value instanceof Number number ? number.intValue() : 0;
    }

    private static double asDouble(Object value) {
        return value instanceof Number number ? number.doubleValue() : 0.0;
    }

    private static double round1(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}
