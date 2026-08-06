package de.kreiscraft.modules.clickthrough;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.kreiscraft.modules.settingsgui.SettingsService;
import de.kreiscraft.testsupport.MockScheduler;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.bukkit.Material;
import org.bukkit.Rotation;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ClickthroughListenerTest {

    private ClickthroughListener listener;
    private SettingsService settingsService;
    private MockScheduler scheduler;
    private Player player;
    private UUID playerId;

    @BeforeEach
    void setUp() {
        settingsService = mock(SettingsService.class);
        scheduler = new MockScheduler();
        player = mock(Player.class);
        playerId = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(playerId);
        when(player.isSneaking()).thenReturn(false);

        AtomicReference<ClickthroughConfig> configRef =
                new AtomicReference<>(new ClickthroughConfig(6));
        listener = new ClickthroughListener(configRef, settingsService, scheduler);
    }

    // --- PlayerInteractEvent (wall sign) ---

    @Test
    void onInteract_signRightClick_whenEnabled_opensContainerAndCancels() {
        when(settingsService.isClickThroughEnabled(playerId)).thenReturn(true);

        Block signBlock = mock(Block.class);
        when(signBlock.getType()).thenReturn(Material.OAK_WALL_SIGN);

        Container container = mock(Container.class);
        Inventory inventory = mock(Inventory.class);
        Block targetBlock = mock(Block.class);
        when(targetBlock.getType()).thenReturn(Material.CHEST);
        when(targetBlock.getState()).thenReturn((BlockState) container);
        when(container.getInventory()).thenReturn(inventory);
        when(signBlock.getRelative(BlockFace.EAST)).thenReturn(targetBlock);

        PlayerInteractEvent event = new PlayerInteractEvent(
                player, Action.RIGHT_CLICK_BLOCK, null, signBlock, BlockFace.EAST);

        listener.onInteract(event);

        verify(player).openInventory(inventory);
        assertThat(event.isCancelled()).isTrue();
    }

    @Test
    void onInteract_signRightClick_whenSneaking_doesNothing() {
        when(player.isSneaking()).thenReturn(true);
        when(settingsService.isClickThroughEnabled(playerId)).thenReturn(true);

        Block signBlock = mock(Block.class);
        when(signBlock.getType()).thenReturn(Material.OAK_WALL_SIGN);

        PlayerInteractEvent event = new PlayerInteractEvent(
                player, Action.RIGHT_CLICK_BLOCK, null, signBlock, BlockFace.EAST);

        listener.onInteract(event);

        verify(player, never()).openInventory(any(Inventory.class));
        assertThat(event.isCancelled()).isFalse();
    }

    @Test
    void onInteract_signRightClick_whenDisabled_doesNothing() {
        when(settingsService.isClickThroughEnabled(playerId)).thenReturn(false);

        Block signBlock = mock(Block.class);
        when(signBlock.getType()).thenReturn(Material.OAK_WALL_SIGN);

        PlayerInteractEvent event = new PlayerInteractEvent(
                player, Action.RIGHT_CLICK_BLOCK, null, signBlock, BlockFace.EAST);

        listener.onInteract(event);

        verify(player, never()).openInventory(any(Inventory.class));
        assertThat(event.isCancelled()).isFalse();
    }

    @Test
    void onInteract_signRightClick_targetIsEnderChest_opensEnderChest() {
        when(settingsService.isClickThroughEnabled(playerId)).thenReturn(true);

        Block signBlock = mock(Block.class);
        when(signBlock.getType()).thenReturn(Material.OAK_WALL_SIGN);

        Block targetBlock = mock(Block.class);
        when(targetBlock.getType()).thenReturn(Material.ENDER_CHEST);
        when(signBlock.getRelative(BlockFace.NORTH)).thenReturn(targetBlock);

        Inventory enderChest = mock(Inventory.class);
        when(player.getEnderChest()).thenReturn(enderChest);

        PlayerInteractEvent event = new PlayerInteractEvent(
                player, Action.RIGHT_CLICK_BLOCK, null, signBlock, BlockFace.NORTH);

        listener.onInteract(event);

        verify(player).openInventory(enderChest);
        assertThat(event.isCancelled()).isTrue();
    }

    @Test
    void onInteract_leftClick_doesNothing() {
        when(settingsService.isClickThroughEnabled(playerId)).thenReturn(true);

        Block signBlock = mock(Block.class);
        when(signBlock.getType()).thenReturn(Material.OAK_WALL_SIGN);

        PlayerInteractEvent event = new PlayerInteractEvent(
                player, Action.LEFT_CLICK_BLOCK, null, signBlock, BlockFace.EAST);

        listener.onInteract(event);

        verify(player, never()).openInventory(any(Inventory.class));
        assertThat(event.isCancelled()).isFalse();
    }

    @Test
    void onInteract_nonWallSign_doesNothing() {
        when(settingsService.isClickThroughEnabled(playerId)).thenReturn(true);

        Block normalBlock = mock(Block.class);
        when(normalBlock.getType()).thenReturn(Material.STONE);

        PlayerInteractEvent event = new PlayerInteractEvent(
                player, Action.RIGHT_CLICK_BLOCK, null, normalBlock, BlockFace.EAST);

        listener.onInteract(event);

        verify(player, never()).openInventory(any(Inventory.class));
        assertThat(event.isCancelled()).isFalse();
    }

    // --- PlayerInteractAtEntityEvent (item frame) ---

    @Test
    void onInteractAtEntity_itemFrame_whenEnabled_opensContainerAndRestoresRotation() {
        when(settingsService.isClickThroughEnabled(playerId)).thenReturn(true);

        ItemStack frameItem = mock(ItemStack.class);
        when(frameItem.getType()).thenReturn(Material.STONE);

        ItemFrame frame = mock(ItemFrame.class);
        when(frame.getItem()).thenReturn(frameItem);
        when(frame.getRotation()).thenReturn(Rotation.FLIPPED);

        Container container = mock(Container.class);
        Inventory inventory = mock(Inventory.class);
        Block targetBlock = mock(Block.class);
        when(targetBlock.getType()).thenReturn(Material.CHEST);
        when(targetBlock.getState()).thenReturn((BlockState) container);
        when(container.getInventory()).thenReturn(inventory);
        when(player.getTargetBlockExact(6)).thenReturn(targetBlock);

        PlayerInteractAtEntityEvent event = new PlayerInteractAtEntityEvent(
                player, frame, new Vector(0, 0, 0));

        listener.onInteractAtEntity(event);

        verify(player).openInventory(inventory);
        assertThat(event.isCancelled()).isTrue();

        scheduler.advance(Duration.ofMillis(50));
        verify(frame).setRotation(Rotation.FLIPPED);
    }

    @Test
    void onInteractAtEntity_itemFrame_whenSneaking_doesNothing() {
        when(player.isSneaking()).thenReturn(true);
        when(settingsService.isClickThroughEnabled(playerId)).thenReturn(true);

        ItemStack frameItem = mock(ItemStack.class);
        when(frameItem.getType()).thenReturn(Material.STONE);

        ItemFrame frame = mock(ItemFrame.class);
        when(frame.getItem()).thenReturn(frameItem);

        PlayerInteractAtEntityEvent event = new PlayerInteractAtEntityEvent(
                player, frame, new Vector(0, 0, 0));

        listener.onInteractAtEntity(event);

        verify(player, never()).openInventory(any(Inventory.class));
        assertThat(event.isCancelled()).isFalse();
    }

    @Test
    void onInteractAtEntity_itemFrame_whenDisabled_doesNothing() {
        when(settingsService.isClickThroughEnabled(playerId)).thenReturn(false);

        ItemStack frameItem = mock(ItemStack.class);
        when(frameItem.getType()).thenReturn(Material.STONE);

        ItemFrame frame = mock(ItemFrame.class);
        when(frame.getItem()).thenReturn(frameItem);

        PlayerInteractAtEntityEvent event = new PlayerInteractAtEntityEvent(
                player, frame, new Vector(0, 0, 0));

        listener.onInteractAtEntity(event);

        verify(player, never()).openInventory(any(Inventory.class));
        assertThat(event.isCancelled()).isFalse();
    }

    @Test
    void onInteractAtEntity_emptyItemFrame_doesNothing() {
        when(settingsService.isClickThroughEnabled(playerId)).thenReturn(true);

        ItemStack frameItem = mock(ItemStack.class);
        when(frameItem.getType()).thenReturn(Material.AIR);

        ItemFrame frame = mock(ItemFrame.class);
        when(frame.getItem()).thenReturn(frameItem);

        PlayerInteractAtEntityEvent event = new PlayerInteractAtEntityEvent(
                player, frame, new Vector(0, 0, 0));

        listener.onInteractAtEntity(event);

        verify(player, never()).openInventory(any(Inventory.class));
        assertThat(event.isCancelled()).isFalse();
    }

    @Test
    void onInteractAtEntity_noTargetBlock_doesNothing() {
        when(settingsService.isClickThroughEnabled(playerId)).thenReturn(true);

        ItemStack frameItem = mock(ItemStack.class);
        when(frameItem.getType()).thenReturn(Material.STONE);

        ItemFrame frame = mock(ItemFrame.class);
        when(frame.getItem()).thenReturn(frameItem);

        when(player.getTargetBlockExact(6)).thenReturn(null);

        PlayerInteractAtEntityEvent event = new PlayerInteractAtEntityEvent(
                player, frame, new Vector(0, 0, 0));

        listener.onInteractAtEntity(event);

        verify(player, never()).openInventory(any(Inventory.class));
        assertThat(event.isCancelled()).isFalse();
    }
}
