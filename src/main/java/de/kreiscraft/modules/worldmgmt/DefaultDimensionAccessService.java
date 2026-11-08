package de.kreiscraft.modules.worldmgmt;

import de.kreiscraft.core.config.ConfigField;
import de.kreiscraft.core.config.ConfigService;
import de.kreiscraft.core.config.ServerConfigSchema;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Default {@link DimensionAccessService}.
 *
 * <p>The two access flags are operator-owned Server Configuration, so they are read from the live
 * configuration root on every check. A command therefore takes effect without a full reload, and
 * {@code /kreiscraft reload} observes values edited directly in {@code config.yml}.
 */
public final class DefaultDimensionAccessService implements DimensionAccessService {

    private static final String MODULE = "world-mgmt";

    private final ConfigService config;
    private final Logger logger;

    public DefaultDimensionAccessService(ConfigService config, Logger logger) {
        this.config = config;
        this.logger = logger;
    }

    @Override
    public boolean isAllowed(Dimension dimension) {
        return config.module(MODULE).node(dimension.configKey()).getBoolean(true);
    }

    @Override
    public boolean setAllowed(Dimension dimension, boolean allowed) {
        try {
            config.applyEdit(fieldFor(dimension), allowed);
            return true;
        } catch (IllegalArgumentException | IllegalStateException e) {
            logger.log(
                    Level.WARNING,
                    "world-mgmt: failed to persist " + dimension.configKey(),
                    e);
            return false;
        }
    }

    private static ConfigField fieldFor(Dimension dimension) {
        return switch (dimension) {
            case NETHER -> ServerConfigSchema.netherAccess();
            case END -> ServerConfigSchema.endAccess();
        };
    }
}
