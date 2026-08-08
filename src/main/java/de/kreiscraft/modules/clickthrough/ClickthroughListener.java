package de.kreiscraft.modules.clickthrough;

import de.kreiscraft.core.event.PriorityListener;
import de.kreiscraft.core.scheduler.Scheduler;
import de.kreiscraft.modules.settingsgui.SettingsService;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
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

    private final AtomicReference<ClickthroughConfig> configRef;
    private final SettingsService settingsService;
    private final Scheduler scheduler;

    public ClickthroughListener(
            AtomicReference<ClickthroughConfig> configRef,
            SettingsService settingsService,
            Scheduler scheduler) {
        this.configRef = configRef;
        this.settingsService = settingsService;
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
        if (!settingsService.isClickThroughEnabled(player.getUniqueId())) {
            return;
        }

        BlockFace face = event.getBlockFace();
        Block target = clickedBlock.getRelative(face.getOppositeFace());

        if (target.getType() == Material.ENDER_CHEST) {
            player.openInventory(player.getEnderChest());
            event.setCancelled(true);
            return;
        }

        BlockState state = target.getState();
        if (state instanceof Container container) {
            player.openInventory(container.getInventory());
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
        if (!settingsService.isClickThroughEnabled(player.getUniqueId())) {
            return;
        }

        ClickthroughConfig config = configRef.get();
        Block target = player.getTargetBlockExact(config.targetBlockMaxDistance());
        if (target == null) {
            return;
        }

        BlockState state = target.getState();
        if (!(state instanceof Container container)) {
            return;
        }

        Rotation originalRotation = frame.getRotation();
        player.openInventory(container.getInventory());
        event.setCancelled(true);
        scheduler.runLater(Duration.ofMillis(50), () -> frame.setRotation(originalRotation));
    }
}
