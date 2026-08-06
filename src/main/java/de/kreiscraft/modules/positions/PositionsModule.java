package de.kreiscraft.modules.positions;

import de.kreiscraft.core.config.ConfigService;
import de.kreiscraft.core.module.Module;
import de.kreiscraft.core.module.ModuleContext;
import de.kreiscraft.core.module.ModuleException;
import de.kreiscraft.modules.positions.commands.PositionCommand;
import de.kreiscraft.shared.gui.Gui;
import java.io.IOException;
import java.util.List;
import org.spongepowered.configurate.ConfigurationNode;

public final class PositionsModule implements Module {

    private PositionsConfig config;
    private DefaultPositionsService service;

    @Override
    public String id() {
        return "positions";
    }

    @Override
    public List<String> dependsOn() {
        return List.of();
    }

    @Override
    public void enable(ModuleContext ctx) {
        try {
            this.config = ctx.moduleConfig().get(PositionsConfig.class);
        } catch (org.spongepowered.configurate.serialize.SerializationException e) {
            throw new ModuleException("failed to load config for module " + id(), e);
        }

        PositionsData data = new PositionsData(ctx.plugin().getDataFolder().toPath());
        try {
            data.load();
        } catch (IOException e) {
            throw new ModuleException("failed to load positions data", e);
        }
        ctx.services().get(ConfigService.class).register(data);

        this.service = new DefaultPositionsService(data);
        ctx.services().publish(PositionsService.class, service);

        Gui gui = ctx.services().get(Gui.class);
        ctx.commands().register(
                PositionCommand.build(service, ctx.messages(), gui, () -> this.config),
                "Manage personal named waypoints");
    }

    @Override
    public void onConfigReload(ConfigurationNode moduleConfig) {
        try {
            this.config = moduleConfig.get(PositionsConfig.class);
        } catch (org.spongepowered.configurate.serialize.SerializationException e) {
            throw new ModuleException("failed to reload config for module " + id(), e);
        }
    }
}
