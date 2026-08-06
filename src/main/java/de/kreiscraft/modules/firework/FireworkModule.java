package de.kreiscraft.modules.firework;

import de.kreiscraft.core.module.Module;
import de.kreiscraft.core.module.ModuleContext;
import de.kreiscraft.core.module.ModuleException;
import de.kreiscraft.platform.PaperPlatform;
import org.bukkit.NamespacedKey;
import org.bukkit.Server;
import org.spongepowered.configurate.serialize.SerializationException;

public final class FireworkModule implements Module {

    private NamespacedKey key;
    private Server server;

    @Override
    public String id() {
        return "firework";
    }

    @Override
    public void enable(ModuleContext ctx) {
        FireworkConfig config;
        try {
            config = ctx.moduleConfig().get(FireworkConfig.class);
        } catch (SerializationException e) {
            throw new ModuleException("failed to load config for module " + id(), e);
        }
        PaperPlatform platform = ctx.services().get(PaperPlatform.class);
        this.server = platform.server();
        this.key = NamespacedKey.fromString(config.namespaceKey());
        FireworkRecipeFactory.register(server, config);
    }

    @Override
    public void disable() {
        if (server != null && key != null) {
            FireworkRecipeFactory.unregister(server, key);
        }
    }
}
