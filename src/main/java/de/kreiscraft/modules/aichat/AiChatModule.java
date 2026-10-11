package de.kreiscraft.modules.aichat;

import de.kreiscraft.core.module.Module;
import de.kreiscraft.core.module.ModuleContext;
import de.kreiscraft.core.module.ModuleException;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.serialize.SerializationException;

public final class AiChatModule implements Module {

    private volatile AiChatConfig config;
    private AiChatService service;

    @Override
    public String id() {
        return "ai-chat";
    }

    @Override
    public void enable(ModuleContext ctx) {
        onConfigReload(ctx.moduleConfig());

        service = new DefaultAiChatService(ctx.scheduler(), ctx.messages(), () -> config);

        ctx.events().register(new AiChatListener(() -> config, service));
    }

    @Override
    public void onConfigReload(ConfigurationNode moduleConfig) {
        try {
            config = moduleConfig.get(AiChatConfig.class);
            if (config == null) {
                config = new AiChatConfig("", "", "", 0);
            }
        } catch (SerializationException e) {
            throw new ModuleException("failed to load config for module " + id(), e);
        }
    }
}
