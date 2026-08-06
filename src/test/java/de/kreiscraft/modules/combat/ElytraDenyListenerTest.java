package de.kreiscraft.modules.combat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.kreiscraft.core.message.MessageService;
import de.kreiscraft.modules.settingsgui.CombatDisplay;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.bukkit.Material;
import org.bukkit.entity.Item;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityToggleGlideEvent;
import org.bukkit.event.player.PlayerAttemptPickupItemEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
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

    // -------------------------------------------------------------------------
    // Existing glide-cancellation tests
    // -------------------------------------------------------------------------

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

    // -------------------------------------------------------------------------
    // Drop-protection: pickup
    // -------------------------------------------------------------------------

    /** Helper: returns a Mockito-mocked Item with PDC set to protect for the given owner. */
    private Item protectedItem(UUID ownerUuid, long expiryMillis) {
        Item item = mock(Item.class);
        PersistentDataContainer pdc = mock(PersistentDataContainer.class);
        when(item.getPersistentDataContainer()).thenReturn(pdc);
        when(pdc.has(ElytraDenyListener.ELYTRA_DROP_UNTIL_KEY, PersistentDataType.LONG)).thenReturn(true);
        when(pdc.get(ElytraDenyListener.ELYTRA_DROP_UNTIL_KEY, PersistentDataType.LONG)).thenReturn(expiryMillis);
        when(pdc.has(ElytraDenyListener.ELYTRA_OWNER_KEY, PersistentDataType.STRING)).thenReturn(true);
        when(pdc.get(ElytraDenyListener.ELYTRA_OWNER_KEY, PersistentDataType.STRING))
                .thenReturn(ownerUuid.toString());
        return item;
    }

    @Test
    void pickup_byOwner_beforeExpiry_isAllowed() {
        PlayerMock owner = server.addPlayer("alice");
        long expiry = System.currentTimeMillis() + 25_000L;
        Item item = protectedItem(owner.getUniqueId(), expiry);

        PlayerAttemptPickupItemEvent event = new PlayerAttemptPickupItemEvent(owner, item);
        listener.onAttemptPickup(event);

        assertThat(event.isCancelled()).isFalse();
    }

    @Test
    void pickup_byNonOwner_beforeExpiry_isCancelled() {
        PlayerMock owner = server.addPlayer("alice");
        PlayerMock thief = server.addPlayer("bob");
        long expiry = System.currentTimeMillis() + 25_000L;
        Item item = protectedItem(owner.getUniqueId(), expiry);

        PlayerAttemptPickupItemEvent event = new PlayerAttemptPickupItemEvent(thief, item);
        listener.onAttemptPickup(event);

        assertThat(event.isCancelled()).isTrue();
    }

    @Test
    void pickup_byNonOwner_afterExpiry_isAllowed() {
        PlayerMock owner = server.addPlayer("alice");
        PlayerMock thief = server.addPlayer("bob");
        long expiry = System.currentTimeMillis() - 1L; // already expired
        Item item = protectedItem(owner.getUniqueId(), expiry);

        PlayerAttemptPickupItemEvent event = new PlayerAttemptPickupItemEvent(thief, item);
        listener.onAttemptPickup(event);

        assertThat(event.isCancelled()).isFalse();
    }

    // -------------------------------------------------------------------------
    // Drop-protection: damage
    // -------------------------------------------------------------------------

    /** Helper: returns a Mockito-mocked Item with only the UNTIL key (damage tests don't need owner). */
    private Item protectedItemForDamage(long expiryMillis) {
        Item item = mock(Item.class);
        PersistentDataContainer pdc = mock(PersistentDataContainer.class);
        when(item.getPersistentDataContainer()).thenReturn(pdc);
        when(pdc.has(ElytraDenyListener.ELYTRA_DROP_UNTIL_KEY, PersistentDataType.LONG)).thenReturn(true);
        when(pdc.get(ElytraDenyListener.ELYTRA_DROP_UNTIL_KEY, PersistentDataType.LONG)).thenReturn(expiryMillis);
        return item;
    }

    @Test
    @SuppressWarnings("deprecation")
    void damage_beforeExpiry_isCancelled() {
        long expiry = System.currentTimeMillis() + 25_000L;
        Item item = protectedItemForDamage(expiry);

        EntityDamageEvent event = new EntityDamageEvent(item, EntityDamageEvent.DamageCause.FIRE, 1.0);
        listener.onEntityDamage(event);

        assertThat(event.isCancelled()).isTrue();
    }

    @Test
    @SuppressWarnings("deprecation")
    void damage_afterExpiry_isAllowed() {
        long expiry = System.currentTimeMillis() - 1L; // expired
        Item item = protectedItemForDamage(expiry);

        EntityDamageEvent event = new EntityDamageEvent(item, EntityDamageEvent.DamageCause.FIRE, 1.0);
        listener.onEntityDamage(event);

        assertThat(event.isCancelled()).isFalse();
    }
}
