package de.kreiscraft.modules.admin.commands;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import de.kreiscraft.modules.admin.CapturingMessageService;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
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
        LiteralCommandNode<CommandSourceStack> node = CommandStylePreviewCommand.build(messages);
        PlayerMock player = server.addPlayer("Alice");
        CommandSourceStack source = mock(CommandSourceStack.class);
        when(source.getSender()).thenReturn(player);

        assertThat(node.getRequirement().test(source)).isFalse();

        player.addAttachment(plugin, "kreiscraft.admin.style-preview", true);
        assertThat(node.getRequirement().test(source)).isTrue();
    }

    @Test
    void execute_showsFourPalettesWithEverySemanticRoleAndHumanHandoff() throws Exception {
        LiteralCommandNode<CommandSourceStack> node = CommandStylePreviewCommand.build(messages);
        PlayerMock player = server.addPlayer("Alice");
        CommandSourceStack source = mock(CommandSourceStack.class);
        when(source.getSender()).thenReturn(player);
        CommandContext<CommandSourceStack> context = mock(CommandContext.class);
        when(context.getSource()).thenReturn(source);

        node.getCommand().run(context);

        assertThat(messages.sentKeys)
                .startsWith("admin.style-preview.temporary")
                .containsSubsequence(
                        List.of(
                                "admin.style-preview.header",
                                "admin.style-preview.inventory-opening",
                                "admin.style-preview.reason",
                                "admin.style-preview.value",
                                "admin.style-preview.success",
                                "admin.style-preview.warning",
                                "admin.style-preview.error",
                                "admin.style-preview.inventory-title",
                                "admin.style-preview.header"))
                .endsWith("admin.style-preview.handoff");
        assertThat(messages.sentKeys).hasSize(34);
        assertThat(sentPaletteNames()).containsExactly("Classic", "Signal", "Ember", "Requested Hybrid");
    }

    private List<String> sentPaletteNames() {
        return messages.sentPlaceholders.stream()
                .filter(placeholders -> placeholders.length == 2)
                .map(placeholders -> (Component) placeholders[1])
                .map(PlainTextComponentSerializer.plainText()::serialize)
                .filter(name -> List.of("Classic", "Signal", "Ember", "Requested Hybrid").contains(name))
                .toList();
    }
}
