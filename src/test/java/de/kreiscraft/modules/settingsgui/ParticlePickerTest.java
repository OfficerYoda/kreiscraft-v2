package de.kreiscraft.modules.settingsgui;

import static org.assertj.core.api.Assertions.assertThat;

import org.bukkit.Particle;
import org.junit.jupiter.api.Test;

class ParticlePickerTest {

    @Test
    void searchFiltersParticleNamesCaseInsensitively() {
        assertThat(ParticlePicker.particles("SoUl_FiRe"))
                .contains(Particle.SOUL_FIRE_FLAME)
                .allMatch(particle -> particle.name().contains("SOUL_FIRE"));
    }

    @Test
    void choicesCanBeEmittedWithoutParticleData() {
        assertThat(ParticlePicker.particles(""))
                .isNotEmpty()
                .allMatch(particle -> particle.getDataType() == Void.class)
                .doesNotContain(Particle.DUST, Particle.BLOCK);
    }
}
