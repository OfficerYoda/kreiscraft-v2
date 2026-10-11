package de.kreiscraft.modules.chatformat;

import de.kreiscraft.core.module.Module;
import de.kreiscraft.core.module.ModuleContext;
import de.kreiscraft.core.module.ModuleException;
import de.kreiscraft.modules.status.StatusService;
import de.kreiscraft.platform.PaperPlatform;
import de.kreiscraft.shared.chat.ChatBridge;
import java.util.List;
import org.spongepowered.configurate.ConfigurationNode;

public final class ChatFormatModule implements Module {

    // Read off the main thread (AsyncChatEvent), so the field must be volatile for a reload to be
    // visible to the listener.
    private volatile ChatFormatConfig config;

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
            config = ctx.moduleConfig().get(ChatFormatConfig.class);
        } catch (org.spongepowered.configurate.serialize.SerializationException e) {
            throw new ModuleException("failed to load config for module " + id(), e);
        }

        StatusService statusService = ctx.services().get(StatusService.class);
        PaperPlatform platform = ctx.services().get(PaperPlatform.class);
        ChatBridge chatBridge = ctx.services().get(ChatBridge.class);

        ctx.events().register(
                new ChatFormatListener(
                        () -> config, statusService, platform, chatBridge, ctx.messages()));
    }

    @Override
    public void onConfigReload(ConfigurationNode moduleConfig) {
        try {
            config = moduleConfig.get(ChatFormatConfig.class);
        } catch (org.spongepowered.configurate.serialize.SerializationException e) {
            throw new ModuleException("failed to reload config for module " + id(), e);
        }
    }
}
