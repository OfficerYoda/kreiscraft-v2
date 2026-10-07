package de.kreiscraft.modules.admin.commands;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import xyz.xenondevs.invui.Click;

class CommandStylePreviewGuiTest {

    private ServerMock server;
    private PlayerMock player;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        player = server.addPlayer("Alice");
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void build_showsAllFourPaletteCardsTogetherWithEverySemanticRole() {
        xyz.xenondevs.invui.gui.Gui gui = CommandStylePreviewGui.build();

        List<ItemStack> cards = new ArrayList<>();
        for (int slot : CommandStylePreviewGui.cardSlots()) {
            assertThat(gui.getItem(slot)).isNotNull();
            cards.add(gui.getItem(slot).getItemProvider(player).get());
        }

        assertThat(cards)
                .extracting(card -> plain(card.getItemMeta().displayName()))
                .containsExactly("Classic", "Signal", "Ember", "Requested Hybrid");
        assertThat(cards).allSatisfy(card -> assertThat(card.lore()).hasSize(7));
        assertThat(cards)
                .allSatisfy(card -> assertThat(plain(card.lore().get(6))).isEqualTo("Inventory of Steve"));
    }

    @Test
    void build_representsEveryFullRoleValueWithItsSemanticColor() {
        xyz.xenondevs.invui.gui.Gui board = CommandStylePreviewGui.build();
        int[][] expectedColors = {
                {0xFFAA00, 0xFF55FF, 0x55FFFF, 0x55FF55, 0xFFFF55, 0xFF5555},
                {0x55FFFF, 0xAAAAFF, 0x5599FF, 0x55FF99, 0xFFCC55, 0xFF6677},
                {0xFFFF55, 0xFFAA55, 0x55FFFF, 0x55FF55, 0xFFAA00, 0xFF5555},
                {0x55FFFF, 0xFFAA55, 0x5599FF, 0x55FF99, 0xFFCC55, 0xFF5555}
        };

        for (int index = 0; index < CommandStylePreviewGui.cardSlots().length; index++) {
            ItemStack card = board.getItem(CommandStylePreviewGui.cardSlots()[index])
                    .getItemProvider(player).get();
            assertRoleColor(card.lore().get(0), "Steve", expectedColors[index][0]);
            assertRoleColor(card.lore().get(1), "Repeated griefing", expectedColors[index][1]);
            assertRoleColor(card.lore().get(2), "19.98", expectedColors[index][2]);
            assertRoleColor(card.lore().get(3), "Position Market was saved.", expectedColors[index][3]);
            assertRoleColor(card.lore().get(4), "Steve is already jailed.", expectedColors[index][4]);
            assertRoleColor(card.lore().get(5), "Player Alex was not found.", expectedColors[index][5]);
        }
    }

    @Test
    void cardsAreNonInteractiveAndDoNotCreateSelectionState() {
        xyz.xenondevs.invui.gui.Gui gui = CommandStylePreviewGui.build();
        ItemStack before = gui.getItem(CommandStylePreviewGui.cardSlots()[0]).getItemProvider(player).get();

        gui.getItem(CommandStylePreviewGui.cardSlots()[0])
                .handleClick(ClickType.LEFT, player, org.mockito.Mockito.mock(Click.class));

        ItemStack after = gui.getItem(CommandStylePreviewGui.cardSlots()[0]).getItemProvider(player).get();
        assertThat(after).usingRecursiveComparison().isEqualTo(before);
        assertThat(CommandStylePreviewGui.class.getMethods())
                .extracting(java.lang.reflect.Method::getName)
                .doesNotContain("select", "save", "persist", "apply");
    }

    @Test
    void eachPaletteHasAnInventoryOfSteveTitleWithDistinctOrdinaryAndPlayerColors() {
        int[] playerColors = {0xFFAA00, 0x55FFFF, 0xFFFF55, 0x55FFFF};
        for (int index = 0; index < CommandStylePreviewGui.Palette.values().length; index++) {
            Component title = CommandStylePreviewGui.title(CommandStylePreviewGui.Palette.values()[index]);
            assertThat(plain(title)).isEqualTo("Inventory of Steve");
            assertThat(title.color()).isNotEqualTo(title.children().getFirst().color());
            assertThat(title.children().getFirst().color()).isEqualTo(TextColor.color(playerColors[index]));
        }
    }

    private static void assertRoleColor(Component line, String expectedValue, int expectedColor) {
        Component roleValue = line.children().getFirst();
        assertThat(plain(roleValue)).isEqualTo(expectedValue);
        assertThat(roleValue.color()).isEqualTo(TextColor.color(expectedColor));
    }

    private static String plain(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }
}
