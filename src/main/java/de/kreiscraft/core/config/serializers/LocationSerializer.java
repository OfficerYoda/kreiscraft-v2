package de.kreiscraft.core.config.serializers;

import de.kreiscraft.core.util.Locations;
import java.lang.reflect.Type;
import java.util.LinkedHashMap;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.serialize.SerializationException;
import org.spongepowered.configurate.serialize.TypeSerializer;

final class LocationSerializer implements TypeSerializer<Location> {

    static final LocationSerializer INSTANCE = new LocationSerializer();

    @Override
    public Location deserialize(Type type, ConfigurationNode node) throws SerializationException {
        if (node.isNull())
            throw new SerializationException("expected location map");
        Map<String, Object> map = new LinkedHashMap<>();
        node.childrenMap().forEach((k, v) -> map.put(String.valueOf(k), v.raw()));
        try {
            return Locations.fromMap(map, Bukkit::getWorld);
        } catch (Exception e) {
            throw new SerializationException(node, type, e.getMessage());
        }
    }

    @Override
    public void serialize(Type type, Location obj, ConfigurationNode node) throws SerializationException {
        if (obj == null) {
            node.set(null);
            return;
        }
        Map<String, Object> map = Locations.toMap(obj);
        for (var entry : map.entrySet()) {
            node.node(entry.getKey()).set(entry.getValue());
        }
    }
}
