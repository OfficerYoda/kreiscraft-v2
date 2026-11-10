package de.kreiscraft.modules.shatteredcrown;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentMatchers.eq;

import org.bukkit.damage.DamageSource;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockito.ArgumentCaptor;

class DragonDamageTest {
    @Test
    void productionDamageSuppliesBothEntitiesRequiredByPaper() {
        MockBukkit.mock();
        try {
            Player player = mock(Player.class);
            EnderDragon dragon = mock(EnderDragon.class);
            DragonBattleAccess access = world -> null;
            access.bombardmentDamage(player, dragon, 6);
            var source = ArgumentCaptor.forClass(DamageSource.class);
            verify(player).damage(eq(6.0), source.capture());
            assertThat(source.getValue().getDirectEntity()).isSameAs(dragon);
            assertThat(source.getValue().getCausingEntity()).isSameAs(dragon);
        } finally {
            MockBukkit.unmock();
        }
    }
}
