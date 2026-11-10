package de.kreiscraft.modules.status;

import static org.assertj.core.api.Assertions.assertThat;

import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

class StatusTest {

    @Test
    void displayName_addsSpacesWithoutAddingColorsOrResets() {
        for (String[] prefixes : new String[][] {
                {"<gold>Owner", "<gold>Owner "},
                {"<gold>Owner ", "<gold>Owner "},
                {"<gold>Owner </gold>", "<gold>Owner </gold>"},
                {"", ""},
                {"Owner", "Owner "}
        }) {
            Status status = new Status(prefixes[0], "<blue>[A]", 0xFF4400);
            assertThat(status.displayName("Bob"))
                    .isEqualTo(MiniMessage.miniMessage().deserialize(prefixes[1] + "Bob <blue>[A]"));
        }
    }

    @Test
    void displayName_preservesExistingSpacesAndLeavesEmptyAffixesAlone() {
        for (String suffix : new String[] {"[A]", " [A]", "<blue> [A]</blue>"}) {
            Status status = new Status("<gold>Owner </gold>", suffix, 0);
            assertThat(PlainTextComponentSerializer.plainText().serialize(status.displayName("Bob")))
                    .isEqualTo("Owner Bob [A]");
        }
        assertThat(PlainTextComponentSerializer.plainText()
                .serialize(new Status("<gold>", "", 0).displayName("Bob"))).isEqualTo("Bob");
    }

    @Test
    void displayName_keepsPlayerNameLiteral() {
        Status status = new Status("<gold>Owner ", " [A]", 0);
        assertThat(PlainTextComponentSerializer.plainText().serialize(status.displayName("<red>Bob")))
                .isEqualTo("Owner <red>Bob [A]");
    }
}
