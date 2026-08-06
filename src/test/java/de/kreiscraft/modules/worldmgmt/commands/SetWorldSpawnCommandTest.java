package de.kreiscraft.modules.worldmgmt.commands;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import de.kreiscraft.core.message.MessageService;
import org.bukkit.Location;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

class SetWorldSpawnCommandTest {

    private ServerMock server;
    private MessageService messages;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        messages = mock(MessageService.class);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void setWorldSpawn_updatesWorldSpawnToPlayerLocation() {
        PlayerMock player = server.addPlayer("Alice");
        Location expected = new Location(player.getWorld(), 10, 64, -5);
        player.teleport(expected);

        SetWorldSpawnCommand.setWorldSpawn(player, messages);

        Location spawn = player.getWorld().getSpawnLocation();
        assertThat(spawn.getBlockX()).isEqualTo(10);
        assertThat(spawn.getBlockZ()).isEqualTo(-5);
    }

    @Test
    void setWorldSpawn_sendsConfirmationMessage() {
        PlayerMock player = server.addPlayer("Alice");

        SetWorldSpawnCommand.setWorldSpawn(player, messages);

        verify(messages).send(player, "world-mgmt.spawn-set");
    }

    @Test
    void build_returnsNonNullNode() {
        assertThat(SetWorldSpawnCommand.build(messages)).isNotNull();
    }
}
