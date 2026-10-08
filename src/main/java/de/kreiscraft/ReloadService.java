package de.kreiscraft;

import de.kreiscraft.core.config.ConfigService;
import de.kreiscraft.core.message.MessageService;
import de.kreiscraft.core.module.ModuleRegistry;
import org.bukkit.command.CommandSender;

/** The shared configuration reload path used by commands and Server Configuration sessions. */
public final class ReloadService {

    private final ConfigService config;
    private final ModuleRegistry modules;
    private final MessageService messages;

    public ReloadService(ConfigService config, ModuleRegistry modules, MessageService messages) {
        this.config = config;
        this.modules = modules;
        this.messages = messages;
    }

    /** Reloads configuration and reports the result to {@code sender}. */
    public boolean reload(CommandSender sender) {
        try {
            config.reload();
            messages.reload();
            modules.reloadConfigs(config.root());
            messages.send(sender, "core.reload.success");
            return true;
        } catch (Exception e) {
            messages.send(
                    sender,
                    "core.reload.failure",
                    "error",
                    e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
            return false;
        }
    }
}
