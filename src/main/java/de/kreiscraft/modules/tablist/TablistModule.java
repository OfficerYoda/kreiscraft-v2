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

    private TablistConfig config;
    private TablistUpdater updater;
    private Scheduler.Task task;

    /** The interval the running task was scheduled with; only changes on re-enable. */
    private Duration runningUpdateInterval;
    private Logger logger;

    @Override
    public String id() {
        return "tablist";
    }

    @Override
    public void enable(ModuleContext ctx) {
        try {
            this.config = ctx.moduleConfig().get(TablistConfig.class);
        } catch (SerializationException e) {
            throw new ModuleException("failed to load config for module " + id(), e);
        }

        this.logger = ctx.plugin().getLogger();

        PaperPlatform platform = ctx.services().get(PaperPlatform.class);
        PlaceholderResolver resolver = ctx.services().get(PlaceholderResolver.class);

        this.runningUpdateInterval = config.updateInterval();
        this.updater = new TablistUpdater(platform.server(), resolver, () -> this.config);
        ctx.events().register(new TablistListener(updater));
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
        try {
            this.config = moduleConfig.get(TablistConfig.class);
        } catch (SerializationException e) {
            throw new ModuleException("failed to reload config for module " + id(), e);
        }

        if (!config.updateInterval().equals(runningUpdateInterval)) {
            // The shared task is never rescheduled, so warn on every reload that requests a
            // different interval until the server is restarted.
            logger.warning(
                    "[tablist] update-interval changed; restart required for new interval to take effect.");
        }
        // The updater reads the live config through its Supplier; nothing else to push.
    }
}
