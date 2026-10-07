package de.kreiscraft.modules.worldmgmt;

import de.kreiscraft.core.message.MessageService;
import de.kreiscraft.core.module.Module;
import de.kreiscraft.core.module.ModuleContext;
import de.kreiscraft.core.module.ModuleException;
import de.kreiscraft.modules.worldmgmt.commands.PortalCommand;
import de.kreiscraft.modules.worldmgmt.commands.SetWorldSpawnCommand;
import de.kreiscraft.modules.worldmgmt.commands.WorldCommand;
import de.kreiscraft.platform.PaperPlatform;
import java.util.List;
import org.spongepowered.configurate.ConfigurationNode;

public final class WorldMgmtModule implements Module {

    private WorldMgmtConfig config;

    @Override
    public String id() {
        return "world-mgmt";
    }

    @Override
    public List<String> dependsOn() {
        return List.of();
    }

    @Override
    public void enable(ModuleContext ctx) {
        try {
            this.config = ctx.moduleConfig().get(WorldMgmtConfig.class);
        } catch (org.spongepowered.configurate.serialize.SerializationException e) {
            throw new ModuleException("failed to load config for module " + id(), e);
        }
        if (this.config == null) {
            this.config = new WorldMgmtConfig();
        }

        MessageService messages = ctx.messages();
        PaperPlatform platform = ctx.services().get(PaperPlatform.class);

        ctx.commands().register(
                WorldCommand.build(messages, platform),
                "Teleport to another world's spawn");
        ctx.commands().register(
                SetWorldSpawnCommand.build(messages),
                "Set the spawn point of the current world",
                "sws");
        ctx.commands().register(
                PortalCommand.build(messages),
                "Compute portal coordinate translations");
    }

    @Override
    public void onConfigReload(ConfigurationNode moduleConfig) {
        try {
            WorldMgmtConfig reloaded = moduleConfig.get(WorldMgmtConfig.class);
            this.config = reloaded != null ? reloaded : new WorldMgmtConfig();
        } catch (org.spongepowered.configurate.serialize.SerializationException e) {
            throw new ModuleException("failed to reload config for module " + id(), e);
        }
    }
}
