package de.kreiscraft.platform;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * An opened offline player-data file, ready to stage and commit inventory edits.
 *
 * <p>Implements the accepted two-phase commit boundary: {@link #stage} converts the edits through
 * the {@link PlayerDataCodec} and writes a verified compressed-NBT temporary file without touching
 * the source; {@link #commit} re-checks that the target is still offline and that the source digest
 * still matches, creates a byte-for-byte backup, then atomically replaces the source. Aborted
 * commits leave the source untouched.
 *
 * <p>Instances are created by {@link OfflinePlayerDataStore#openForEdit} and are safe to hold
 * across a GUI session.
 */
public final class OfflinePlayerEditSession {

    /** Suffix of the temporary file holding a staged, verified compressed-NBT result. */
    static final String STAGED_SUFFIX = ".staged";

    /** Suffix of the byte-for-byte backup created immediately before replacement. */
    static final String BACKUP_SUFFIX = ".backup";

    private final PlayerDataCodec codec;
    private final Path source;
    private final byte[] sourceNbt;
    private final byte[] sourceDigest;
    private final OfflinePlayerSnapshot snapshot;

    OfflinePlayerEditSession(
            PlayerDataCodec codec,
            Path source,
            byte[] sourceNbt,
            byte[] sourceDigest,
            OfflinePlayerSnapshot snapshot) {
        this.codec = codec;
        this.source = source;
        this.sourceNbt = sourceNbt.clone();
        this.sourceDigest = sourceDigest.clone();
        this.snapshot = snapshot;
    }

    /** @return the player-data file this session will replace */
    public Path source() {
        return source;
    }

    /** @return SHA-256 of the source file as it was opened */
    public byte[] sourceDigest() {
        return sourceDigest.clone();
    }

    /** @return the decoded inventory and Ender Chest snapshot as it was opened */
    public OfflinePlayerSnapshot snapshot() {
        return snapshot;
    }

    /**
     * Converts {@code inventory} and {@code enderItems} into compressed player-data NBT and writes
     * a verified temporary file next to the source.
     *
     * <p>The source file is never touched: only a sibling temporary file is created. The staged
     * bytes are decoded back before this method returns, so a result that cannot be read is
     * rejected here rather than at commit time.
     *
     * @param inventory the ordinary inventory slots to persist (Bukkit slots 0–40)
     * @param enderItems the Ender Chest slots to persist (0–26)
     * @return the staged write, ready to pass to {@link #commit}
     * @throws IOException if encoding, writing, flushing, or read-back verification fails
     */
    public StagedOfflineWrite stage(List<SlottedItem> inventory, List<SlottedItem> enderItems)
            throws IOException {
        byte[] edited = codec.encode(sourceNbt, inventory, enderItems);
        Path directory = source.getParent();
        Path staged = Files.createTempFile(directory, prefix(), STAGED_SUFFIX);
        try {
            Files.write(
                    staged,
                    edited,
                    StandardOpenOption.WRITE,
                    StandardOpenOption.TRUNCATE_EXISTING);
            force(staged);
            codec.decode(Files.readAllBytes(staged));
        } catch (IOException e) {
            Files.deleteIfExists(staged);
            throw e;
        }
        return new StagedOfflineWrite(source, staged, sourceDigest);
    }

    /**
     * Replaces the source with the staged write when the target is still offline and the source is
     * unchanged.
     *
     * <p>Must be invoked in a single server-main-thread turn so that a player join cannot
     * interleave between the offline check and the replace. Both abort paths delete the staged
     * file and leave the source untouched. The backup is a byte-for-byte copy of the source
     * created immediately before the atomic replace.
     *
     * @param write the result of {@link #stage}
     * @param targetStillOffline re-checked inside this main-thread turn
     * @return the commit outcome
     * @throws IOException if the backup or replace fails, or atomic replacement is unsupported
     */
    public OfflineCommitResult commit(StagedOfflineWrite write, BooleanSupplier targetStillOffline)
            throws IOException {
        if (!targetStillOffline.getAsBoolean()) {
            discard(write);
            return OfflineCommitResult.ABORTED_TARGET_ONLINE;
        }
        if (!MessageDigest.isEqual(write.sourceDigest(), sha256(Files.readAllBytes(source)))) {
            discard(write);
            return OfflineCommitResult.ABORTED_SOURCE_CHANGED;
        }
        backupSource();
        try {
            Files.move(
                    write.staged(),
                    source,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            throw new IOException("atomic replacement is not supported for " + source, e);
        }
        return OfflineCommitResult.COMMITTED;
    }

    private void backupSource() throws IOException {
        Path backup = Files.createTempFile(source.getParent(), prefix(), BACKUP_SUFFIX);
        Files.copy(
                source,
                backup,
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.COPY_ATTRIBUTES);
        force(backup);
    }

    private static void discard(StagedOfflineWrite write) throws IOException {
        Files.deleteIfExists(write.staged());
    }

    private String prefix() {
        return source.getFileName().toString() + ".";
    }

    private static void force(Path file) throws IOException {
        try (FileChannel channel = FileChannel.open(file, StandardOpenOption.WRITE)) {
            channel.force(true);
        }
    }

    static byte[] sha256(byte[] bytes) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(bytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required", e);
        }
    }
}
