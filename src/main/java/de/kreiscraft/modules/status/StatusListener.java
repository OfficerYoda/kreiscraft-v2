package de.kreiscraft.modules.status;

import de.kreiscraft.core.event.PriorityListener;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

public final class StatusListener implements PriorityListener {

    private final StatusService service;
    private final Server server;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    public StatusListener(StatusService service, Server server) {
        this.service = service;
        this.server = server;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onJoin(PlayerJoinEvent event) {
        applyTeam(event.getPlayer());
    }

    @EventHandler
    public void onStatusChanged(StatusChangedEvent event) {
        Player player = server.getPlayer(event.playerId());
        if (player != null) {
            applyTeam(player);
        }
    }

    private void applyTeam(Player player) {
        Status status = service.get(player.getUniqueId());
        String teamName = "kc_" + player.getUniqueId().toString().substring(0, 8);
        Scoreboard sb = player.getScoreboard();
        Team team = sb.getTeam(teamName);
        if (team == null) {
            team = sb.registerNewTeam(teamName);
        }
        team.prefix(miniMessage.deserialize(status.prefix()));
        team.suffix(miniMessage.deserialize(status.suffix()));
        team.addEntry(player.getName());
    }
}
