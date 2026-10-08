package de.kreiscraft.modules.status;

import de.kreiscraft.core.event.PriorityListener;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
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

    public StatusListener(StatusService service, Server server) {
        this.service = service;
        this.server = server;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onJoin(PlayerJoinEvent event) {
        Player joiner = event.getPlayer();
        // The joiner's fresh scoreboard needs every online player's representation, and every
        // online player's identity is refreshed so a later join cannot leave a stale name.
        for (Player subject : server.getOnlinePlayers()) {
            applyIdentity(subject);
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
        applyIdentity(player);
        for (Player viewer : server.getOnlinePlayers()) {
            applyStatusToScoreboard(player, viewer.getScoreboard());
        }
    }

    /**
     * Applies the status prefix, coloured name, and suffix to the surfaces that are global to the
     * player rather than per-viewer: the Bukkit display name and the tab-list (player list) name.
     * The scoreboard team contributes the prefix and suffix in the in-game list, so the list name
     * carries only the exact status colour to avoid duplicating them.
     */
    private void applyIdentity(Player player) {
        Status status = service.get(player.getUniqueId());
        TextColor color = TextColor.color(status.color() & 0x00FFFFFF);
        Component coloredName = Component.text(player.getName()).color(color);
        player.displayName(
                status.prefixComponent().append(coloredName).append(status.suffixComponent()));
        player.playerListName(coloredName);
    }

    private void applyStatusToScoreboard(Player subject, Scoreboard scoreboard) {
        Status status = service.get(subject.getUniqueId());
        String teamName = "kc_" + subject.getUniqueId().toString().substring(0, 8);
        Team team = scoreboard.getTeam(teamName);
        if (team == null) {
            team = scoreboard.registerNewTeam(teamName);
        }
        team.prefix(status.prefixComponent());
        team.suffix(status.suffixComponent());
        team.color(NamedTextColor.nearestTo(TextColor.color(status.color() & 0x00FFFFFF)));
        team.addEntry(subject.getName());
    }

}
