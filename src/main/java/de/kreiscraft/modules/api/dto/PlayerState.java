package de.kreiscraft.modules.api.dto;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;

/** Shared main-thread snapshot for REST and the scene stream. */
public record PlayerState(String playerId, String playerName, boolean isInvisible,
        boolean isPumpkinWorn, double health, double maxHealth, double absorption,
        List<String> effects, int level) {

    public static PlayerState snapshot(Player player) {
        List<String> effects = new ArrayList<>();
        if (player.isFrozen()) {
            effects.add("freezing");
        }
        if (player.hasPotionEffect(PotionEffectType.WITHER)) {
            effects.add("wither");
        }
        if (player.hasPotionEffect(PotionEffectType.POISON)) {
            effects.add("poison");
        }
        if (player.getFireTicks() > 0) {
            effects.add("burning");
        }
        var maximum = player.getAttribute(Attribute.MAX_HEALTH);
        var helmet = player.getInventory().getHelmet();
        return new PlayerState(player.getUniqueId().toString(), player.getName(),
                player.isInvisible() || player.hasPotionEffect(PotionEffectType.INVISIBILITY),
                helmet != null && helmet.getType() == Material.CARVED_PUMPKIN,
                player.getHealth(), maximum == null ? 20.0 : maximum.getValue(),
                player.getAbsorptionAmount(), List.copyOf(effects), player.getLevel());
    }

    public String fireState() {
        return effects.contains("burning") ? (health <= 6 ? "firepanic" : "onfire") : "none";
    }
}
