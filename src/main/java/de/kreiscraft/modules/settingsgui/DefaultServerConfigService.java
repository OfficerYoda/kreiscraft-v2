package de.kreiscraft.modules.settingsgui;

import de.kreiscraft.core.config.ConfigEdit;
import de.kreiscraft.core.config.ConfigEditResult;
import de.kreiscraft.core.config.ConfigField;
import de.kreiscraft.core.config.ConfigService;
import de.kreiscraft.core.config.ServerConfigSchema;
import de.kreiscraft.modules.admin.TpsBarConfig;
import de.kreiscraft.modules.api.ApiConfig;
import java.util.List;
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

    /** Access flags whose absent keys display as enabled without rewriting live configuration. */
    private static final List<List<Object>> DEFAULT_ENABLED_PATHS =
            List.of(
                    List.of("modules", "world-mgmt", "allow-nether"),
                    List.of("modules", "world-mgmt", "allow-end"));

    @Override
    public Object current(ConfigField field) {
        ConfigurationNode node = config.root().node(field.pathArray());
        try {
            if (node.virtual() || node.isNull()) {
                if (DEFAULT_ENABLED_PATHS.contains(field.path())) {
                    return true;
                }
                ConfigurationNode defaults = node.copy();
                if (field.category().equals("api") && !field.moduleEnabled()) {
                    defaults.set(ApiConfig.class, new ApiConfig(null, 0, null, null, null, null, null, null, null));
                    node = defaults.node(field.path().subList(2, field.path().size()).toArray());
                } else if (field.path().size() != 4
                        || !field.path().subList(0, 3).equals(java.util.List.of("modules", "admin", "tps-bar"))) {
                    return null;
                } else {
                    // Existing installations may not have the new section yet. Display and step the
                    // same defaults the admin module uses, without writing defaults into live config.
                    defaults.set(TpsBarConfig.class, new TpsBarConfig());
                    node = defaults.node(field.path().getLast());
                }
            }
            return switch (field.type()) {
                case BOOLEAN -> node.getBoolean();
                case INTEGER -> node.getInt();
                case DOUBLE -> node.getDouble();
                case DURATION, ENUM, MATERIAL, STRING -> node.getString();
                case LOCATION -> node.get(Location.class);
                case STRING_LIST -> node.getList(String.class);
                case INT_LIST -> node.getList(Integer.class);
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
