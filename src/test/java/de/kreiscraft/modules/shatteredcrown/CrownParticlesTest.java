package de.kreiscraft.modules.shatteredcrown;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;

class CrownParticlesTest {
    @Test
    void purpleDustUsesAColoredPayloadAtTheFlamePosition() {
        Player player = mock(Player.class);
        Location location = new Location(null, 10, 80, 0);
        CrownParticles.spawn(player, Particle.DUST, location, 2, 0.15, 0.15, 0.15, 0);
        var data = ArgumentCaptor.forClass(Particle.DustOptions.class);
        verify(player).spawnParticle(ArgumentMatchers.eq(Particle.DUST), ArgumentMatchers.same(location),
                ArgumentMatchers.eq(2), ArgumentMatchers.eq(0.15), ArgumentMatchers.eq(0.15),
                ArgumentMatchers.eq(0.15), ArgumentMatchers.eq(0.0), data.capture());
        assertThat(data.getValue().getColor()).isEqualTo(Color.fromRGB(170, 40, 220));
        assertThat(data.getValue().getSize()).isEqualTo(1);
    }
}
