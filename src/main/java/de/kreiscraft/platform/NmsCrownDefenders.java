package de.kreiscraft.platform;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.EnumSet;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.monster.Guardian;
import org.bukkit.craftbukkit.entity.CraftGuardian;
import org.bukkit.craftbukkit.entity.CraftMob;

/** Version-pinned owned Guardian/Blaze pursuit; attacks and damage remain native. */
public final class NmsCrownDefenders {
    private static final VarHandle NAVIGATION = field("navigation", PathNavigation.class);
    private static final VarHandle MOVE_CONTROL = field("moveControl", MoveControl.class);

    private NmsCrownDefenders() {}

    /** Bukkit's raw Mob target setter does not update the Brute/Breeze Brain attack target. */
    public static void target(org.bukkit.entity.Mob entity, org.bukkit.entity.Player target) {
        entity.setTarget(target);
        var brain = ((CraftMob) entity).getHandle().getBrain();
        if (target == null) {
            brain.eraseMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.ATTACK_TARGET);
            brain.eraseMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.WALK_TARGET);
            brain.eraseMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.LOOK_TARGET);
        } else {
            brain.setMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.ATTACK_TARGET,
                    ((org.bukkit.craftbukkit.entity.CraftPlayer) target).getHandle());
        }
        brain.eraseMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.ANGRY_AT);
    }

    private static VarHandle field(String name, Class<?> type) {
        try {
            return MethodHandles.privateLookupIn(Mob.class, MethodHandles.lookup()).findVarHandle(Mob.class, name,
                    type);
        } catch (ReflectiveOperationException error) {
            throw new ExceptionInInitializerError(error);
        }
    }

    public static void prepare(org.bukkit.entity.Mob entity) {
        Mob mob = ((CraftMob) entity).getHandle();
        if (entity instanceof org.bukkit.entity.Blaze) {
            // Enlarged acquisition range must not make native Blazes stand still shooting across the arena.
            mob.getGoalSelector().addGoal(3, approach(mob, 1));
            return;
        }
        Guardian guardian = ((CraftGuardian) entity).getHandle();
        NAVIGATION.set(guardian, new GroundPathNavigation(guardian, guardian.level()) {
            @Override
            protected boolean canUpdatePath() {
                // Native dry Guardians hop; the ground route must still update during their short airborne intervals.
                return true;
            }
        });
        MOVE_CONTROL.set(guardian, new MoveControl<>(guardian) {
            @Override
            public void tick() {
                boolean walking = operation == Operation.MOVE_TO && !guardian.getNavigation().isDone();
                var direction = new net.minecraft.world.phys.Vec3(wantedX - guardian.getX(), 0,
                        wantedZ - guardian.getZ());
                super.tick();
                if (!guardian.isInWater()) {
                    // Steer the native hop along its ground path, not the random horizontal flop from aiStep.
                    var horizontal = walking ? direction.normalize().scale(guardian.getSpeed() * 0.4)
                            : net.minecraft.world.phys.Vec3.ZERO;
                    guardian.setDeltaMovement(horizontal.x, guardian.getDeltaMovement().y, horizontal.z);
                }
            }
        });
        guardian.getGoalSelector().removeGoal(guardian.guardianAttackGoal);
        guardian.getGoalSelector().addGoal(4, new Guardian.GuardianAttackGoal(guardian) {
            @Override
            public boolean canUse() {
                return super.canUse() && guardian.distanceToSqr(guardian.getTarget()) <= 256
                        && guardian.hasLineOfSight(guardian.getTarget());
            }
        });
        guardian.getGoalSelector().addGoal(3, approach(guardian, 0.6));
    }

    private static Goal approach(Mob mob, double speed) {
        return new Goal() {
            {
                setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
            }

            @Override
            public boolean canUse() {
                var target = mob.getTarget();
                return target != null && target.isAlive()
                        && (mob.distanceToSqr(target) > 256 || !mob.hasLineOfSight(target));
            }

            @Override
            public boolean requiresUpdateEveryTick() {
                return true;
            }

            @Override
            public void tick() {
                var target = mob.getTarget();
                if (target != null) {
                    mob.getLookControl().setLookAt(target, 90, 90);
                    mob.getNavigation().moveTo(target, speed);
                }
            }

            @Override
            public void stop() {
                mob.getNavigation().stop();
            }
        };
    }
}
