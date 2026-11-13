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
import java.time.Duration;
import org.bukkit.Material;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffectType;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.spongepowered.configurate.BasicConfigurationNode;

class DragonEggModuleTest {
    @Test
    void inventoryPollingHandlesLoginLogoutDeathAndDisableWithoutPermanentEffects() {
        var server = MockBukkit.mock();
        var module = new DragonEggModule();
        var plugin = MockBukkit.createMockPlugin();
        var events = new EventBus(plugin);
        try {
            var services = new ServiceRegistry();
            services.publish(ConfigService.class, mock(ConfigService.class));
            services.publish(de.kreiscraft.platform.PaperPlatform.class,
                    mock(de.kreiscraft.platform.PaperPlatform.class));
            services.publish(de.kreiscraft.platform.OfflinePlayerDataEditor.class,
                    mock(de.kreiscraft.platform.OfflinePlayerDataEditor.class));
            var scheduler = new MockScheduler();
            var player = server.addPlayer();
            player.getInventory().setItem(0, new ItemStack(Material.DRAGON_EGG));
            module.enable(new ModuleContext(plugin, BasicConfigurationNode.root(), mock(MessageService.class),
                    services, scheduler, mock(CommandRegistrar.class), events));
            scheduler.advance(Duration.ofMillis(50));
            assertThat(player.getPotionEffect(PotionEffectType.STRENGTH).getAmplifier()).isZero();
            assertThat(player.getPotionEffect(PotionEffectType.HASTE).getAmplifier()).isEqualTo(1);
            var egg = player.getInventory().getItem(0);
            player.disconnect();
            scheduler.advance(Duration.ofSeconds(1));
            server.getScheduler().performTicks(10);
            player.tick();
            assertThat(player.getActivePotionEffects()).isEmpty();
            var login = server.addPlayer();
            login.getInventory().setItem(0, egg);
            scheduler.advance(Duration.ofMillis(50));
            assertThat(login.hasPotionEffect(PotionEffectType.HASTE)).isTrue();
            login.setHealth(0);
            scheduler.advance(Duration.ofMillis(50));
            server.getScheduler().performTicks(10);
            login.tick();
            scheduler.advance(Duration.ofMillis(50));
            assertThat(login.getActivePotionEffects()).isEmpty();
            var survivor = server.addPlayer();
            survivor.getInventory().setItem(0, egg);
            scheduler.advance(Duration.ofMillis(50));
            assertThat(survivor.hasPotionEffect(PotionEffectType.HASTE)).isTrue();
            module.disable();
            server.getScheduler().performTicks(10);
            survivor.tick();
            scheduler.advance(Duration.ofSeconds(2));
            assertThat(survivor.getActivePotionEffects()).isEmpty();
        } finally {
            module.disable();
            events.unregisterAll();
            MockBukkit.unmock();
        }
    }

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
            services.publish(de.kreiscraft.platform.PaperPlatform.class,
                    mock(de.kreiscraft.platform.PaperPlatform.class));
            services.publish(de.kreiscraft.platform.OfflinePlayerDataEditor.class,
                    mock(de.kreiscraft.platform.OfflinePlayerDataEditor.class));
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
