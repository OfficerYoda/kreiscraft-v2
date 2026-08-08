package de.kreiscraft.core.config.serializers;

import java.lang.reflect.Type;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.serialize.SerializationException;
import org.spongepowered.configurate.serialize.TypeSerializer;

final class ComponentSerializer implements TypeSerializer<Component> {

    static final ComponentSerializer INSTANCE = new ComponentSerializer();
    private static final MiniMessage MM = MiniMessage.miniMessage();

    @Override
    public Component deserialize(Type type, ConfigurationNode node) throws SerializationException {
        String s = node.getString();
        if (s == null) {
            throw new SerializationException("expected MiniMessage string");
        }
        return MM.deserialize(s);
    }

    @Override
    public void serialize(Type type, Component obj, ConfigurationNode node) throws SerializationException {
        node.set(obj == null ? null : MM.serialize(obj));
    }
}
