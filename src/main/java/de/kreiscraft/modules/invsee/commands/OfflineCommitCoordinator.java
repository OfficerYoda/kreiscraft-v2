package de.kreiscraft.modules.invsee.commands;

import de.kreiscraft.core.message.MessageService;
import de.kreiscraft.core.scheduler.Scheduler;
import de.kreiscraft.platform.OfflineCommitResult;
import de.kreiscraft.platform.OfflinePlayerEditSession;
import de.kreiscraft.platform.SlottedItem;
import de.kreiscraft.platform.StagedOfflineWrite;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import org.bukkit.Server;
import org.bukkit.entity.Player;

/**
 * Shared two-phase commit orchestration for the offline inventory and Ender Chest commands.
 *
 * <p>Stages the full edited inventory and Ender Chest (each command supplies its untouched half so
 * unrelated data survives), then commits in one server-main-thread turn and reports the outcome.
 */
final class OfflineCommitCoordinator {

    private OfflineCommitCoordinator() {}

    static void commit(
            Player viewer,
            UUID targetId,
            String displayName,
            OfflinePlayerEditSession session,
            List<SlottedItem> inventory,
            List<SlottedItem> enderItems,
            MessageService messages,
            Scheduler scheduler,
            Server server) {
        StagedOfflineWrite staged;
        try {
            staged = session.stage(inventory, enderItems);
        } catch (IOException e) {
            messages.send(viewer, "invsee.write-error");
            return;
        }
        scheduler.runOnMain(
                () -> {
                    OfflineCommitResult result;
                    try {
                        result =
                                session.commit(
                                        staged, () -> server.getPlayer(targetId) == null);
                    } catch (IOException e) {
                        messages.send(viewer, "invsee.write-error");
                        return;
                    }
                    switch (result) {
                        case COMMITTED ->
                            messages.send(viewer, "invsee.saved", "player", displayName);
                        case ABORTED_TARGET_ONLINE ->
                            messages.send(
                                    viewer,
                                    "invsee.commit-player-online",
                                    "player",
                                    displayName);
                        case ABORTED_SOURCE_CHANGED ->
                            messages.send(
                                    viewer,
                                    "invsee.commit-source-changed",
                                    "player",
                                    displayName);
                    }
                });
    }
}
