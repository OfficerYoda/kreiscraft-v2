package de.kreiscraft.modules.spawn;

import de.kreiscraft.core.config.ConfigService;
import de.kreiscraft.core.module.Module;
import de.kreiscraft.core.module.ModuleContext;
import de.kreiscraft.core.module.ModuleException;
import de.kreiscraft.modules.spawn.commands.SetSpawnCommand;
import de.kreiscraft.modules.spawn.commands.SpawnCommand;
import java.util.List;
import org.spongepowered.configurate.ConfigurationNode;

/** Spawn module — configures server spawn, glide zone, swap-hand boost, and launch pad. */
public final class SpawnModule implements Module {

    private SpawnConfig config;
    private DefaultSpawnService service;
    private GlideListener glideListener;

    @Override
    public String id() {
        return "spawn";
    }

    @Override
    public List<String> dependsOn() {
        return List.of();
    }

    @Override
    public void enable(ModuleContext ctx) {
        try {
            this.config = ctx.moduleConfig().get(SpawnConfig.class);
        } catch (org.spongepowered.configurate.serialize.SerializationException e) {
            throw new ModuleException("failed to load config for module " + id(), e);
        }
        if (config == null) {
            config = new SpawnConfig(100, null, null, null, null);
        }

        // ── service ─────────────────────────────────────────────────────────
        this.service = new DefaultSpawnService(ctx.services().get(ConfigService.class), config);
        ctx.services().publish(SpawnService.class, service);

        // ── listeners (config supplied via lambda so reloads propagate) ─────
        if (config.glide().enabled()) {
            this.glideListener = new GlideListener(
                    service, () -> this.config, ctx.scheduler(), ctx.plugin().getServer());
            ctx.events().register(glideListener);
        }
        if (config.swapHandBoost().enabled()) {
            ctx.events().register(new SwapHandBoostListener(() -> this.config));
        }
        if (config.spawnLaunch().enabled()) {
            ctx.events().register(new SpawnLaunchListener(() -> this.config));
        }

        // ── commands ────────────────────────────────────────────────────────
        ctx.commands().register(SpawnCommand.build(service, ctx.messages()),
                "Teleport to spawn");
        ctx.commands().register(SetSpawnCommand.build(service, ctx.messages()),
                "Set the server spawn point");
    }

    @Override
    public void disable() {
        if (glideListener != null) {
            glideListener.shutdown();
        }
    }

    @Override
    public void onConfigReload(ConfigurationNode moduleConfig) {
        try {
            this.config = moduleConfig.get(SpawnConfig.class);
        } catch (org.spongepowered.configurate.serialize.SerializationException e) {
            throw new ModuleException("failed to reload config for module " + id(), e);
        }
        if (config == null) {
            config = new SpawnConfig(100, null, null, null, null);
        }
        if (service != null) {
            service.updateConfig(config);
        }
        // Listener instances read config.get() on every event — no further update needed.
    }
}
