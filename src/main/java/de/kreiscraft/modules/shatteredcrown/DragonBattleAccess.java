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

    default de.kreiscraft.platform.DragonFlightControl controlFlight(EnderDragon dragon, Runnable tick) {
        return new de.kreiscraft.platform.NmsDragonFlight(dragon, tick);
    }

    default void preventDespawn(Mob defender) {
        defender.setRemoveWhenFarAway(false);
        defender.setCanPickupItems(false);
    }

    /** Raw native dragon-breath units; retains the separately configured pool/lane/sweep semantics. */
    default void bombardmentDamage(Player player, EnderDragon dragon, double damage) {
        player.damage(damage, DamageSource.builder(DamageType.DRAGON_BREATH)
                .withDirectEntity(dragon).withCausingEntity(dragon).build());
    }

    /** Health points lost by full Protection IV netherite, without Resistance or absorption. */
    default void referenceDamage(Player player, EnderDragon dragon, double healthPoints) {
        if (!Double.isFinite(healthPoints) || healthPoints < 0 || healthPoints > 100) {
            throw new IllegalArgumentException("Reference damage must be 0–100 finite health points");
        }
        // Paper dragon breath bypasses armor, not enchantments: four Protection IV pieces give EPF 16.
        double normal = healthPoints / 0.36;
        double raw = switch (player.getWorld().getDifficulty()) {
            case PEACEFUL -> 0;
            case EASY -> normal <= 2 ? normal : 2 * (normal - 1);
            case NORMAL -> normal;
            case HARD -> normal / 1.5;
        };
        if (raw > 0) {
            bombardmentDamage(player, dragon, raw);
        }
    }

    default void faceWarningToViewer(TextDisplay warning) {
        warning.setBillboard(Display.Billboard.CENTER);
    }
}
