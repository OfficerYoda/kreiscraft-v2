package de.kreiscraft.modules.combat;

import de.kreiscraft.core.event.PriorityListener;
import java.time.Duration;
import java.util.function.Supplier;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.projectiles.ProjectileSource;

public final class CombatListener implements PriorityListener {

    private final CombatService service;
    private final Supplier<CombatConfig> config;

    public CombatListener(CombatService service, Supplier<CombatConfig> config) {
        this.service = service;
        this.config = config;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        Entity victim = event.getEntity();
        Entity damager = event.getDamager();

        if (!(victim instanceof Player victimPlayer))
            return;

        Player attackerPlayer = null;
        if (damager instanceof Player player) {
            attackerPlayer = player;
        } else if (damager instanceof Projectile projectile) {
            ProjectileSource shooter = projectile.getShooter();
            if (shooter instanceof Player player) {
                attackerPlayer = player;
            }
        }

        if (attackerPlayer == null)
            return;

        CombatConfig cfg = config.get();
        if (isExcludedWorld(victimPlayer, cfg))
            return;

        Duration tagDuration = cfg.tagDuration();
        service.tag(victimPlayer.getUniqueId(), tagDuration);
        service.tag(attackerPlayer.getUniqueId(), tagDuration);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onProjectileHit(ProjectileHitEvent event) {
        Entity hitEntity = event.getHitEntity();
        if (!(hitEntity instanceof Player victim))
            return;

        ProjectileSource shooter = event.getEntity().getShooter();
        if (!(shooter instanceof Player attacker))
            return;

        CombatConfig cfg = config.get();
        if (isExcludedWorld(event.getEntity(), cfg))
            return;

        Duration tagDuration = cfg.tagDuration();
        service.tag(victim.getUniqueId(), tagDuration);
        service.tag(attacker.getUniqueId(), tagDuration);
    }

    private boolean isExcludedWorld(Entity entity, CombatConfig cfg) {
        return cfg.excludedWorlds().contains(entity.getWorld().getName());
    }
}
