package de.kreiscraft.modules.admin.commands;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import de.kreiscraft.modules.admin.AdminConfig;
import de.kreiscraft.modules.admin.AdminConfig.Links;
import de.kreiscraft.modules.admin.CapturingMessageService;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.net.URI;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.plugin.PluginMock;

class KcHelpCommandTest {

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
    void build_requiresPermission_whenMissing_returnsFalse() {
        LiteralCommandNode<CommandSourceStack> node =
                KcHelpCommand.build(messages, () -> new AdminConfig(null));
        PlayerMock player = server.addPlayer("Alice");
        CommandSourceStack src = mock(CommandSourceStack.class);
        when(src.getSender()).thenReturn(player);
        assertThat(node.getRequirement().test(src)).isFalse();
    }

    @Test
    void build_requiresPermission_whenGranted_returnsTrue() {
        LiteralCommandNode<CommandSourceStack> node =
                KcHelpCommand.build(messages, () -> new AdminConfig(null));
        PlayerMock player = server.addPlayer("Alice");
        player.addAttachment(plugin, "kreiscraft.admin.help", true);
        CommandSourceStack src = mock(CommandSourceStack.class);
        when(src.getSender()).thenReturn(player);
        assertThat(node.getRequirement().test(src)).isTrue();
    }

    @SuppressWarnings("unchecked")
    @Test
    void execute_sendsHelpTextKey() throws Exception {
        LiteralCommandNode<CommandSourceStack> node =
                KcHelpCommand.build(messages, () -> new AdminConfig(null));
        PlayerMock player = server.addPlayer("Alice");
        CommandSourceStack src = mock(CommandSourceStack.class);
        when(src.getSender()).thenReturn(player);
        CommandContext<CommandSourceStack> ctx = mock(CommandContext.class);
        when(ctx.getSource()).thenReturn(src);

        node.getCommand().run(ctx);

        assertThat(messages.sentKeys).containsExactly("admin.help.text");
    }

    @SuppressWarnings("unchecked")
    @Test
    void execute_afterConfigChange_usesNewWebsiteUrl() throws Exception {
        AdminConfig[] configHolder = {new AdminConfig(null)};
        LiteralCommandNode<CommandSourceStack> node =
                KcHelpCommand.build(messages, () -> configHolder[0]);
        PlayerMock player = server.addPlayer("Alice");
        CommandSourceStack src = mock(CommandSourceStack.class);
        when(src.getSender()).thenReturn(player);
        CommandContext<CommandSourceStack> ctx = mock(CommandContext.class);
        when(ctx.getSource()).thenReturn(src);

        // First invocation — default website
        node.getCommand().run(ctx);
        assertThat((String) messages.sentPlaceholders.get(0)[1])
                .isEqualTo("https://kreiscraft.de");

        // Simulate config reload with new URL
        configHolder[0] =
                new AdminConfig(
                        new Links(
                                URI.create("https://new-website.example"),
                                URI.create("https://discord.gg/new"),
                                URI.create("https://youtube.com/@new")));
        messages.sentKeys.clear();
        messages.sentPlaceholders.clear();

        // Second invocation — must use the updated URL
        node.getCommand().run(ctx);
        assertThat((String) messages.sentPlaceholders.get(0)[1])
                .isEqualTo("https://new-website.example");
    }
}
