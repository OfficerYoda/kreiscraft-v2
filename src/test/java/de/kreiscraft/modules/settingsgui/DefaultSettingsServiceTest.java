package de.kreiscraft.modules.settingsgui;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DefaultSettingsServiceTest {

    private DefaultSettingsService newService(Path tmp) throws IOException {
        SettingsData data = new SettingsData(tmp);
        data.load();
        return new DefaultSettingsService(data);
    }

    @Test
    void unknownPlayer_clickThrough_returnsDefaultTrue(@TempDir Path tmp) throws IOException {
        DefaultSettingsService svc = newService(tmp);
        assertThat(svc.isClickThroughEnabled(UUID.randomUUID())).isTrue();
    }

    @Test
    void unknownPlayer_combatDisplay_returnsDefaultActionbar(@TempDir Path tmp) throws IOException {
        DefaultSettingsService svc = newService(tmp);
        assertThat(svc.getCombatDisplay(UUID.randomUUID())).isEqualTo(CombatDisplay.ACTIONBAR);
    }

    @Test
    void setClickThroughEnabled_false_persists(@TempDir Path tmp) throws IOException {
        DefaultSettingsService svc = newService(tmp);
        UUID alice = UUID.randomUUID();
        svc.setClickThroughEnabled(alice, false);
        assertThat(svc.isClickThroughEnabled(alice)).isFalse();
    }

    @Test
    void setClickThroughEnabled_true_persists(@TempDir Path tmp) throws IOException {
        DefaultSettingsService svc = newService(tmp);
        UUID alice = UUID.randomUUID();
        svc.setClickThroughEnabled(alice, false);
        svc.setClickThroughEnabled(alice, true);
        assertThat(svc.isClickThroughEnabled(alice)).isTrue();
    }

    @Test
    void setCombatDisplay_chat_persists(@TempDir Path tmp) throws IOException {
        DefaultSettingsService svc = newService(tmp);
        UUID alice = UUID.randomUUID();
        svc.setCombatDisplay(alice, CombatDisplay.CHAT);
        assertThat(svc.getCombatDisplay(alice)).isEqualTo(CombatDisplay.CHAT);
    }

    @Test
    void setCombatDisplay_title_persists(@TempDir Path tmp) throws IOException {
        DefaultSettingsService svc = newService(tmp);
        UUID alice = UUID.randomUUID();
        svc.setCombatDisplay(alice, CombatDisplay.TITLE);
        assertThat(svc.getCombatDisplay(alice)).isEqualTo(CombatDisplay.TITLE);
    }

    @Test
    void setCombatDisplay_doesNotAffectClickThrough(@TempDir Path tmp) throws IOException {
        DefaultSettingsService svc = newService(tmp);
        UUID alice = UUID.randomUUID();
        svc.setClickThroughEnabled(alice, false);
        svc.setCombatDisplay(alice, CombatDisplay.CHAT);
        assertThat(svc.isClickThroughEnabled(alice)).isFalse();
    }

    @Test
    void toggleClickThrough_firstCall_accepted_andFlips(@TempDir Path tmp) throws IOException {
        DefaultSettingsService svc = newService(tmp);
        UUID alice = UUID.randomUUID();
        // default is true, first toggle should flip to false
        boolean accepted = svc.toggleClickThrough(alice);
        assertThat(accepted).isTrue();
        assertThat(svc.isClickThroughEnabled(alice)).isFalse();
    }

    @Test
    void toggleClickThrough_immediateSecondCall_debounced(@TempDir Path tmp) throws IOException {
        DefaultSettingsService svc = newService(tmp);
        UUID alice = UUID.randomUUID();
        boolean first = svc.toggleClickThrough(alice);
        boolean second = svc.toggleClickThrough(alice); // within 1 second
        assertThat(first).isTrue();
        assertThat(second).isFalse();
        // value should only be flipped once (from true → false)
        assertThat(svc.isClickThroughEnabled(alice)).isFalse();
    }
}
