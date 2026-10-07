package de.kreiscraft.modules.glow;

import static org.assertj.core.api.Assertions.assertThat;

import de.kreiscraft.testsupport.InMemoryPlayerPreferencesService;
import net.kyori.adventure.text.Component;
import org.bukkit.event.player.PlayerJoinEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.plugin.PluginMock;

class GlowListenerTest {

    private ServerMock server;
    private PluginMock plugin;
    private InMemoryPlayerPreferencesService preferences;
    private DefaultGlowService service;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        preferences = new InMemoryPlayerPreferencesService();
        service = new DefaultGlowService(server, preferences);
        server.getPluginManager().registerEvents(new GlowListener(service), plugin);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void join_appliesPersistedGlowOntoEntity() {
        PlayerMock alice = server.addPlayer("Alice");
        preferences.setGlowEnabled(alice.getUniqueId(), true);

        server.getPluginManager().callEvent(new PlayerJoinEvent(alice, Component.empty()));

        assertThat(alice.isGlowing()).isTrue();
    }

    @Test
    void join_withoutPreference_leavesEntityUnglowing() {
        PlayerMock alice = server.addPlayer("Alice");

        server.getPluginManager().callEvent(new PlayerJoinEvent(alice, Component.empty()));

        assertThat(alice.isGlowing()).isFalse();
    }

    @Test
    void join_persistedFalse_overridesEntityGlowing() {
        PlayerMock alice = server.addPlayer("Alice");
        preferences.setGlowEnabled(alice.getUniqueId(), false);
        alice.setGlowing(true);

        server.getPluginManager().callEvent(new PlayerJoinEvent(alice, Component.empty()));

        assertThat(alice.isGlowing()).isFalse();
    }
}
