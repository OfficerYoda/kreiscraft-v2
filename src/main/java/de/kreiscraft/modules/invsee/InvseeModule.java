package de.kreiscraft.modules.invsee;

import de.kreiscraft.core.message.MessageService;
import de.kreiscraft.core.module.Module;
import de.kreiscraft.core.module.ModuleContext;
import de.kreiscraft.core.module.ModuleException;
import de.kreiscraft.modules.invsee.commands.EnderChestCommand;
import de.kreiscraft.modules.invsee.commands.InvseeCommand;
import de.kreiscraft.platform.OfflineInventoryReader;
import de.kreiscraft.shared.gui.Gui;
import java.util.List;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.serialize.SerializationException;

/** Provides {@code /invsee} and {@code /enderchest} (aliases: {@code ec}, {@code echest}). */
public final class InvseeModule implements Module {

    private InvseeConfig config;

    @Override
    public String id() {
        return "invsee";
    }

    @Override
    public List<String> dependsOn() {
        return List.of();
    }

    @Override
    public void enable(ModuleContext ctx) {
        try {
            this.config = ctx.moduleConfig().get(InvseeConfig.class);
        } catch (SerializationException e) {
            throw new ModuleException("failed to load config for module " + id(), e);
        }

        Gui gui = ctx.services().get(Gui.class);
        MessageService messages = ctx.messages();
        OfflineInventoryReader offlineReader = ctx.services().get(OfflineInventoryReader.class);

        ctx.commands()
                .register(
                        InvseeCommand.build(gui, messages, offlineReader),
                        "View another player's inventory");
        ctx.commands()
                .register(
                        EnderChestCommand.build(gui, messages),
                        "View another player's ender chest",
                        "ec",
                        "echest");
    }

    @Override
    public void onConfigReload(ConfigurationNode moduleConfig) {
        try {
            this.config = moduleConfig.get(InvseeConfig.class);
        } catch (SerializationException e) {
            throw new ModuleException("failed to reload config for module " + id(), e);
        }
    }

    // config field is populated on enable; suppress the "may be null" concern.
    @SuppressWarnings("unused")
    InvseeConfig config() {
        return config;
    }
}
