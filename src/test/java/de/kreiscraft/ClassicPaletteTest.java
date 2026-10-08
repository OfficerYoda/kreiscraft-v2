package de.kreiscraft;

import static org.assertj.core.api.Assertions.assertThat;

import de.kreiscraft.core.message.ConfigurateMessageService;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextColor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

/**
 * Verifies that the approved Classic Command Message Style is applied consistently.
 *
 * <p>Classic semantic roles (all standard Minecraft named colours):
 * <ul>
 *   <li>player names → {@link NamedTextColor#GOLD} (#FFAA00)
 *   <li>reasons → {@link NamedTextColor#LIGHT_PURPLE} (#FF55FF)
 *   <li>values → {@link NamedTextColor#AQUA} (#55FFFF)
 * </ul>
 *
 * <p>Tests render messages via the real language files (the bundled classpath resources) and
 * walk the component tree to find the effective colour of each semantic token.
 */
class ClassicPaletteTest {

    private ServerMock server;
    private ConfigurateMessageService messages;

    @BeforeEach
    void setUp(@TempDir Path tmp) throws IOException {
        server = MockBukkit.mock();
        messages = new ConfigurateMessageService(tmp, getClass().getClassLoader(), "en", server);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    // ------------------------------------------------------------------
    // Player-name role: gold (#FFAA00)
    // ------------------------------------------------------------------

    @Test
    void adminIp_playerNameIsGold() {
        Component c = messages.render("admin.ip", "player", "Steve", "ip", "1.2.3.4");
        assertThat(colorOf(c, "Steve")).isEqualTo(NamedTextColor.GOLD);
    }

    @Test
    void adminPing_playerNameIsGold() {
        Component c = messages.render("admin.ping", "player", "Steve", "ping", "42");
        assertThat(colorOf(c, "Steve")).isEqualTo(NamedTextColor.GOLD);
    }

    @Test
    void adminLastonline_playerNameIsGold() {
        Component c = messages.render(
                "admin.lastonline", "player", "Steve", "when", "2026-01-01", "relative", "5 days ago");
        assertThat(colorOf(c, "Steve")).isEqualTo(NamedTextColor.GOLD);
    }

    @Test
    void combatManualTag_playerNameIsGold() {
        Component c = messages.render("combat.manual-tag", "player", "Steve", "seconds", "5");
        assertThat(colorOf(c, "Steve")).isEqualTo(NamedTextColor.GOLD);
    }

    @Test
    void jailJailed_playerNameIsGold() {
        Component c = messages.render("jail.jailed", "player", "Steve", "reason", "griefing");
        assertThat(colorOf(c, "Steve")).isEqualTo(NamedTextColor.GOLD);
    }

    @Test
    void jailReleased_playerNameIsGold() {
        Component c = messages.render("jail.released", "player", "Steve");
        assertThat(colorOf(c, "Steve")).isEqualTo(NamedTextColor.GOLD);
    }

    @Test
    void bedrockAuthorized_playerNameIsGold() {
        Component c = messages.render("bedrock.authorized", "player", "Steve");
        assertThat(colorOf(c, "Steve")).isEqualTo(NamedTextColor.GOLD);
    }

    @Test
    void invseeGuiTitle_playerNameIsGold() {
        Component c = messages.render("invsee.gui-title-inv", "player", "Steve");
        assertThat(colorOf(c, "Steve")).isEqualTo(NamedTextColor.GOLD);
    }

    // ------------------------------------------------------------------
    // Reason role: light_purple (#FF55FF)
    // ------------------------------------------------------------------

    @Test
    void jailJailedTarget_reasonIsLightPurple() {
        Component c = messages.render("jail.jailed-target", "reason", "griefing");
        assertThat(colorOf(c, "griefing")).isEqualTo(NamedTextColor.LIGHT_PURPLE);
    }

    @Test
    void jailJailed_reasonIsLightPurple() {
        Component c = messages.render("jail.jailed", "player", "Steve", "reason", "griefing");
        assertThat(colorOf(c, "griefing")).isEqualTo(NamedTextColor.LIGHT_PURPLE);
    }

    @Test
    void rebootCountdownSubtitle_reasonIsLightPurple() {
        Component c = messages.render("reboot.countdown-subtitle", "reason", "maintenance");
        assertThat(colorOf(c, "maintenance")).isEqualTo(NamedTextColor.LIGHT_PURPLE);
    }

    // ------------------------------------------------------------------
    // Value role: aqua (#55FFFF)
    // ------------------------------------------------------------------

    @Test
    void adminPing_valueIsAqua() {
        Component c = messages.render("admin.ping", "player", "Steve", "ping", "42");
        assertThat(colorOf(c, "42")).isEqualTo(NamedTextColor.AQUA);
    }

    @Test
    void adminTps_valueIsAqua() {
        Component c = messages.render("admin.tps", "tps", "19.98");
        assertThat(colorOf(c, "19.98")).isEqualTo(NamedTextColor.AQUA);
    }

    // ------------------------------------------------------------------
    // Helper: walk the Adventure component tree for effective colour
    // ------------------------------------------------------------------

    private record StyledText(String text, TextColor color) {
    }

    private static List<StyledText> styledTexts(Component root) {
        List<StyledText> out = new ArrayList<>();
        collect(root, Style.empty(), out);
        return out;
    }

    private static void collect(Component component, Style inherited, List<StyledText> out) {
        // Component's own explicit style takes priority; inherited fills in what's absent.
        Style effective = component.style().merge(inherited, Style.Merge.Strategy.IF_ABSENT_ON_TARGET);
        if (component instanceof TextComponent text && !text.content().isEmpty()) {
            out.add(new StyledText(text.content(), effective.color()));
        }
        for (Component child : component.children()) {
            collect(child, effective, out);
        }
    }

    private static TextColor colorOf(Component root, String needle) {
        return styledTexts(root).stream()
                .filter(s -> s.text().contains(needle))
                .map(StyledText::color)
                .filter(java.util.Objects::nonNull)
                .findFirst()
                .orElse(null);
    }
}
