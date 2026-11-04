package de.kreiscraft.shared.preferences;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import org.bukkit.Particle;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DefaultPlayerPreferencesServiceTest {

    @Mock
    private PlayerPreferencesData data;

    private final Map<UUID, PlayerPreferences> store = new HashMap<>();
    private DefaultPlayerPreferencesService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        when(data.get()).thenReturn(store);
        lenient().doAnswer(inv -> {
            ((Consumer<Map<UUID, PlayerPreferences>>) inv.getArgument(0)).accept(store);
            return null;
        }).when(data).update(any());
        service = new DefaultPlayerPreferencesService(data);
    }

    @Test
    void unknownPlayer_returnsEmptySnapshot() {
        assertThat(service.preferences(UUID.randomUUID())).isEqualTo(PlayerPreferences.empty());
    }

    @Test
    void clickThrough_usesModuleFallbackWhenUnset() {
        UUID id = UUID.randomUUID();
        assertThat(service.clickThrough(id, true)).isTrue();
        assertThat(service.clickThrough(id, false)).isFalse();
    }

    @Test
    void combatDisplay_usesModuleFallbackWhenUnset() {
        UUID id = UUID.randomUUID();
        assertThat(service.combatDisplay(id, CombatDisplay.TITLE)).isEqualTo(CombatDisplay.TITLE);
    }

    @Test
    void storedPreferences_overrideFallbacks() {
        UUID id = UUID.randomUUID();
        service.setClickThrough(id, false);
        service.setCombatDisplay(id, CombatDisplay.CHAT);

        assertThat(service.clickThrough(id, true)).isFalse();
        assertThat(service.combatDisplay(id, CombatDisplay.TITLE)).isEqualTo(CombatDisplay.CHAT);
    }

    @Test
    void setters_areIndependentPerFieldAndPlayer() {
        UUID alice = UUID.randomUUID();
        UUID bob = UUID.randomUUID();

        service.setLanguage(alice, PlayerLanguage.EN);
        service.setEffectsEnabled(alice, true);
        service.setEffectsParticle(alice, Particle.FLAME);
        service.setClickThrough(bob, false);

        PlayerPreferences a = service.preferences(alice);
        assertThat(a.language()).contains(PlayerLanguage.EN);
        assertThat(a.effectsEnabled()).contains(true);
        assertThat(a.effectsParticle()).contains(Particle.FLAME);
        assertThat(a.clickThrough()).isEmpty();

        assertThat(service.preferences(bob).clickThrough()).contains(false);
        assertThat(service.preferences(bob).language()).isEmpty();
    }
}
