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
    private record StyledText(String text, TextColor color) {
    }

    private static List<StyledText> styledTexts(Component root) {
        List<StyledText> out = new ArrayList<>();
        collect(root, Style.empty(), out);
        return out;
    }

    private static void collect(Component component, Style inherited, List<StyledText> out) {
        Style effective = inherited.merge(component.style(), Style.Merge.Strategy.IF_ABSENT_ON_TARGET);
        if (component instanceof TextComponent text && !text.content().isEmpty()) {
            out.add(new StyledText(text.content(), effective.color()));
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
}
