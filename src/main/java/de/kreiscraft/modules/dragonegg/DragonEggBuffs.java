package de.kreiscraft.modules.dragonegg;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * Direct inventory only: never cursor, open inventories, or nested container contents. Call each
 * tick; stopping updates lets leases expire within 10 game ticks, including after login if saved
 * while offline. No effect ownership guesses or destructive removal of vanilla hidden chains.
 */
public final class DragonEggBuffs {
    private final DragonEggTracker tracker;

    public DragonEggBuffs(DragonEggTracker tracker) {
        this.tracker = tracker;
    }

    public void update(Player player) {
        if (!player.isOnline() || player.isDead()) {
            return;
        }
        for (ItemStack item : player.getInventory().getContents()) {
            if (tracker.isTrackedEgg(item)) {
                apply(player, PotionEffectType.STRENGTH, 0);
                apply(player, PotionEffectType.HASTE, 1);
                return;
            }
        }
    }

    private void apply(Player player, PotionEffectType type, int amplifier) {
        PotionEffect existing = player.getPotionEffect(type);
        if (existing == null || existing.getAmplifier() < amplifier) {
            // ponytail: expire within 10 ticks; never remove effects or overwrite equal/stronger ones.
            // Vanilla retains weaker hidden effects; explicit removal would delete that chain too.
            player.addPotionEffect(new PotionEffect(type, 10, amplifier, true, false, false));
        }
    }
}
