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
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({"EASY,31.333333333333336", "NORMAL,16.666666666666668",
            "HARD,11.111111111111112"})
    void referenceBoundarySuppliesDifficultyCompensatedRawUnits(org.bukkit.Difficulty difficulty, double raw) {
        MockBukkit.mock();
        try {
            Player player = mock(Player.class);
            var world = mock(org.bukkit.World.class);
            org.mockito.Mockito.when(player.getWorld()).thenReturn(world);
            org.mockito.Mockito.when(world.getDifficulty()).thenReturn(difficulty);
            EnderDragon dragon = mock(EnderDragon.class);
            DragonBattleAccess access = ignored -> null;
            access.referenceDamage(player, dragon, 6);
            verify(player).damage(eq(raw), org.mockito.ArgumentMatchers.any(DamageSource.class));
            // Units/dispatch contract only: DragonDamageProbe asserts actual native health loss.
        } finally {
            MockBukkit.unmock();
        }
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(doubles = {-1, 101, Double.NaN, Double.POSITIVE_INFINITY})
    void referenceHealthTargetsRejectInvalidInput(double target) {
        DragonBattleAccess access = world -> null;
        org.assertj.core.api.Assertions.assertThatIllegalArgumentException()
                .isThrownBy(() -> access.referenceDamage(mock(Player.class), mock(EnderDragon.class), target));
    }

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
