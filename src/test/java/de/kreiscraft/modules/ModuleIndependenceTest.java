package de.kreiscraft.modules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import de.kreiscraft.core.command.CommandRegistrar;
import de.kreiscraft.core.config.serializers.KreiscraftSerializers;
import de.kreiscraft.core.event.EventBus;
import de.kreiscraft.core.message.MessageService;
import de.kreiscraft.core.module.ModuleContext;
import de.kreiscraft.core.module.ModuleRegistry;
import de.kreiscraft.core.module.StartupSummary;
import de.kreiscraft.core.service.ServiceRegistry;
import de.kreiscraft.modules.clickthrough.ClickthroughModule;
import de.kreiscraft.modules.combat.CombatModule;
import de.kreiscraft.modules.combat.CombatService;
import de.kreiscraft.platform.PaperPlatform;
import de.kreiscraft.shared.preferences.PlayerPreferencesService;
import de.kreiscraft.testsupport.InMemoryPlayerPreferencesService;
import de.kreiscraft.testsupport.MockScheduler;
import java.io.BufferedReader;
import java.io.StringReader;
import java.util.logging.Logger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.plugin.PluginMock;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

/**
 * Guards the remediation requirement that combat and click-through no longer depend on the Player
 * Settings GUI module: both must enable and publish their behaviour with only the always-available
 * preferences boundary present, with `settings-gui` absent from the registry entirely.
 */
class ModuleIndependenceTest {

    private ServerMock server;
    private PluginMock plugin;
    private ServiceRegistry services;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        services = new ServiceRegistry();
        services.publish(PaperPlatform.class, new PaperPlatform(plugin, server));
        services.publish(PlayerPreferencesService.class, new InMemoryPlayerPreferencesService());
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void combatAndClickthrough_enableWithoutTheSettingsGuiModule() throws Exception {
        ConfigurationNode root = config();
        ModuleRegistry registry = new ModuleRegistry(
                m -> new ModuleContext(
                        plugin,
                        root.node("modules", m.id()),
                        mock(MessageService.class),
                        services,
                        new MockScheduler(),
                        mock(CommandRegistrar.class),
                        mock(EventBus.class)),
                root,
                Logger.getAnonymousLogger());

        registry.register(new CombatModule());
        registry.register(new ClickthroughModule());

        StartupSummary summary = registry.enableAll();

        assertThat(summary.failed()).isZero();
        assertThat(summary.enabled()).isEqualTo(2);
        assertThat(services.get(CombatService.class)).isNotNull();
    }

    @Test
    void neitherModuleDeclaresASettingsGuiDependency() {
        assertThat(new CombatModule().dependsOn()).doesNotContain("settings-gui");
        assertThat(new ClickthroughModule().dependsOn()).doesNotContain("settings-gui");
    }

    private static ConfigurationNode config() throws Exception {
        String yaml = "modules:\n"
                + "  combat:\n"
                + "    enabled: true\n"
                + "  clickthrough:\n"
                + "    enabled: true\n";
        return YamlConfigurationLoader.builder()
                .source(() -> new BufferedReader(new StringReader(yaml)))
                .defaultOptions(o -> o.serializers(KreiscraftSerializers.builder().build()))
                .build()
                .load();
    }
}
