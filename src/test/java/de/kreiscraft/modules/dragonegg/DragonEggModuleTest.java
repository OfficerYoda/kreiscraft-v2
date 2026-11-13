package de.kreiscraft.modules.dragonegg;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import de.kreiscraft.core.command.CommandRegistrar;
import de.kreiscraft.core.config.ConfigService;
import de.kreiscraft.core.event.EventBus;
import de.kreiscraft.core.message.MessageService;
import de.kreiscraft.core.module.ModuleContext;
import de.kreiscraft.core.service.ServiceRegistry;
import de.kreiscraft.testsupport.MockScheduler;
import org.bukkit.Material;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.spongepowered.configurate.BasicConfigurationNode;

class DragonEggModuleTest {
    @Test
    void independentlyEnabledModulePublishesTrackerRegistersRecipeAndStopsCleanly() {
        var server = MockBukkit.mock();
        var module = new DragonEggModule();
        var events = new EventBus(MockBukkit.createMockPlugin());
        try {
            var plugin = MockBukkit.createMockPlugin();
            var services = new ServiceRegistry();
            var config = mock(ConfigService.class);
            services.publish(ConfigService.class, config);
            var scheduler = new MockScheduler();
            module.enable(new ModuleContext(plugin, BasicConfigurationNode.root(), mock(MessageService.class),
                    services, scheduler, mock(CommandRegistrar.class), events));
            assertThat(module.id()).isEqualTo("dragon-egg");
            assertThat(module.dependsOn()).isEmpty();
            assertThat(server.getRecipe(DragonEggCompass.KEY)).isNotNull();
            var tracker = services.get(DragonEggTracker.class);
            var player = server.addPlayer();
            player.getInventory().setItem(0, new ItemStack(Material.DRAGON_EGG));
            server.getPluginManager()
                    .callEvent(new PlayerJoinEvent(player, net.kyori.adventure.text.Component.empty()));
            assertThat(tracker.holdsTrackedEgg(player)).isTrue();
            module.disable();
            assertThat(server.getRecipe(DragonEggCompass.KEY)).isNull();
            verify(config).register(org.mockito.ArgumentMatchers.any(DragonEggData.class));
        } finally {
            module.disable();
            events.unregisterAll();
            MockBukkit.unmock();
        }
    }
}
