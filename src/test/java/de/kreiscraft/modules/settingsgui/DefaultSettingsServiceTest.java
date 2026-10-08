package de.kreiscraft.modules.settingsgui;

import static org.assertj.core.api.Assertions.assertThat;

import de.kreiscraft.shared.preferences.CombatDisplay;
import de.kreiscraft.shared.preferences.PlayerLanguage;
import de.kreiscraft.testsupport.InMemoryPlayerPreferencesService;
import java.util.UUID;
import org.bukkit.Particle;
import org.junit.jupiter.api.Test;

class DefaultSettingsServiceTest {

    private static final PlayerLanguage SERVER_LANGUAGE = PlayerLanguage.DE;
    private static final Particle SERVER_PARTICLE = Particle.FLAME;

    private DefaultSettingsService service() {
        return new DefaultSettingsService(
                new InMemoryPlayerPreferencesService(), SERVER_LANGUAGE, SERVER_PARTICLE);
    }

    @Test
    void unknownPlayer_language_returnsServerLanguage() {
        assertThat(service().language(UUID.randomUUID())).isEqualTo(SERVER_LANGUAGE);
    }

    @Test
    void unknownPlayer_clickThrough_returnsDefaultTrue() {
        assertThat(service().clickThrough(UUID.randomUUID())).isTrue();
    }

    @Test
    void unknownPlayer_combatDisplay_returnsDefaultActionbar() {
        assertThat(service().combatDisplay(UUID.randomUUID())).isEqualTo(CombatDisplay.ACTIONBAR);
    }

    @Test
    void unknownPlayer_effectsEnabled_returnsDefaultTrue() {
        assertThat(service().effectsEnabled(UUID.randomUUID())).isTrue();
    }

    @Test
    void unknownPlayer_effectsParticle_returnsServerDefault() {
        assertThat(service().effectsParticle(UUID.randomUUID())).isEqualTo(SERVER_PARTICLE);
    }

    @Test
    void unknownPlayer_glowEnabled_returnsDefaultFalse() {
        assertThat(service().glowEnabled(UUID.randomUUID())).isFalse();
    }

    @Test
    void setLanguage_persists() {
        DefaultSettingsService svc = service();
        UUID alice = UUID.randomUUID();
        svc.setLanguage(alice, PlayerLanguage.EN);
        assertThat(svc.language(alice)).isEqualTo(PlayerLanguage.EN);
    }

    @Test
    void setClickThrough_false_persists() {
        DefaultSettingsService svc = service();
        UUID alice = UUID.randomUUID();
        svc.setClickThrough(alice, false);
        assertThat(svc.clickThrough(alice)).isFalse();
    }

    @Test
    void setClickThrough_true_persists() {
        DefaultSettingsService svc = service();
        UUID alice = UUID.randomUUID();
        svc.setClickThrough(alice, false);
        svc.setClickThrough(alice, true);
        assertThat(svc.clickThrough(alice)).isTrue();
    }

    @Test
    void setCombatDisplay_chat_persists() {
        DefaultSettingsService svc = service();
        UUID alice = UUID.randomUUID();
        svc.setCombatDisplay(alice, CombatDisplay.CHAT);
        assertThat(svc.combatDisplay(alice)).isEqualTo(CombatDisplay.CHAT);
    }

    @Test
    void setEffectsEnabled_false_persists() {
        DefaultSettingsService svc = service();
        UUID alice = UUID.randomUUID();
        svc.setEffectsEnabled(alice, false);
        assertThat(svc.effectsEnabled(alice)).isFalse();
    }

    @Test
    void setEffectsParticle_persistsWithoutEnablingEffects() {
        DefaultSettingsService svc = service();
        UUID alice = UUID.randomUUID();
        svc.setEffectsEnabled(alice, false);
        svc.setEffectsParticle(alice, Particle.HEART);
        assertThat(svc.effectsParticle(alice)).isEqualTo(Particle.HEART);
        assertThat(svc.effectsEnabled(alice)).isFalse();
    }

    @Test
    void setGlowEnabled_true_persists() {
        DefaultSettingsService svc = service();
        UUID alice = UUID.randomUUID();
        svc.setGlowEnabled(alice, true);
        assertThat(svc.glowEnabled(alice)).isTrue();
    }

    @Test
    void setCombatDisplay_doesNotAffectClickThrough() {
        DefaultSettingsService svc = service();
        UUID alice = UUID.randomUUID();
        svc.setClickThrough(alice, false);
        svc.setCombatDisplay(alice, CombatDisplay.CHAT);
        assertThat(svc.clickThrough(alice)).isFalse();
    }

    @Test
    void consecutiveWrites_areNotDebounced() {
        DefaultSettingsService svc = service();
        UUID alice = UUID.randomUUID();
        svc.setClickThrough(alice, false);
        svc.setClickThrough(alice, true);
        assertThat(svc.clickThrough(alice)).isTrue();
    }
}
