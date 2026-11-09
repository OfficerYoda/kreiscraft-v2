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
        return List.of();
    }

    @Override
    public void enable(ModuleContext ctx) {
        try {
            var config = ctx.moduleConfig().get(ShatteredCrownConfig.class);
            if (!ctx.moduleConfig().node("enabled").getBoolean(true)) {
                return;
            }
            encounter =
                    new EncounterController(ctx.plugin().getServer(), battles, config, ctx.scheduler(), ctx.messages());
            ctx.events().register(encounter);
            encounter.start();
        } catch (SerializationException e) {
            throw new ModuleException("failed to load shattered-crown configuration", e);
        }
    }

    @Override
    public void onConfigReload(ConfigurationNode node) {
        try {
            ShatteredCrownConfig next = node.get(ShatteredCrownConfig.class);
            if (encounter != null) {
                encounter.configure(next);
                if (!node.node("enabled").getBoolean(true)) {
                    encounter.close();
                }
            }
        } catch (SerializationException e) {
            throw new ModuleException("failed to reload shattered-crown configuration", e);
        }
    }

    @Override
    public void disable() {
        if (encounter != null) {
            encounter.close();
        }
    }
}
