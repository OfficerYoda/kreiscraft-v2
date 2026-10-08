package de.kreiscraft;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.kreiscraft.core.config.ConfigService;
import de.kreiscraft.core.message.MessageService;
import de.kreiscraft.core.module.ModuleRegistry;
import java.io.IOException;
import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.Test;
import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.ConfigurationNode;

class ReloadServiceTest {

    @Test
    void reload_usesCompletePluginReloadPath() throws IOException {
        ConfigService config = mock(ConfigService.class);
        MessageService messages = mock(MessageService.class);
        ModuleRegistry modules = mock(ModuleRegistry.class);
        CommandSender sender = mock(CommandSender.class);
        ConfigurationNode root = CommentedConfigurationNode.root();
        when(config.root()).thenReturn(root);

        boolean applied = new ReloadService(config, modules, messages).reload(sender);

        assertThat(applied).isTrue();
        verify(config).reload();
        verify(messages).reload();
        verify(modules).reloadConfigs(root);
        verify(messages).send(sender, "core.reload.success");
    }

    @Test
    void reloadFailure_isReportedAndStopsThePath() throws IOException {
        ConfigService config = mock(ConfigService.class);
        MessageService messages = mock(MessageService.class);
        ModuleRegistry modules = mock(ModuleRegistry.class);
        CommandSender sender = mock(CommandSender.class);
        doThrow(new IOException("disk")).when(config).reload();

        boolean applied = new ReloadService(config, modules, messages).reload(sender);

        assertThat(applied).isFalse();
        verify(messages).send(sender, "core.reload.failure", "error", "disk");
        verify(messages, never()).reload();
        verify(modules, never()).reloadConfigs(org.mockito.ArgumentMatchers.any());
    }
}
