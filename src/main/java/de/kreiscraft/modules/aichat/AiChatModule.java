package de.kreiscraft.modules.aichat;

import de.kreiscraft.core.module.Module;
import de.kreiscraft.core.module.ModuleContext;
import de.kreiscraft.core.module.ModuleException;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.serialize.SerializationException;

public final class AiChatModule implements Module {

    // Read off the main thread (AsyncChatEvent), so the field must be volatile for a reload to be
    // visible to the listener.
    private volatile AiChatConfig config;

    @Override
    public String id() {
        return "ai-chat";
    }

    @Override
    public void enable(ModuleContext ctx) {
        onConfigReload(ctx.moduleConfig());
        ctx.events().register(new AiChatListener(() -> config));
    }

    @Override
    public void onConfigReload(ConfigurationNode moduleConfig) {
        try {
            AiChatConfig loaded = moduleConfig.get(AiChatConfig.class);
            config = loaded == null ? new AiChatConfig("", "", "", 0) : loaded;
        } catch (SerializationException e) {
            throw new ModuleException("failed to load config for module " + id(), e);
        }
    }
}
