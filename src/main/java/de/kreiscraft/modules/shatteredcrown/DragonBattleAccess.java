package de.kreiscraft.modules.shatteredcrown;

import org.bukkit.World;
import org.bukkit.boss.DragonBattle;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.EnderDragon;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.Display;
import org.bukkit.entity.TextDisplay;

/** Narrow supported Paper operations absent from MockBukkit. No encounter rules live here. */
@FunctionalInterface
public interface DragonBattleAccess {
    DragonBattle battle(World world);

    default void preventDespawn(Mob defender) {
        defender.setRemoveWhenFarAway(false);
    }

    default void bombardmentDamage(Player player, EnderDragon dragon, double damage) {
        player.damage(damage, DamageSource.builder(DamageType.DRAGON_BREATH).withCausingEntity(dragon).build());
    }

    default void faceWarningToViewer(TextDisplay warning) {
        warning.setBillboard(Display.Billboard.CENTER);
    }
}
