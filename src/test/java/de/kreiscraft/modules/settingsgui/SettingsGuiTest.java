package de.kreiscraft.modules.settingsgui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import de.kreiscraft.core.message.MessageService;
import de.kreiscraft.core.module.ModuleEnablement;
import de.kreiscraft.modules.settingsgui.SettingsGui.Page;
import de.kreiscraft.shared.preferences.CombatDisplay;
import de.kreiscraft.shared.preferences.PlayerLanguage;
import de.kreiscraft.testsupport.InMemoryPlayerPreferencesService;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
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

class SettingsGuiTest {

    private ServerMock server;
    private PlayerMock player;
    private UUID playerId;
    private InMemoryPlayerPreferencesService preferences;
    private DefaultSettingsService settings;
    private FakeMessages messages;
    private NavigatorRecorder navigator;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        player = server.addPlayer("Alice");
        playerId = player.getUniqueId();
        preferences = spy(new InMemoryPlayerPreferencesService());
        settings = new DefaultSettingsService(preferences, PlayerLanguage.DE, Particle.FLAME);
        messages = new FakeMessages();
        navigator = new NavigatorRecorder();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private static ModuleEnablement allEnabled() {
        return id -> true;
    }

    private Gui root() {
        return SettingsGui.buildRoot(settings, allEnabled(), messages, player, navigator);
    }

    private Gui rootWith(ModuleEnablement modules) {
        return SettingsGui.buildRoot(settings, modules, messages, player, navigator);
    }

    private static String plain(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    private ItemStack stackAt(Gui gui, int slot) {
        return gui.getItem(slot).getItemProvider(player).get();
    }

    private void click(Gui gui, int slot) {
        gui.handleClick(slot, new Click(player, ClickType.LEFT));
    }

    // ------------------------------------------------------------------
    // Module awareness
    // ------------------------------------------------------------------

    @Test
    void root_whenAllFeatureModulesDisabled_showsOnlyLanguage() {
        Gui gui = rootWith(id -> false);

        assertThat(gui.getItem(SettingsGui.SLOT_LANGUAGE)).isNotNull();
        assertThat(gui.getItem(SettingsGui.SLOT_CLICK_THROUGH)).isNull();
        assertThat(gui.getItem(SettingsGui.SLOT_COMBAT_DISPLAY)).isNull();
        assertThat(gui.getItem(SettingsGui.SLOT_EFFECTS)).isNull();
        assertThat(gui.getItem(SettingsGui.SLOT_GLOW)).isNull();
    }

    @Test
    void root_omitsControlsWhoseModuleIsDisabled() {
        Gui gui = rootWith(id -> id.equals("combat"));

        assertThat(gui.getItem(SettingsGui.SLOT_LANGUAGE)).as("language is always available").isNotNull();
        assertThat(gui.getItem(SettingsGui.SLOT_COMBAT_DISPLAY)).isNotNull();
        assertThat(gui.getItem(SettingsGui.SLOT_CLICK_THROUGH)).isNull();
        assertThat(gui.getItem(SettingsGui.SLOT_EFFECTS)).isNull();
        assertThat(gui.getItem(SettingsGui.SLOT_GLOW)).isNull();
    }

    @Test
    void root_showsEffectsAndGlowWhenTheirModulesAreEnabled() {
        Gui gui = rootWith(id -> id.equals("effects") || id.equals("glow"));

        assertThat(gui.getItem(SettingsGui.SLOT_EFFECTS)).isNotNull();
        assertThat(gui.getItem(SettingsGui.SLOT_GLOW)).isNotNull();
    }

    // ------------------------------------------------------------------
    // Immediate persistence and reopen reflection
    // ------------------------------------------------------------------

    @Test
    void clickThrough_oneClick_mutatesExactlyOnce() {
        Gui gui = root();

        click(gui, SettingsGui.SLOT_CLICK_THROUGH);

        assertThat(settings.clickThrough(playerId)).isFalse();
        verify(preferences, times(1)).setClickThrough(playerId, false);
    }

    @Test
    void clickThrough_twoClicks_mutateTwice_withoutDebounce() {
        Gui gui = root();

        click(gui, SettingsGui.SLOT_CLICK_THROUGH);
        click(gui, SettingsGui.SLOT_CLICK_THROUGH);

        assertThat(settings.clickThrough(playerId)).isTrue();
        verify(preferences, times(2))
                .setClickThrough(org.mockito.ArgumentMatchers.eq(playerId),
                        org.mockito.ArgumentMatchers.anyBoolean());
        verify(preferences).setClickThrough(playerId, false);
        verify(preferences).setClickThrough(playerId, true);
    }

    @Test
    void combatDisplay_click_persistsAndIsReflectedOnReopen() {
        Gui first = root();
        click(first, SettingsGui.SLOT_COMBAT_DISPLAY);

        assertThat(settings.combatDisplay(playerId)).isEqualTo(CombatDisplay.TITLE);

        Gui reopened = root();
        ItemStack stack = stackAt(reopened, SettingsGui.SLOT_COMBAT_DISPLAY);
        assertThat(stack.getType()).isEqualTo(Material.NETHERITE_SWORD);
        assertThat(plain(stack.getItemMeta().displayName()))
                .isEqualTo("settings-gui.combat-display-title");
    }

    @Test
    void language_click_persistsAndIsReflectedOnReopen() {
        Gui first = root();
        click(first, SettingsGui.SLOT_LANGUAGE);

        assertThat(settings.language(playerId)).isEqualTo(PlayerLanguage.EN);

        Gui reopened = root();
        ItemStack stack = stackAt(reopened, SettingsGui.SLOT_LANGUAGE);
        assertThat(plain(stack.getItemMeta().displayName())).contains("settings-gui.language-en");
    }

    @Test
    void glow_click_persists() {
        Gui gui = root();
        click(gui, SettingsGui.SLOT_GLOW);

        assertThat(settings.glowEnabled(playerId)).isTrue();
        verify(preferences, times(1)).setGlowEnabled(playerId, true);
    }

    // ------------------------------------------------------------------
    // Effects page
    // ------------------------------------------------------------------

    @Test
    void effectsPage_togglesEnabledAndCyclesParticle() {
        Gui gui = SettingsGui.buildEffects(settings, messages, navigator);

        click(gui, SettingsGui.SLOT_EFFECTS_ENABLED);
        assertThat(settings.effectsEnabled(playerId)).isFalse();

        click(gui, SettingsGui.SLOT_EFFECTS_PARTICLE);
        assertThat(settings.effectsParticle(playerId))
                .isEqualTo(SettingsGui.nextParticle(Particle.FLAME));
    }

    @Test
    void effectsPage_hasNoOtherControls() {
        Gui gui = SettingsGui.buildEffects(settings, messages, navigator);

        List<Integer> occupied = new ArrayList<>();
        for (int slot = 0; slot < 27; slot++) {
            if (gui.getItem(slot) != null) {
                occupied.add(slot);
            }
        }
        assertThat(occupied)
                .containsExactlyInAnyOrder(
                        SettingsGui.SLOT_EFFECTS_ENABLED,
                        SettingsGui.SLOT_EFFECTS_PARTICLE,
                        SettingsGui.SLOT_BACK);
    }

    // ------------------------------------------------------------------
    // Navigation
    // ------------------------------------------------------------------

    @Test
    void effectsControl_opensEffectsPage() {
        Gui gui = root();

        click(gui, SettingsGui.SLOT_EFFECTS);

        assertThat(navigator.pages).containsExactly(Page.EFFECTS);
    }

    @Test
    void backControl_returnsToRoot() {
        Gui gui = SettingsGui.buildEffects(settings, messages, navigator);

        click(gui, SettingsGui.SLOT_BACK);

        assertThat(navigator.pages).containsExactly(Page.ROOT);
    }

    // ------------------------------------------------------------------
    // Test doubles
    // ------------------------------------------------------------------

    private static final class NavigatorRecorder implements SettingsGui.Navigator {
        private final List<Page> pages = new ArrayList<>();

        @Override
        public void open(Player player, Page page) {
            pages.add(page);
        }
    }

    private static final class FakeMessages implements MessageService {
        @Override
        public Component render(String key, Object... placeholders) {
            return Component.text(renderKey(key, placeholders));
        }

        @Override
        public Component renderFor(CommandSender recipient, String key, Object... placeholders) {
            return Component.text(renderKey(key, placeholders));
        }

        private static String renderKey(String key, Object... placeholders) {
            StringBuilder out = new StringBuilder(key);
            for (int i = 0; i + 1 < placeholders.length; i += 2) {
                out.append(' ').append(placeholders[i + 1]);
            }
            return out.toString();
        }

        @Override
        public void send(CommandSender sender, String key, Object... placeholders) {
            // no-op
        }

        @Override
        public void broadcast(String key, Object... placeholders) {
            // no-op
        }

        @Override
        public void reload() throws IOException {
            // no-op
        }
    }
}
