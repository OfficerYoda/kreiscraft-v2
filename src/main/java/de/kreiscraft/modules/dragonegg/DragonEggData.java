package de.kreiscraft.modules.dragonegg;

import de.kreiscraft.core.config.DataFile;
import java.nio.file.Path;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.channels.FileChannel;
import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.NamespacedKey;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.objectmapping.ConfigSerializable;
import org.spongepowered.configurate.serialize.SerializationException;

public final class DragonEggData extends DataFile<DragonEggData.State> {
    private final Path file;

    public DragonEggData(Path folder) {
        super(folder, "data/dragon-egg.yml");
        file = folder.resolve("data/dragon-egg.yml");
    }

    @Override
    protected void write(ConfigurationNode node) throws IOException {
        Path staged = Files.createTempFile(file.getParent(), "dragon-egg-", ".staged");
        try {
            org.spongepowered.configurate.yaml.YamlConfigurationLoader.builder().path(staged).build().save(node);
            try (var channel = FileChannel.open(staged, StandardOpenOption.WRITE)) {
                channel.force(true);
            }
            Files.move(staged, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            try (var directory = FileChannel.open(file.getParent(), StandardOpenOption.READ)) {
                directory.force(true);
            }
        } finally {
            Files.deleteIfExists(staged);
        }
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
        if (node.node("logout-position", "world").virtual()) {
            state.logoutPosition = null;
        }
        if (node.node("return-journal", "phase").virtual()) {
            state.returnJournal = null;
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
            if (state.offlineDeadline < 0 || state.lastOnlineAt < 0) {
                throw new IllegalArgumentException("negative offline deadline");
            }
            if (state.offlineDeadline != 0) {
                UUID.fromString(state.offlineCarrier);
                if (state.logoutPosition == null) {
                    throw new IllegalArgumentException("missing logout position");
                }
                validate(state.logoutPosition);
            }
            if (state.returnJournal != null) {
                var journal = state.returnJournal;
                UUID.fromString(journal.carrier);
                UUID.fromString(journal.token);
                if (!Set.of("PREPARED", "REMOVED").contains(journal.phase) || state.eggId == null
                        || journal.destination == null || journal.egg == null
                        || state.offlineDeadline == 0 || !journal.carrier.equals(state.offlineCarrier)
                        || !journal.originalDigest.matches("[0-9a-f]{64}")
                        || !journal.editedDigest.matches("[0-9a-f]{64}")) {
                    throw new IllegalArgumentException("invalid offline return journal");
                }
                java.util.Base64.getDecoder().decode(journal.egg);
                validate(journal.destination);
            }
            for (var notification : state.notifications.entrySet()) {
                UUID.fromString(notification.getKey());
                if (notification.getValue() == null) {
                    throw new IllegalArgumentException("missing notification location");
                }
                validate(notification.getValue());
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
        if (state.logoutPosition == null) {
            node.node("logout-position").set(null);
        }
        if (state.returnJournal == null) {
            node.node("return-journal").set(null);
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
        String offlineCarrier;
        DragonEggTracker.Position logoutPosition;
        long offlineDeadline;
        long lastOnlineAt;
        ReturnJournal returnJournal;
        Map<String, DragonEggTracker.Position> notifications = new java.util.HashMap<>();
    }

    @ConfigSerializable
    public static final class ReturnJournal {
        String phase;
        String carrier;
        String token;
        String egg;
        String originalDigest;
        String editedDigest;
        DragonEggTracker.Position destination;
    }
}
