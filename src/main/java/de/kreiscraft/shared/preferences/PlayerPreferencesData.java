package de.kreiscraft.shared.preferences;

import de.kreiscraft.core.config.DataFile;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.Particle;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.serialize.SerializationException;

/**
 * Persists per-player preferences to {@code data/settings.yml}.
 *
 * <p>The file is owned by the always-available preferences boundary, not by the settings GUI
 * module, so preferences survive even when that module is disabled. Every preference is optional:
 * absent keys load as an empty preference and are not written back on save.
 *
 * <p>Malformed legacy values (for example an unknown combat-display or particle enum constant) are
 * discarded rather than migrated or repaired, matching the remediation decision to make no recovery
 * attempt for legacy {@code !!CombatDisplay} data.
 */
public final class PlayerPreferencesData extends DataFile<Map<UUID, PlayerPreferences>> {

    private static final Logger LOGGER = Logger.getLogger(PlayerPreferencesData.class.getName());

    public PlayerPreferencesData(Path dataFolder) {
        super(dataFolder, "data/settings.yml");
    }

    @Override
    protected Map<UUID, PlayerPreferences> load(ConfigurationNode node) {
        Map<UUID, PlayerPreferences> map = new HashMap<>();
        for (var entry : node.childrenMap().entrySet()) {
            ConfigurationNode n = entry.getValue();
            UUID id = UUID.fromString(String.valueOf(entry.getKey()));
            map.put(id, new PlayerPreferences(
                    readLanguage(id, n.node("language")),
                    Optional.ofNullable(readBoolean(n.node("click-through"))),
                    Optional.ofNullable(readCombatDisplay(n.node("combat-display"))),
                    Optional.ofNullable(readBoolean(n.node("effects-enabled"))),
                    Optional.ofNullable(readParticle(n.node("effects-particle"))),
                    Optional.ofNullable(readBoolean(n.node("tps-bar-enabled")))));
        }
        return map;
    }

    @Override
    protected void save(Map<UUID, PlayerPreferences> state, ConfigurationNode node)
            throws SerializationException {
        node.set(null);
        for (var entry : state.entrySet()) {
            ConfigurationNode n = node.node(entry.getKey().toString());
            PlayerPreferences prefs = entry.getValue();
            if (prefs.language().isPresent()) {
                n.node("language").set(prefs.language().get().code());
            }
            if (prefs.clickThrough().isPresent()) {
                n.node("click-through").set(prefs.clickThrough().get());
            }
            if (prefs.combatDisplay().isPresent()) {
                // Write the enum name as a plain scalar: Configurate's typed set() would emit a
                // !!<class> YAML tag, and the loader rejects global tags on read.
                n.node("combat-display").set(prefs.combatDisplay().get().name());
            }
            if (prefs.effectsEnabled().isPresent()) {
                n.node("effects-enabled").set(prefs.effectsEnabled().get());
            }
            if (prefs.effectsParticle().isPresent()) {
                n.node("effects-particle").set(prefs.effectsParticle().get().name());
            }
            if (prefs.tpsBarEnabled().isPresent()) {
                n.node("tps-bar-enabled").set(prefs.tpsBarEnabled().get());
            }
        }
    }

    @Override
    protected Map<UUID, PlayerPreferences> empty() {
        return new HashMap<>();
    }

    /**
     * A file written by the former settings-gui persistence can contain {@code !!CombatDisplay}
     * global tags, which the YAML loader rejects. Recovery or migration is explicitly out of scope,
     * so such a file is abandoned rather than repaired: the plugin starts with no stored
     * preferences instead of failing startup.
     */
    @Override
    protected Map<UUID, PlayerPreferences> onUnreadable(IOException failure) {
        LOGGER.log(
                Level.WARNING,
                "Player preferences file could not be read (likely legacy !!CombatDisplay data); "
                        + "starting with no stored preferences. Tried: " + relativePath(),
                failure);
        return empty();
    }

    /**
     * Resolves a stored language code. An unrecognised value is not migrated: it warns and loads as
     * absent so rendering falls back to the server language.
     */
    private static Optional<PlayerLanguage> readLanguage(UUID id, ConfigurationNode node) {
        String raw = node.getString();
        if (raw == null) {
            return Optional.empty();
        }
        Optional<PlayerLanguage> language = PlayerLanguage.fromCode(raw);
        if (language.isEmpty()) {
            LOGGER.log(
                    Level.WARNING,
                    "invalid stored language ''{0}'' for player {1}; falling back to the server "
                            + "language",
                    new Object[] {raw, id});
        }
        return language;
    }

    private static Boolean readBoolean(ConfigurationNode node) {
        if (node.isNull()) {
            return null;
        }
        return node.getBoolean();
    }

    private static CombatDisplay readCombatDisplay(ConfigurationNode node) {
        if (node.isNull()) {
            return null;
        }
        try {
            return node.get(CombatDisplay.class);
        } catch (SerializationException e) {
            LOGGER.log(Level.FINE, "discarding malformed combat-display preference", e);
            return null;
        }
    }

    private static Particle readParticle(ConfigurationNode node) {
        if (node.isNull()) {
            return null;
        }
        try {
            return node.get(Particle.class);
        } catch (SerializationException e) {
            LOGGER.log(Level.FINE, "discarding malformed effects-particle preference", e);
            return null;
        }
    }
}
