package de.kreiscraft.modules.sit;

import de.kreiscraft.core.message.MessageService;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDismountEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

/** Temporary seats; sitting is an action, not a persisted player preference. */
public final class SitService implements Listener {

    private final MessageService messages;
    private final Map<UUID, ArmorStand> seats = new HashMap<>();

    public SitService(MessageService messages) {
        this.messages = messages;
    }

    public void toggle(Player player) {
        if (seats.containsKey(player.getUniqueId())) {
            stand(player);
            messages.send(player, "sit.stood");
            return;
        }
        if (player.isInsideVehicle()) {
            messages.send(player, "sit.unavailable");
            return;
        }

        ArmorStand seat = player.getWorld().spawn(player.getLocation(), ArmorStand.class, stand -> {
            stand.setVisible(false);
            stand.setMarker(true);
            stand.setGravity(false);
            stand.setInvulnerable(true);
            stand.setSilent(true);
            stand.setPersistent(false);
        });

        // Track before mounting so nested Bukkit events can clean up this seat too.
        seats.put(player.getUniqueId(), seat);
        if (!seat.addPassenger(player) || !seat.equals(player.getVehicle())) {
            stand(player);
            messages.send(player, "sit.unavailable");
            return;
        }
        messages.send(player, "sit.sat");
    }

    private void stand(Player player) {
        ArmorStand seat = seats.remove(player.getUniqueId());
        if (seat != null) {
            seat.remove();
        }
    }

    public void standAll() {
        // Remove tracking first: removing a seat can fire another dismount event.
        List<ArmorStand> remaining = List.copyOf(seats.values());
        seats.clear();
        remaining.forEach(ArmorStand::remove);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDismount(EntityDismountEvent event) {
        if (event.getEntity() instanceof Player player
                && event.getDismounted().equals(seats.get(player.getUniqueId()))) {
            stand(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        stand(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        stand(event.getEntity());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        stand(event.getPlayer());
    }
}
