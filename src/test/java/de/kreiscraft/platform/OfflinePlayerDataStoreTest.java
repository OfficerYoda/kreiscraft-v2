package de.kreiscraft.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;

class OfflinePlayerDataStoreTest {

    @TempDir
    Path playerDataDir;

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void read_missingFile_returnsEmptySnapshotWithoutDecoding() throws IOException {
        RecordingCodec codec = new RecordingCodec();
        OfflinePlayerDataStore store = new OfflinePlayerDataStore(playerDataDir, codec);

        OfflinePlayerSnapshot snapshot = store.read(UUID.randomUUID());

        assertThat(snapshot.isEmpty()).isTrue();
        assertThat(codec.calls).isZero();
    }

    @Test
    void read_existingFile_decodesFileBytes() throws IOException {
        UUID playerId = UUID.randomUUID();
        byte[] raw = {1, 2, 3, 4};
        Files.write(playerDataDir.resolve(playerId + ".dat"), raw);
        RecordingCodec codec = new RecordingCodec();
        codec.result =
                new OfflinePlayerSnapshot(
                        List.of(new SlottedItem(9, new ItemStack(Material.DIRT))), List.of());
        OfflinePlayerDataStore store = new OfflinePlayerDataStore(playerDataDir, codec);

        OfflinePlayerSnapshot snapshot = store.read(playerId);

        assertThat(codec.lastBytes).isEqualTo(raw);
        assertThat(snapshot).isSameAs(codec.result);
    }

    @Test
    void read_doesNotMutateSourceFile() throws IOException {
        UUID playerId = UUID.randomUUID();
        Path file = playerDataDir.resolve(playerId + ".dat");
        byte[] raw = {9, 8, 7, 6, 5};
        Files.write(file, raw);
        OfflinePlayerDataStore store =
                new OfflinePlayerDataStore(playerDataDir, new RecordingCodec());

        store.read(playerId);

        assertThat(Files.readAllBytes(file)).isEqualTo(raw);
    }

    @Test
    void read_createsNoNewFiles() throws IOException {
        UUID playerId = UUID.randomUUID();
        Files.write(playerDataDir.resolve(playerId + ".dat"), new byte[] {1});
        OfflinePlayerDataStore store =
                new OfflinePlayerDataStore(playerDataDir, new RecordingCodec());

        store.read(playerId);

        assertThat(Files.list(playerDataDir).count()).isEqualTo(1);
    }

    @Test
    void read_decoderFailure_propagatesIOException() throws IOException {
        UUID playerId = UUID.randomUUID();
        Files.write(playerDataDir.resolve(playerId + ".dat"), new byte[] {1});
        RecordingCodec codec = new RecordingCodec();
        codec.failure = new IOException("corrupt");
        OfflinePlayerDataStore store = new OfflinePlayerDataStore(playerDataDir, codec);

        assertThatThrownBy(() -> store.read(playerId))
                .isInstanceOf(IOException.class)
                .hasMessage("corrupt");
    }

    // -----------------------------------------------------------------------
    // openForEdit
    // -----------------------------------------------------------------------

    @Test
    void openForEdit_missingFile_returnsEmptyAndDoesNotDecode() throws IOException {
        RecordingCodec codec = new RecordingCodec();
        OfflinePlayerDataStore store = new OfflinePlayerDataStore(playerDataDir, codec);

        assertThat(store.openForEdit(UUID.randomUUID())).isEmpty();
        assertThat(codec.calls).isZero();
    }

    @Test
    void openForEdit_existingFile_returnsSessionWithDecodedSnapshotAndDigest() throws IOException {
        UUID playerId = UUID.randomUUID();
        byte[] raw = {1, 2, 3, 4};
        Files.write(playerDataDir.resolve(playerId + ".dat"), raw);
        RecordingCodec codec = new RecordingCodec();
        OfflinePlayerSnapshot decoded =
                new OfflinePlayerSnapshot(
                        List.of(new SlottedItem(9, new ItemStack(Material.DIRT))), List.of());
        codec.result = decoded;
        OfflinePlayerDataStore store = new OfflinePlayerDataStore(playerDataDir, codec);

        OfflinePlayerEditSession session = store.openForEdit(playerId).orElseThrow();

        assertThat(codec.lastBytes).isEqualTo(raw);
        assertThat(session.snapshot()).isSameAs(decoded);
        assertThat(session.sourceDigest())
                .isEqualTo(OfflinePlayerEditSession.sha256(raw));
        assertThat(session.source()).isEqualTo(playerDataDir.resolve(playerId + ".dat"));
    }

    private static final class RecordingCodec implements PlayerDataCodec {

        private int calls;
        private byte[] lastBytes;
        private OfflinePlayerSnapshot result = OfflinePlayerSnapshot.empty();
        private IOException failure;

        @Override
        public OfflinePlayerSnapshot decode(byte[] nbtBytes) throws IOException {
            calls++;
            lastBytes = Arrays.copyOf(nbtBytes, nbtBytes.length);
            if (failure != null) {
                throw failure;
            }
            return result;
        }

        @Override
        public byte[] encode(
                byte[] sourceNbtBytes, List<SlottedItem> inventory, List<SlottedItem> enderItems) {
            throw new UnsupportedOperationException("read-only codec");
        }
    }
}
