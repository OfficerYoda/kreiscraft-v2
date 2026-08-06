package de.kreiscraft.modules.effects;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.bukkit.Particle;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DefaultEffectsServiceTest {

    private EffectsConfig config;
    private DefaultEffectsService service;

    @BeforeEach
    void setUp() {
        config = new EffectsConfig(Particle.HAPPY_VILLAGER, 25, 0.9, 0.9, 0.9, true);
        service = new DefaultEffectsService(config);
    }

    @Test
    void isEnabled_whenNoState_returnsFalse() {
        assertThat(service.isEnabled(UUID.randomUUID())).isFalse();
    }

    @Test
    void toggle_whenNoState_enablesEffects() {
        UUID id = UUID.randomUUID();
        service.toggle(id);
        assertThat(service.isEnabled(id)).isTrue();
    }

    @Test
    void toggle_whenEnabled_disablesEffects() {
        UUID id = UUID.randomUUID();
        service.toggle(id); // on
        service.toggle(id); // off
        assertThat(service.isEnabled(id)).isFalse();
    }

    @Test
    void toggle_flipsBetweenOnAndOff() {
        UUID id = UUID.randomUUID();
        assertThat(service.isEnabled(id)).isFalse();
        service.toggle(id);
        assertThat(service.isEnabled(id)).isTrue();
        service.toggle(id);
        assertThat(service.isEnabled(id)).isFalse();
    }

    @Test
    void getParticle_whenNoState_returnsDefault() {
        assertThat(service.getParticle(UUID.randomUUID())).isEqualTo(Particle.HAPPY_VILLAGER);
    }

    @Test
    void setParticle_overridesParticle() {
        UUID id = UUID.randomUUID();
        service.setParticle(id, Particle.FLAME);
        assertThat(service.getParticle(id)).isEqualTo(Particle.FLAME);
    }

    @Test
    void setParticle_doesNotToggleEnabled() {
        UUID id = UUID.randomUUID();
        service.setParticle(id, Particle.FLAME);
        // Setting particle alone must not enable effects
        assertThat(service.isEnabled(id)).isFalse();
    }

    @Test
    void removePlayer_clearsState() {
        UUID id = UUID.randomUUID();
        service.toggle(id); // creates state
        service.removePlayer(id);
        assertThat(service.isEnabled(id)).isFalse();
        assertThat(service.getParticle(id)).isEqualTo(Particle.HAPPY_VILLAGER);
    }

    @Test
    void updateConfig_changesReturnedConfig() {
        EffectsConfig newConfig = new EffectsConfig(Particle.FLAME, 5, 0.1, 0.1, 0.1, false);
        service.updateConfig(newConfig);
        assertThat(service.getConfig()).isEqualTo(newConfig);
    }

    @Test
    void multiplePlayersHaveIndependentState() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        service.toggle(a);
        assertThat(service.isEnabled(a)).isTrue();
        assertThat(service.isEnabled(b)).isFalse();
    }
}
