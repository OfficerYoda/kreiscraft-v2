package de.kreiscraft.modules.updatemode;

import java.io.IOException;
import java.util.logging.Logger;

public final class DefaultUpdateModeService implements UpdateModeService {

    private final UpdateModeData data;
    private final Logger logger;
    private UpdateModeConfig config;

    public DefaultUpdateModeService(UpdateModeData data, UpdateModeConfig config, Logger logger) {
        this.data = data;
        this.config = config;
        this.logger = logger;
    }

    @Override
    public boolean isActive() {
        return data.get()[0];
    }

    @Override
    public void setActive(boolean active) {
        data.update(s -> s[0] = active);
        try {
            data.flushIfDirty();
        } catch (IOException e) {
            logger.warning("update-mode: failed to persist state: " + e.getMessage());
        }
    }

    public UpdateModeConfig config() {
        return config;
    }

    public void updateConfig(UpdateModeConfig config) {
        this.config = config;
    }
}
