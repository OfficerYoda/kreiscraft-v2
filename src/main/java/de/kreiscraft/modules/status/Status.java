package de.kreiscraft.modules.status;

import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

public record Status(String prefix, String suffix, int color) {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    /** Default status applied when no player-specific entry exists. */
    public static Status defaults() {
        return new Status("<gray>Player</gray>", "", 0xAAAAAA);
    }

    public Component prefixComponent() {
        Component content = trimLeading(MINI_MESSAGE.deserialize(prefix));
        content = trimTrailing(content);
        return PLAIN.serialize(content).isEmpty()
                ? Component.empty()
                : content.append(Component.space());
    }

    public Component suffixComponent() {
        Component content = trimTrailing(trimLeading(MINI_MESSAGE.deserialize(suffix)));
        return PLAIN.serialize(content).isEmpty()
                ? Component.empty()
                : Component.space().append(content);
    }

    public TextColor nameColor() {
        return finalTextColor(prefixComponent(), TextColor.color(color & 0x00FFFFFF));
    }

    public Component displayName(String name) {
        return prefixComponent()
                .append(Component.text(name).color(nameColor()))
                .append(suffixComponent());
    }

    private static TextColor finalTextColor(Component component, TextColor inherited) {
        TextColor current = component.color() == null ? inherited : component.color();
        List<Component> children = component.children();
        for (int i = children.size() - 1; i >= 0; i--) {
            Component child = children.get(i);
            if (!PLAIN.serialize(child).isBlank()) {
                return finalTextColor(child, current);
            }
        }
        return current;
    }

    private static Component trimLeading(Component component) {
        Component result = component;
        if (component instanceof TextComponent text) {
            String content = text.content().stripLeading();
            result = text.content(content);
            if (!content.isEmpty()) {
                return result;
            }
        }

        List<Component> children = new ArrayList<>(result.children());
        for (int i = 0; i < children.size(); i++) {
            Component child = trimLeading(children.get(i));
            children.set(i, child);
            if (!PLAIN.serialize(child).isEmpty()) {
                break;
            }
        }
        return result.children(children);
    }

    private static Component trimTrailing(Component component) {
        List<Component> children = new ArrayList<>(component.children());
        for (int i = children.size() - 1; i >= 0; i--) {
            Component child = trimTrailing(children.get(i));
            children.set(i, child);
            if (!PLAIN.serialize(child).isEmpty()) {
                return component.children(children);
            }
        }

        Component result = component.children(children);
        return result instanceof TextComponent text
                ? text.content(text.content().stripTrailing())
                : result;
    }
}
