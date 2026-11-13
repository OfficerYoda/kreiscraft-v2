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
    void offlineReturnFailureCannotStarveSamplingOrCompassUpdates() throws Exception {
        var server = MockBukkit.mock();
        var module = new DragonEggModule();
        var plugin = MockBukkit.createMockPlugin();
        var events = new EventBus(plugin);
        try {
            var services = new ServiceRegistry();
            var config = mock(ConfigService.class);
            services.publish(ConfigService.class, config);
            services.publish(de.kreiscraft.platform.PaperPlatform.class,
                    mock(de.kreiscraft.platform.PaperPlatform.class));
            var editor = mock(de.kreiscraft.platform.OfflinePlayerDataEditor.class);
            services.publish(de.kreiscraft.platform.OfflinePlayerDataEditor.class, editor);
            var scheduler = new MockScheduler();
            var messages = mock(MessageService.class);
            module.enable(new ModuleContext(plugin, BasicConfigurationNode.root(), messages,
                    services, scheduler, mock(CommandRegistrar.class), events));
            var captured = org.mockito.ArgumentCaptor.forClass(DragonEggData.class);
            verify(config).register(captured.capture());
            var data = captured.getValue();
            var tracker = services.get(DragonEggTracker.class);
            var old = server.addPlayer();
            ItemStack egg = new ItemStack(Material.DRAGON_EGG);
            tracker.claim(egg);
            old.getInventory().setItem(0, egg);
            tracker.trackHolder(old);
            old.disconnect();
            data.update(state -> {
                state.offlineCarrier = old.getUniqueId().toString();
                state.logoutPosition = state.position;
                state.offlineDeadline = 1;
            });
            org.mockito.Mockito.when(editor.openForEdit(old.getUniqueId()))
                    .thenThrow(new java.io.IOException("unreadable"));
            var viewer = server.addPlayer();
            viewer.getInventory().setItem(0, server.getRecipe(DragonEggCompass.KEY).getResult());
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> scheduler.advance(Duration.ofSeconds(1)))
                    .isInstanceOf(de.kreiscraft.core.module.ModuleException.class);
            assertThat(tracker.ping()).isPresent();
            var meta = (org.bukkit.inventory.meta.CompassMeta) viewer.getInventory().getItem(0).getItemMeta();
            assertThat(meta.getLodestone()).isEqualTo(old.getLocation());
            assertThat(data.get().offlineDeadline).isEqualTo(1);
        } finally {
            module.disable();
            events.unregisterAll();
            MockBukkit.unmock();
        }
    }

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
            var view = player.openInventory(player.getEnderChest());
            player.setItemOnCursor(new ItemStack(Material.DRAGON_EGG));
            var click = new org.bukkit.event.inventory.InventoryClickEvent(view,
                    org.bukkit.event.inventory.InventoryType.SlotType.CONTAINER, 0,
                    org.bukkit.event.inventory.ClickType.LEFT, org.bukkit.event.inventory.InventoryAction.PLACE_ALL);
            server.getPluginManager().callEvent(click);
            assertThat(click.isCancelled()).isFalse();
            verify(config).register(org.mockito.ArgumentMatchers.any(DragonEggData.class));
        } finally {
            module.disable();
            events.unregisterAll();
            MockBukkit.unmock();
        }
    }
}
