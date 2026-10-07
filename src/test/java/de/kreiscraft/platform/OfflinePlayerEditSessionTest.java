package de.kreiscraft.platform;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;

/**
 * File-protocol tests for the accepted two-phase offline commit boundary.
 *
 * <p>Runs against real temporary files with a substitute {@link PlayerDataCodec}, so the staging,
 * verification, backup, abort, and atomic-replace behaviour is exercised without the NMS surface.
 */
class OfflinePlayerEditSessionTest {

    private static final byte[] SOURCE = "SOURCE-NBT".getBytes(UTF_8);

    @TempDir
    Path dir;

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    // -----------------------------------------------------------------------
    // stage
    // -----------------------------------------------------------------------

    @Test
    void stage_writesVerifiedFileWithoutTouchingSource() throws IOException {
        Path source = writeSource();
        FakeCodec codec = new FakeCodec();
        OfflinePlayerEditSession session = newSession(codec, source);

        StagedOfflineWrite write =
                session.stage(List.of(new SlottedItem(9, new ItemStack(Material.DIRT))), List.of());

        assertThat(codec.encodeCalls).isEqualTo(1);
        assertThat(codec.lastSource).isEqualTo(SOURCE);
        assertThat(codec.decodeCalls).isEqualTo(1); // read-back verification
        assertThat(Files.readAllBytes(write.staged()))
                .isEqualTo("ENCODED:10:1:0".getBytes(UTF_8));
        assertThat(Files.readAllBytes(source)).isEqualTo(SOURCE);
        assertThat(write.source()).isEqualTo(source);
        assertThat(write.sourceDigest()).isEqualTo(OfflinePlayerEditSession.sha256(SOURCE));
        assertThat(filesEndingWith(OfflinePlayerEditSession.BACKUP_SUFFIX)).isEmpty();
    }

    @Test
    void stage_readBackFailure_deletesStagedFileAndLeavesSource() throws IOException {
        Path source = writeSource();
        FakeCodec codec = new FakeCodec();
        OfflinePlayerEditSession session = newSession(codec, source);
        codec.decodeFailure = new IOException("staged data is unreadable");

        assertThatThrownBy(() -> session.stage(List.of(), List.of()))
                .isInstanceOf(IOException.class)
                .hasMessage("staged data is unreadable");

        assertThat(Files.readAllBytes(source)).isEqualTo(SOURCE);
        assertThat(filesEndingWith(OfflinePlayerEditSession.STAGED_SUFFIX)).isEmpty();
    }

    @Test
    void stage_encodeFailure_propagatesWithoutWritingAnything() throws IOException {
        Path source = writeSource();
        FakeCodec codec = new FakeCodec();
        OfflinePlayerEditSession session = newSession(codec, source);
        codec.encodeFailure = new IOException("cannot encode");

        assertThatThrownBy(() -> session.stage(List.of(), List.of()))
                .isInstanceOf(IOException.class)
                .hasMessage("cannot encode");

        assertThat(Files.readAllBytes(source)).isEqualTo(SOURCE);
        assertThat(filesEndingWith(OfflinePlayerEditSession.STAGED_SUFFIX)).isEmpty();
    }

    @Test
    void stage_forwardsOrdinaryArmorOffhandAndEnderItemsToCodec() throws IOException {
        Path source = writeSource();
        FakeCodec codec = new FakeCodec();
        OfflinePlayerEditSession session = newSession(codec, source);

        session.stage(
                List.of(
                        new SlottedItem(0, new ItemStack(Material.STONE)),
                        new SlottedItem(9, new ItemStack(Material.DIRT)),
                        new SlottedItem(36, new ItemStack(Material.DIAMOND_BOOTS)),
                        new SlottedItem(39, new ItemStack(Material.DIAMOND_HELMET)),
                        new SlottedItem(40, new ItemStack(Material.SHIELD))),
                List.of(
                        new SlottedItem(0, new ItemStack(Material.SHULKER_BOX)),
                        new SlottedItem(26, new ItemStack(Material.ENDER_PEARL))));

        assertThat(codec.lastInventory).extracting(SlottedItem::slot).containsExactly(
                0, 9, 36, 39, 40);
        assertThat(codec.lastEnder).extracting(SlottedItem::slot).containsExactly(0, 26);
    }

    @Test
    void stage_emptyInventoryAndEnderItems_passesEmptyLists() throws IOException {
        Path source = writeSource();
        FakeCodec codec = new FakeCodec();
        OfflinePlayerEditSession session = newSession(codec, source);

        session.stage(List.of(), List.of());

        assertThat(codec.lastInventory).isEmpty();
        assertThat(codec.lastEnder).isEmpty();
    }

    @Test
    void stage_passesMetadataRichItemThroughUnchanged() throws IOException {
        Path source = writeSource();
        FakeCodec codec = new FakeCodec();
        OfflinePlayerEditSession session = newSession(codec, source);
        ItemStack sword = new ItemStack(Material.DIAMOND_SWORD);
        org.bukkit.inventory.meta.ItemMeta meta = sword.getItemMeta();
        meta.displayName(net.kyori.adventure.text.Component.text("Excalibur"));
        sword.setItemMeta(meta);

        session.stage(List.of(new SlottedItem(0, sword)), List.of());

        assertThat(codec.lastInventory).singleElement().satisfies(slotted -> {
            assertThat(slotted.stack().getItemMeta().displayName())
                    .isEqualTo(net.kyori.adventure.text.Component.text("Excalibur"));
        });
    }

    // -----------------------------------------------------------------------
    // commit — abort paths
    // -----------------------------------------------------------------------

    @Test
    void commit_targetOnline_abortsAndDeletesStaged() throws IOException {
        Path source = writeSource();
        FakeCodec codec = new FakeCodec();
        OfflinePlayerEditSession session = newSession(codec, source);
        StagedOfflineWrite write = session.stage(List.of(), List.of());

        OfflineCommitResult result = session.commit(write, () -> false);

        assertThat(result).isEqualTo(OfflineCommitResult.ABORTED_TARGET_ONLINE);
        assertThat(Files.readAllBytes(source)).isEqualTo(SOURCE);
        assertThat(Files.exists(write.staged())).isFalse();
        assertThat(filesEndingWith(OfflinePlayerEditSession.BACKUP_SUFFIX)).isEmpty();
    }

    @Test
    void commit_sourceChanged_abortsAndLeavesNewerSource() throws IOException {
        Path source = writeSource();
        FakeCodec codec = new FakeCodec();
        OfflinePlayerEditSession session = newSession(codec, source);
        StagedOfflineWrite write = session.stage(List.of(), List.of());
        byte[] newer = "NEWER-SERVER-STATE".getBytes(UTF_8);
        Files.write(source, newer);

        OfflineCommitResult result = session.commit(write, () -> true);

        assertThat(result).isEqualTo(OfflineCommitResult.ABORTED_SOURCE_CHANGED);
        assertThat(Files.readAllBytes(source)).isEqualTo(newer);
        assertThat(Files.exists(write.staged())).isFalse();
        assertThat(filesEndingWith(OfflinePlayerEditSession.BACKUP_SUFFIX)).isEmpty();
    }

    @Test
    void commit_missingSource_propagatesWriteFailure() throws IOException {
        Path source = writeSource();
        FakeCodec codec = new FakeCodec();
        OfflinePlayerEditSession session = newSession(codec, source);
        StagedOfflineWrite write = session.stage(List.of(), List.of());
        Files.delete(source);

        assertThatThrownBy(() -> session.commit(write, () -> true))
                .isInstanceOf(IOException.class);
    }

    // -----------------------------------------------------------------------
    // commit — success
    // -----------------------------------------------------------------------

    @Test
    void commit_successReplacesSourceAndKeepsByteForByteBackup() throws IOException {
        Path source = writeSource();
        FakeCodec codec = new FakeCodec();
        OfflinePlayerEditSession session = newSession(codec, source);
        StagedOfflineWrite write =
                session.stage(List.of(new SlottedItem(0, new ItemStack(Material.STONE))), List.of());

        OfflineCommitResult result = session.commit(write, () -> true);

        assertThat(result).isEqualTo(OfflineCommitResult.COMMITTED);
        assertThat(Files.readAllBytes(source)).isEqualTo("ENCODED:10:1:0".getBytes(UTF_8));
        assertThat(Files.exists(write.staged())).isFalse();
        List<Path> backups = filesEndingWith(OfflinePlayerEditSession.BACKUP_SUFFIX);
        assertThat(backups).hasSize(1);
        assertThat(Files.readAllBytes(backups.get(0))).isEqualTo(SOURCE);
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private Path writeSource() throws IOException {
        return Files.write(dir.resolve("player.dat"), SOURCE);
    }

    private static OfflinePlayerEditSession newSession(FakeCodec codec, Path source) {
        return new OfflinePlayerEditSession(
                codec, source, SOURCE, OfflinePlayerEditSession.sha256(SOURCE),
                OfflinePlayerSnapshot.empty());
    }

    private List<Path> filesEndingWith(String suffix) throws IOException {
        try (Stream<Path> files = Files.list(dir)) {
            return files.filter(p -> p.getFileName().toString().endsWith(suffix)).toList();
        }
    }

    private static final class FakeCodec implements PlayerDataCodec {

        private int encodeCalls;
        private int decodeCalls;
        private byte[] lastSource;
        private List<SlottedItem> lastInventory;
        private List<SlottedItem> lastEnder;
        private IOException encodeFailure;
        private IOException decodeFailure;
        private Function<byte[], OfflinePlayerSnapshot> decodeResult =
                bytes -> OfflinePlayerSnapshot.empty();

        @Override
        public OfflinePlayerSnapshot decode(byte[] nbtBytes) throws IOException {
            decodeCalls++;
            if (decodeFailure != null) {
                throw decodeFailure;
            }
            return decodeResult.apply(nbtBytes);
        }

        @Override
        public byte[] encode(
                byte[] sourceNbtBytes, List<SlottedItem> inventory, List<SlottedItem> enderItems)
                throws IOException {
            encodeCalls++;
            lastSource = sourceNbtBytes.clone();
            lastInventory = inventory;
            lastEnder = enderItems;
            if (encodeFailure != null) {
                throw encodeFailure;
            }
            return ("ENCODED:" + sourceNbtBytes.length + ":" + inventory.size() + ":"
                    + enderItems.size())
                    .getBytes(UTF_8);
        }
    }
}
