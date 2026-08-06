package de.kreiscraft.core.config.serializers;

import java.time.Duration;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.util.Vector;
import org.spongepowered.configurate.serialize.TypeSerializerCollection;

public final class KreiscraftSerializers {

    private KreiscraftSerializers() {}

    public static TypeSerializerCollection.Builder builder() {
        return TypeSerializerCollection.defaults().childBuilder()
                .register(Duration.class, DurationSerializer.INSTANCE)
                .register(Location.class, LocationSerializer.INSTANCE)
                .register(Component.class, ComponentSerializer.INSTANCE)
                .register(Material.class, MaterialSerializer.INSTANCE)
                .register(Vector.class, VectorSerializer.INSTANCE);
    }
}
