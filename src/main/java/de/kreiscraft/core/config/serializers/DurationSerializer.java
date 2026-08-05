package de.kreiscraft.core.config.serializers;

import de.kreiscraft.core.util.Durations;
import java.lang.reflect.Type;
import java.time.Duration;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.serialize.SerializationException;
import org.spongepowered.configurate.serialize.TypeSerializer;

final class DurationSerializer implements TypeSerializer<Duration> {

    static final DurationSerializer INSTANCE = new DurationSerializer();

    @Override
    public Duration deserialize(Type type, ConfigurationNode node) throws SerializationException {
        String s = node.getString();
        if (s == null)
            throw new SerializationException("expected duration string");
        try {
            return Durations.parse(s);
        } catch (IllegalArgumentException e) {
            throw new SerializationException(node, type, e.getMessage());
        }
    }

    @Override
    public void serialize(Type type, Duration obj, ConfigurationNode node) throws SerializationException {
        node.set(obj == null ? null : Durations.format(obj));
    }
}
