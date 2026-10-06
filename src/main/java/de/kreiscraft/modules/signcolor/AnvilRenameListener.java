package de.kreiscraft.modules.signcolor;

import de.kreiscraft.core.event.PriorityListener;
import java.util.List;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.view.AnvilView;

public final class AnvilRenameListener implements PriorityListener {

    private final LegacyTextFormatter formatter;

    public AnvilRenameListener(LegacyTextFormatter formatter) {
        this.formatter = formatter;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPrepareAnvil(PrepareAnvilEvent event) {
        List<HumanEntity> viewers = event.getViewers();
        if (viewers.isEmpty()) {
            return;
        }
        HumanEntity viewer = viewers.get(0);
        if (!viewer.hasPermission("kreiscraft.signcolor.anvil")) {
            return;
        }
        ItemStack result = event.getResult();
        if (result == null) {
            return;
        }
        AnvilView view = event.getView();
        if (view == null) {
            return;
        }
        String renameText = view.getRenameText();
        if (renameText == null || renameText.isBlank()) {
            return;
        }
        Player context = viewer instanceof Player player ? player : null;
        Component component = formatter.format(renameText, context);
        result.editMeta(meta -> meta.displayName(component));
    }
}
