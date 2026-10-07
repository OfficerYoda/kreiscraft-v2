package de.kreiscraft.platform;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads player-data files from a fixed directory and delegates NBT decoding to a {@link
 * PlayerDataCodec}.
 *
 * <p>Contains no NMS imports, so the file protocol is testable against real temporary files. Reads
 * only ever read the source file; edits are staged and committed through {@link
 * OfflinePlayerEditSession}, which also never touches the source until commit.
 */
public final class OfflinePlayerDataStore implements OfflinePlayerData, OfflinePlayerDataEditor {

    private final Path playerDataDirectory;
    private final PlayerDataCodec codec;

    public OfflinePlayerDataStore(Path playerDataDirectory, PlayerDataCodec codec) {
        this.playerDataDirectory = playerDataDirectory;
        this.codec = codec;
    }

    @Override
    public OfflinePlayerSnapshot read(UUID playerId) throws IOException {
        return openForEdit(playerId)
                .map(OfflinePlayerEditSession::snapshot)
                .orElseGet(OfflinePlayerSnapshot::empty);
    }

    @Override
    public Optional<OfflinePlayerEditSession> openForEdit(UUID playerId) throws IOException {
        Path file = playerDataDirectory.resolve(playerId + ".dat");
        if (!Files.exists(file)) {
            return Optional.empty();
        }
        byte[] bytes = Files.readAllBytes(file);
        OfflinePlayerSnapshot snapshot = codec.decode(bytes);
        return Optional.of(
                new OfflinePlayerEditSession(
                        codec, file, bytes, OfflinePlayerEditSession.sha256(bytes), snapshot));
    }
}
