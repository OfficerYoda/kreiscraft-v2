package de.kreiscraft.modules.status;

import de.kreiscraft.core.event.PriorityListener;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
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
        Player joiner = event.getPlayer();
        // The joiner's fresh scoreboard needs every online player's representation.
        for (Player subject : server.getOnlinePlayers()) {
            applyStatusToScoreboard(subject, joiner.getScoreboard());
        }
        // Every existing scoreboard needs the joining player's representation.
        for (Player viewer : server.getOnlinePlayers()) {
            applyStatusToScoreboard(joiner, viewer.getScoreboard());
        }
    }

    @EventHandler
    public void onStatusChanged(StatusChangedEvent event) {
        Player player = server.getPlayer(event.playerId());
        if (player == null) {
            return;
        }
        for (Player viewer : server.getOnlinePlayers()) {
            applyStatusToScoreboard(player, viewer.getScoreboard());
        }
    }

    private void applyStatusToScoreboard(Player subject, Scoreboard scoreboard) {
        Status status = service.get(subject.getUniqueId());
        String teamName = "kc_" + subject.getUniqueId().toString().substring(0, 8);
        Team team = scoreboard.getTeam(teamName);
        if (team == null) {
            team = scoreboard.registerNewTeam(teamName);
        }
        team.prefix(miniMessage.deserialize(status.prefix()));
        team.suffix(miniMessage.deserialize(status.suffix()));
        team.color(NamedTextColor.nearestTo(TextColor.color(status.color() & 0x00FFFFFF)));
        team.addEntry(subject.getName());
    }
}
