package de.kreiscraft.modules.wither;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.kreiscraft.core.command.CommandRegistrar;
import de.kreiscraft.core.event.EventBus;
import de.kreiscraft.core.message.MessageService;
import de.kreiscraft.core.module.ModuleContext;
import de.kreiscraft.core.service.ServiceRegistry;
import de.kreiscraft.testsupport.MockScheduler;
import java.io.BufferedReader;
import java.io.StringReader;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.damage.DamageSource;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.WitherMock;
import org.mockbukkit.mockbukkit.world.WorldMock;
import org.mockito.ArgumentCaptor;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

class WitherModuleTest {

    private ServerMock server;
    private MessageService messages;
    private MockScheduler scheduler;
    private EventBus events;
    private WitherModule module;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        messages = mock(MessageService.class);
        when(messages.render(anyString())).thenReturn(Component.empty());
        scheduler = new MockScheduler();
        events = mock(EventBus.class);
        module = new WitherModule();
    }

    @AfterEach
    void tearDown() {
        module.disable();
        MockBukkit.unmock();
    }

    @Test
    void enable_emptyConfigSection_usesExplosionDefaults() throws Exception {
        WitherListener listener = enableWith(parseYaml("{}"));

        WorldMock world = spiedWorldWithExplosionSupport("module-defaults");
        listener.onEntityDamage(damageEvent(halfHealthWither(world)));

        verify(world)
                .createExplosion(anyDouble(), anyDouble(), anyDouble(), eq(4.0f), eq(true), eq(true));
    }

    @Test
    void onConfigReload_subsequentExplosion_usesReloadedSettings() throws Exception {
        WitherListener listener = enableWith(parseYaml(
                "half-health-explosion-power: 4.0\n"
                        + "half-health-explosion-fire: true\n"
                        + "half-health-explosion-block-damage: true\n"));

        WorldMock before = spiedWorldWithExplosionSupport("module-reload-before");
        listener.onEntityDamage(damageEvent(halfHealthWither(before)));
        verify(before)
                .createExplosion(anyDouble(), anyDouble(), anyDouble(), eq(4.0f), eq(true), eq(true));

        // Exercise the real reload entry point rather than swapping the supplier directly.
        module.onConfigReload(parseYaml(
                "half-health-explosion-power: 8.0\n"
                        + "half-health-explosion-fire: false\n"
                        + "half-health-explosion-block-damage: false\n"));

        WorldMock after = spiedWorldWithExplosionSupport("module-reload-after");
        listener.onEntityDamage(damageEvent(halfHealthWither(after)));
        verify(after)
                .createExplosion(anyDouble(), anyDouble(), anyDouble(), eq(8.0f), eq(false), eq(false));
    }

    // ---- helpers ----

    private WitherListener enableWith(ConfigurationNode cfg) {
        ModuleContext ctx = new ModuleContext(
                null,
                cfg,
                messages,
                mock(ServiceRegistry.class),
                scheduler,
                mock(CommandRegistrar.class),
                events);
        module.enable(ctx);

        ArgumentCaptor<Listener> captor = ArgumentCaptor.forClass(Listener.class);
        verify(events).register(captor.capture());
        assertThat(captor.getValue()).isInstanceOf(WitherListener.class);
        return (WitherListener) captor.getValue();
    }

    private WitherMock halfHealthWither(WorldMock world) {
        WitherMock wither = new WitherMock(server, UUID.randomUUID(), world);
        wither.setMaxHealth(600.0);
        wither.setHealth(300.0);
        return wither;
    }

    private static EntityDamageEvent damageEvent(WitherMock wither) {
        return new EntityDamageEvent(
                wither,
                EntityDamageEvent.DamageCause.ENTITY_ATTACK,
                mock(DamageSource.class),
                10.0);
    }

    private static ConfigurationNode parseYaml(String yaml) throws Exception {
        return YamlConfigurationLoader.builder()
                .source(() -> new BufferedReader(new StringReader(yaml)))
                .build()
                .load();
    }

    /**
     * Creates a spy WorldMock with createExplosion methods stubbed to return true
     * (MockBukkit's WorldMock throws UnimplementedOperationException for these).
     */
    private WorldMock spiedWorldWithExplosionSupport(String name) {
        WorldMock spy = spy(server.addSimpleWorld(name));
        doReturn(true).when(spy)
                .createExplosion(anyDouble(), anyDouble(), anyDouble(), anyFloat());
        doReturn(true).when(spy)
                .createExplosion(
                        anyDouble(), anyDouble(), anyDouble(), anyFloat(),
                        anyBoolean(), anyBoolean());
        return spy;
    }
}
