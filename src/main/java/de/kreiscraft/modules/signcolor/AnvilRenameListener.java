package de.kreiscraft.modules.signcolor;

import de.kreiscraft.core.event.PriorityListener;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.entity.HumanEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.view.AnvilView;

public final class AnvilRenameListener implements PriorityListener {

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPrepareAnvil(PrepareAnvilEvent event) {
        java.util.List<HumanEntity> viewers = event.getViewers();
        if (viewers.isEmpty()) {
            return;
        }
        if (!viewers.get(0).hasPermission("kreiscraft.signcolor.anvil")) {
            return;
        }
        ItemStack result = event.getResult();
        if (result == null) {
            return;
        }
        AnvilView view = event.getView();
        String renameText = view.getRenameText();
        if (renameText == null || renameText.isBlank()) {
            return;
        }
        Component component = MiniMessage.miniMessage().deserialize(renameText);
        result.editMeta(meta -> meta.displayName(component));
    }
}
