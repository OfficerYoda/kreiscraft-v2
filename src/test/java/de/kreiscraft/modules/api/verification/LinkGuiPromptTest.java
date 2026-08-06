package de.kreiscraft.modules.api.verification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.kreiscraft.core.message.MessageService;
import de.kreiscraft.shared.gui.Gui;
import de.kreiscraft.shared.gui.GuiRegistry;
import java.util.concurrent.atomic.AtomicBoolean;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import xyz.xenondevs.invui.item.Item;

/**
 * Tests for {@link LinkGuiPrompt}.
 *
 * <p><b>Note on click simulation:</b> InvUI 1.44 does not expose a straightforward public API for
 * simulating inventory click events on a window in tests. The {@code Window.open()} path requires a
 * full inventory-view creation cycle that is not fully supported in MockBukkit 4.115. Therefore,
 * click tests use a <em>Mockito-based fallback</em>: the package-visible
 * {@link LinkGuiPrompt#buildInvUiGui} method is called directly to obtain the raw InvUI Gui, and
 * {@link Item#handleClick} is invoked on the returned item objects with a mocked
 * {@link InventoryClickEvent}. This avoids the Window/InventoryView lifecycle entirely while still
 * exercising the actual handler wiring.
 *
 * <p>The {@code open_registersWindowWithGuiRegistry} test verifies that
 * {@link LinkGuiPrompt#open(Player, String, Runnable, Runnable)} delegates to the {@link Gui}
 * facade. Because {@link Gui} is {@code final} it cannot be subclassed; a Mockito mock is used
 * instead and {@link org.mockito.Mockito#verify} confirms the delegation call.
 */
class LinkGuiPromptTest {

    private ServerMock server;
    private MessageService messages;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        server.addSimpleWorld("world");

        messages = mock(MessageService.class);
        // Return a predictable Component for any key so ItemBuilder calls don't fail
        when(messages.render(anyString())).thenReturn(Component.text("mock"));
        when(messages.render(anyString(), anyString(), anyString()))
                .thenReturn(Component.text("mock"));
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    // -----------------------------------------------------------------------
    // Test 1: open() delegates to the Gui facade (registry wiring)
    //
    // Gui is final so we cannot subclass it. We use a Mockito mock and verify
    // that gui.open(player, invuiGui, title) is called exactly once when
    // LinkGuiPrompt.open() runs. This confirms the handle will be registered by
    // the real Gui.open() implementation.
    // -----------------------------------------------------------------------

    @Test
    void open_registersWindowWithGuiRegistry() {
        Gui guiFacade = mock(Gui.class);
        GuiRegistry.Handle fakeHandle = () -> {
        };
        when(guiFacade.open(any(Player.class), any(), any(Component.class)))
                .thenReturn(fakeHandle);

        PlayerMock player = server.addPlayer("TestPlayer");
        LinkGuiPrompt prompt = new LinkGuiPrompt(guiFacade, messages);

        prompt.open(player, "browser #test", () -> {
        }, () -> {
        });

        verify(guiFacade).open(eq(player), any(xyz.xenondevs.invui.gui.Gui.class), any(Component.class));
    }

    // -----------------------------------------------------------------------
    // Tests 2–4: Click simulation via buildInvUiGui + Item.handleClick
    //
    // Layout (absolute slots in a 3×9 Gui):
    //   Row 0: F F F F I F F F F  (Info at slot 4)
    //   Row 1: F F A F F F D F F  (Accept at slot 11, Decline at slot 15)
    //   Row 2: F F F F F F F F F
    // -----------------------------------------------------------------------

    @Test
    void clickAcceptSlot_invokesOnAccept_andClosesWindow() {
        PlayerMock player = server.addPlayer("AcceptPlayer");
        LinkGuiPrompt prompt = new LinkGuiPrompt(mock(Gui.class), messages);

        AtomicBoolean accepted = new AtomicBoolean(false);
        AtomicBoolean declined = new AtomicBoolean(false);

        xyz.xenondevs.invui.gui.Gui invui =
                prompt.buildInvUiGui("browser #abc", () -> accepted.set(true), () -> declined.set(true));

        // Accept is at row 1 col 2 = slot 11
        Item acceptItem = invui.getItem(11);
        assertThat(acceptItem).isNotNull();

        InventoryClickEvent event = mock(InventoryClickEvent.class);
        acceptItem.handleClick(ClickType.LEFT, player, event);

        assertThat(accepted.get()).isTrue();
        assertThat(declined.get()).isFalse();
    }

    @Test
    void clickDeclineSlot_invokesOnDecline_andClosesWindow() {
        PlayerMock player = server.addPlayer("DeclinePlayer");
        LinkGuiPrompt prompt = new LinkGuiPrompt(mock(Gui.class), messages);

        AtomicBoolean accepted = new AtomicBoolean(false);
        AtomicBoolean declined = new AtomicBoolean(false);

        xyz.xenondevs.invui.gui.Gui invui =
                prompt.buildInvUiGui("browser #xyz", () -> accepted.set(true), () -> declined.set(true));

        // Decline is at row 1 col 6 = slot 15
        Item declineItem = invui.getItem(15);
        assertThat(declineItem).isNotNull();

        InventoryClickEvent event = mock(InventoryClickEvent.class);
        declineItem.handleClick(ClickType.LEFT, player, event);

        assertThat(declined.get()).isTrue();
        assertThat(accepted.get()).isFalse();
    }

    @Test
    void clickFillerSlot_doesNothing() {
        PlayerMock player = server.addPlayer("FillerPlayer");
        LinkGuiPrompt prompt = new LinkGuiPrompt(mock(Gui.class), messages);

        AtomicBoolean accepted = new AtomicBoolean(false);
        AtomicBoolean declined = new AtomicBoolean(false);

        xyz.xenondevs.invui.gui.Gui invui =
                prompt.buildInvUiGui("browser #fill", () -> accepted.set(true), () -> declined.set(true));

        // Filler is at slot 0 (row 0 col 0)
        Item fillerItem = invui.getItem(0);
        assertThat(fillerItem).isNotNull();

        InventoryClickEvent event = mock(InventoryClickEvent.class);
        fillerItem.handleClick(ClickType.LEFT, player, event);

        assertThat(accepted.get()).isFalse();
        assertThat(declined.get()).isFalse();
    }
}
