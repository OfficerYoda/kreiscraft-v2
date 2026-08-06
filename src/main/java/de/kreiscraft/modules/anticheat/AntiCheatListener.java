package de.kreiscraft.modules.anticheat;

import de.kreiscraft.core.event.PriorityListener;
import de.kreiscraft.core.message.MessageService;
import de.kreiscraft.core.scheduler.Scheduler;
import de.kreiscraft.platform.PlayerBrandProvider;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class AntiCheatListener implements PriorityListener {

    private final AtomicReference<AntiCheatConfig> configRef;
    private final DefaultHackLogService service;
    private final Scheduler scheduler;
    private final MessageService messages;
    private final PlayerBrandProvider brandProvider;

    public AntiCheatListener(
            AtomicReference<AntiCheatConfig> configRef,
            DefaultHackLogService service,
            Scheduler scheduler,
            MessageService messages,
            PlayerBrandProvider brandProvider) {
        this.configRef = configRef;
        this.service = service;
        this.scheduler = scheduler;
        this.messages = messages;
        this.brandProvider = brandProvider;
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onJoin(PlayerJoinEvent event) {
        AntiCheatConfig config = configRef.get();
        if (!config.kickOnFabricForge())
            return;

        var player = event.getPlayer();
        var brandOpt = brandProvider.brandOf(player);
        if (brandOpt.isEmpty())
            return;

        String brand = brandOpt.get().toLowerCase(Locale.ROOT);
        if (!brand.contains("fabric") && !brand.contains("forge"))
            return;

        var playerId = player.getUniqueId();
        scheduler.runLater(config.handshakeGraceDuration(), () -> {
            if (!player.isOnline())
                return;
            if (service.hasHandshakePassed(playerId))
                return;
            player.kick(messages.render("anti-cheat.fabric-forge-kick"));
        });
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onQuit(PlayerQuitEvent event) {
        service.clearHandshake(event.getPlayer().getUniqueId());
    }
}
