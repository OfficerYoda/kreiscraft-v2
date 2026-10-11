package de.kreiscraft.modules.effects;

import static org.assertj.core.api.Assertions.assertThat;

import de.kreiscraft.testsupport.InMemoryPlayerPreferencesService;
import java.util.UUID;
import org.bukkit.Particle;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DefaultEffectsServiceTest {

    private InMemoryPlayerPreferencesService preferences;
    private EffectsConfig config;

    @BeforeEach
    void setUp() {
        preferences = new InMemoryPlayerPreferencesService();
        config = new EffectsConfig(Particle.HAPPY_VILLAGER, null, 25, 0.9, 0.9, 0.9, true);
    }

    private DefaultEffectsService service() {
        return new DefaultEffectsService(preferences, () -> config, true);
    }

    @Test
    void isEnabled_whenNoState_returnsModuleDefault() {
        assertThat(service().isEnabled(UUID.randomUUID())).isTrue();
    }

    @Test
    void isEnabled_whenModuleDefaultDisabled_returnsFalse() {
        DefaultEffectsService svc =
                new DefaultEffectsService(preferences, () -> config, false);
        assertThat(svc.isEnabled(UUID.randomUUID())).isFalse();
    }

    @Test
    void toggle_whenNoState_persistsDisabled() {
        UUID id = UUID.randomUUID();
        DefaultEffectsService svc = service();

        svc.toggle(id);

        assertThat(svc.isEnabled(id)).isFalse();
        assertThat(new DefaultEffectsService(preferences, () -> config, true).isEnabled(id)).isFalse();
    }

    @Test
    void toggle_twice_returnsToEnabled() {
        UUID id = UUID.randomUUID();
        DefaultEffectsService svc = service();

        svc.toggle(id);
        svc.toggle(id);

        assertThat(svc.isEnabled(id)).isTrue();
    }

    @Test
    void toggle_flipsFromModuleDefault() {
        UUID id = UUID.randomUUID();
        DefaultEffectsService svc = service();

        assertThat(svc.isEnabled(id)).isTrue();
        svc.toggle(id);
        assertThat(svc.isEnabled(id)).isFalse();
        svc.toggle(id);
        assertThat(svc.isEnabled(id)).isTrue();
    }

    @Test
    void getParticle_whenNoState_returnsConfiguredDefault() {
        assertThat(service().getParticle(UUID.randomUUID()))
                .isEqualTo(Particle.HAPPY_VILLAGER);
    }

    @Test
    void selectedParticle_whenNoState_isEmpty() {
        assertThat(service().selectedParticle(UUID.randomUUID())).isEmpty();
    }

    @Test
    void setParticle_overridesAndPersists() {
        UUID id = UUID.randomUUID();
        service().setParticle(id, Particle.FLAME);

        assertThat(service().getParticle(id)).isEqualTo(Particle.FLAME);
        assertThat(new DefaultEffectsService(preferences, () -> config, true).getParticle(id))
                .isEqualTo(Particle.FLAME);
        assertThat(new DefaultEffectsService(preferences, () -> config, true).selectedParticle(id))
                .contains(Particle.FLAME);
    }

    @Test
    void setParticle_enablesEffectsImmediately() {
        UUID id = UUID.randomUUID();
        DefaultEffectsService svc =
                new DefaultEffectsService(preferences, () -> config, false);

        svc.setParticle(id, Particle.FLAME);

        assertThat(svc.isEnabled(id)).isTrue();
    }

    @Test
    void stateAndParticle_surviveReconnect() {
        UUID id = UUID.randomUUID();
        DefaultEffectsService first = service();
        first.toggle(id);
        first.setParticle(id, Particle.HEART);

        DefaultEffectsService afterReconnect = service();

        assertThat(afterReconnect.isEnabled(id)).isTrue();
        assertThat(afterReconnect.getParticle(id)).isEqualTo(Particle.HEART);
    }

    @Test
    void configSwap_observableByExistingService() {
        DefaultEffectsService svc = service();
        EffectsConfig newConfig =
                new EffectsConfig(Particle.FLAME, null, 5, 0.1, 0.1, 0.1, false);

        config = newConfig;

        assertThat(svc.getParticle(UUID.randomUUID())).isEqualTo(Particle.FLAME);
    }

    @Test
    void getParticle_afterConfigUpdate_usesNewDefault() {
        UUID id = UUID.randomUUID();
        DefaultEffectsService svc = service();
        config = new EffectsConfig(Particle.LAVA, null, 5, 0.1, 0.1, 0.1, false);

        assertThat(svc.getParticle(id)).isEqualTo(Particle.LAVA);
    }

    @Test
    void multiplePlayersHaveIndependentState() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        DefaultEffectsService svc = service();

        svc.toggle(a);

        assertThat(svc.isEnabled(a)).isFalse();
        assertThat(svc.isEnabled(b)).isTrue();
    }
}
