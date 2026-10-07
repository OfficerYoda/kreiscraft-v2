package de.kreiscraft.platform;

/** Outcome of an {@link OfflinePlayerEditSession#commit} attempt. */
public enum OfflineCommitResult {

    /** The staged data replaced the source after a byte-for-byte backup was created. */
    COMMITTED,

    /** The target logged in after the snapshot was opened; the source was left untouched. */
    ABORTED_TARGET_ONLINE,

    /** The source changed after the snapshot was opened; the newer file was left untouched. */
    ABORTED_SOURCE_CHANGED
}
