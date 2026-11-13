package de.kreiscraft.modules.dragonegg;

import de.kreiscraft.core.config.DataFile;
import java.nio.file.Path;
import java.util.Set;
import java.util.UUID;
import org.bukkit.NamespacedKey;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.objectmapping.ConfigSerializable;
import org.spongepowered.configurate.serialize.SerializationException;

public final class DragonEggData extends DataFile<DragonEggData.State> {
    public DragonEggData(Path folder) {
        super(folder, "data/dragon-egg.yml");
    }

    @Override
    protected State load(ConfigurationNode node) throws SerializationException {
        State state = node.get(State.class, new State());
        if (node.node("position", "world").virtual()) {
            state.position = null;
        }
        if (node.node("sampled-position", "world").virtual()) {
            state.sampledPosition = null;
        }
        try {
            if (!Set.of("NONE", "BLOCK", "PLAYER", "ITEM", "FALLING", "CONTAINER", "INVENTORY_ENTITY", "UNKNOWN",
                    "DESTROYED").contains(state.kind)) {
                throw new IllegalArgumentException("unknown custody");
            }
            if (!state.kind.equals("NONE") && state.eggId == null) {
                throw new IllegalArgumentException("missing egg identity");
            }
            if (state.eggId != null) {
                UUID.fromString(state.eggId);
            }
            if (Set.of("PLAYER", "ITEM", "FALLING", "INVENTORY_ENTITY").contains(state.kind)) {
                UUID.fromString(state.carrier);
            }
            if (Set.of("BLOCK", "PLAYER", "ITEM", "FALLING", "CONTAINER", "INVENTORY_ENTITY").contains(state.kind)
                    && state.position == null) {
                throw new IllegalArgumentException("missing known position");
            }
            validate(state.position);
            validate(state.sampledPosition);
            if (state.sampledAt < 0) {
                throw new IllegalArgumentException("negative ping timestamp");
            }
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new SerializationException("invalid dragon egg Activity Data: " + e.getMessage());
        }
        return state;
    }

    private static void validate(DragonEggTracker.Position position) {
        if (position != null) {
            UUID.fromString(position.world());
            if (position.dimension() == null || NamespacedKey.fromString(position.dimension()) == null
                    || !Double.isFinite(position.x()) || !Double.isFinite(position.y())
                    || !Double.isFinite(position.z())) {
                throw new IllegalArgumentException("invalid egg position");
            }
        }
    }

    @Override
    protected void save(State state, ConfigurationNode node) throws SerializationException {
        node.set(State.class, state);
        if (state.position == null) {
            node.node("position").set(null);
        }
        if (state.sampledPosition == null) {
            node.node("sampled-position").set(null);
        }
    }

    @Override
    protected State empty() {
        return new State();
    }

    @ConfigSerializable
    public static final class State {
        String eggId;
        String kind = "NONE";
        String carrier;
        DragonEggTracker.Position position;
        DragonEggTracker.Position sampledPosition;
        long sampledAt;
    }
}
