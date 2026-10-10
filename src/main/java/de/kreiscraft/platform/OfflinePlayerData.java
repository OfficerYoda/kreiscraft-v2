package de.kreiscraft.platform;

import java.io.IOException;
import java.util.UUID;

/**
 * Version-independent boundary for reading an offline player's persisted inventory data.
 *
 * <p>Modules depend on this interface, never on the NMS reader. The single version-specific
 * implementation ({@link NmsPlayerDataCodec} plus {@link OfflinePlayerDataStore}) is constructed in
 * the composition root, so the NBT read/conversion surface stays isolated from gameplay code and
 * can be extended for offline writes.
 */
public interface OfflinePlayerData {

    /**
     * Reads a consistent snapshot of the player's ordinary inventory and Ender Chest.
     *
     * <p>Reading never writes to the source player file.
     *
     * @param playerId offline player UUID
     * @return the snapshot; empty when the player's data file does not exist
     * @throws IOException if the file exists but cannot be read
     */
    OfflinePlayerSnapshot read(UUID playerId) throws IOException;
}
