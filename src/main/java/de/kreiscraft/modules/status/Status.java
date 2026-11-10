package de.kreiscraft.modules.status;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

public record Status(String prefix, String suffix, int color) {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    /** Default status applied when no player-specific entry exists. */
    public static Status defaults() {
        return new Status("<gray>Player ", "", 0xAAAAAA);
    }

    public Component prefixComponent() {
        return MINI_MESSAGE.deserialize(spacedPrefix());
    }

    public Component suffixComponent() {
        return MINI_MESSAGE.deserialize(spacedSuffix());
    }

    public String spacedPrefix() {
        String text = PlainTextComponentSerializer.plainText().serialize(MINI_MESSAGE.deserialize(prefix));
        return text.isEmpty() || text.endsWith(" ") ? prefix : prefix + " ";
    }

    public String spacedSuffix() {
        String text = PlainTextComponentSerializer.plainText().serialize(MINI_MESSAGE.deserialize(suffix));
        return text.isEmpty() || text.startsWith(" ") ? suffix : " " + suffix;
    }

    public Component displayName(String name) {
        return MINI_MESSAGE.deserialize(spacedPrefix() + MINI_MESSAGE.escapeTags(name) + spacedSuffix());
    }
}
