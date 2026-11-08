package de.kreiscraft.modules.worldmgmt;

import de.kreiscraft.core.config.ConfigService;
import de.kreiscraft.core.message.MessageService;
import de.kreiscraft.core.module.Module;
import de.kreiscraft.core.module.ModuleContext;
import de.kreiscraft.core.module.ModuleException;
import de.kreiscraft.modules.worldmgmt.commands.DimensionCommand;
import de.kreiscraft.modules.worldmgmt.commands.PortalCommand;
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
        ConfigService configService = ctx.services().get(ConfigService.class);

        DimensionAccessService dimensions =
                new DefaultDimensionAccessService(configService, ctx.plugin().getLogger());
        ctx.events().register(new DimensionAccessListener(dimensions, messages));

        ctx.commands().register(
                WorldCommand.build(messages, platform),
                "Teleport to another world's spawn");
        ctx.commands().register(
                PortalCommand.build(messages),
                "Compute portal coordinate translations");
        ctx.commands().register(
                DimensionCommand.build(dimensions, messages),
                "Enable or disable Nether and End portal access");
    }

    @Override
    public void onConfigReload(ConfigurationNode moduleConfig) {
        try {
            this.config = moduleConfig.get(WorldMgmtConfig.class);
            if (this.config == null) {
                this.config = new WorldMgmtConfig();
            }
        } catch (org.spongepowered.configurate.serialize.SerializationException e) {
            throw new ModuleException("failed to reload config for module " + id(), e);
        }
    }
}
