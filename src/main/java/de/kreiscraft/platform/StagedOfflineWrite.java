package de.kreiscraft.platform;

import java.nio.file.Path;

/**
 * A verified, staged offline player-data write that has not yet replaced its source.
 *
 * <p>Holds the compressed-NBT temporary file produced by {@link OfflinePlayerEditSession#stage}
 * together with the source path and the digest of the source as it was when the snapshot was
 * opened. The source file is never touched while a write is staged; {@link
 * OfflinePlayerEditSession#commit} performs the check-and-replace.
 *
 * @param source the player-data file this write will replace
 * @param staged the verified compressed-NBT file waiting to replace {@code source}
 * @param sourceDigest SHA-256 of {@code source} as opened, re-checked immediately before the
 *     replace
 */
public record StagedOfflineWrite(Path source, Path staged, byte[] sourceDigest) {

    public StagedOfflineWrite {
        sourceDigest = sourceDigest.clone();
    }

    @Override
    public byte[] sourceDigest() {
        return sourceDigest.clone();
    }
}
