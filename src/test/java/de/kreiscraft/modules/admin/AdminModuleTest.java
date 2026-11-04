package de.kreiscraft.modules.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.kreiscraft.core.event.EventBus;
import de.kreiscraft.core.message.MessageService;
import de.kreiscraft.core.message.PlaceholderResolver;
import de.kreiscraft.core.module.ModuleContext;
import de.kreiscraft.core.scheduler.Scheduler;
import de.kreiscraft.core.service.ServiceRegistry;
import de.kreiscraft.testsupport.CapturingCommandRegistrar;
import de.kreiscraft.testsupport.InMemoryPlayerPreferencesService;
import de.kreiscraft.shared.preferences.PlayerPreferencesService;
import java.time.Clock;
import java.time.Duration;
import java.time.ZoneId;
import java.util.UUID;
import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.Server;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.plugin.PluginMock;
import org.mockito.ArgumentCaptor;
import org.spongepowered.configurate.CommentedConfigurationNode;

class AdminModuleTest {

    private ServerMock server;
    private PluginMock plugin;

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
    void enable_doesNotRegisterStylePreviewAfterPaletteApproval() {
        CapturingCommandRegistrar registrar = enableAdminModule();
        assertThat(registrar.names()).doesNotContain("commandstylepreview");
    }

    @Test
    void enable_registersExpectedProductionCommands() {
        CapturingCommandRegistrar registrar = enableAdminModule();
        assertThat(registrar.names()).contains("item", "skull", "ip", "uuid", "ping", "tps");
    }

    @Test
    void performanceBar_schedulesUpdatesAndCleansUpOnDisable() throws Exception {
        Scheduler scheduler = mock(Scheduler.class);
        Scheduler.Task task = mock(Scheduler.Task.class);
        when(scheduler.runRepeating(any(), any(), any())).thenReturn(task);
        EventBus events = mock(EventBus.class);
        JavaPlugin modulePlugin = mock(JavaPlugin.class);
        Server metricServer = mock(Server.class);
        when(modulePlugin.getServer()).thenReturn(metricServer);
        when(metricServer.getTPS()).thenReturn(new double[] {18.5});
        when(metricServer.getAverageTickTime()).thenReturn(42.0);
        ServiceRegistry services = new ServiceRegistry();
        PlaceholderResolver resolver = mock(PlaceholderResolver.class);
        services.publish(PlaceholderResolver.class, resolver);
        services.publish(PlayerPreferencesService.class, new InMemoryPlayerPreferencesService());
        CapturingMessageService messages = new CapturingMessageService();
        AdminModule module = new AdminModule();
        module.enable(new ModuleContext(modulePlugin, CommentedConfigurationNode.root(), messages, services,
                scheduler, new CapturingCommandRegistrar(), events));
        ArgumentCaptor<Runnable> tick = ArgumentCaptor.forClass(Runnable.class);
        verify(scheduler).runRepeating(eq(Duration.ofSeconds(1)), eq(Duration.ofSeconds(1)), tick.capture());
        ArgumentCaptor<Listener> listeners = ArgumentCaptor.forClass(Listener.class);
        verify(events, times(2)).register(listeners.capture());
        TpsBar bar = listeners.getAllValues().stream().filter(TpsBar.class::isInstance)
                .map(TpsBar.class::cast).findFirst().orElseThrow();
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.isOnline()).thenReturn(true);
        when(player.hasPermission("kreiscraft.admin.tps")).thenReturn(true);
        when(player.getPing()).thenReturn(100);
        bar.toggle(player);
        ArgumentCaptor<BossBar> shown = ArgumentCaptor.forClass(BossBar.class);
        verify(player).showBossBar(shown.capture());

        assertThat(shown.getValue().color()).isEqualTo(BossBar.Color.YELLOW);
        assertThat(shown.getValue().progress()).isEqualTo(0.84f);
        var reloaded = CommentedConfigurationNode.root();
        reloaded.node("tps-bar", "mspt-green-max").set(43.0);
        reloaded.node("tps-bar", "mspt-full").set(100.0);
        module.onConfigReload(reloaded);
        tick.getValue().run();
        assertThat(shown.getValue().color()).isEqualTo(BossBar.Color.GREEN);
        assertThat(shown.getValue().progress()).isEqualTo(0.42f);
        assertThat(messages.renderedKeys).containsExactly("admin.tps-bar.title", "admin.tps-bar.title");
        module.disable();
        module.disable();
        tick.getValue().run();

        verify(task).cancel();
        verify(player).hideBossBar(shown.getValue());
        assertThat(messages.renderedKeys).hasSize(2);
    }

    @Test
    void enable_restoresSavedBarsForAlreadyOnlinePlayersAndDisablePreservesChoice() {
        Server metricServer = mock(Server.class);
        JavaPlugin modulePlugin = mock(JavaPlugin.class);
        when(modulePlugin.getServer()).thenReturn(metricServer);
        when(metricServer.getTPS()).thenReturn(new double[] {20.0});
        when(metricServer.getAverageTickTime()).thenReturn(25.0);
        Player enabled = mock(Player.class);
        Player disabled = mock(Player.class);
        Player denied = mock(Player.class);
        for (Player player : java.util.List.of(enabled, disabled, denied)) {
            when(player.getUniqueId()).thenReturn(UUID.randomUUID());
            when(player.isOnline()).thenReturn(true);
        }
        when(enabled.hasPermission("kreiscraft.admin.tps")).thenReturn(true);
        when(disabled.hasPermission("kreiscraft.admin.tps")).thenReturn(true);
        when(metricServer.getOnlinePlayers()).thenAnswer(invocation -> java.util.List.of(enabled, disabled, denied));
        var preferences = new InMemoryPlayerPreferencesService();
        preferences.setTpsBarEnabled(enabled.getUniqueId(), true);
        preferences.setTpsBarEnabled(disabled.getUniqueId(), false);
        preferences.setTpsBarEnabled(denied.getUniqueId(), true);
        ServiceRegistry services = new ServiceRegistry();
        services.publish(PlayerPreferencesService.class, preferences);
        services.publish(PlaceholderResolver.class, mock(PlaceholderResolver.class));
        var messages = new CapturingMessageService();
        AdminModule module = new AdminModule();

        module.enable(new ModuleContext(modulePlugin, CommentedConfigurationNode.root(), messages, services,
                mock(Scheduler.class), new CapturingCommandRegistrar(), mock(EventBus.class)));

        ArgumentCaptor<BossBar> shown = ArgumentCaptor.forClass(BossBar.class);
        verify(enabled).showBossBar(shown.capture());
        verify(disabled, org.mockito.Mockito.never()).showBossBar(any());
        verify(denied, org.mockito.Mockito.never()).showBossBar(any());
        assertThat(shown.getValue().progress()).isEqualTo(0.5f);
        assertThat(shown.getValue().overlay()).isEqualTo(BossBar.Overlay.NOTCHED_20);
        module.disable();
        verify(enabled).hideBossBar(shown.getValue());
        assertThat(preferences.preferences(enabled.getUniqueId()).tpsBarEnabled()).contains(true);
        assertThat(preferences.preferences(denied.getUniqueId()).tpsBarEnabled()).contains(true);
    }

    private CapturingCommandRegistrar enableAdminModule() {
        CapturingCommandRegistrar registrar = new CapturingCommandRegistrar();
        ServiceRegistry services = new ServiceRegistry();
        services.publish(PlayerPreferencesService.class, new InMemoryPlayerPreferencesService());
        services.publish(
                PlaceholderResolver.class,
                new PlaceholderResolver(server, ZoneId.of("Europe/Berlin"), Clock.systemUTC()));
        new AdminModule()
                .enable(new ModuleContext(
                        plugin,
                        CommentedConfigurationNode.root(),
                        mock(MessageService.class),
                        services,
                        mock(Scheduler.class),
                        registrar,
                        mock(EventBus.class)));
        return registrar;
    }
}
