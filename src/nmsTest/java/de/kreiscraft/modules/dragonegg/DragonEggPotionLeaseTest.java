package de.kreiscraft.modules.dragonegg;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Checks the native hidden-effect contract that MockBukkit's priority queue does not reproduce. */
class DragonEggPotionLeaseTest {
    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void shortHasteTwoLeaseRestoresWeakerExternalHasteWithItsRemainingDuration() {
        var effect = new MobEffectInstance(MobEffects.HASTE, 200, 0);
        effect.update(new MobEffectInstance(MobEffects.HASTE, 10, 1, true, false, false));
        assertThat(effect.hiddenEffect).isNotNull();
        var level = mock(ServerLevel.class);
        var entity = mock(LivingEntity.class);
        for (int tick = 0; tick < 10; tick++) {
            effect.tickServer(level, entity, () -> {
            });
        }
        assertThat(effect.getAmplifier()).isZero();
        assertThat(effect.getDuration()).isEqualTo(190);
    }

    @Test
    void strongerExternalEffectDoesNotResurrectHiddenEggLeaseAfterItExpires() {
        var effect = new MobEffectInstance(MobEffects.HASTE, 10, 1, true, false, false);
        effect.update(new MobEffectInstance(MobEffects.HASTE, 5, 2));
        assertThat(effect.hiddenEffect).isNotNull();
        var level = mock(ServerLevel.class);
        var entity = mock(LivingEntity.class);
        for (int tick = 0; tick < 10; tick++) {
            effect.tickServer(level, entity, () -> {
            });
        }
        assertThat(effect.getDuration()).isZero();
        assertThat(effect.tickServer(level, entity, () -> {
        })).isFalse();
    }
}
