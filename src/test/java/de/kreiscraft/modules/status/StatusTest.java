package de.kreiscraft.modules.status;

import static org.assertj.core.api.Assertions.assertThat;

import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.junit.jupiter.api.Test;

class StatusTest {

    @Test
    void nameColor_usesFinalVisiblePrefixColor() {
        for (String prefix : new String[] {
                "<red>Admin <gold>Owner</gold></red>",
                "<red>Admin</red><gold>Owner</gold>  ",
                "<gold>Owner</gold><blue> </blue>",
                "<gradient:red:gold>AB</gradient>",
                "<gold>Owner"
        }) {
            assertThat(new Status(prefix, "<blue>suffix", 0xFF4400).nameColor())
                    .as(prefix)
                    .isEqualTo(NamedTextColor.GOLD);
        }
    }

    @Test
    void nameColor_respectsInheritedAndResetColors() {
        assertThat(new Status("<red>Admin <bold>Owner</bold></red>", "", 0).nameColor())
                .isEqualTo(NamedTextColor.RED);
        assertThat(new Status("<red>Admin<reset>Owner", "", 0xFF4400).nameColor())
                .isEqualTo(TextColor.color(0xFF4400));
        assertThat(new Status("<red>Admin</red>Owner", "", 0xFF4400).nameColor())
                .isEqualTo(TextColor.color(0xFF4400));
    }

    @Test
    void nameColor_withoutVisibleColoredPrefixKeepsStoredColor() {
        for (String prefix : new String[] {"", "Owner", "<red>  </red>"}) {
            assertThat(new Status(prefix, "", 0xAAFF4400).nameColor())
                    .isEqualTo(TextColor.color(0xFF4400));
        }
    }
}
