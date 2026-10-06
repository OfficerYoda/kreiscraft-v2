package de.kreiscraft.modules.tablist;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.kreiscraft.core.command.CommandRegistrar;
import de.kreiscraft.core.config.serializers.KreiscraftSerializers;
import de.kreiscraft.core.event.EventBus;
import de.kreiscraft.core.message.MessageService;
import de.kreiscraft.core.message.PlaceholderResolver;
import de.kreiscraft.core.module.ModuleContext;
import de.kreiscraft.core.service.ServiceRegistry;
import de.kreiscraft.platform.PaperPlatform;
import de.kreiscraft.testsupport.MockScheduler;
import java.io.BufferedReader;
import java.io.StringReader;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.logging.Logger;
import net.kyori.adventure.text.Component;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.spongepowered.configurate.BasicConfigurationNode;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.ConfigurationOptions;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

@ExtendWith(MockitoExtension.class)
class TablistModuleTest {

    private static final ConfigurationOptions OPTS =
            ConfigurationOptions.defaults().serializers(KreiscraftSerializers.builder().build());

    @Mock
    private PlaceholderResolver resolver;

    @Mock
    private Server server;

    @Mock
    private Player alice;

    @Mock
    private Player bob;

    @Mock
    private JavaPlugin plugin;

    @Mock
    private Logger logger;

    @Mock
    private MessageService messages;

    @Mock
    private CommandRegistrar commands;

    private ServiceRegistry services;
    private MockScheduler scheduler;
    private EventBus events;
    private TablistModule module;

    @BeforeEach
    void setUp() {
        services = new ServiceRegistry();
        services.publish(PaperPlatform.class, new PaperPlatform(plugin, server));
        services.publish(PlaceholderResolver.class, resolver);
        scheduler = new MockScheduler();
        events = mock(EventBus.class);
        module = new TablistModule();
        lenient().when(plugin.getLogger()).thenReturn(logger);
    }

    private void enable(ConfigurationNode cfg) {
        module.enable(new ModuleContext(
                plugin, cfg, messages, services, scheduler, commands, events));
    }

    @Test
    void enable_registersSingleListener() {
        enable(BasicConfigurationNode.root(OPTS));

        ArgumentCaptor<Listener> captor = ArgumentCaptor.forClass(Listener.class);
        verify(events).register(captor.capture());
        assertThat(captor.getValue()).isInstanceOf(TablistListener.class);
    }

    @Test
    @SuppressWarnings("unchecked")
    void tick_refreshesEveryOnlinePlayerOnTheConfiguredInterval() {
        when(server.getOnlinePlayers()).thenReturn((Collection) List.of(alice, bob));
        when(resolver.resolveString(any(), any())).thenReturn("x");

        enable(BasicConfigurationNode.root(OPTS));
        scheduler.advance(Duration.ofMillis(1)); // the task's immediate first run

        verify(alice, times(1)).sendPlayerListHeaderAndFooter(any(), any());
        verify(bob, times(1)).sendPlayerListHeaderAndFooter(any(), any());

        scheduler.advance(Duration.ofMillis(20 * 50L));
        verify(alice, times(2)).sendPlayerListHeaderAndFooter(any(), any());
        verify(bob, times(2)).sendPlayerListHeaderAndFooter(any(), any());
    }

    @Test
    void onJoin_sendsHeaderAndFooterToJoiningPlayerImmediately() {
        when(resolver.resolveString(any(), eq(alice))).thenReturn("x");

        enable(BasicConfigurationNode.root(OPTS));
        capturedListener().onJoin(new PlayerJoinEvent(alice, Component.empty()));

        verify(alice).sendPlayerListHeaderAndFooter(any(), any());
        verify(resolver, times(2)).resolveString(any(), eq(alice));
    }

    @Test
    @SuppressWarnings("unchecked")
    void onConfigReload_swapsTemplatesButKeepsTheOriginalInterval() {
        when(server.getOnlinePlayers()).thenReturn((Collection) List.of(alice));
        when(resolver.resolveString(any(), eq(alice))).thenReturn("x");

        enable(yaml("update-interval: 20t\nheader: \"old header\"\nfooter: \"old footer\"\n"));
        module.onConfigReload(
                yaml("update-interval: 40t\nheader: \"new header\"\nfooter: \"new footer\"\n"));

        // Advancing one original interval (20t) still fires the task: the interval change needs a
        // restart and does not reschedule the shared task.
        scheduler.advance(Duration.ofMillis(1));
        scheduler.advance(Duration.ofMillis(20 * 50L));

        verify(resolver, atLeastOnce()).resolveString(eq("new header"), eq(alice));
        verify(resolver, atLeastOnce()).resolveString(eq("new footer"), eq(alice));
        verify(resolver, never()).resolveString(eq("old header"), eq(alice));
        verify(resolver, never()).resolveString(eq("old footer"), eq(alice));
        verify(logger)
                .warning(eq(
                        "[tablist] update-interval changed; restart required for new interval to take effect."));
    }

    @Test
    void onConfigReload_sameInterval_doesNotWarnAboutRestart() {
        enable(yaml("update-interval: 20t\nheader: \"header\"\nfooter: \"footer\"\n"));
        module.onConfigReload(
                yaml("update-interval: 20t\nheader: \"new header\"\nfooter: \"new footer\"\n"));

        verify(logger, never()).warning(anyString());
    }

    @Test
    @SuppressWarnings("unchecked")
    void disable_cancelsTheGlobalTask() {
        when(server.getOnlinePlayers()).thenReturn((Collection) List.of(alice));
        when(resolver.resolveString(any(), eq(alice))).thenReturn("x");

        enable(BasicConfigurationNode.root(OPTS));
        scheduler.advance(Duration.ofMillis(1));
        verify(alice, times(1)).sendPlayerListHeaderAndFooter(any(), any());

        module.disable();
        scheduler.advance(Duration.ofSeconds(10));

        verify(alice, times(1)).sendPlayerListHeaderAndFooter(any(), any());
    }

    private TablistListener capturedListener() {
        ArgumentCaptor<Listener> captor = ArgumentCaptor.forClass(Listener.class);
        verify(events).register(captor.capture());
        return (TablistListener) captor.getValue();
    }

    private static ConfigurationNode yaml(String yaml) {
        try {
            return YamlConfigurationLoader.builder()
                    .source(() -> new BufferedReader(new StringReader(yaml)))
                    .defaultOptions(OPTS)
                    .build()
                    .load();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
