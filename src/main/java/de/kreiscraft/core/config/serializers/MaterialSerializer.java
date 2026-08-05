package de.kreiscraft.core.config.serializers;

import java.lang.reflect.Type;
import org.bukkit.Material;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.serialize.SerializationException;
import org.spongepowered.configurate.serialize.TypeSerializer;

final class MaterialSerializer implements TypeSerializer<Material> {

    static final MaterialSerializer INSTANCE = new MaterialSerializer();

    @Override
    public Material deserialize(Type type, ConfigurationNode node) throws SerializationException {
        String s = node.getString();
        if (s == null)
            throw new SerializationException("expected material name");
        Material m = Material.matchMaterial(s);
        if (m == null)
            throw new SerializationException(node, type, "unknown material: " + s);
        return m;
    }

    @Override
    public void serialize(Type type, Material obj, ConfigurationNode node) throws SerializationException {
        node.set(obj == null ? null : obj.name());
    }
}
