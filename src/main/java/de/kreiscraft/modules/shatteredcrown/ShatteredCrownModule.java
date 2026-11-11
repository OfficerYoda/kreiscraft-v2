package de.kreiscraft.modules.shatteredcrown;

import de.kreiscraft.core.module.Module;
import de.kreiscraft.core.module.ModuleContext;
import de.kreiscraft.core.module.ModuleException;
import java.util.List;
import org.bukkit.World;
import org.spongepowered.configurate.serialize.SerializationException;
import org.spongepowered.configurate.ConfigurationNode;

public final class ShatteredCrownModule implements Module {
    private final DragonBattleAccess battles;
    private EncounterController encounter;
    private EncounterRecovery recovery;

    public ShatteredCrownModule() {
        this(World::getEnderDragonBattle);
    }

    public ShatteredCrownModule(DragonBattleAccess battles) {
        this.battles = battles;
    }

    @Override
    public String id() {
        return "shattered-crown";
    }

    @Override
    public List<String> dependsOn() {
        return List.of("wither");
    }

    @Override
    public void enable(ModuleContext ctx) {
        installRecovery(ctx.plugin().getServer(), ctx.events());
        try {
            var config = ShatteredCrownConfig.load(ctx.moduleConfig());
            if (ctx.moduleConfig().virtual() || !ctx.moduleConfig().node("enabled").getBoolean(true)) {
                return;
            }
            if (encounter != null) {
                return;
            }
            encounter =
                    new EncounterController(ctx.plugin().getServer(), battles, config, ctx.scheduler(), ctx.messages());
            ctx.services().publish(EncounterService.class, encounter);
            ctx.events().register(encounter);
            encounter.start();
            ctx.commands().register(DragonDebugCommand.build(() -> encounter, ctx.messages(), ctx.plugin().getServer()),
                    "Inspect and manually trigger Shattered Crown attacks");
        } catch (SerializationException e) {
            throw configurationError("load", e);
        }
    }

    /** Bootstrap installs recovery independently of the restart-required combat enable flag. */
    public void installRecovery(org.bukkit.Server server, de.kreiscraft.core.event.EventBus events) {
        if (recovery == null) {
            recovery = new EncounterRecovery(entity -> encounter != null && encounter.owns(entity));
            events.register(recovery);
            recovery.start(server);
        }
    }

    @Override
    public void onConfigReload(ConfigurationNode node) {
        try {
            ShatteredCrownConfig next = ShatteredCrownConfig.load(node);
            if (encounter != null) {
                encounter.configure(next);
                if (node.virtual() || !node.node("enabled").getBoolean(true)) {
                    encounter.close();
                    encounter = null;
                }
            }
        } catch (SerializationException e) {
            throw configurationError("reload", e);
        }
    }

    private static ModuleException configurationError(String operation, SerializationException error) {
        Throwable cause = error;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        return new ModuleException("failed to " + operation + " shattered-crown configuration: "
                + error.getMessage() + "; " + cause.getMessage(), error);
    }

    @Override
    public void disable() {
        if (encounter != null) {
            encounter.close();
            encounter = null;
        }
    }
}
