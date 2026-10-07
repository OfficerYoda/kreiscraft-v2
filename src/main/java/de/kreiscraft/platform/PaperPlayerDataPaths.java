package de.kreiscraft.platform;

import java.nio.file.Path;
import net.minecraft.server.dedicated.DedicatedServer;
import org.bukkit.craftbukkit.CraftServer;

/**
 * Resolves the directory that holds {@code <uuid>.dat} player-data files.
 *
 * <p>Not unit-tested (NMS surface); it is the only piece of the offline-read boundary that needs
 * server internals.
 */
public final class PaperPlayerDataPaths {

    private PaperPlayerDataPaths() {}

    /** @return {@code <world>/playerdata} for the server's default level */
    public static Path playerDataDirectory(PaperPlatform platform) {
        DedicatedServer server = ((CraftServer) platform.server()).getServer();
        Path worldDir = server.getServerDirectory().resolve(server.getLevelIdName());
        return worldDir.resolve("playerdata");
    }
}
