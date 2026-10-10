package de.kreiscraft.modules.settingsgui;

import static org.assertj.core.api.Assertions.assertThat;

import de.kreiscraft.core.message.ConfigurateMessageService;
import de.kreiscraft.core.message.MessageService;
import de.kreiscraft.modules.settingsgui.SettingsGui.Page;
import de.kreiscraft.shared.preferences.PlayerLanguage;
import de.kreiscraft.testsupport.InMemoryPlayerPreferencesService;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import xyz.xenondevs.invui.gui.Gui;

/**
 * Verifies the Player Settings GUI renders its labels and titles in each recipient's persisted
 * language (with the server language as fallback) rather than a single hard-coded locale.
 */
class SettingsGuiLocaleTest {

    private ServerMock server;
    private MessageService messages;
    private DefaultSettingsService settings;

    @BeforeEach
    void setUp(@TempDir Path tmp) throws IOException {
        server = MockBukkit.mock();
        messages = new ConfigurateMessageService(
                tmp,
                getClass().getClassLoader(),
                "de",
                server,
                recipient -> "Alice".equals(recipient.getName())
                        ? Optional.of("en")
                        : Optional.of("de"));
        settings = new DefaultSettingsService(
                new InMemoryPlayerPreferencesService(), PlayerLanguage.DE, Particle.FLAME);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private static String plain(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    private String label(Player player, Gui gui, int slot) {
        ItemStack stack = gui.getItem(slot).getItemProvider(player).get();
        return plain(stack.getItemMeta().displayName());
    }

    @Test
    void labels_renderInEachPlayersLanguage() {
        PlayerMock alice = server.addPlayer("Alice"); // stored en
        PlayerMock bob = server.addPlayer("Bob"); // stored de
        Gui gui = SettingsGui.buildRoot(settings, id -> true, messages, alice, (p, page) -> {
        });

        assertThat(label(alice, gui, SettingsGui.SLOT_COMBAT_DISPLAY)).contains("Combat Display");
        assertThat(label(bob, gui, SettingsGui.SLOT_COMBAT_DISPLAY)).contains("Combat-Anzeige");
        assertThat(label(alice, gui, SettingsGui.SLOT_GLOW)).contains("Glow");
        assertThat(label(bob, gui, SettingsGui.SLOT_GLOW)).contains("Leuchten");
    }

    @Test
    void languageLabel_isLocalised() {
        PlayerMock alice = server.addPlayer("Alice");
        PlayerMock bob = server.addPlayer("Bob");
        Gui gui = SettingsGui.buildRoot(settings, id -> true, messages, alice, (p, page) -> {
        });

        assertThat(label(alice, gui, SettingsGui.SLOT_LANGUAGE)).contains("Language");
        assertThat(label(bob, gui, SettingsGui.SLOT_LANGUAGE)).contains("Sprache");
    }

    @Test
    void combatDisplayValues_areVisuallyDistinctFromTheirLabelsInBothLanguages() {
        PlayerMock alice = server.addPlayer("Alice");
        PlayerMock bob = server.addPlayer("Bob");

        for (String key : List.of(
                "settings-gui.combat-display-chat",
                "settings-gui.combat-display-actionbar",
                "settings-gui.combat-display-title")) {
            assertThat(textRunColors(messages.renderFor(alice, key)))
                    .as("English %s", key)
                    .containsExactly(NamedTextColor.GRAY, NamedTextColor.AQUA);
            assertThat(textRunColors(messages.renderFor(bob, key)))
                    .as("German %s", key)
                    .containsExactly(NamedTextColor.GRAY, NamedTextColor.AQUA);
        }
    }

    @Test
    void pageTitles_areLocalisedPerRecipient() {
        PlayerMock alice = server.addPlayer("Alice");
        PlayerMock bob = server.addPlayer("Bob");

        assertThat(plain(messages.renderFor(alice, Page.ROOT.titleKey()))).contains("Settings");
        assertThat(plain(messages.renderFor(bob, Page.ROOT.titleKey()))).contains("Einstellungen");
        assertThat(plain(messages.renderFor(alice, Page.EFFECTS.titleKey())))
                .contains("Effects Settings");
        assertThat(plain(messages.renderFor(bob, Page.EFFECTS.titleKey())))
                .contains("Effekt-Einstellungen");
    }

    @Test
    void serverConfigurationPrompts_areLocalisedPerRecipient() {
        PlayerMock alice = server.addPlayer("Alice");
        PlayerMock bob = server.addPlayer("Bob");

        assertThat(plain(messages.renderFor(alice, "server-config.title")))
                .contains("Server Configuration");
        assertThat(plain(messages.renderFor(bob, "server-config.title")))
                .contains("Server-Konfiguration");
        assertThat(plain(messages.renderFor(alice, "server-config.close.restart-required", "count", 1)))
                .containsIgnoringCase("restart");
        assertThat(plain(messages.renderFor(bob, "server-config.close.restart-required", "count", 1)))
                .containsIgnoringCase("Neustart");
    }

    private static List<TextColor> textRunColors(Component root) {
        List<TextColor> colors = new ArrayList<>();
        collectTextRunColors(root, Style.empty(), colors);
        return colors;
    }

    private static void collectTextRunColors(
            Component component, Style inherited, List<TextColor> colors) {
        Style effective = component.style().merge(inherited, Style.Merge.Strategy.IF_ABSENT_ON_TARGET);
        if (component instanceof TextComponent text && !text.content().isEmpty()) {
            TextColor color = effective.color();
            if (colors.isEmpty() || !colors.getLast().equals(color)) {
                colors.add(color);
            }
        }
        for (Component child : component.children()) {
            collectTextRunColors(child, effective, colors);
        }
    }
}
