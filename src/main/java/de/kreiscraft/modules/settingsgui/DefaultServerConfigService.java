package de.kreiscraft.modules.settingsgui;

import de.kreiscraft.core.config.ConfigEdit;
import de.kreiscraft.core.config.ConfigEditResult;
import de.kreiscraft.core.config.ConfigField;
import de.kreiscraft.core.config.ConfigService;
import de.kreiscraft.core.config.ServerConfigSchema;
import org.bukkit.Location;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.serialize.SerializationException;

/**
 * GUI-facing client over the validated {@link ConfigService} boundary.
 *
 * <p>Reads never mutate the configuration. Writes delegate to {@link ConfigService#edit(ConfigEdit)},
 * which validates against a detached candidate and only keeps the result when the complete candidate
 * has been durably written.
 */
public final class DefaultServerConfigService implements ServerConfigService {

    private final ConfigService config;

    public DefaultServerConfigService(ConfigService config) {
        this.config = config;
    }

    @Override
    public ServerConfigSchema schema() {
        return config.configSchema();
    }

    @Override
    public Object current(ConfigField field) {
        ConfigurationNode node = config.root().node(field.pathArray());
        if (node.virtual() || node.isNull()) {
            return null;
        }
        try {
            return switch (field.type()) {
                case BOOLEAN -> node.getBoolean();
                case INTEGER -> node.getInt();
                case DOUBLE -> node.getDouble();
                case DURATION, ENUM, MATERIAL, STRING -> node.getString();
                case LOCATION -> node.get(Location.class);
                case STRING_LIST -> node.getList(String.class);
            };
        } catch (SerializationException e) {
            return null;
        }
    }

    @Override
    public ConfigEditResult edit(ConfigField field, Object value) {
        return config.edit(new ConfigEdit(field, value));
    }
}
