package de.kreiscraft.modules.dragonegg;

import de.kreiscraft.core.message.MessageService;
import de.kreiscraft.core.module.ModuleException;
import de.kreiscraft.platform.OfflineCommitResult;
import de.kreiscraft.platform.OfflinePlayerDataEditor;
import de.kreiscraft.platform.SlottedItem;
import de.kreiscraft.shared.inventory.ItemContents;
import io.papermc.paper.connection.PlayerConfigurationConnection;
import io.papermc.paper.connection.PlayerConnection;
import io.papermc.paper.connection.PlayerLoginConnection;
import io.papermc.paper.event.connection.PlayerConnectionValidateLoginEvent;
import java.io.IOException;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.LongSupplier;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Server;
import org.bukkit.block.Barrel;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

/** Offline-return lifecycle; inventory commits and world changes run on the server thread. */
public final class DragonEggOfflineReturn implements Listener {
    private static final NamespacedKey RETURN_KEY = new NamespacedKey("kreiscraft", "egg_return");
    private final Server server;
    private final DragonEggTracker tracker;
    private final DragonEggData data;
    private final OfflinePlayerDataEditor editor;
    private final MessageService messages;
    private final LongSupplier now;
    private final DropStorage storage;
    private final Map<PlayerConnection, UUID> logins = new HashMap<>();

    public DragonEggOfflineReturn(Server server, DragonEggTracker tracker, DragonEggData data,
            OfflinePlayerDataEditor editor, MessageService messages, LongSupplier now, DropStorage storage) {
        this.server = server;
        this.tracker = tracker;
        this.data = data;
        this.editor = editor;
        this.messages = messages;
        this.now = now;
        this.storage = storage;
    }

    @FunctionalInterface
    public interface DropStorage {
        void saveAndVerify(Location location, String token, ItemStack egg) throws IOException;
    }

    public void quit(Player player) throws IOException {
        if (data.get().returnJournal != null) {
            return;
        }
        if (tracker.holdsTrackedEgg(player)) {
            tracker.trackHolder(player);
            data.update(s -> {
                s.offlineCarrier = player.getUniqueId().toString();
                s.logoutPosition = DragonEggTracker.Position.of(player.getLocation());
                s.offlineDeadline = now.getAsLong() + Duration.ofHours(24).toMillis();
            });
            data.flushIfDirty();
        }
    }

    public void join(Player player) throws IOException {
        if (data.get().returnJournal != null
                && data.get().returnJournal.carrier.equals(player.getUniqueId().toString())) {
            throw new IOException("carrier joined during an unresolved egg return");
        }
        data.flushIfDirty();
        var notification = data.get().notifications.get(player.getUniqueId().toString());
        if (notification != null) {
            messages.send(player, "dragon-egg.offline-return", coordinates(notification));
            data.update(s -> s.notifications.remove(player.getUniqueId().toString()));
            data.flushIfDirty();
        }
        if (player.getUniqueId().toString().equals(data.get().offlineCarrier)) {
            clearDeadline();
        }
    }

    public void tick() throws IOException {
        // A failed flush leaves in-memory changes dirty: persist them before any external effect.
        data.flushIfDirty();
        logins.keySet().removeIf(connection -> !connection.isConnected());
        var s = data.get();
        if (s.returnJournal == null) {
            if (s.offlineDeadline == 0 && s.kind.equals("PLAYER") && offline(UUID.fromString(s.carrier))) {
                data.update(state -> {
                    state.offlineCarrier = state.carrier;
                    state.logoutPosition = state.position;
                    state.offlineDeadline = (state.lastOnlineAt == 0 ? now.getAsLong() : state.lastOnlineAt)
                            + Duration.ofHours(24).toMillis();
                });
                data.flushIfDirty();
            }
            if (s.offlineDeadline == 0 || now.getAsLong() < s.offlineDeadline) {
                return;
            }
            UUID carrier = UUID.fromString(s.offlineCarrier);
            if (!offline(carrier)) {
                return;
            }
            var session = editor.openForEdit(carrier).orElse(null);
            if (session == null) {
                throw new IOException("missing offline carrier data: " + carrier);
            }
            Block destination = nearestSafe(s.logoutPosition.location(server));
            if (destination == null) {
                return;
            }
            ItemStack[] removed = new ItemStack[1];
            List<SlottedItem> inventory = remove(session.snapshot().inventory(), removed);
            List<SlottedItem> enderItems = remove(session.snapshot().enderItems(), removed);
            if (removed[0] == null) {
                throw new IOException("tracked egg missing from offline carrier: " + carrier);
            }
            if (removed[0].getAmount() != 1) {
                throw new IOException("tracked offline egg stack must contain exactly one egg");
            }
            var write = session.stage(inventory, enderItems);
            var journal = new DragonEggData.ReturnJournal();
            journal.phase = "PREPARED";
            journal.carrier = carrier.toString();
            journal.token = UUID.randomUUID().toString();
            journal.egg = Base64.getEncoder().encodeToString(removed[0].serializeAsBytes());
            journal.originalDigest = HexFormat.of().formatHex(session.sourceDigest());
            journal.editedDigest = digest(Files.readAllBytes(write.staged()));
            journal.destination = DragonEggTracker.Position.of(destination.getLocation());
            data.update(state -> state.returnJournal = journal);
            data.flushIfDirty();
            var result = session.commit(write, () -> offline(carrier));
            if (result != OfflineCommitResult.COMMITTED) {
                data.update(state -> state.returnJournal = null);
                data.flushIfDirty();
                return;
            }
            data.update(state -> state.returnJournal.phase = "REMOVED");
            data.flushIfDirty();
        } else if (s.returnJournal.phase.equals("PREPARED")) {
            UUID carrier = UUID.fromString(s.returnJournal.carrier);
            if (!offline(carrier)) {
                return;
            }
            var session = editor.openForEdit(carrier).orElseThrow(
                    () -> new IOException("missing journal carrier data: " + carrier));
            String current = HexFormat.of().formatHex(session.sourceDigest());
            if (current.equals(s.returnJournal.originalDigest)) {
                // Atomic replace did not happen. Inventory remains owner; retry from a new snapshot.
                data.update(state -> state.returnJournal = null);
                data.flushIfDirty();
                return;
            }
            if (!current.equals(s.returnJournal.editedDigest)) {
                throw new IOException(
                        "ambiguous offline egg commit; retain journal and reconcile player file for " + carrier);
            }
            data.update(state -> state.returnJournal.phase = "REMOVED");
            data.flushIfDirty();
        }
        placeRemovedEgg();
    }

    private List<SlottedItem> remove(List<SlottedItem> slots, ItemStack[] removed) throws IOException {
        List<SlottedItem> edited = new ArrayList<>();
        for (var slot : slots) {
            ItemStack stack = slot.stack().clone();
            while (ItemContents.any(stack, tracker::isTrackedEgg)) {
                if (removed[0] != null) {
                    throw new IOException("multiple tracked eggs in offline inventory; refusing ambiguous removal");
                }
                ItemContents.editFirst(stack, egg -> {
                    if (!tracker.isTrackedEgg(egg)) {
                        return false;
                    }
                    removed[0] = egg.clone();
                    egg.setAmount(0);
                    return true;
                });
            }
            if (!stack.getType().isAir() && stack.getAmount() > 0) {
                edited.add(new SlottedItem(slot.slot(), stack));
            }
        }
        return edited;
    }

    private void placeRemovedEgg() throws IOException {
        var journal = data.get().returnJournal;
        Location location = journal.destination.location(server);
        if (location == null) {
            return;
        }
        Block block = location.getBlock();
        Barrel barrel = block.getState() instanceof Barrel found ? found : null;
        boolean ours = barrel != null && journal.token.equals(
                barrel.getPersistentDataContainer().get(RETURN_KEY, PersistentDataType.STRING));
        if (ours) {
            if (!tracker.isTrackedEgg(barrel.getInventory().getItem(13))) {
                throw new IOException("return barrel lost its journal egg; manual reconciliation required");
            }
        } else {
            if (!safe(block)) {
                Block nearby = nearestSafe(data.get().logoutPosition.location(server));
                if (nearby == null) {
                    return;
                }
                data.update(
                        state -> state.returnJournal.destination = DragonEggTracker.Position.of(nearby.getLocation()));
                data.flushIfDirty();
                block = nearby;
            }
            ItemStack egg = ItemStack.deserializeBytes(Base64.getDecoder().decode(journal.egg));
            if (!tracker.isTrackedEgg(egg) || egg.getAmount() != 1) {
                throw new IOException("invalid egg in return journal");
            }
            block.setType(Material.BARREL, false);
            barrel = (Barrel) block.getState();
            barrel.getSnapshotInventory().setItem(13, egg);
            barrel.getPersistentDataContainer().set(RETURN_KEY, PersistentDataType.STRING, journal.token);
            if (!barrel.update(false, false)) {
                throw new IOException("could not persist return barrel block state");
            }
        }
        storage.saveAndVerify(block.getLocation(), journal.token,
                ItemStack.deserializeBytes(Base64.getDecoder().decode(journal.egg)));
        var actual = journal.destination;
        data.update(state -> {
            state.kind = "CONTAINER";
            state.carrier = null;
            state.position = actual;
            state.notifications.put(journal.carrier, actual);
            state.offlineCarrier = null;
            state.logoutPosition = null;
            state.offlineDeadline = 0;
            state.returnJournal = null;
        });
        // ponytail: durable at-most-once broadcast claim; crash here may omit chat, never replay it.
        data.flushIfDirty();
        messages.broadcast("dragon-egg.offline-drop", coordinates(actual));
    }

    private boolean offline(UUID player) {
        return server.getPlayer(player) == null && !logins.containsValue(player);
    }

    private void clearDeadline() throws IOException {
        data.update(s -> {
            s.offlineCarrier = null;
            s.logoutPosition = null;
            s.offlineDeadline = 0;
        });
        data.flushIfDirty();
    }

    private static Object[] coordinates(DragonEggTracker.Position position) {
        return new Object[] {"dimension", position.dimension(), "x", (int) Math.floor(position.x()),
                "y", (int) Math.floor(position.y()), "z", (int) Math.floor(position.z())};
    }

    private static String digest(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required", e);
        }
    }

    private static boolean safe(Block block) {
        return block.getY() >= block.getWorld().getMinHeight() && block.getY() < block.getWorld().getMaxHeight()
                && block.getWorld().getWorldBorder().isInside(block.getLocation()) && block.getType().isAir();
    }

    private static Block nearestSafe(Location logout) {
        if (logout == null) {
            return null;
        }
        Block origin = logout.getBlock();
        if (safe(origin)) {
            return origin;
        }
        // ponytail: bounded 16-block search; retain ownership and retry if no safe air is nearby.
        Block nearest = null;
        int distance = 257;
        for (int x = -16; x <= 16; x++) {
            for (int y = -16; y <= 16; y++) {
                for (int z = -16; z <= 16; z++) {
                    int squared = x * x + y * y + z * z;
                    if (squared < distance && squared <= 256) {
                        Block candidate = origin.getRelative(x, y, z);
                        if (safe(candidate)) {
                            nearest = candidate;
                            distance = squared;
                        }
                    }
                }
            }
        }
        return nearest;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void validateLogin(PlayerConnectionValidateLoginEvent event) {
        var connection = event.getConnection();
        var profile = connection instanceof PlayerLoginConnection login ? login.getAuthenticatedProfile()
                : connection instanceof PlayerConfigurationConnection configuring ? configuring.getProfile() : null;
        if (profile == null || profile.getId() == null || !event.isAllowed()) {
            return;
        }
        UUID id = profile.getId();
        if (id.toString().equals(data.get().offlineCarrier) && data.get().offlineDeadline > 0
                && data.get().offlineDeadline <= now.getAsLong()) {
            // Finish overdue returns before any inventory can be loaded; a late login cannot evade them.
            try {
                tick();
            } catch (IOException | RuntimeException e) {
                event.kickMessage(messages.render("dragon-egg.return-recovering"));
                throw new ModuleException("failed to return overdue dragon egg before carrier login", e);
            }
            if (id.toString().equals(data.get().offlineCarrier)) {
                event.kickMessage(messages.render("dragon-egg.return-recovering"));
                return;
            }
        }
        var journal = data.get().returnJournal;
        if (journal != null && journal.carrier.equals(id.toString())) {
            event.kickMessage(messages.render("dragon-egg.return-recovering"));
        } else {
            // Reserve before configuration/player-data loading; release disconnected attempts in tick.
            logins.put(connection, id);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) {
        try {
            join(event.getPlayer());
        } catch (IOException e) {
            throw new ModuleException("failed to deliver offline dragon egg return", e);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        try {
            quit(event.getPlayer());
        } catch (IOException e) {
            throw new ModuleException("failed to remember offline dragon egg carrier", e);
        }
    }

    private boolean reserved(Location location) {
        var journal = data.get().returnJournal;
        return journal != null && location != null && journal.destination.equals(
                DragonEggTracker.Position.of(location.getBlock().getLocation()));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void protectBreak(org.bukkit.event.block.BlockBreakEvent event) {
        if (reserved(event.getBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void protectBurn(org.bukkit.event.block.BlockBurnEvent event) {
        if (reserved(event.getBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void protectOpen(org.bukkit.event.inventory.InventoryOpenEvent event) {
        if (reserved(event.getInventory().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void protectMove(org.bukkit.event.inventory.InventoryMoveItemEvent event) {
        if (reserved(event.getSource().getLocation()) || reserved(event.getDestination().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void protectEntityExplosion(org.bukkit.event.entity.EntityExplodeEvent event) {
        event.blockList().removeIf(block -> reserved(block.getLocation()));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void protectBlockExplosion(org.bukkit.event.block.BlockExplodeEvent event) {
        event.blockList().removeIf(block -> reserved(block.getLocation()));
    }
}
