package de.kreiscraft.modules.status;

import de.kreiscraft.core.event.PriorityListener;
import de.kreiscraft.core.message.MessageService;
import java.util.function.Supplier;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

public final class StatusListener implements PriorityListener {

    private final StatusService service;
    private final Server server;
    private final Supplier<StatusConfig> config;
    private final MessageService messages;

    public StatusListener(StatusService service, Server server, Supplier<StatusConfig> config,
            MessageService messages) {
        this.service = service;
        this.server = server;
        this.config = config;
        this.messages = messages;
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
        if (event.joinMessage() != null) {
            event.joinMessage(render(config.get().joinFormat(), joiner));
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onQuit(PlayerQuitEvent event) {
        if (event.quitMessage() != null) {
            event.quitMessage(render(config.get().leaveFormat(), event.getPlayer()));
        }
    }

    private Component render(String format, Player player) {
        Status status = service.get(player.getUniqueId());
        return MiniMessage.miniMessage().deserialize(format
                .replace("<player-prefix>", status.spacedPrefix())
                .replace("<player-suffix>", status.spacedSuffix()),
                Placeholder.component("kc-prefix", messages.render("prefix")),
                Placeholder.unparsed("name-colored", player.getName()));
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
     * A custom list name replaces the client's scoreboard-team formatting, so it must include
     * the full status too. Teams still format the overhead nametag.
     */
    private void applyIdentity(Player player) {
        Status status = service.get(player.getUniqueId());
        player.displayName(status.displayName(player.getName()));
        player.playerListName(status.displayName(player.getName()));
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
