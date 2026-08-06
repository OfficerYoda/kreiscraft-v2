package de.kreiscraft.modules.combat.commands;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.mojang.brigadier.tree.LiteralCommandNode;
import de.kreiscraft.core.message.MessageService;
import de.kreiscraft.modules.combat.CombatService;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CombatCommandTest {

    @Mock
    private CombatService service;
    @Mock
    private MessageService messages;

    private ServerMock server;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void build_returnsNonNullNode() {
        assertThat(CombatCommand.build(service, messages)).isNotNull();
    }

    @Test
    void build_nodeNameIsCombat() {
        assertThat(CombatCommand.build(service, messages).getName()).isEqualTo("combat");
    }

    @Test
    void permissionGate_withoutPermission_requiresFails() {
        LiteralCommandNode<CommandSourceStack> node = CombatCommand.build(service, messages);

        CommandSender noPerms = mock(CommandSender.class);
        when(noPerms.hasPermission("kreiscraft.combat.admin")).thenReturn(false);

        CommandSourceStack src = mock(CommandSourceStack.class);
        when(src.getSender()).thenReturn(noPerms);

        assertThat(node.getRequirement().test(src)).isFalse();
    }

    @Test
    void permissionGate_withPermission_requiresPasses() {
        LiteralCommandNode<CommandSourceStack> node = CombatCommand.build(service, messages);

        CommandSender withPerms = mock(CommandSender.class);
        when(withPerms.hasPermission("kreiscraft.combat.admin")).thenReturn(true);

        CommandSourceStack src = mock(CommandSourceStack.class);
        when(src.getSender()).thenReturn(withPerms);

        assertThat(node.getRequirement().test(src)).isTrue();
    }
}
