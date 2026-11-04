package de.kreiscraft.modules.sit;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
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
import de.kreiscraft.core.message.MessageService;

/** Temporary seats; sitting is an action, not a persisted player preference. */
public final class SitService implements Listener {

    private final MessageService messages;
    private final Map<UUID, Seat> seats = new HashMap<>();

    public SitService(MessageService messages) {
        this.messages = messages;
    }

    public void toggle(Player player) {
        if (seats.containsKey(player.getUniqueId())) {
            Seat seat = stand(player);
            restorePosition(seat);
            messages.send(player, "sit.stood");
            return;
        }
        if (player.isInsideVehicle() || !isOnGround(player)) {
            messages.send(player, "sit.unavailable");
            return;
        }

        sit(player);
    }

    private void sit(Player player) {
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
        seats.clear();
        remaining.forEach(seat -> {
            seat.entity().remove();
            restorePosition(seat);
        });
    }

    public boolean isOnGround(Player player) {
        Location loc = player.getLocation();

        if (Math.abs(loc.getY() - loc.getBlockY()) < 0.3) {
            loc.setY(loc.getBlockY() - 1);
            Block block = player.getWorld().getBlockAt(loc);

            return !block.isLiquid() && !block.isEmpty() && block.getType() != Material.AIR;
        }

        return false;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDismount(EntityDismountEvent event) {
        if (event.getEntity() instanceof Player player) {
            Seat seat = seats.get(player.getUniqueId());
            if (seat == null || !event.getDismounted().equals(seat.entity())) {
                return;
            }
            stand(player);
            restorePosition(seat);
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
