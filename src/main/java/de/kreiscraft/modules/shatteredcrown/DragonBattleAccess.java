package de.kreiscraft.modules.shatteredcrown;

import org.bukkit.World;
import org.bukkit.boss.DragonBattle;
import org.bukkit.entity.Mob;

/** Narrow supported Paper operations absent from MockBukkit. No encounter rules live here. */
@FunctionalInterface
public interface DragonBattleAccess {
    DragonBattle battle(World world);

    default void preventDespawn(Mob defender) {
        defender.setRemoveWhenFarAway(false);
    }
}
