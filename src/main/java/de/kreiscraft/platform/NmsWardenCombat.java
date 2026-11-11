package de.kreiscraft.platform;

import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.craftbukkit.entity.CraftWarden;
import org.bukkit.entity.Player;
import org.bukkit.entity.Warden;

/** Version-pinned brain operation absent from Paper's ordinary Mob target setter. */
public final class NmsWardenCombat {
    private NmsWardenCombat() {}

    public static void target(Warden warden, Player target) {
        var handle = ((CraftWarden) warden).getHandle();
        if (target == null) {
            handle.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
            handle.getBrain().eraseMemory(MemoryModuleType.ROAR_TARGET);
            handle.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        } else {
            warden.setAnger(target, 150);
            handle.setAttackTarget(((CraftPlayer) target).getHandle());
        }
    }
}
