package de.kreiscraft.modules.signcolor;

import de.kreiscraft.core.event.PriorityListener;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.SignChangeEvent;

public final class SignEditListener implements PriorityListener {

    private final LegacyTextFormatter formatter;

    public SignEditListener(LegacyTextFormatter formatter) {
        this.formatter = formatter;
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onSignChange(SignChangeEvent event) {
        if (!event.getPlayer().hasPermission("kreiscraft.signcolor.use")) {
            return;
        }
        for (int i = 0; i < 4; i++) {
            Component component = event.line(i);
            if (component == null) {
                continue;
            }
            String rawText = PlainTextComponentSerializer.plainText().serialize(component);
            event.line(i, formatter.format(rawText, event.getPlayer()));
        }
    }
}
