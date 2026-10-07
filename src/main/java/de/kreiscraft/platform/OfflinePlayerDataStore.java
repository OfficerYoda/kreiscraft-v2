package de.kreiscraft.platform;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/**
 * Reads player-data files from a fixed directory and delegates NBT decoding to a {@link
 * PlayerDataCodec}.
 *
 * <p>Contains no NMS imports, so the file protocol is testable against real temporary files. The
 * source file is only ever read; nothing here writes to the player's data.
 */
public final class OfflinePlayerDataStore implements OfflinePlayerData {

    private final Path playerDataDirectory;
    private final PlayerDataCodec codec;

    public OfflinePlayerDataStore(Path playerDataDirectory, PlayerDataCodec codec) {
        this.playerDataDirectory = playerDataDirectory;
        this.codec = codec;
    }

    @Override
    public OfflinePlayerSnapshot read(UUID playerId) throws IOException {
        Path file = playerDataDirectory.resolve(playerId + ".dat");
        if (!Files.exists(file)) {
            return OfflinePlayerSnapshot.empty();
        }
        return codec.decode(Files.readAllBytes(file));
    }
}
