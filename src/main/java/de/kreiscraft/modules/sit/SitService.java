package de.kreiscraft.modules.sit;

import de.kreiscraft.core.message.MessageService;
import de.kreiscraft.core.scheduler.Scheduler;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDismountEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

/** Temporary seats; sitting is an action, not a persisted player preference. */
public final class SitService implements Listener {

    private final MessageService messages;
    private final Scheduler scheduler;
    private final Map<UUID, Seat> seats = new HashMap<>();
    private final Map<UUID, Seat> pendingStand = new HashMap<>();

    public SitService(MessageService messages, Scheduler scheduler) {
        this.messages = messages;
        this.scheduler = scheduler;
    }

    public void toggle(Player player) {
        if (seats.containsKey(player.getUniqueId())) {
            Seat seat = stand(player);
            restorePosition(seat);
            messages.send(player, "sit.stood");
            return;
        }
        pendingStand.remove(player.getUniqueId());
        if (player.isInsideVehicle()) {
            messages.send(player, "sit.unavailable");
            return;
        }

        Location standing = player.getLocation();
        ArmorStand seat = player.getWorld().spawn(standing, ArmorStand.class, stand -> {
            stand.setVisible(false);
            stand.setMarker(true);
            stand.setGravity(false);
            stand.setInvulnerable(true);
            stand.setSilent(true);
            stand.setPersistent(false);
        });

        // Track before mounting so nested Bukkit events can clean up this seat too.
        seats.put(player.getUniqueId(), new Seat(player, seat, standing));
        if (!seat.addPassenger(player) || !seat.equals(player.getVehicle())) {
            stand(player);
            messages.send(player, "sit.unavailable");
            return;
        }
        messages.send(player, "sit.sat");
    }

    private Seat stand(Player player) {
        pendingStand.remove(player.getUniqueId());
        Seat seat = seats.remove(player.getUniqueId());
        if (seat != null) {
            seat.entity().remove();
        }
        return seat;
    }

    private static void restorePosition(Seat seat) {
        if (seat == null) {
            return;
        }
        Player player = seat.player();
        if (player.isOnline() && !player.isDead() && !player.isInsideVehicle()
                && player.getWorld().equals(seat.standing().getWorld())) {
            // Keep the direction the player is now looking, not their pre-sit direction.
            Location destination = seat.standing().clone();
            destination.setYaw(player.getLocation().getYaw());
            destination.setPitch(player.getLocation().getPitch());
            player.teleport(destination);
        }
    }

    public void standAll() {
        // Remove tracking first: removing a seat can fire another dismount event.
        List<Seat> remaining = new ArrayList<>(seats.values());
        remaining.addAll(pendingStand.values());
        seats.clear();
        pendingStand.clear();
        remaining.forEach(seat -> {
            seat.entity().remove();
            restorePosition(seat);
        });
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDismount(EntityDismountEvent event) {
        if (event.getEntity() instanceof Player player) {
            Seat seat = seats.get(player.getUniqueId());
            if (seat == null || !event.getDismounted().equals(seat.entity())) {
                return;
            }
            stand(player);
            pendingStand.put(player.getUniqueId(), seat);
            // Vanilla finishes dismount positioning after this event. Correct it next tick.
            scheduler.runLater(Duration.ofMillis(50), () -> {
                if (pendingStand.remove(player.getUniqueId(), seat)) {
                    restorePosition(seat);
                }
            });
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

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player) {
            stand(player);
        }
    }

    private record Seat(Player player, ArmorStand entity, Location standing) {
    }
}
