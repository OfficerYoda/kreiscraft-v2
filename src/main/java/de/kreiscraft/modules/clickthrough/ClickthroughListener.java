package de.kreiscraft.modules.clickthrough;

import de.kreiscraft.core.event.PriorityListener;
import de.kreiscraft.core.scheduler.Scheduler;
import de.kreiscraft.shared.preferences.PlayerPreferencesService;
import java.time.Duration;
import org.bukkit.Material;
import org.bukkit.Rotation;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;

public final class ClickthroughListener implements PriorityListener {

    /** Fixed ray-cast distance, matching RusticPrism's original click-through behaviour. */
    private static final int TARGET_BLOCK_MAX_DISTANCE = 6;

    private final PlayerPreferencesService preferences;
    private final boolean defaultEnabled;
    private final Scheduler scheduler;

    public ClickthroughListener(
            PlayerPreferencesService preferences, boolean defaultEnabled, Scheduler scheduler) {
        this.preferences = preferences;
        this.defaultEnabled = defaultEnabled;
        this.scheduler = scheduler;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Block clickedBlock = event.getClickedBlock();
        if (clickedBlock == null) {
            return;
        }
        String typeName = clickedBlock.getType().name();
        if (!typeName.endsWith("_WALL_SIGN") && !typeName.endsWith("_WALL_HANGING_SIGN")) {
            return;
        }

        Player player = event.getPlayer();
        if (player.isSneaking()) {
            return;
        }
        if (!preferences.clickThrough(player.getUniqueId(), defaultEnabled)) {
            return;
        }

        BlockFace face = event.getBlockFace();
        Block target = clickedBlock.getRelative(face.getOppositeFace());

        if (openTargetInventory(player, target)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteractAtEntity(PlayerInteractAtEntityEvent event) {
        Entity entity = event.getRightClicked();
        if (!(entity instanceof ItemFrame frame)) {
            return;
        }
        if (frame.getItem().getType().isAir()) {
            return;
        }

        Player player = event.getPlayer();
        if (player.isSneaking()) {
            return;
        }
        if (!preferences.clickThrough(player.getUniqueId(), defaultEnabled)) {
            return;
        }

        Block target = player.getTargetBlockExact(TARGET_BLOCK_MAX_DISTANCE);
        if (target == null) {
            return;
        }

        Rotation originalRotation = frame.getRotation();
        if (!openTargetInventory(player, target)) {
            return;
        }

        event.setCancelled(true);
        scheduler.runLater(Duration.ofMillis(50), () -> frame.setRotation(originalRotation));
    }

    /**
     * Opens the inventory behind {@code target}: the player's own Ender Chest for an Ender Chest
     * block, otherwise the container's inventory. Returns whether an inventory was opened.
     */
    private static boolean openTargetInventory(Player player, Block target) {
        if (target.getType() == Material.ENDER_CHEST) {
            player.openInventory(player.getEnderChest());
            return true;
        }
        BlockState state = target.getState();
        if (state instanceof Container container) {
            player.openInventory(container.getInventory());
            return true;
        }
        return false;
    }
}
