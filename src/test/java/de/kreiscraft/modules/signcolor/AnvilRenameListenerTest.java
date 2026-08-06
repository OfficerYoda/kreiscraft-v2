package de.kreiscraft.modules.signcolor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.view.AnvilView;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.plugin.PluginMock;
import org.mockito.ArgumentCaptor;

class AnvilRenameListenerTest {

    private ServerMock server;
    private PluginMock plugin;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void onPrepareAnvil_withPermissionAndText_setsDisplayName() {
        PlayerMock player = server.addPlayer("alice");
        player.addAttachment(plugin, "kreiscraft.signcolor.anvil", true);

        AnvilView view = mock(AnvilView.class);
        when(view.getRenameText()).thenReturn("<red>Sword");

        ItemMeta meta = mock(ItemMeta.class);
        ItemStack result = mock(ItemStack.class);
        doAnswer(inv -> {
            java.util.function.Consumer<ItemMeta> consumer = inv.getArgument(0);
            consumer.accept(meta);
            return null;
        }).when(result).editMeta(any());

        PrepareAnvilEvent event = mock(PrepareAnvilEvent.class);
        when(event.getViewers()).thenReturn(List.of(player));
        when(event.getView()).thenReturn(view);
        when(event.getResult()).thenReturn(result);

        AnvilRenameListener listener = new AnvilRenameListener();
        listener.onPrepareAnvil(event);

        // Capture the component passed to displayName
        ArgumentCaptor<Component> captor = ArgumentCaptor.forClass(Component.class);
        verify(meta).displayName(captor.capture());

        Component expected = MiniMessage.miniMessage().deserialize("<red>Sword");
        assertThat(captor.getValue()).isEqualTo(expected);

        // Plain text of captured component should be "Sword"
        String plain = PlainTextComponentSerializer.plainText().serialize(captor.getValue());
        assertThat(plain).isEqualTo("Sword");
    }

    @Test
    void onPrepareAnvil_withoutPermission_doesNotSetDisplayName() {
        PlayerMock player = server.addPlayer("bob");
        // bob has no kreiscraft.signcolor.anvil permission

        ItemStack result = mock(ItemStack.class);
        PrepareAnvilEvent event = mock(PrepareAnvilEvent.class);
        when(event.getViewers()).thenReturn(List.of(player));
        when(event.getResult()).thenReturn(result);

        AnvilRenameListener listener = new AnvilRenameListener();
        listener.onPrepareAnvil(event);

        verify(result, never()).editMeta(any());
    }

    @Test
    void onPrepareAnvil_nullResult_doesNotThrow() {
        PlayerMock player = server.addPlayer("carol");
        player.addAttachment(plugin, "kreiscraft.signcolor.anvil", true);

        PrepareAnvilEvent event = mock(PrepareAnvilEvent.class);
        when(event.getViewers()).thenReturn(List.of(player));
        when(event.getResult()).thenReturn(null);

        AnvilRenameListener listener = new AnvilRenameListener();
        // Should not throw
        listener.onPrepareAnvil(event);
    }

    @Test
    void onPrepareAnvil_blankRenameText_doesNotSetDisplayName() {
        PlayerMock player = server.addPlayer("dave");
        player.addAttachment(plugin, "kreiscraft.signcolor.anvil", true);

        AnvilView view = mock(AnvilView.class);
        when(view.getRenameText()).thenReturn("   ");

        ItemStack result = mock(ItemStack.class);
        PrepareAnvilEvent event = mock(PrepareAnvilEvent.class);
        when(event.getViewers()).thenReturn(List.of(player));
        when(event.getView()).thenReturn(view);
        when(event.getResult()).thenReturn(result);

        AnvilRenameListener listener = new AnvilRenameListener();
        listener.onPrepareAnvil(event);

        verify(result, never()).editMeta(any());
    }

    @Test
    void onPrepareAnvil_noViewers_doesNotSetDisplayName() {
        ItemStack result = mock(ItemStack.class);
        PrepareAnvilEvent event = mock(PrepareAnvilEvent.class);
        when(event.getViewers()).thenReturn(List.of());
        when(event.getResult()).thenReturn(result);

        AnvilRenameListener listener = new AnvilRenameListener();
        listener.onPrepareAnvil(event);

        verify(result, never()).editMeta(any());
    }
}
