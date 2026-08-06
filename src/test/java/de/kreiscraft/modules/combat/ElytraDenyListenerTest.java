package de.kreiscraft.modules.combat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.kreiscraft.core.message.MessageService;
import de.kreiscraft.modules.settingsgui.CombatDisplay;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.bukkit.Material;
import org.bukkit.event.entity.EntityToggleGlideEvent;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ElytraDenyListenerTest {

    @Mock
    private CombatService service;
    @Mock
    private MessageService messages;

    private ServerMock server;
    private CombatConfig config;
    private ElytraDenyListener listener;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        config = new CombatConfig(Duration.ofSeconds(10), Boolean.TRUE, List.of("Lobby"), CombatDisplay.ACTIONBAR);
        listener = new ElytraDenyListener(service, messages, () -> config);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void glideStart_inCombat_disableElytraOn_eventCancelled() {
        PlayerMock player = server.addPlayer("alice");
        UUID uuid = player.getUniqueId();
        when(service.isInCombat(uuid)).thenReturn(true);

        EntityToggleGlideEvent event = new EntityToggleGlideEvent(player, true);
        listener.onToggleGlide(event);

        assertThat(event.isCancelled()).isTrue();
    }

    @Test
    void glideStart_inCombat_disableElytraOn_messagesSent() {
        PlayerMock player = server.addPlayer("alice");
        when(service.isInCombat(player.getUniqueId())).thenReturn(true);

        EntityToggleGlideEvent event = new EntityToggleGlideEvent(player, true);
        listener.onToggleGlide(event);

        verify(messages).send(player, "combat.elytra-denied");
    }

    @Test
    void glideStart_inCombat_withElytra_stripFromInventory() {
        PlayerMock player = server.addPlayer("alice");
        when(service.isInCombat(player.getUniqueId())).thenReturn(true);
        player.getInventory().setChestplate(new ItemStack(Material.ELYTRA));

        EntityToggleGlideEvent event = new EntityToggleGlideEvent(player, true);
        listener.onToggleGlide(event);

        assertThat(player.getInventory().getChestplate()).isNull();
    }

    @Test
    void glideStart_notInCombat_eventNotCancelled() {
        PlayerMock player = server.addPlayer("alice");
        when(service.isInCombat(player.getUniqueId())).thenReturn(false);

        EntityToggleGlideEvent event = new EntityToggleGlideEvent(player, true);
        listener.onToggleGlide(event);

        assertThat(event.isCancelled()).isFalse();
    }

    @Test
    void glideStop_inCombat_eventNotCancelled() {
        PlayerMock player = server.addPlayer("alice");
        when(service.isInCombat(player.getUniqueId())).thenReturn(true);

        // isGliding=false means stopping glide — should not be cancelled
        EntityToggleGlideEvent event = new EntityToggleGlideEvent(player, false);
        listener.onToggleGlide(event);

        assertThat(event.isCancelled()).isFalse();
    }

    @Test
    void glideStart_inCombat_disableElytraOff_eventNotCancelled() {
        CombatConfig cfgDisabled = new CombatConfig(
                Duration.ofSeconds(10), Boolean.FALSE, List.of("Lobby"), CombatDisplay.ACTIONBAR);
        listener = new ElytraDenyListener(service, messages, () -> cfgDisabled);

        PlayerMock player = server.addPlayer("alice");
        when(service.isInCombat(player.getUniqueId())).thenReturn(true);

        EntityToggleGlideEvent event = new EntityToggleGlideEvent(player, true);
        listener.onToggleGlide(event);

        assertThat(event.isCancelled()).isFalse();
    }
}
