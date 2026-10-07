package de.kreiscraft.platform;

import java.io.IOException;
import java.util.Optional;
import java.util.UUID;

/**
 * Version-independent boundary for opening an offline player's persisted data for editing.
 *
 * <p>Opening produces an {@link OfflinePlayerEditSession} that holds the decoded snapshot and the
 * digest of the source file as it was opened. Modules depend on this interface; the single
 * version-specific implementation ({@link OfflinePlayerDataStore} over a {@link PlayerDataCodec})
 * is constructed in the composition root. Opening never writes to the player file — staging and
 * commit are explicit later steps.
 */
public interface OfflinePlayerDataEditor {

    /**
     * Opens the player's data file for editing.
     *
     * @param playerId offline player UUID
     * @return an edit session, or empty when the player's data file does not exist
     * @throws IOException if the file exists but cannot be read or decoded
     */
    Optional<OfflinePlayerEditSession> openForEdit(UUID playerId) throws IOException;
}
