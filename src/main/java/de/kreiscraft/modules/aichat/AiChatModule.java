package de.kreiscraft.modules.aichat;

import de.kreiscraft.core.module.Module;
import de.kreiscraft.core.module.ModuleContext;
import de.kreiscraft.core.module.ModuleException;
import de.kreiscraft.shared.chat.ChatBridge;
import de.kreiscraft.shared.chat.ChatBridgeEvent;
import java.util.Objects;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.serialize.SerializationException;

public final class AiChatModule implements Module {

    private volatile AiChatConfig config;
    private AiChatService service;
    private ChatBridge.Subscription chatSubscription;

    @Override
    public String id() {
        return "ai-chat";
    }

    @Override
    public void enable(ModuleContext ctx) {
        onConfigReload(ctx.moduleConfig());

        ChatBridge chatBridge = ctx.services().get(ChatBridge.class);
        service = new DefaultAiChatService(ctx.scheduler(), ctx.messages(), () -> config, chatBridge);
        chatSubscription = chatBridge.subscribe(this::recordChatMessage);

        ctx.events().register(new AiChatListener(() -> config, service));
    }

    @Override
    public void disable() {
        if (chatSubscription != null) {
            chatSubscription.unsubscribe();
            chatSubscription = null;
        }
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

    private void recordChatMessage(ChatBridgeEvent event) {
        Objects.requireNonNull(service).chatMessage(new MinecraftChatMessage(
                event.timestamp(), event.source(), event.senderName(), event.plainText()));
    }
}
