package de.kreiscraft.modules.settingsgui;

import static org.assertj.core.api.Assertions.assertThat;

import de.kreiscraft.core.module.ModuleEnablement;
import de.kreiscraft.shared.preferences.PlayerLanguage;
import de.kreiscraft.testsupport.InMemoryPlayerPreferencesService;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import xyz.xenondevs.invui.Click;
import xyz.xenondevs.invui.gui.Gui;
import xyz.xenondevs.invui.item.ItemProvider;

import java.io.IOException;

/**
 * Language-switch slot position and item-material contract for ticket #61 (Classic style).
 *
 * <p>The language switch must sit in the bottom-right slot of the 9×3 root page (slot 26) and
 * must use a textured {@link Material#PLAYER_HEAD} (German or UK flag) rather than a book.
 */
class SettingsGuiLanguageHeadTest {

    private ServerMock server;
    private PlayerMock player;
    private DefaultSettingsService settings;
    private FakeMessages messages;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        player = server.addPlayer("Alice");
        settings = new DefaultSettingsService(
                new InMemoryPlayerPreferencesService(), PlayerLanguage.DE, Particle.FLAME);
        messages = new FakeMessages();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private static ModuleEnablement allEnabled() {
        return id -> true;
    }

    private Gui root() {
        return SettingsGui.buildRoot(settings, allEnabled(), messages, player, (p, page) -> {
        });
    }

    private ItemStack stackAt(Gui gui, int slot) {
        return gui.getItem(slot).getItemProvider(player).get();
    }

    @Test
    void languageSlot_isBottomRightOfNineByThreePage() {
        // 9×3 grid has 27 slots (0–26); bottom-right is slot 26
        assertThat(SettingsGui.SLOT_LANGUAGE).isEqualTo(26);
    }

    @Test
    void languageItem_isMadeOfPlayerHead() {
        Gui gui = root();
        ItemStack stack = stackAt(gui, SettingsGui.SLOT_LANGUAGE);
        assertThat(stack.getType()).isEqualTo(Material.PLAYER_HEAD);
    }

    @Test
    void languageItem_staysPlayerHeadAfterToggle() {
        Gui gui = root();
        gui.handleClick(SettingsGui.SLOT_LANGUAGE, new Click(player, ClickType.LEFT));
        ItemStack stack = stackAt(gui, SettingsGui.SLOT_LANGUAGE);
        assertThat(stack.getType()).isEqualTo(Material.PLAYER_HEAD);
    }

    // ------------------------------------------------------------------
    // Minimal fake that avoids pulling in the full lang file
    // ------------------------------------------------------------------

    private static final class FakeMessages implements de.kreiscraft.core.message.MessageService {
        @Override
        public Component render(String key, Object... placeholders) {
            return Component.text(key);
        }

        @Override
        public Component renderFor(CommandSender recipient, String key, Object... placeholders) {
            return Component.text(key);
        }

        @Override
        public void send(CommandSender sender, String key, Object... placeholders) {}

        @Override
        public void broadcast(String key, Object... placeholders) {}

        @Override
        public void reload() throws IOException {}
    }
}
