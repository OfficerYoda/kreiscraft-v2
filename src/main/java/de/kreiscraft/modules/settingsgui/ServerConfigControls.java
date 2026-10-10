package de.kreiscraft.modules.settingsgui;

import de.kreiscraft.core.config.ConfigField;
import java.util.List;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/**
 * Deterministic value transforms for the Server Configuration GUI's typed controls.
 *
 * <p>Each supported {@link de.kreiscraft.core.config.ConfigFieldType} maps to one interaction:
 * booleans toggle, numbers step, durations/enums/materials cycle a bounded option set that the
 * field declares, and a location is set to the editing player's current position. Text and text
 * lists are not transformed here; strings use chat input and lists use the anvil editor.
 */
public final class ServerConfigControls {

    /** Fallback duration choices for a duration field that declares no options of its own. */
    static final List<String> DURATION_OPTIONS = List.of("1s", "3s", "5s", "10s", "30s", "1m");

    /** Fallback material choices for a material field that declares no options of its own. */
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
            case STRING -> true;
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
            case DURATION -> cycle(optionsOr(field, DURATION_OPTIONS), current, direction);
            case ENUM -> cycle(field.options(), current, direction);
            case MATERIAL -> cycle(optionsOr(field, MATERIAL_OPTIONS), current, direction);
            case LOCATION -> player.getLocation();
            case STRING, STRING_LIST, INT_LIST -> null;
        };
    }

    /** The field's declared options, or a type default when the field declares none. */
    static List<String> optionsOr(ConfigField field, List<String> fallback) {
        return field.options().isEmpty() ? fallback : field.options();
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
