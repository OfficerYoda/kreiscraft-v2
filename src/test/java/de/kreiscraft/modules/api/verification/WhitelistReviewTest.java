package de.kreiscraft.modules.api.verification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mojang.brigadier.CommandDispatcher;
import de.kreiscraft.core.message.MessageService;
import de.kreiscraft.core.message.ConfigurateMessageService;
import de.kreiscraft.core.scheduler.Scheduler;
import de.kreiscraft.modules.api.verification.WhitelistRequests.Status;
import de.kreiscraft.shared.gui.Gui;
import de.kreiscraft.shared.whitelist.WhitelistService;
import de.kreiscraft.testsupport.MockScheduler;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.datacomponent.DataComponentTypes;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.player.PlayerJoinEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import xyz.xenondevs.invui.Click;
import xyz.xenondevs.invui.item.ItemWrapper;

class WhitelistReviewTest {
    @TempDir
    Path directory;
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-12T12:00:00Z"), ZoneOffset.UTC);
    private final Set<String> members = new HashSet<>();
    private ServerMock server;
    private PlayerMock admin;
    private WhitelistRequests requests;
    private WhitelistService whitelist;
    private WhitelistReview review;
    private MessageService messages;
    private Gui facade;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        admin = server.addPlayer("Reviewer");
        admin.setOp(true);
        whitelist = mock(WhitelistService.class);
        when(whitelist.allNames()).thenAnswer(invocation -> List.copyOf(members));
        doAnswer(invocation -> members.add(invocation.getArgument(0))).when(whitelist).addJavaPlayer(anyString());
        doAnswer(invocation -> members.remove(invocation.getArgument(0))).when(whitelist).remove(anyString());
        messages = mock(MessageService.class, invocation -> invocation.getMethod().getReturnType() == Component.class
                ? Component.text((String) invocation.getArgument(1))
                : null);
        facade = mock(Gui.class);
        requests = new WhitelistRequests(directory, new ObjectMapper(), clock, entry -> review.submitted(entry));
        review = new WhitelistReview(requests, whitelist, facade, messages, new MockScheduler(), server,
                Logger.getLogger("whitelist-test"));
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void submissionsNotifyOnlyReviewersOnceAndJoinCountsOnlyPending() throws Exception {
        PlayerMock ordinary = server.addPlayer("Ordinary");
        requests.get("Alice", true, false);
        requests.get("ALICE", true, false);
        Component notification = admin.nextComponentMessage();
        assertThat(notification).isNotNull();
        String id = requests.list().getFirst().submissionId();
        assertThat(notification.children().get(1).clickEvent())
                .isEqualTo(ClickEvent.runCommand("/whitelist-requests approve Alice " + id));
        assertThat(notification.children().get(3).clickEvent())
                .isEqualTo(ClickEvent.runCommand("/whitelist-requests deny Alice " + id));
        assertThat(admin.nextComponentMessage()).isNull();
        assertThat(ordinary.nextComponentMessage()).isNull();
        review.onJoin(new PlayerJoinEvent(admin, Component.empty()));
        assertThat(admin.nextComponentMessage().clickEvent()).isEqualTo(ClickEvent.runCommand("/whitelist-requests"));
        members.add("Alice");
        review.onJoin(new PlayerJoinEvent(admin, Component.empty()));
        review.onJoin(new PlayerJoinEvent(ordinary, Component.empty()));
        assertThat(admin.nextComponentMessage()).isNull();
        assertThat(ordinary.nextComponentMessage()).isNull();
    }

    @Test
    void headClicksApproveDenyAndReversePreviousDecisions() throws Exception {
        requests.get("Alice", true, false);
        click(review.build(admin, requests.list(), Status.PENDING, 0), 0, ClickType.LEFT);
        assertThat(members).containsExactly("Alice");
        assertThat(requests.list().getFirst().status()).isEqualTo(Status.APPROVED);
        assertThat(requests.get("alice", false, true).body()).containsEntry("resolvedAt", clock.instant().toString());
        var approved = review.build(admin, requests.list(), Status.APPROVED, 0);
        assertThat(approved.getItem(0).getItemProvider(admin).get().getType()).isEqualTo(Material.PLAYER_HEAD);
        click(approved, 0, ClickType.RIGHT);
        assertThat(members).isEmpty();
        assertThat(requests.get("Alice", false, false).body()).containsEntry("status", "declined");
        click(review.build(admin, requests.list(), Status.DECLINED, 0), 0, ClickType.LEFT);
        assertThat(members).containsExactly("Alice");
        var restarted = new WhitelistRequests(directory, new ObjectMapper(), clock);
        assertThat(restarted.list().getFirst().status()).isEqualTo(Status.APPROVED);
    }

    @Test
    void headsHideOnlyProfileTooltipAndUseLocalizedActionColors() throws Exception {
        requests.get("Alice", true, false);
        for (String language : List.of("en", "de")) {
            var localized = new ConfigurateMessageService(directory.resolve(language), getClass().getClassLoader(),
                    language, server);
            // MockBukkit's ItemStack clone drops data components; inspect the stack handed to InvUI.
            try (var wrappers = mockConstruction(ItemWrapper.class, (wrapper, context) -> when(wrapper.get())
                    .thenReturn((org.bukkit.inventory.ItemStack) context.arguments().getFirst()))) {
                var menu = new WhitelistReview(requests, whitelist, facade, localized, new MockScheduler(), server,
                        Logger.getLogger("whitelist-test")).build(admin, requests.list(), Status.PENDING, 0);
                var head = menu.getItem(0).getItemProvider(admin).get();
                var tooltip = head.getData(DataComponentTypes.TOOLTIP_DISPLAY);
                assertThat(tooltip).isNotNull();
                assertThat(tooltip.hideTooltip()).isFalse();
                assertThat(tooltip.hiddenComponents()).containsExactly(DataComponentTypes.PROFILE);
                assertThat(head.getItemMeta().displayName().color()).isEqualTo(NamedTextColor.GOLD);
                String lore = MiniMessage.miniMessage().serialize(head.getItemMeta().lore().getFirst());
                assertThat(lore).contains(language.equals("en") ? "<green>approve" : "<green>annehmen")
                        .contains(language.equals("en") ? "<red>deny" : "<red>ablehnen");
            }
        }
    }

    @Test
    void staleMenusAndChatCannotOverrideDecisionsOrReopenedRequests() throws Exception {
        requests.get("Alice", true, false);
        var original = requests.list().getFirst();
        var stale = review.build(admin, requests.list(), Status.PENDING, 0);
        execute("approve Alice " + original.submissionId());
        execute("deny Alice " + original.submissionId());
        assertThat(members).containsExactly("Alice");
        click(stale, 0, ClickType.RIGHT);
        assertThat(members).containsExactly("Alice");
        click(review.build(admin, requests.list(), Status.APPROVED, 0), 0, ClickType.RIGHT);
        requests.get("Alice", true, false, true);
        execute("approve Alice " + original.submissionId());
        assertThat(members).isEmpty();
        assertThat(requests.list().getFirst().status()).isEqualTo(Status.PENDING);
        assertThat(requests.list().getFirst().submissionId()).isNotEqualTo(original.submissionId());
    }

    @Test
    void permissionRevocationAndUnsupportedClicksCannotMutateWhitelist() throws Exception {
        requests.get("Alice", true, false);
        var menu = review.build(admin, requests.list(), Status.PENDING, 0);
        click(menu, 0, ClickType.SHIFT_LEFT);
        assertThat(members).isEmpty();
        admin.setOp(false);
        click(menu, 0, ClickType.LEFT);
        var source = mock(CommandSourceStack.class);
        when(source.getSender()).thenReturn(admin);
        assertThat(review.command().canUse(source)).isFalse();
        assertThat(members).isEmpty();
        assertThat(requests.list().getFirst().status()).isEqualTo(Status.PENDING);
    }

    @Test
    void repeatedDecisionsAtTheSameTimestampStillInvalidateOldSnapshots() throws Exception {
        requests.get("Alice", true, false);
        requests.resolve(requests.list().getFirst(), Status.APPROVED);
        var oldApproval = requests.list().getFirst();
        requests.resolve(oldApproval, Status.DECLINED);
        requests.resolve(requests.list().getFirst(), Status.APPROVED);
        assertThatThrownBy(() -> requests.resolve(oldApproval, Status.DECLINED)).isInstanceOf(IOException.class);
        assertThat(requests.list().getFirst().status()).isEqualTo(Status.APPROVED);
    }

    @Test
    void allRequestsAreReachableThroughPaginationAndTabs() throws Exception {
        for (int index = 0; index < 50; index++) {
            requests.get("Player" + index, true, false);
        }
        var first = review.build(admin, requests.list(), Status.PENDING, 0);
        assertThat(first.getItem(44)).isNotNull();
        assertThat(first.getItem(45)).isNull();
        assertThat(first.getItem(53)).isNotNull();
        var last = review.build(admin, requests.list(), Status.PENDING, 99);
        assertThat(last.getItem(4)).isNotNull();
        assertThat(last.getItem(5)).isNull();
        assertThat(last.getItem(45)).isNotNull();
        assertThat(last.getItem(53)).isNull();
        click(first, 48, ClickType.LEFT);
        verify(facade).open(org.mockito.ArgumentMatchers.eq(admin), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(Component.class));
    }

    @Test
    void failedSaveRestoresMembershipAndPreservesRequest() throws Exception {
        requests.get("Alice", true, false);
        Path file = directory.resolve("alice.json");
        String original = Files.readString(file);
        doAnswer(invocation -> {
            members.add("Alice");
            Files.writeString(file, "broken");
            return null;
        }).when(whitelist).addJavaPlayer("Alice");
        click(review.build(admin, requests.list(), Status.PENDING, 0), 0, ClickType.LEFT);
        assertThat(members).isEmpty();
        assertThat(Files.readString(file)).isEqualTo("broken");
        verify(messages).send(admin, "api.whitelist-error");
        Files.writeString(file, original);
        assertThat(requests.list().getFirst().status()).isEqualTo(Status.PENDING);
    }

    @Test
    void legacyRequestsMigrateAndDeclinedReopeningIsExplicitAndRateLimited() throws Exception {
        Files.writeString(directory.resolve("alice.json"),
                "{\"playerName\":\"Alice\",\"requestedAt\":\"2026-11-10T12:00:00Z\",\"submissionId\":\"legacy\"}");
        var legacy = requests.list().getFirst();
        assertThat(legacy.status()).isEqualTo(Status.PENDING);
        requests.resolve(legacy, Status.DECLINED);
        assertThat(requests.get("ALICE", true, false).body()).containsEntry("status", "declined");
        assertThatThrownBy(() -> requests.resolve(legacy, Status.APPROVED)).isInstanceOf(IOException.class);
        var reopened = requests.get("alice", true, false, true);
        assertThat(reopened.statusCode()).isEqualTo(202);
        assertThat(reopened.body()).containsEntry("status", "pending").doesNotContainKey("resolvedAt");
        assertThat(reopened.body().get("submissionId")).isNotEqualTo("legacy");
        for (int index = 0; index < 59; index++) {
            requests.get("Player" + index, true, false);
        }
        requests.resolve(requests.list().stream().filter(e -> e.playerName().equals("Alice")).findFirst().orElseThrow(),
                Status.DECLINED);
        assertThat(requests.get("Alice", true, false, true).statusCode()).isEqualTo(429);
    }

    @Test
    void writeFailuresRestoreApprovedMembershipWithoutChangingTheSavedDecision() throws Exception {
        requests.get("Alice", true, false);
        members.add("Alice");
        String original = Files.readString(directory.resolve("alice.json"));
        var failing = spy(requests);
        doThrow(new IOException("disk full")).when(failing).resolve(any(), eq(Status.DECLINED));
        review = new WhitelistReview(failing, whitelist, facade, messages, new MockScheduler(), server,
                Logger.getLogger("whitelist-test"));
        click(review.build(admin, requests.list(), Status.APPROVED, 0), 0, ClickType.RIGHT);
        assertThat(members).containsExactly("Alice");
        assertThat(Files.readString(directory.resolve("alice.json"))).isEqualTo(original);
        verify(messages).send(admin, "api.whitelist-error");
    }

    @Test
    void queuedLoadsRecheckPermissionAndDecisionsStaySerialized() throws Exception {
        requests.get("Alice", true, false);
        var queue = new ArrayDeque<Runnable>();
        var scheduler = mock(Scheduler.class);
        when(scheduler.runAsync(any())).thenAnswer(invocation -> {
            queue.add(invocation.getArgument(0));
            return null;
        });
        when(scheduler.runOnMain(any())).thenAnswer(invocation -> {
            invocation.<Runnable>getArgument(0).run();
            return null;
        });
        review = new WhitelistReview(requests, whitelist, facade, messages, scheduler, server,
                Logger.getLogger("whitelist-test"));
        var menu = review.build(admin, requests.list(), Status.PENDING, 0);
        click(menu, 0, ClickType.LEFT);
        assertThat(members).isEmpty();
        admin.setOp(false);
        queue.remove().run();
        assertThat(members).isEmpty();
        admin.setOp(true);
        click(menu, 0, ClickType.LEFT);
        click(menu, 0, ClickType.RIGHT);
        queue.remove().run(); // approve: starts the asynchronous save
        assertThat(members).containsExactly("Alice");
        queue.remove().run(); // deny: still busy, cannot alter the whitelist
        assertThat(members).containsExactly("Alice");
        verify(messages).send(admin, "api.whitelist-busy");
        queue.remove().run(); // persist approval
        assertThat(requests.list().getFirst().status()).isEqualTo(Status.APPROVED);
        review.shutdown();
        while (!queue.isEmpty()) {
            queue.remove().run();
        }
        click(menu, 0, ClickType.RIGHT);
        assertThat(queue).isEmpty();
        assertThat(members).containsExactly("Alice");
    }

    private void execute(String arguments) throws Exception {
        var dispatcher = new CommandDispatcher<CommandSourceStack>();
        dispatcher.getRoot().addChild(review.command());
        var source = mock(CommandSourceStack.class);
        when(source.getSender()).thenReturn(admin);
        dispatcher.execute("whitelist-requests " + arguments, source);
    }

    private void click(xyz.xenondevs.invui.gui.Gui menu, int slot, ClickType type) {
        menu.getItem(slot).handleClick(type, admin, new Click(admin, type));
    }
}
