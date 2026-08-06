package de.kreiscraft.core.config.serializers;

import java.lang.reflect.Type;
import org.bukkit.util.Vector;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.serialize.SerializationException;
import org.spongepowered.configurate.serialize.TypeSerializer;

public final class VectorSerializer implements TypeSerializer<Vector> {

    public static final VectorSerializer INSTANCE = new VectorSerializer();

    private VectorSerializer() {}

    @Override
    public Vector deserialize(Type type, ConfigurationNode node) throws SerializationException {
        if (node.virtual() || node.isNull())
            return null;
        if (!node.isMap())
            throw new SerializationException(node, type, "expected vector map { x, y, z }");
        double x = node.node("x").getDouble(0.0);
        double y = node.node("y").getDouble(0.0);
        double z = node.node("z").getDouble(0.0);
        return new Vector(x, y, z);
    }

    @Override
    public void serialize(Type type, Vector obj, ConfigurationNode node) throws SerializationException {
        if (obj == null) {
            node.set(null);
            return;
        }
        node.node("x").set(obj.getX());
        node.node("y").set(obj.getY());
        node.node("z").set(obj.getZ());
    }
}
