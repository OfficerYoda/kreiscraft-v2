package de.kreiscraft.platform;

import java.io.IOException;
import java.util.List;

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

    /**
     * Encodes edited inventory and Ender Chest items as compressed player-data NBT, preserving
     * every unrelated root tag and item component from {@code sourceNbtBytes}.
     *
     * <p>Only {@code Inventory}, the editable armor/offhand entries in {@code equipment}, and
     * {@code EnderItems} are replaced; the complete source
     * document is otherwise copied through untouched, so no unrelated player data or item metadata
     * is lost.
     *
     * @param sourceNbtBytes the compressed {@code <uuid>.dat} contents the edits were made against
     * @param inventory the ordinary inventory slots to write (Bukkit slots 0–40)
     * @param enderItems the Ender Chest slots to write (0–26)
     * @return compressed player-data NBT with the edited inventory, equipment, and Ender Chest
     * @throws IOException if the source bytes are not valid player-data NBT or encoding fails
     */
    byte[] encode(
            byte[] sourceNbtBytes, List<SlottedItem> inventory, List<SlottedItem> enderItems)
            throws IOException;
}
