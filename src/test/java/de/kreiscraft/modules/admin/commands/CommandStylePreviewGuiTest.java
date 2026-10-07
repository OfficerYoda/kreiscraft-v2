package de.kreiscraft.modules.admin.commands;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

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
    void build_representsDistinctSemanticColorsAndHasNoSelectionOrMutationApi() {
        xyz.xenondevs.invui.gui.Gui first = CommandStylePreviewGui.build();
        xyz.xenondevs.invui.gui.Gui second = CommandStylePreviewGui.build();

        ItemStack card = first.getItem(CommandStylePreviewGui.cardSlots()[0]).getItemProvider(player).get();
        List<TextColor> colors = colors(card.lore());

        assertThat(colors)
                .contains(TextColor.color(0xAAAAAA), TextColor.color(0xFFAA00), TextColor.color(0xFF55FF),
                        TextColor.color(0x55FFFF), TextColor.color(0x55FF55), TextColor.color(0xFFFF55),
                        TextColor.color(0xFF5555));
        assertThat(second.getItem(CommandStylePreviewGui.cardSlots()[0]))
                .isNotSameAs(first.getItem(CommandStylePreviewGui.cardSlots()[0]));
        assertThat(CommandStylePreviewGui.class.getMethods())
                .extracting(java.lang.reflect.Method::getName)
                .contains("build", "title")
                .doesNotContain("select", "save", "persist", "apply");
    }

    @Test
    void title_isARealInventoryTitlePreviewingAllFourCandidates() {
        assertThat(plain(CommandStylePreviewGui.title()))
                .isEqualTo("Classic | Signal | Ember | Requested Hybrid");
        assertThat(colors(List.of(CommandStylePreviewGui.title())))
                .contains(TextColor.color(0xFFAA00), TextColor.color(0x55FFFF), TextColor.color(0xFFFF55));
    }

    private static List<TextColor> colors(List<Component> components) {
        List<TextColor> colors = new ArrayList<>();
        for (Component component : components) {
            collectColors(component, colors);
        }
        return colors;
    }

    private static void collectColors(Component component, List<TextColor> colors) {
        if (component.color() != null) {
            colors.add(component.color());
        }
        for (Component child : component.children()) {
            collectColors(child, colors);
        }
    }

    private static String plain(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }
}
