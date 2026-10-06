package de.kreiscraft.modules.signcolor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.kreiscraft.core.message.PlaceholderResolver;
import java.time.Clock;
import java.time.ZoneId;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
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
    private LegacyTextFormatter formatter;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        formatter =
                new LegacyTextFormatter(
                        new PlaceholderResolver(server, ZoneId.of("UTC"), Clock.systemUTC()));
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private AnvilRenameListener newListener() {
        return new AnvilRenameListener(formatter);
    }

    @Test
    void onPrepareAnvil_withPermissionAndText_setsDisplayName() {
        PlayerMock player = server.addPlayer("alice");
        player.addAttachment(plugin, "kreiscraft.signcolor.anvil", true);

        AnvilView view = mock(AnvilView.class);
        when(view.getRenameText()).thenReturn("<red>Sword");

        ItemStack result = mock(ItemStack.class);
        ItemMeta meta = mock(ItemMeta.class);
        stubEditMeta(result, meta);

        PrepareAnvilEvent event = mock(PrepareAnvilEvent.class);
        when(event.getViewers()).thenReturn(List.of(player));
        when(event.getView()).thenReturn(view);
        when(event.getResult()).thenReturn(result);

        newListener().onPrepareAnvil(event);

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
    void onPrepareAnvil_translatesLegacyAmpersandColours() {
        PlayerMock player = server.addPlayer("alice");
        player.addAttachment(plugin, "kreiscraft.signcolor.anvil", true);

        AnvilView view = mock(AnvilView.class);
        when(view.getRenameText()).thenReturn("&cRed &lBold");

        ItemStack result = mock(ItemStack.class);
        ItemMeta meta = mock(ItemMeta.class);
        stubEditMeta(result, meta);

        PrepareAnvilEvent event = mock(PrepareAnvilEvent.class);
        when(event.getViewers()).thenReturn(List.of(player));
        when(event.getView()).thenReturn(view);
        when(event.getResult()).thenReturn(result);

        newListener().onPrepareAnvil(event);

        ArgumentCaptor<Component> captor = ArgumentCaptor.forClass(Component.class);
        verify(meta).displayName(captor.capture());
        assertThat(captor.getValue())
                .isEqualTo(LegacyComponentSerializer.legacyAmpersand().deserialize("&cRed &lBold"));
    }

    @Test
    void onPrepareAnvil_resolvesEstablishedKreiscraftPlaceholders() {
        PlayerMock player = server.addPlayer("alice");
        player.addAttachment(plugin, "kreiscraft.signcolor.anvil", true);

        AnvilView view = mock(AnvilView.class);
        when(view.getRenameText()).thenReturn("%player_name%'s Sword");

        ItemStack result = mock(ItemStack.class);
        ItemMeta meta = mock(ItemMeta.class);
        stubEditMeta(result, meta);

        PrepareAnvilEvent event = mock(PrepareAnvilEvent.class);
        when(event.getViewers()).thenReturn(List.of(player));
        when(event.getView()).thenReturn(view);
        when(event.getResult()).thenReturn(result);

        newListener().onPrepareAnvil(event);

        ArgumentCaptor<Component> captor = ArgumentCaptor.forClass(Component.class);
        verify(meta).displayName(captor.capture());
        assertThat(PlainTextComponentSerializer.plainText().serialize(captor.getValue()))
                .isEqualTo("alice's Sword");
    }

    @Test
    void onPrepareAnvil_disallowedMiniMessageTag_isRenderedLiterally() {
        PlayerMock player = server.addPlayer("alice");
        player.addAttachment(plugin, "kreiscraft.signcolor.anvil", true);

        String raw = "<hover:show_text:'boo'>Sword";
        AnvilView view = mock(AnvilView.class);
        when(view.getRenameText()).thenReturn(raw);

        ItemStack result = mock(ItemStack.class);
        ItemMeta meta = mock(ItemMeta.class);
        stubEditMeta(result, meta);

        PrepareAnvilEvent event = mock(PrepareAnvilEvent.class);
        when(event.getViewers()).thenReturn(List.of(player));
        when(event.getView()).thenReturn(view);
        when(event.getResult()).thenReturn(result);

        newListener().onPrepareAnvil(event);

        ArgumentCaptor<Component> captor = ArgumentCaptor.forClass(Component.class);
        verify(meta).displayName(captor.capture());
        assertThat(captor.getValue().hoverEvent()).isNull();
        assertThat(PlainTextComponentSerializer.plainText().serialize(captor.getValue()))
                .isEqualTo(raw);
    }

    @Test
    void onPrepareAnvil_withoutPermission_doesNotSetDisplayName() {
        PlayerMock player = server.addPlayer("bob");
        // bob has no kreiscraft.signcolor.anvil permission

        ItemStack result = mock(ItemStack.class);
        PrepareAnvilEvent event = mock(PrepareAnvilEvent.class);
        when(event.getViewers()).thenReturn(List.of(player));
        when(event.getResult()).thenReturn(result);

        newListener().onPrepareAnvil(event);

        verify(result, never()).editMeta(any());
    }

    @Test
    void onPrepareAnvil_nullResult_doesNotThrow() {
        PlayerMock player = server.addPlayer("carol");
        player.addAttachment(plugin, "kreiscraft.signcolor.anvil", true);

        PrepareAnvilEvent event = mock(PrepareAnvilEvent.class);
        when(event.getViewers()).thenReturn(List.of(player));
        when(event.getResult()).thenReturn(null);

        // Should not throw
        newListener().onPrepareAnvil(event);
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

        newListener().onPrepareAnvil(event);

        verify(result, never()).editMeta(any());
    }

    @Test
    void onPrepareAnvil_nullView_doesNotSetDisplayName() {
        PlayerMock player = server.addPlayer("erin");
        player.addAttachment(plugin, "kreiscraft.signcolor.anvil", true);

        ItemStack result = mock(ItemStack.class);
        PrepareAnvilEvent event = mock(PrepareAnvilEvent.class);
        when(event.getViewers()).thenReturn(List.of(player));
        when(event.getView()).thenReturn(null);
        when(event.getResult()).thenReturn(result);

        newListener().onPrepareAnvil(event);

        verify(result, never()).editMeta(any());
    }

    @Test
    void onPrepareAnvil_noViewers_doesNotSetDisplayName() {
        ItemStack result = mock(ItemStack.class);
        PrepareAnvilEvent event = mock(PrepareAnvilEvent.class);
        when(event.getViewers()).thenReturn(List.of());
        when(event.getResult()).thenReturn(result);

        newListener().onPrepareAnvil(event);

        verify(result, never()).editMeta(any());
    }

    private static void stubEditMeta(ItemStack result, ItemMeta meta) {
        doAnswer(
                inv -> {
                    java.util.function.Consumer<ItemMeta> consumer = inv.getArgument(0);
                    consumer.accept(meta);
                    return null;
                })
                .when(result)
                .editMeta(any());
    }
}
