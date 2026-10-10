package de.kreiscraft.modules.chatformat;

import de.kreiscraft.core.module.Module;
import de.kreiscraft.core.module.ModuleContext;
import de.kreiscraft.core.module.ModuleException;
import de.kreiscraft.modules.status.StatusService;
import de.kreiscraft.platform.PaperPlatform;
import de.kreiscraft.shared.chat.ChatBridge;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.spongepowered.configurate.ConfigurationNode;

public final class ChatFormatModule implements Module {

    private final AtomicReference<ChatFormatConfig> configRef = new AtomicReference<>();

    @Override
    public String id() {
        return "chat-format";
    }

    @Override
    public List<String> dependsOn() {
        return List.of("status");
    }

    @Override
    public void enable(ModuleContext ctx) {
        try {
            configRef.set(ctx.moduleConfig().get(ChatFormatConfig.class));
        } catch (org.spongepowered.configurate.serialize.SerializationException e) {
            throw new ModuleException("failed to load config for module " + id(), e);
        }

        StatusService statusService = ctx.services().get(StatusService.class);
        PaperPlatform platform = ctx.services().get(PaperPlatform.class);
        ChatBridge chatBridge = ctx.services().get(ChatBridge.class);

        ctx.events().register(
                new ChatFormatListener(
                        configRef::get, statusService, platform, chatBridge, ctx.messages()));
    }

    @Override
    public void onConfigReload(ConfigurationNode moduleConfig) {
        try {
            configRef.set(moduleConfig.get(ChatFormatConfig.class));
        } catch (org.spongepowered.configurate.serialize.SerializationException e) {
            throw new ModuleException("failed to reload config for module " + id(), e);
        }
    }
}
