package de.kreiscraft.modules.worldmgmt;

import java.util.Optional;
import org.bukkit.World;

/** A dimension whose portal travel can be enabled or disabled by an operator. */
public enum Dimension {
    NETHER("nether", "allow-nether", World.Environment.NETHER, "Nether"), END("end", "allow-end",
            World.Environment.THE_END, "End");

    private final String key;
    private final String configKey;
    private final World.Environment environment;
    private final String displayName;

    Dimension(
            String key, String configKey, World.Environment environment, String displayName) {
        this.key = key;
        this.configKey = configKey;
        this.environment = environment;
        this.displayName = displayName;
    }

    /** The command literal for this dimension, for example {@code nether}. */
    public String key() {
        return key;
    }

    /** The config key under {@code modules.world-mgmt}, for example {@code allow-nether}. */
    public String configKey() {
        return configKey;
    }

    /** The operator-facing name used in command and portal messages. */
    public String displayName() {
        return displayName;
    }

    /** The dimension for a Bukkit environment, or empty for the Overworld and custom worlds. */
    public static Optional<Dimension> fromEnvironment(World.Environment environment) {
        for (Dimension dimension : values()) {
            if (dimension.environment == environment) {
                return Optional.of(dimension);
            }
        }
        return Optional.empty();
    }
}
