package de.kreiscraft.platform;

import java.nio.file.Path;
import net.minecraft.server.dedicated.DedicatedServer;
import net.minecraft.world.level.storage.LevelResource;
import org.bukkit.craftbukkit.CraftServer;

/**
 * Resolves the directory that holds {@code <uuid>.dat} player-data files.
 *
 * <p>Uses the server's storage path so the directory layout and configured world container are
 * respected. The mapped-server tests cover this lookup together with offline file opening.
 */
public final class PaperPlayerDataPaths {

    private PaperPlayerDataPaths() {}

    /** @return the server's player-data directory for its default level */
    public static Path playerDataDirectory(PaperPlatform platform) {
        DedicatedServer server = ((CraftServer) platform.server()).getServer();
        return server.getWorldPath(LevelResource.PLAYER_DATA_DIR);
    }
}
