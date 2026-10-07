package de.kreiscraft.modules.signcolor;

import de.kreiscraft.core.message.PlaceholderResolver;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.minimessage.tag.standard.StandardTags;
import org.bukkit.entity.Player;

/**
 * Renders player-authored sign and anvil text into an Adventure {@link Component}.
 *
 * <p>Three input styles are supported: established
 * Kreiscraft {@code %token%} placeholders, legacy ampersand colour and decoration codes (for
 * example {@code &c} or {@code &l}), and MiniMessage tags.
 *
 * <p>MiniMessage is deliberately restricted to colour, decoration, rainbow, reset and gradient
 * tags so that players cannot inject interactive or client-affecting tags such as
 * {@code <click>}, {@code <hover>}, {@code <font>}, {@code <nbt>} or {@code <selector>}.
 * Unsupported tags are rendered as literal text rather than parsed.
 */
final class LegacyTextFormatter {

    private static final MiniMessage MINI_MESSAGE =
            MiniMessage.builder()
                    .tags(
                            TagResolver.resolver(
                                    StandardTags.color(),
                                    StandardTags.decorations(),
                                    StandardTags.rainbow(),
                                    StandardTags.reset(),
                                    StandardTags.gradient()))
                    .build();

    private final PlaceholderResolver placeholders;

    LegacyTextFormatter(PlaceholderResolver placeholders) {
        this.placeholders = placeholders;
    }

    Component format(String raw, Player player) {
        String resolved = placeholders.resolveString(raw, player);
        return MINI_MESSAGE.deserialize(translateLegacyCodes(resolved));
    }

    private static String translateLegacyCodes(String text) {
        StringBuilder out = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char current = text.charAt(i);
            if (current == '&' && i + 1 < text.length()) {
                String tag = legacyTag(Character.toLowerCase(text.charAt(i + 1)));
                if (tag != null) {
                    out.append('<').append(tag).append('>');
                    i++;
                    continue;
                }
            }
            out.append(current);
        }
        return out.toString();
    }

    private static String legacyTag(char code) {
        return switch (code) {
            case '0' -> "black";
            case '1' -> "dark_blue";
            case '2' -> "dark_green";
            case '3' -> "dark_aqua";
            case '4' -> "dark_red";
            case '5' -> "dark_purple";
            case '6' -> "gold";
            case '7' -> "gray";
            case '8' -> "dark_gray";
            case '9' -> "blue";
            case 'a' -> "green";
            case 'b' -> "aqua";
            case 'c' -> "red";
            case 'd' -> "light_purple";
            case 'e' -> "yellow";
            case 'f' -> "white";
            case 'k' -> "obfuscated";
            case 'l' -> "bold";
            case 'm' -> "strikethrough";
            case 'n' -> "underlined";
            case 'o' -> "italic";
            case 'r' -> "reset";
            default -> null;
        };
    }
}
