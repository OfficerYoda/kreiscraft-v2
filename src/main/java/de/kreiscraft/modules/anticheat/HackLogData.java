package de.kreiscraft.modules.anticheat;

import de.kreiscraft.core.config.DataFile;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.serialize.SerializationException;

public final class HackLogData extends DataFile<Map<UUID, List<HackEntry>>> {

    public HackLogData(Path dataFolder) {
        super(dataFolder, "hackerlog.yml");
    }

    @Override
    protected Map<UUID, List<HackEntry>> load(ConfigurationNode node) throws SerializationException {
        Map<UUID, List<HackEntry>> map = new HashMap<>();
        for (var uuidEntry : node.childrenMap().entrySet()) {
            UUID uuid;
            try {
                uuid = UUID.fromString(String.valueOf(uuidEntry.getKey()));
            } catch (IllegalArgumentException e) {
                continue; // skip malformed keys
            }
            List<HackEntry> entries = new ArrayList<>();
            ConfigurationNode uuidNode = uuidEntry.getValue();
            loadKind(uuidNode.node("cheats"), uuid, HackEntry.Kind.CHEAT, entries);
            loadKind(uuidNode.node("xray"), uuid, HackEntry.Kind.XRAY, entries);
            if (!entries.isEmpty()) {
                map.put(uuid, entries);
            }
        }
        return map;
    }

    private static void loadKind(
            ConfigurationNode kindNode, UUID uuid, HackEntry.Kind kind, List<HackEntry> out) {
        if (kindNode.virtual())
            return;
        for (var milliEntry : kindNode.childrenMap().entrySet()) {
            long epochMillis;
            try {
                epochMillis = Long.parseLong(String.valueOf(milliEntry.getKey()));
            } catch (NumberFormatException e) {
                continue; // skip malformed keys
            }
            Instant when = Instant.ofEpochMilli(epochMillis);
            ConfigurationNode entryNode = milliEntry.getValue();
            String playerName = entryNode.node("player-name").getString("");
            String detail = entryNode.node("detail").getString("");
            out.add(new HackEntry(uuid, playerName, kind, detail, when));
        }
    }

    @Override
    protected void save(Map<UUID, List<HackEntry>> state, ConfigurationNode node)
            throws SerializationException {
        node.set(null);
        for (var playerEntry : state.entrySet()) {
            String uuidStr = playerEntry.getKey().toString();
            for (HackEntry entry : playerEntry.getValue()) {
                String kindKey = entry.kind() == HackEntry.Kind.CHEAT ? "cheats" : "xray";
                String milliKey = String.valueOf(entry.when().toEpochMilli());
                ConfigurationNode entryNode = node.node(uuidStr, kindKey, milliKey);
                entryNode.node("player-name").set(entry.playerName());
                entryNode.node("detail").set(entry.detail());
            }
        }
    }

    @Override
    protected Map<UUID, List<HackEntry>> empty() {
        return new HashMap<>();
    }
}
