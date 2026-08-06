package de.kreiscraft.modules.tablist;

import de.kreiscraft.core.message.PlaceholderResolver;
import de.kreiscraft.core.module.Module;
import de.kreiscraft.core.module.ModuleContext;
import de.kreiscraft.core.module.ModuleException;
import de.kreiscraft.core.scheduler.Scheduler;
import de.kreiscraft.platform.PaperPlatform;
import java.time.Duration;
import java.util.logging.Logger;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.serialize.SerializationException;

public final class TablistModule implements Module {

    private TablistUpdater updater;
    private Scheduler.Task task;
    private Duration currentUpdateInterval;
    private Logger logger;

    @Override
    public String id() {
        return "tablist";
    }

    @Override
    public void enable(ModuleContext ctx) {
        TablistConfig config;
        try {
            config = ctx.moduleConfig().get(TablistConfig.class);
        } catch (SerializationException e) {
            throw new ModuleException("failed to load config for module " + id(), e);
        }

        this.logger = ctx.plugin().getLogger();

        PaperPlatform platform = ctx.services().get(PaperPlatform.class);
        PlaceholderResolver resolver = ctx.services().get(PlaceholderResolver.class);

        this.currentUpdateInterval = config.updateInterval();
        this.updater =
                new TablistUpdater(
                        platform.server(), resolver, config.headerTemplate(), config.footerTemplate());
        this.task = ctx.scheduler().runRepeating(Duration.ZERO, config.updateInterval(), updater::tick);
    }

    @Override
    public void disable() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    @Override
    public void onConfigReload(ConfigurationNode moduleConfig) {
        TablistConfig config;
        try {
            config = moduleConfig.get(TablistConfig.class);
        } catch (SerializationException e) {
            throw new ModuleException("failed to reload config for module " + id(), e);
        }

        if (!config.updateInterval().equals(currentUpdateInterval)) {
            logger.warning(
                    "[tablist] update-interval changed; restart required for new interval to take effect.");
            currentUpdateInterval = config.updateInterval();
        }

        if (updater != null) {
            updater.templates(config.headerTemplate(), config.footerTemplate());
        }
    }
}
