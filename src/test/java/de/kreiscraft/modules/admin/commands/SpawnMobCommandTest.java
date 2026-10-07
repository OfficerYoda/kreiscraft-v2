package de.kreiscraft.modules.admin.commands;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import de.kreiscraft.core.command.SuggestionTestSupport;
import de.kreiscraft.core.message.MessageService;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.util.List;
import org.bukkit.entity.EntityType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.plugin.PluginMock;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SpawnMobCommandTest {

    private ServerMock server;
    private PluginMock plugin;

    @Mock
    private MessageService messages;

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
    void build_requiresPermission_whenMissing_returnsFalse() {
        LiteralCommandNode<CommandSourceStack> node = SpawnMobCommand.build(messages);
        PlayerMock player = server.addPlayer("Alice");
        CommandSourceStack src = mock(CommandSourceStack.class);
        when(src.getSender()).thenReturn(player);
        assertThat(node.getRequirement().test(src)).isFalse();
    }

    @Test
    void build_requiresPermission_whenGranted_returnsTrue() {
        LiteralCommandNode<CommandSourceStack> node = SpawnMobCommand.build(messages);
        PlayerMock player = server.addPlayer("Alice");
        player.addAttachment(plugin, "kreiscraft.admin.spawnmob", true);
        CommandSourceStack src = mock(CommandSourceStack.class);
        when(src.getSender()).thenReturn(player);
        assertThat(node.getRequirement().test(src)).isTrue();
    }

    @SuppressWarnings("unchecked")
    @Test
    void execute_spawnsMobAtPlayerLocation() throws Exception {
        LiteralCommandNode<CommandSourceStack> node = SpawnMobCommand.build(messages);
        PlayerMock player = server.addPlayer("Alice");
        int entityCountBefore =
                player.getWorld().getEntitiesByClass(org.bukkit.entity.Zombie.class).size();

        CommandSourceStack src = mock(CommandSourceStack.class);
        when(src.getSender()).thenReturn(player);
        CommandContext<CommandSourceStack> ctx = mock(CommandContext.class);
        when(ctx.getSource()).thenReturn(src);
        when(ctx.getArgument("entityType", String.class)).thenReturn("ZOMBIE");
        when(ctx.getArgument("count", int.class)).thenReturn(2);

        node.getChild("entityType").getChild("count").getCommand().run(ctx);

        int entityCountAfter =
                player.getWorld().getEntitiesByClass(org.bukkit.entity.Zombie.class).size();
        assertThat(entityCountAfter).isEqualTo(entityCountBefore + 2);
    }

    @Test
    void entityTypeArgument_suggestsSpawnableTypes() {
        LiteralCommandNode<CommandSourceStack> node = SpawnMobCommand.build(messages);
        PlayerMock player = server.addPlayer("Alice");
        player.addAttachment(plugin, "kreiscraft.admin.spawnmob", true);
        CommandSourceStack src = mock(CommandSourceStack.class);
        when(src.getSender()).thenReturn(player);

        List<String> suggestions =
                SuggestionTestSupport.suggestions(node.getChild("entityType"), src, "zomb");

        assertThat(suggestions).contains("zombie");
    }
}
