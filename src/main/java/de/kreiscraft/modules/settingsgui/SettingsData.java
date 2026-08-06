package de.kreiscraft.modules.settingsgui;

import de.kreiscraft.core.config.DataFile;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.serialize.SerializationException;

/**
 * Persists per-player settings to {@code data/settings.yml}.
 *
 * <p>Only {@code click-through} and {@code combat-display} are written. The {@code lastClickAt}
 * field of {@link Settings} is intentionally excluded — it is transient debounce state that resets
 * on restart.
 */
public final class SettingsData extends DataFile<Map<UUID, Settings>> {

    public SettingsData(Path dataFolder) {
        super(dataFolder, "settings.yml");
    }

    @Override
    protected Map<UUID, Settings> load(ConfigurationNode node) throws SerializationException {
        Map<UUID, Settings> map = new HashMap<>();
        for (var entry : node.childrenMap().entrySet()) {
            UUID id = UUID.fromString(String.valueOf(entry.getKey()));
            ConfigurationNode n = entry.getValue();
            boolean clickThrough = n.node("click-through").getBoolean(true);
            CombatDisplay combatDisplay =
                    n.node("combat-display").get(CombatDisplay.class, CombatDisplay.ACTIONBAR);
            map.put(id, new Settings(clickThrough, combatDisplay));
        }
        return map;
    }

    @Override
    protected void save(Map<UUID, Settings> state, ConfigurationNode node)
            throws SerializationException {
        node.set(null);
        for (var entry : state.entrySet()) {
            ConfigurationNode n = node.node(entry.getKey().toString());
            Settings s = entry.getValue();
            n.node("click-through").set(s.clickThrough());
            n.node("combat-display").set(CombatDisplay.class, s.combatDisplay());
        }
    }

    @Override
    protected Map<UUID, Settings> empty() {
        return new HashMap<>();
    }
}
