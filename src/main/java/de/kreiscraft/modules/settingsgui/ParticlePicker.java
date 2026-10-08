package de.kreiscraft.modules.settingsgui;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import xyz.xenondevs.invui.gui.Markers;
import xyz.xenondevs.invui.gui.PagedGui;
import xyz.xenondevs.invui.item.Item;
import xyz.xenondevs.invui.item.ItemBuilder;
import xyz.xenondevs.invui.window.AnvilWindow;

/** Searchable particle choices shared by Player Settings and Server Configuration. */
final class ParticlePicker {

    private ParticlePicker() {}

    static List<Particle> particles(String search) {
        String query = search.toUpperCase(Locale.ROOT);
        return Arrays.stream(Particle.values())
                .filter(particle -> particle.name().contains(query))
                .toList();
    }

    static void open(
            Player player, Consumer<Particle> selection, Runnable cancellation) {
        AtomicBoolean selected = new AtomicBoolean();
        PagedGui<Item> gui = PagedGui.itemsBuilder()
                .setStructure(
                        "x x x x x x x x x",
                        "x x x x x x x x x",
                        "x x x x x x x x x",
                        "x x x x x x x x x")
                .addIngredient('x', Markers.CONTENT_LIST_SLOT_HORIZONTAL)
                .setContent(items("", selected, selection))
                .build();

        AnvilWindow.builder()
                .addRenameHandler(search -> gui.setContent(items(search, selected, selection)))
                .setLowerGui(gui)
                .addCloseHandler(reason -> {
                    if (!selected.get()) {
                        cancellation.run();
                    }
                })
                .open(player);
    }

    private static List<Item> items(
            String search, AtomicBoolean selected, Consumer<Particle> selection) {
        return particles(search).stream()
                .map(particle -> Item.builder()
                        .setItemProvider(new ItemBuilder(Material.FIREWORK_STAR)
                                .setName(Component.text(particle.name())))
                        .addClickHandler(click -> {
                            selected.set(true);
                            click.player().closeInventory();
                            selection.accept(particle);
                        })
                        .build())
                .toList();
    }
}
