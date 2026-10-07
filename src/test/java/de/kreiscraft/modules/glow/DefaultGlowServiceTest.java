package de.kreiscraft.modules.glow;

import static org.assertj.core.api.Assertions.assertThat;

import de.kreiscraft.testsupport.InMemoryPlayerPreferencesService;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

class DefaultGlowServiceTest {

    private ServerMock server;
    private InMemoryPlayerPreferencesService preferences;
    private DefaultGlowService service;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        preferences = new InMemoryPlayerPreferencesService();
        service = new DefaultGlowService(server, preferences);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void isGlowing_beforeAnyAction_returnsFalse() {
        PlayerMock alice = server.addPlayer("Alice");

        assertThat(service.isGlowing(alice.getUniqueId())).isFalse();
    }

    @Test
    void isGlowing_whenEntityIsGlowing_returnsTrue() {
        PlayerMock alice = server.addPlayer("Alice");
        alice.setGlowing(true);

        assertThat(service.isGlowing(alice.getUniqueId())).isTrue();
    }

    @Test
    void isGlowing_unknownPlayer_returnsFalse() {
        assertThat(service.isGlowing(UUID.randomUUID())).isFalse();
    }

    @Test
    void toggle_whenEntityNotGlowing_turnsOnAndPersistsDesiredTrue() {
        PlayerMock alice = server.addPlayer("Alice");

        boolean result = service.toggle(alice);

        assertThat(result).isTrue();
        assertThat(alice.isGlowing()).isTrue();
        assertThat(preferences.preferences(alice.getUniqueId()).glowEnabled()).contains(true);
    }

    @Test
    void toggle_whenEntityGlowing_turnsOffAndPersistsDesiredFalse() {
        PlayerMock alice = server.addPlayer("Alice");
        alice.setGlowing(true);

        boolean result = service.toggle(alice);

        assertThat(result).isFalse();
        assertThat(alice.isGlowing()).isFalse();
        assertThat(preferences.preferences(alice.getUniqueId()).glowEnabled()).contains(false);
    }

    @Test
    void toggle_usesEntityStateAsTruth_evenWhenPersistedPreferenceDisagrees() {
        PlayerMock alice = server.addPlayer("Alice");
        preferences.setGlowEnabled(alice.getUniqueId(), true);
        alice.setGlowing(false);

        boolean result = service.toggle(alice);

        assertThat(result).isTrue();
        assertThat(alice.isGlowing()).isTrue();
        assertThat(preferences.preferences(alice.getUniqueId()).glowEnabled()).contains(true);
    }

    @Test
    void reconcile_appliesPersistedTrueOntoEntity() {
        PlayerMock alice = server.addPlayer("Alice");
        preferences.setGlowEnabled(alice.getUniqueId(), true);

        service.reconcile(alice);

        assertThat(alice.isGlowing()).isTrue();
    }

    @Test
    void reconcile_withoutPreference_appliesDefaultFalse() {
        PlayerMock alice = server.addPlayer("Alice");
        alice.setGlowing(true);

        service.reconcile(alice);

        assertThat(alice.isGlowing()).isFalse();
    }

    @Test
    void reconcile_persistedFalse_overridesWrongEntityState() {
        PlayerMock alice = server.addPlayer("Alice");
        preferences.setGlowEnabled(alice.getUniqueId(), false);
        alice.setGlowing(true);

        service.reconcile(alice);

        assertThat(alice.isGlowing()).isFalse();
    }

    @Test
    void disableAll_clearsGlowForPlayerWhoseDesiredStateIsEnabled() {
        PlayerMock alice = server.addPlayer("Alice");
        service.toggle(alice);

        service.disableAll();

        assertThat(alice.isGlowing()).isFalse();
    }

    @Test
    void disableAll_leavesUnmanagedGlowUntouched() {
        PlayerMock alice = server.addPlayer("Alice");
        alice.setGlowing(true);

        service.disableAll();

        assertThat(alice.isGlowing()).isTrue();
    }
}
