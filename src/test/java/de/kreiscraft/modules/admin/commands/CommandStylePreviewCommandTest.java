package de.kreiscraft.modules.admin.commands;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import de.kreiscraft.modules.admin.CapturingMessageService;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.plugin.PluginMock;

class CommandStylePreviewCommandTest {

    private ServerMock server;
    private PluginMock plugin;
    private CapturingMessageService messages;
    private xyz.xenondevs.invui.gui.Gui openedBoard;
    private net.kyori.adventure.text.Component openedTitle;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        messages = new CapturingMessageService();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void build_requiresTemporaryAdminPermission() {
        LiteralCommandNode<CommandSourceStack> node = previewCommand();
        PlayerMock player = server.addPlayer("Alice");
        CommandSourceStack source = mock(CommandSourceStack.class);
        when(source.getSender()).thenReturn(player);

        assertThat(node.getRequirement().test(source)).isFalse();

        player.addAttachment(plugin, "kreiscraft.admin.style-preview", true);
        assertThat(node.getRequirement().test(source)).isTrue();
    }

    @Test
    void execute_opensComparisonBoardAndHandsOffToHumanSelection() throws Exception {
        LiteralCommandNode<CommandSourceStack> node = previewCommand();
        PlayerMock player = server.addPlayer("Alice");
        CommandSourceStack source = mock(CommandSourceStack.class);
        when(source.getSender()).thenReturn(player);
        CommandContext<CommandSourceStack> context = mock(CommandContext.class);
        when(context.getSource()).thenReturn(source);

        node.getCommand().run(context);

        assertThat(messages.sentKeys)
                .containsExactly("admin.style-preview.temporary", "admin.style-preview.handoff");
        assertThat(openedBoard).isNotNull();
        assertThat(net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                .serialize(openedTitle))
                .isEqualTo("Classic | Signal | Ember | Requested Hybrid");
    }

    private LiteralCommandNode<CommandSourceStack> previewCommand() {
        return CommandStylePreviewCommand.build(
                messages,
                (player, board, title) -> {
                    openedBoard = board;
                    openedTitle = title;
                });
    }
}
