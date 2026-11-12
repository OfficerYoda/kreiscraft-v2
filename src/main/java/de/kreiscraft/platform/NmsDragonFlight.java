package de.kreiscraft.platform;

import java.lang.reflect.Field;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.boss.enderdragon.phases.DragonChargePlayerPhase;
import net.minecraft.world.entity.boss.enderdragon.phases.DragonPhaseInstance;
import net.minecraft.world.entity.boss.enderdragon.phases.EnderDragonPhase;
import net.minecraft.world.entity.boss.enderdragon.phases.EnderDragonPhaseManager;
import net.minecraft.world.phys.Vec3;
import org.bukkit.Location;
import org.bukkit.craftbukkit.entity.CraftEnderDragon;
import org.bukkit.entity.EnderDragon;

/** Paper 26.3 boundary: replace one dragon's charge instance, never the global phase registry. */
public final class NmsDragonFlight implements DragonFlightControl {
    private final CraftEnderDragon entity;
    private final net.minecraft.world.entity.boss.enderdragon.EnderDragon dragon;
    private final EnderDragonPhaseManager manager;
    private final Field current;
    private final DragonPhaseInstance[] slots;
    private final DragonPhaseInstance originalSlot;
    private final EnderDragonPhase<?> originalPhase;
    private final ControlledCharge controlled;
    private boolean closed;

    public NmsDragonFlight(EnderDragon entity, Runnable tick) {
        if (!(entity instanceof CraftEnderDragon craft) || !entity.hasAI() || !entity.isValid() || entity.isDead()) {
            throw new IllegalStateException("Native flight needs a live AI-enabled Paper dragon");
        }
        this.entity = craft;
        dragon = craft.getHandle();
        manager = dragon.getPhaseManager();
        try {
            Field cache = EnderDragonPhaseManager.class.getDeclaredField("phases");
            current = EnderDragonPhaseManager.class.getDeclaredField("currentPhase");
            if (cache.getType() != DragonPhaseInstance[].class || current.getType() != DragonPhaseInstance.class) {
                throw new IllegalStateException("Unsupported Paper dragon phase layout");
            }
            cache.setAccessible(true);
            current.setAccessible(true);
            slots = (DragonPhaseInstance[]) cache.get(manager);
            if (slots.length != EnderDragonPhase.getCount()) {
                throw new IllegalStateException("Unsupported Paper dragon phase cache");
            }
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Unsupported Paper dragon phase layout", error);
        }
        originalPhase = manager.getCurrentPhase().getPhase();
        originalSlot = manager.getPhase(EnderDragonPhase.CHARGING_PLAYER);
        if (originalSlot instanceof ControlledCharge) {
            throw new IllegalStateException("Dragon already has a controlled flight owner");
        }
        controlled = new ControlledCharge(dragon, tick);
        slots[EnderDragonPhase.CHARGING_PLAYER.getId()] = controlled;
        try {
            if (originalPhase == EnderDragonPhase.CHARGING_PLAYER) {
                originalSlot.end();
                current.set(manager, controlled);
                controlled.begin();
            } else {
                manager.setPhase(EnderDragonPhase.CHARGING_PLAYER);
            }
            if (!active()) {
                throw new IllegalStateException("Native charge phase was refused or redirected");
            }
        } catch (RuntimeException | IllegalAccessException error) {
            close();
            throw new IllegalStateException("Could not acquire dragon flight", error);
        }
    }

    @Override
    public boolean active() {
        return !closed && manager.getCurrentPhase() == controlled && entity.hasAI() && entity.isValid()
                && !entity.isDead();
    }

    @Override
    public void move(Location destination) {
        if (!active() || !entity.getWorld().equals(destination.getWorld())) {
            throw new IllegalStateException("Lost native dragon flight ownership");
        }
        destination.checkFinite();
        var delta = new Vec3(destination.getX(), destination.getY(), destination.getZ()).subtract(dragon.position());
        if (delta.lengthSqr() > 64) {
            throw new IllegalArgumentException("Scripted dragon step exceeds eight blocks");
        }
        dragon.setDeltaMovement(delta);
        dragon.setYRot(destination.getYaw());
        dragon.setXRot(destination.getPitch());
        dragon.move(MoverType.SELF, delta);
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        controlled.stopped = true;
        if (slots[EnderDragonPhase.CHARGING_PLAYER.getId()] == controlled) {
            slots[EnderDragonPhase.CHARGING_PLAYER.getId()] = originalSlot;
        }
        if (manager.getCurrentPhase() != controlled) {
            return; // A later owner or native DYING phase must keep control.
        }
        EnderDragonPhase<?> restore = originalPhase == EnderDragonPhase.DYING
                ? EnderDragonPhase.HOLDING_PATTERN
                : originalPhase;
        try {
            manager.setPhase(restore);
        } finally {
            // Same-ID requests and cancelled restoration must never leave a dead callback installed.
            if (manager.getCurrentPhase() == controlled) {
                try {
                    current.set(manager, originalSlot);
                    if (originalPhase != EnderDragonPhase.CHARGING_PLAYER) {
                        originalSlot.begin();
                        ((DragonChargePlayerPhase) originalSlot).setTarget(dragon.position().add(0, 12, 0));
                    }
                } catch (IllegalAccessException error) {
                    throw new IllegalStateException("Could not restore native dragon charge", error);
                }
            }
            dragon.setDeltaMovement(Vec3.ZERO);
        }
    }

    private static final class ControlledCharge extends DragonChargePlayerPhase {
        private final Runnable tick;
        private boolean stopped;

        private ControlledCharge(net.minecraft.world.entity.boss.enderdragon.EnderDragon dragon, Runnable tick) {
            super(dragon);
            this.tick = tick;
        }

        @Override
        public void doServerTick(ServerLevel level) {
            if (!stopped) {
                tick.run();
            }
        }

        @Override
        public Vec3 getFlyTargetLocation() {
            return null; // Exact movement already happened above; no second steering/movement pass.
        }
    }
}
