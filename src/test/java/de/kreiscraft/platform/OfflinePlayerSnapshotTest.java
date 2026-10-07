package de.kreiscraft.platform;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

class OfflinePlayerSnapshotTest {

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void empty_hasNoInventoryOrEnderItems() {
        OfflinePlayerSnapshot snapshot = OfflinePlayerSnapshot.empty();

        assertThat(snapshot.inventory()).isEmpty();
        assertThat(snapshot.enderItems()).isEmpty();
        assertThat(snapshot.isEmpty()).isTrue();
    }

    @Test
    void constructor_defensivelyCopiesSlotLists() {
        List<SlottedItem> inventory =
                new ArrayList<>(List.of(new SlottedItem(9, new ItemStack(Material.DIRT))));
        List<SlottedItem> enderItems =
                new ArrayList<>(List.of(new SlottedItem(0, new ItemStack(Material.ENDER_PEARL))));

        OfflinePlayerSnapshot snapshot = new OfflinePlayerSnapshot(inventory, enderItems);

        inventory.clear();
        enderItems.clear();

        assertThat(snapshot.inventory()).hasSize(1);
        assertThat(snapshot.enderItems()).hasSize(1);
    }

    @Test
    void inventoryOnly_isNotEmpty() {
        OfflinePlayerSnapshot snapshot =
                new OfflinePlayerSnapshot(
                        List.of(new SlottedItem(0, new ItemStack(Material.STONE))), List.of());

        assertThat(snapshot.isEmpty()).isFalse();
    }

    @Test
    void enderItemsOnly_isNotEmpty() {
        OfflinePlayerSnapshot snapshot =
                new OfflinePlayerSnapshot(
                        List.of(), List.of(new SlottedItem(0, new ItemStack(Material.STONE))));

        assertThat(snapshot.isEmpty()).isFalse();
    }
}
