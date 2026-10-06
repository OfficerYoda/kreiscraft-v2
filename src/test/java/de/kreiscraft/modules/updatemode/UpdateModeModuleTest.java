package de.kreiscraft.modules.updatemode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import de.kreiscraft.core.command.CommandRegistrar;
import de.kreiscraft.core.config.ConfigService;
import de.kreiscraft.core.config.serializers.KreiscraftSerializers;
import de.kreiscraft.core.event.EventBus;
import de.kreiscraft.core.message.MessageService;
import de.kreiscraft.core.module.ModuleContext;
import de.kreiscraft.core.service.ServiceRegistry;
import de.kreiscraft.platform.PaperPlatform;
import de.kreiscraft.testsupport.MockScheduler;
import java.io.BufferedReader;
import java.io.StringReader;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.plugin.PluginMock;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

class UpdateModeModuleTest {

    private ServerMock server;
    private PluginMock plugin;
    private ServiceRegistry services;
    private UpdateModeModule module;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        services = new ServiceRegistry();
        services.publish(PaperPlatform.class, new PaperPlatform(plugin, server));
        services.publish(ConfigService.class, mock(ConfigService.class));
        module = new UpdateModeModule();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void enable_publishesUpdateModeService() throws Exception {
        module.enable(context(config("kreiscraft.updatemode.bypass")));

        assertThat(services.get(UpdateModeService.class)).isNotNull();
    }

    @Test
    void onConfigReload_honorsUpdatedBypassPermission() throws Exception {
        module.enable(context(config("kreiscraft.updatemode.bypass")));
        UpdateModeService service = services.get(UpdateModeService.class);

        PlayerMock alice = server.addPlayer("Alice");
        alice.addAttachment(plugin, "kreiscraft.custom.bypass", true);
        assertThat(service.isBypassed(alice)).isFalse();

        // Simulate /kreiscraft reload re-reading the module config.
        module.onConfigReload(config("kreiscraft.custom.bypass"));

        assertThat(service.isBypassed(alice)).isTrue();
    }

    private ModuleContext context(ConfigurationNode cfg) {
        return new ModuleContext(
                plugin,
                cfg,
                mock(MessageService.class),
                services,
                new MockScheduler(),
                mock(CommandRegistrar.class),
                mock(EventBus.class));
    }

    private static ConfigurationNode config(String bypassPermission) throws Exception {
        String yaml = "bypass-permission: \"" + bypassPermission + "\"\nbypass-names: []\n";
        return YamlConfigurationLoader.builder()
                .source(() -> new BufferedReader(new StringReader(yaml)))
                .defaultOptions(o -> o.serializers(KreiscraftSerializers.builder().build()))
                .build()
                .load();
    }
}
