package de.kreiscraft.modules.clickthrough;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.kreiscraft.testsupport.InMemoryPlayerPreferencesService;
import de.kreiscraft.testsupport.MockScheduler;
import java.util.UUID;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Verifies that click-through is driven by the always-available preference, falling back to the
 * click-through module's default when the player has no stored preference.
 */
class ClickthroughPreferenceTest {

    private InMemoryPlayerPreferencesService preferences;
    private Player player;
    private UUID playerId;
    private Block signBlock;
    private Inventory inventory;
    private boolean defaultEnabled;

    @BeforeEach
    void setUp() {
        preferences = new InMemoryPlayerPreferencesService();
        player = mock(Player.class);
        playerId = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(playerId);
        when(player.isSneaking()).thenReturn(false);

        signBlock = mock(Block.class);
        when(signBlock.getType()).thenReturn(Material.OAK_WALL_SIGN);

        Container container = mock(Container.class);
        inventory = mock(Inventory.class);
        Block target = mock(Block.class);
        when(target.getType()).thenReturn(Material.CHEST);
        when(target.getState()).thenReturn((BlockState) container);
        when(container.getInventory()).thenReturn(inventory);
        when(signBlock.getRelative(BlockFace.WEST)).thenReturn(target);
    }

    private void rightClickWallSign() {
        PlayerInteractEvent event = new PlayerInteractEvent(
                player, Action.RIGHT_CLICK_BLOCK, null, signBlock, BlockFace.EAST);
        new ClickthroughListener(preferences, defaultEnabled, new MockScheduler()).onInteract(event);
    }

    @Test
    void moduleDefaultEnabled_whenNoStoredPreference_opensContainer() {
        defaultEnabled = true;
        rightClickWallSign();

        verify(player).openInventory(inventory);
    }

    @Test
    void moduleDefaultDisabled_whenNoStoredPreference_doesNotOpen() {
        defaultEnabled = false;
        rightClickWallSign();

        verify(player, never()).openInventory(inventory);
    }

    @Test
    void storedPreferenceDisabled_overridesEnabledModuleDefault() {
        defaultEnabled = true;
        preferences.setClickThrough(playerId, false);
        rightClickWallSign();

        verify(player, never()).openInventory(inventory);
    }

    @Test
    void storedPreferenceEnabled_overridesDisabledModuleDefault() {
        defaultEnabled = false;
        preferences.setClickThrough(playerId, true);
        rightClickWallSign();

        verify(player).openInventory(inventory);
    }

    @Test
    void moduleDefault_isEnabled() {
        assertThat(ClickthroughModule.CLICK_THROUGH_DEFAULT_ENABLED).isTrue();
    }
}
