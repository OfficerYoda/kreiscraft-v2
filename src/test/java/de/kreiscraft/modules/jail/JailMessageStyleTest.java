package de.kreiscraft.modules.jail;

import static org.assertj.core.api.Assertions.assertThat;

import de.kreiscraft.core.message.ConfigurateMessageService;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

/**
 * The direct message a jailed player receives must be rendered in the recipient's language and the
 * reason must carry a style semantically distinct from the surrounding prose.
 */
class JailMessageStyleTest {

    private ServerMock server;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    /** A rendered text segment together with its effective (style-inherited) colour. */
    private record StyledText(String text, TextColor color, boolean italic) {
    }

    private static List<StyledText> styledTexts(Component root) {
        List<StyledText> out = new ArrayList<>();
        collect(root, Style.empty(), out);
        return out;
    }

    private static void collect(Component component, Style inherited, List<StyledText> out) {
        Style effective = inherited.merge(component.style(), Style.Merge.Strategy.IF_ABSENT_ON_TARGET);
        if (component instanceof TextComponent text && !text.content().isEmpty()) {
            out.add(new StyledText(
                    text.content(),
                    effective.color(),
                    effective.decoration(TextDecoration.ITALIC) == TextDecoration.State.TRUE));
        }
        for (Component child : component.children()) {
            collect(child, effective, out);
        }
    }

    private static TextColor colorOf(List<StyledText> segments, String needle) {
        return segments.stream()
                .filter(segment -> segment.text().contains(needle))
                .map(StyledText::color)
                .filter(java.util.Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    @Test
    void jailedTarget_reasonHasDistinctStyleFromSurroundingProse(@TempDir Path tmp)
            throws IOException {
        ConfigurateMessageService messages =
                new ConfigurateMessageService(
                        tmp, getClass().getClassLoader(), "en", server);

        Component rendered = messages.render("jail.jailed-target", "reason", "griefing");
        List<StyledText> segments = styledTexts(rendered);

        TextColor reason = colorOf(segments, "griefing");
        TextColor prose = colorOf(segments, "You have been jailed");

        assertThat(reason).as("the reason must carry its own style").isNotNull();
        assertThat(prose).as("the surrounding prose must be styled").isNotNull();
        assertThat(reason).as("the reason must be visually distinct from prose").isNotEqualTo(prose);
    }

    @Test
    void jailedTarget_rendersInTheRecipientsLanguage(@TempDir Path tmp) throws IOException {
        ConfigurateMessageService messages =
                new ConfigurateMessageService(
                        tmp,
                        getClass().getClassLoader(),
                        "de",
                        server,
                        recipient -> Optional.of("en"));
        Player target = server.addPlayer("Target");

        Component rendered = messages.renderFor(target, "jail.jailed-target", "reason", "griefing");

        assertThat(PlainTextComponentSerializer.plainText().serialize(rendered))
                .as("an English-preferring recipient must receive the English message")
                .contains("You have been jailed")
                .doesNotContain("Du wurdest eingesperrt");
    }

    @Test
    void noReason_rendersTheLocalizedPlaceholderInItalics(@TempDir Path tmp) throws IOException {
        ConfigurateMessageService messages =
                new ConfigurateMessageService(tmp, getClass().getClassLoader(), "en", server);

        Component rendered = messages.render("jail.no-reason");

        assertThat(PlainTextComponentSerializer.plainText().serialize(rendered))
                .as("the no-reason placeholder is shown verbatim")
                .isEqualTo("<no reason>");
        assertThat(styledTexts(rendered))
                .as("the no-reason placeholder is italicised to stand apart from ordinary prose")
                .anySatisfy(segment -> {
                    assertThat(segment.text()).isEqualTo("<no reason>");
                    assertThat(segment.italic()).isTrue();
                });
    }

    @Test
    void noReason_rendersInTheRecipientsLanguage(@TempDir Path tmp) throws IOException {
        ConfigurateMessageService messages =
                new ConfigurateMessageService(
                        tmp,
                        getClass().getClassLoader(),
                        "en",
                        server,
                        recipient -> Optional.of("de"));
        Player target = server.addPlayer("Target");

        assertThat(
                PlainTextComponentSerializer.plainText()
                        .serialize(messages.renderFor(target, "jail.no-reason")))
                .as("the no-reason placeholder must be translated, not a hard-coded English token")
                .isEqualTo("<kein Grund>");
    }
}
