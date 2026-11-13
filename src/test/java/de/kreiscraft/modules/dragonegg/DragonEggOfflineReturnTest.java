package de.kreiscraft.modules.dragonegg;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import de.kreiscraft.core.message.MessageService;
import de.kreiscraft.platform.OfflinePlayerDataEditor;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

class DragonEggOfflineReturnTest {
    @TempDir
    Path folder;
    ServerMock server;
    DragonEggData data;
    DragonEggTracker tracker;
    OfflinePlayerDataEditor editor;
    MessageService messages;
    DragonEggOfflineReturn returns;
    DragonEggOfflineReturn.DropStorage storage;
    AtomicLong now = new AtomicLong(1_000_000);

    @BeforeEach
    void setUp() throws Exception {
        server = MockBukkit.mock();
        server.addSimpleWorld("world");
        data = new DragonEggData(folder);
        data.load();
        tracker = new DragonEggTracker(server, data, now::get);
        editor = mock(OfflinePlayerDataEditor.class);
        messages = mock(MessageService.class);
        storage = mock(DragonEggOfflineReturn.DropStorage.class);
        returns = new DragonEggOfflineReturn(server, tracker, data, editor, messages, now::get, storage);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void deadlineIncludesDowntimeAndReturningBeforeItClearsDeadline() throws Exception {
        var player = server.addPlayer();
        player.teleport(new Location(server.getWorld("world"), 12, 70, 4));
        ItemStack egg = new ItemStack(Material.DRAGON_EGG);
        tracker.claim(egg);
        player.getInventory().setItem(0, egg);
        returns.quit(player);
        data.load();
        assertThat(data.get().offlineDeadline).isEqualTo(87_400_000);
        now.addAndGet(Duration.ofHours(23).toMillis());
        returns.tick();
        verifyNoInteractions(editor);
        returns.join(player);
        data.load();
        assertThat(data.get().offlineDeadline).isZero();
    }

    @Test
    void overdueNestedEggIsRemovedThenPlacedAtLogoutAndNotifiedOnce() throws Exception {
        var player = server.addPlayer();
        player.teleport(new Location(server.getWorld("world"), 12, 70, 4));
        ItemStack egg = new ItemStack(Material.DRAGON_EGG);
        tracker.claim(egg);
        ItemStack nested = DragonEggTrackerTest.bundle(egg);
        player.getInventory().setItem(0, nested);
        returns.quit(player);
        player.disconnect();
        var session = mock(de.kreiscraft.platform.OfflinePlayerEditSession.class);
        var stagedFile = folder.resolve("edited.dat");
        java.nio.file.Files.writeString(stagedFile, "edited");
        var source = folder.resolve("player.dat");
        java.nio.file.Files.writeString(source, "original");
        var write = new de.kreiscraft.platform.StagedOfflineWrite(source, stagedFile, new byte[32]);
        when(session.sourceDigest()).thenReturn(new byte[32]);
        when(session.source()).thenReturn(source);
        when(session.snapshot()).thenReturn(de.kreiscraft.platform.OfflinePlayerSnapshot.ofInventory(
                java.util.List.of(new de.kreiscraft.platform.SlottedItem(0, nested))));
        when(session.stage(anyList(), anyList())).thenReturn(write);
        when(session.commit(eq(write), any())).thenAnswer(call -> {
            assertThat(((java.util.function.BooleanSupplier) call.getArgument(1)).getAsBoolean()).isTrue();
            var block = player.getWorld().getBlockAt(12, 70, 4);
            assertThat(block.getType()).isEqualTo(Material.AIR);
            return de.kreiscraft.platform.OfflineCommitResult.COMMITTED;
        });
        when(editor.openForEdit(player.getUniqueId())).thenReturn(java.util.Optional.of(session));
        now.addAndGet(Duration.ofHours(24).toMillis());
        data.load();
        returns.tick();
        var barrel = (org.bukkit.block.Barrel) player.getWorld().getBlockAt(12, 70, 4).getState();
        assertThat(tracker.isTrackedEgg(barrel.getInventory().getItem(13))).isTrue();
        var edited = org.mockito.ArgumentCaptor.forClass(java.util.List.class);
        verify(session).stage(edited.capture(), anyList());
        var outer = ((de.kreiscraft.platform.SlottedItem) edited.getValue().getFirst()).stack();
        assertThat(outer.getType()).isEqualTo(Material.BUNDLE);
        assertThat(tracker.containsTrackedEgg(outer)).isFalse();
        verify(messages).broadcast(eq("dragon-egg.offline-drop"), any(Object[].class));
        data.load();
        returns.tick();
        verify(messages, times(1)).broadcast(eq("dragon-egg.offline-drop"), any(Object[].class));
        returns.join(player);
        verify(messages).send(eq(player), eq("dragon-egg.offline-return"), any(Object[].class));
        data.load();
        returns.join(player);
        verify(messages, times(1)).send(eq(player), eq("dragon-egg.offline-return"), any(Object[].class));
    }

    private org.mockbukkit.mockbukkit.entity.PlayerMock removedJournal() throws Exception {
        var player = server.addPlayer();
        player.teleport(new Location(server.getWorld("world"), 12, 70, 4));
        ItemStack egg = new ItemStack(Material.DRAGON_EGG);
        tracker.claim(egg);
        tracker.mark(egg);
        player.getInventory().setItem(0, egg);
        returns.quit(player);
        player.disconnect();
        var journal = new DragonEggData.ReturnJournal();
        journal.phase = "REMOVED";
        journal.carrier = player.getUniqueId().toString();
        journal.token = java.util.UUID.randomUUID().toString();
        journal.egg = java.util.Base64.getEncoder().encodeToString(egg.serializeAsBytes());
        journal.originalDigest = "0".repeat(64);
        journal.editedDigest = "1".repeat(64);
        journal.destination = DragonEggTracker.Position.of(player.getLocation().getBlock().getLocation());
        data.update(state -> state.returnJournal = journal);
        data.flushIfDirty();
        return player;
    }

    @Test
    void occupiedLogoutBlockUsesNearestAirAndKeepsTwoMinuteSampling() throws Exception {
        var player = removedJournal();
        var origin = player.getWorld().getBlockAt(12, 70, 4);
        origin.setType(Material.STONE);
        // Already sampled logout position must not be refreshed early by return placement.
        tracker.sample();
        returns.tick();
        assertThat(origin.getType()).isEqualTo(Material.STONE);
        assertThat(data.get().position.x()).isEqualTo(11);
        assertThat(data.get().position.y()).isEqualTo(70);
        assertThat(data.get().position.z()).isEqualTo(4);
        assertThat(tracker.ping().orElseThrow().position().x()).isEqualTo(12);
        now.addAndGet(120_000);
        tracker.sample();
        assertThat(tracker.ping().orElseThrow().position().x()).isEqualTo(11);
        returns.join(player);
        verify(messages).send(player, "dragon-egg.offline-return", "dimension", "minecraft:overworld",
                "x", 11, "y", 70, "z", 4);
    }

    @Test
    void pendingBarrelCannotBeTakenWhileWorldSaveFailsAndRecoveryDoesNotDuplicate() throws Exception {
        var player = removedJournal();
        doThrow(new IllegalStateException("world save failed")).doNothing().when(storage).saveAndVerify(any(), any(),
                any());
        assertThatThrownBy(returns::tick).isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(messages);
        var block = player.getWorld().getBlockAt(12, 70, 4);
        var breaking = new org.bukkit.event.block.BlockBreakEvent(block, player);
        returns.protectBreak(breaking);
        assertThat(breaking.isCancelled()).isTrue();
        var barrel = (org.bukkit.block.Barrel) block.getState();
        assertThat(tracker.isTrackedEgg(barrel.getInventory().getItem(13))).isTrue();
        data.load();
        returns.tick();
        assertThat(barrel.getInventory().getContents()).filteredOn(java.util.Objects::nonNull).hasSize(1);
        verify(messages, times(1)).broadcast(eq("dragon-egg.offline-drop"), any(Object[].class));
    }

    @Test
    void preparedRemovalRecoversOnlyWhenDigestProvesWhichFileWon() throws Exception {
        var player = removedJournal();
        var session = mock(de.kreiscraft.platform.OfflinePlayerEditSession.class);
        when(editor.openForEdit(player.getUniqueId())).thenReturn(java.util.Optional.of(session));
        data.update(state -> state.returnJournal.phase = "PREPARED");
        data.flushIfDirty();
        when(session.sourceDigest()).thenReturn(new byte[32]);
        returns.tick();
        assertThat(data.get().returnJournal).isNull();
        assertThat(player.getWorld().getBlockAt(12, 70, 4).getType()).isEqualTo(Material.AIR);
        verifyNoInteractions(messages, storage);

        player = removedJournal();
        when(editor.openForEdit(player.getUniqueId())).thenReturn(java.util.Optional.of(session));
        data.update(state -> state.returnJournal.phase = "PREPARED");
        data.flushIfDirty();
        when(session.sourceDigest()).thenReturn(java.util.HexFormat.of().parseHex("2".repeat(64)));
        assertThatThrownBy(returns::tick).isInstanceOf(java.io.IOException.class).hasMessageContaining("ambiguous");
        data.load();
        assertThat(data.get().returnJournal.phase).isEqualTo("PREPARED");
        verifyNoInteractions(messages, storage);
        when(session.sourceDigest()).thenReturn(java.util.HexFormat.of().parseHex("1".repeat(64)));
        returns.tick();
        assertThat(player.getWorld().getBlockAt(12, 70, 4).getType()).isEqualTo(Material.BARREL);
        verify(messages).broadcast(eq("dragon-egg.offline-drop"), any(Object[].class));
    }

    @Test
    void configurationLoginReservesPlayerFileAndRecoveryBlocksCarrierLogin() throws Exception {
        var player = removedJournal();
        var connection = mock(io.papermc.paper.connection.PlayerConfigurationConnection.class);
        var profile = mock(com.destroystokyo.paper.profile.PlayerProfile.class);
        when(profile.getId()).thenReturn(player.getUniqueId());
        when(connection.getProfile()).thenReturn(profile);
        when(connection.isConnected()).thenReturn(true);
        when(messages.render("dragon-egg.return-recovering"))
                .thenReturn(net.kyori.adventure.text.Component.text("retry"));
        var blocked = new io.papermc.paper.event.connection.PlayerConnectionValidateLoginEvent(connection, null);
        returns.validateLogin(blocked);
        assertThat(blocked.isAllowed()).isFalse();
        // No removal has begun after an early return: reserve configuration until disconnect.
        data.update(state -> state.returnJournal = null);
        data.flushIfDirty();
        var allowed = new io.papermc.paper.event.connection.PlayerConnectionValidateLoginEvent(connection, null);
        returns.validateLogin(allowed);
        assertThat(allowed.isAllowed()).isTrue();
        now.addAndGet(Duration.ofHours(24).toMillis());
        returns.tick();
        verifyNoInteractions(editor, storage);
        when(connection.isConnected()).thenReturn(false);
        assertThatThrownBy(returns::tick).isInstanceOf(java.io.IOException.class)
                .hasMessageContaining("missing offline carrier data");
    }

    @Test
    void loginNotificationIsRetainedUntilSendReturnsSuccessfully() throws Exception {
        var player = removedJournal();
        returns.tick();
        doThrow(new IllegalStateException("chat failed")).doNothing().when(messages)
                .send(eq(player), eq("dragon-egg.offline-return"), any(Object[].class));
        assertThatThrownBy(() -> returns.join(player)).isInstanceOf(IllegalStateException.class);
        data.load();
        assertThat(data.get().notifications).containsKey(player.getUniqueId().toString());
        returns.join(player);
        data.load();
        assertThat(data.get().notifications).isEmpty();
    }

    @Test
    void noSafeBlockLeavesPlayerInventoryOwnerAndRetriesWithoutAnnouncement() throws Exception {
        var player = removedJournal();
        data.update(state -> state.returnJournal = null);
        data.flushIfDirty();
        var origin = player.getLocation().getBlock();
        for (int x = -16; x <= 16; x++) {
            for (int y = -16; y <= 16; y++) {
                for (int z = -16; z <= 16; z++) {
                    origin.getRelative(x, y, z).setType(Material.STONE);
                }
            }
        }
        var session = mock(de.kreiscraft.platform.OfflinePlayerEditSession.class);
        when(editor.openForEdit(player.getUniqueId())).thenReturn(java.util.Optional.of(session));
        now.addAndGet(Duration.ofHours(24).toMillis());
        returns.tick();
        returns.tick();
        assertThat(tracker.containsTrackedEgg(player.getInventory().getItem(0))).isTrue();
        verifyNoInteractions(session, storage, messages);
        assertThat(data.get().offlineDeadline).isPositive();
    }

    @Test
    void overdueLoginCannotResetDeadlineBeforeTheStartupReturnRuns() throws Exception {
        var player = removedJournal();
        data.update(state -> state.returnJournal = null);
        data.flushIfDirty();
        now.addAndGet(Duration.ofHours(24).toMillis());
        var connection = mock(io.papermc.paper.connection.PlayerConfigurationConnection.class);
        var profile = mock(com.destroystokyo.paper.profile.PlayerProfile.class);
        when(profile.getId()).thenReturn(player.getUniqueId());
        when(connection.getProfile()).thenReturn(profile);
        when(messages.render("dragon-egg.return-recovering"))
                .thenReturn(net.kyori.adventure.text.Component.text("retry"));
        var login = new io.papermc.paper.event.connection.PlayerConnectionValidateLoginEvent(connection, null);
        assertThatThrownBy(() -> returns.validateLogin(login))
                .isInstanceOf(de.kreiscraft.core.module.ModuleException.class);
        assertThat(login.isAllowed()).isFalse();
        assertThat(data.get().offlineDeadline).isEqualTo(87_400_000);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.EnumSource(de.kreiscraft.platform.OfflineCommitResult.class)
    void offlineCommitOutcomeControlsPlacementAndPersistenceFailuresLeaveJournalRecoverable(
            de.kreiscraft.platform.OfflineCommitResult outcome) throws Exception {
        var player = removedJournal();
        data.update(state -> state.returnJournal = null);
        data.flushIfDirty();
        var session = mock(de.kreiscraft.platform.OfflinePlayerEditSession.class);
        var file = folder.resolve("staged.dat");
        java.nio.file.Files.writeString(file, "edited");
        var write = new de.kreiscraft.platform.StagedOfflineWrite(folder.resolve("source.dat"), file, new byte[32]);
        when(editor.openForEdit(player.getUniqueId())).thenReturn(java.util.Optional.of(session));
        when(session.sourceDigest()).thenReturn(new byte[32]);
        when(session.snapshot()).thenReturn(de.kreiscraft.platform.OfflinePlayerSnapshot.ofInventory(
                java.util.List.of(new de.kreiscraft.platform.SlottedItem(0, player.getInventory().getItem(0)))));
        when(session.stage(anyList(), anyList())).thenReturn(write);
        when(session.commit(eq(write), any())).thenReturn(outcome);
        now.addAndGet(Duration.ofHours(24).toMillis());
        // Make only the journal destination unwritable: the player source must remain untouched.
        var activityFile = folder.resolve("data/dragon-egg.yml");
        java.nio.file.Files.delete(activityFile);
        java.nio.file.Files.createDirectory(activityFile);
        assertThatThrownBy(returns::tick).isInstanceOf(java.io.IOException.class);
        verify(session, never()).commit(any(), any());
        verifyNoInteractions(storage, messages);
        java.nio.file.Files.delete(activityFile);
        // Retry first flushes/reconciles PREPARED (original source won), then starts a fresh edit.
        returns.tick();
        returns.tick();
        assertThat(player.getWorld().getBlockAt(12, 70, 4).getType())
                .isEqualTo(outcome == de.kreiscraft.platform.OfflineCommitResult.COMMITTED ? Material.BARREL
                        : Material.AIR);
        if (outcome == de.kreiscraft.platform.OfflineCommitResult.COMMITTED) {
            verify(messages).broadcast(eq("dragon-egg.offline-drop"), any(Object[].class));
        } else {
            verifyNoInteractions(messages, storage);
        }
    }
}
