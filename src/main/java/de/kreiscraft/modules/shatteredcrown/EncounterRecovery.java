package de.kreiscraft.modules.shatteredcrown;

import java.util.function.Predicate;
import org.bukkit.NamespacedKey;
import org.bukkit.Server;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.persistence.PersistentDataType;

/** Recovery is available even when combat is disabled; it never starts or completes a battle. */
final class EncounterRecovery implements Listener {
    static final NamespacedKey ARTIFACT = new NamespacedKey("kreiscraft", "crown_artifact");
    static final NamespacedKey ORIGINAL_HEALTH = new NamespacedKey("kreiscraft", "crown_original_health");
    static final NamespacedKey ORIGINAL_PHASE = new NamespacedKey("kreiscraft", "crown_original_phase");
    private final Predicate<Entity> active;

    EncounterRecovery(Predicate<Entity> active) {
        this.active = active;
    }

    void start(Server server) {
        server.getWorlds().forEach(world -> world.getEntities().forEach(this::recover));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldLoad(WorldLoadEvent event) {
        event.getWorld().getEntities().forEach(this::recover);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntitiesLoad(EntitiesLoadEvent event) {
        event.getEntities().forEach(this::recover);
    }

    private void recover(Entity entity) {
        recover(entity, active.test(entity));
    }

    static void recover(Entity entity, boolean owned) {
        if (owned || entity instanceof Player) {
            return;
        }
        if (entity instanceof EnderDragon dragon) {
            if (dragon.isDead()) {
                return;
            }
            if (dragon.getPersistentDataContainer().has(ORIGINAL_PHASE)) {
                // A restart is a fresh retry, never a resumed custom landing/attack window.
                dragon.setPhase(EnderDragon.Phase.CIRCLING);
                dragon.getPersistentDataContainer().remove(ORIGINAL_PHASE);
            }
            if (!dragon.getPersistentDataContainer().has(ORIGINAL_HEALTH)) {
                return;
            }
            Double health = dragon.getPersistentDataContainer().get(ORIGINAL_HEALTH, PersistentDataType.DOUBLE);
            if (health == null || !Double.isFinite(health) || health <= 0) {
                dragon.getServer().getLogger().warning(
                        "[Shattered Crown] Invalid dragon recovery health; restoring vanilla maximum (200)");
                health = 200.0;
            }
            dragon.getAttribute(Attribute.MAX_HEALTH).setBaseValue(health);
            dragon.setHealth(dragon.getAttribute(Attribute.MAX_HEALTH).getValue());
            dragon.getPersistentDataContainer().remove(ORIGINAL_HEALTH);
        } else if (entity.getPersistentDataContainer().has(ARTIFACT, PersistentDataType.STRING)) {
            entity.remove();
        }
    }
}
