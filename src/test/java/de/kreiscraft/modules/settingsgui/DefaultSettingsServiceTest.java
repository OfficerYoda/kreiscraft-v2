package de.kreiscraft.modules.settingsgui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DefaultSettingsServiceTest {

    @Mock
    private SettingsData data;

    private final Map<UUID, Settings> store = new HashMap<>();

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        when(data.get()).thenReturn(store);
        // lenient: update is a no-op for read-only tests
        lenient()
                .doAnswer(inv -> {
                    ((Consumer<Map<UUID, Settings>>) inv.getArgument(0)).accept(store);
                    return null;
                })
                .when(data)
                .update(any());
    }

    @Test
    void unknownPlayer_clickThrough_returnsDefaultTrue() {
        DefaultSettingsService svc = new DefaultSettingsService(data);
        assertThat(svc.isClickThroughEnabled(UUID.randomUUID())).isTrue();
    }

    @Test
    void unknownPlayer_combatDisplay_returnsDefaultActionbar() {
        DefaultSettingsService svc = new DefaultSettingsService(data);
        assertThat(svc.getCombatDisplay(UUID.randomUUID())).isEqualTo(CombatDisplay.ACTIONBAR);
    }

    @Test
    void setClickThroughEnabled_false_persists() {
        DefaultSettingsService svc = new DefaultSettingsService(data);
        UUID alice = UUID.randomUUID();
        svc.setClickThroughEnabled(alice, false);
        assertThat(svc.isClickThroughEnabled(alice)).isFalse();
    }

    @Test
    void setClickThroughEnabled_true_persists() {
        DefaultSettingsService svc = new DefaultSettingsService(data);
        UUID alice = UUID.randomUUID();
        svc.setClickThroughEnabled(alice, false);
        svc.setClickThroughEnabled(alice, true);
        assertThat(svc.isClickThroughEnabled(alice)).isTrue();
    }

    @Test
    void setCombatDisplay_chat_persists() {
        DefaultSettingsService svc = new DefaultSettingsService(data);
        UUID alice = UUID.randomUUID();
        svc.setCombatDisplay(alice, CombatDisplay.CHAT);
        assertThat(svc.getCombatDisplay(alice)).isEqualTo(CombatDisplay.CHAT);
    }

    @Test
    void setCombatDisplay_title_persists() {
        DefaultSettingsService svc = new DefaultSettingsService(data);
        UUID alice = UUID.randomUUID();
        svc.setCombatDisplay(alice, CombatDisplay.TITLE);
        assertThat(svc.getCombatDisplay(alice)).isEqualTo(CombatDisplay.TITLE);
    }

    @Test
    void setCombatDisplay_doesNotAffectClickThrough() {
        DefaultSettingsService svc = new DefaultSettingsService(data);
        UUID alice = UUID.randomUUID();
        svc.setClickThroughEnabled(alice, false);
        svc.setCombatDisplay(alice, CombatDisplay.CHAT);
        assertThat(svc.isClickThroughEnabled(alice)).isFalse();
    }

    @Test
    void toggleClickThrough_firstCall_accepted_andFlips() {
        DefaultSettingsService svc = new DefaultSettingsService(data);
        UUID alice = UUID.randomUUID();
        boolean accepted = svc.toggleClickThrough(alice);
        assertThat(accepted).isTrue();
        assertThat(svc.isClickThroughEnabled(alice)).isFalse();
    }

    @Test
    void toggleClickThrough_immediateSecondCall_debounced() {
        DefaultSettingsService svc = new DefaultSettingsService(data);
        UUID alice = UUID.randomUUID();
        boolean first = svc.toggleClickThrough(alice);
        boolean second = svc.toggleClickThrough(alice); // within 1 second
        assertThat(first).isTrue();
        assertThat(second).isFalse();
        // value should be flipped only once (true → false)
        assertThat(svc.isClickThroughEnabled(alice)).isFalse();
    }
}
