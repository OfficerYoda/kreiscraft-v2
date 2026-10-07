package de.kreiscraft.platform;

import java.io.IOException;

/**
 * Version-specific decode boundary for an offline player's compressed NBT bytes.
 *
 * <p>This is the only place that touches the NMS NBT and item-conversion surface. Implementations
 * convert a player-data document into a version-independent {@link OfflinePlayerSnapshot}. Keeping
 * the codec separate from file access lets {@link OfflinePlayerDataStore} be exercised against real
 * temporary files while the NMS decode is supplied as a seam.
 */
public interface PlayerDataCodec {

    /**
     * Decodes compressed player-data NBT bytes.
     *
     * @param nbtBytes the raw compressed {@code <uuid>.dat} contents
     * @return the decoded inventory and Ender Chest snapshot
     * @throws IOException if the bytes are not valid player-data NBT
     */
    OfflinePlayerSnapshot decode(byte[] nbtBytes) throws IOException;
}
