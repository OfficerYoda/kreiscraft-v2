package de.kreiscraft.platform;

import java.util.concurrent.atomic.AtomicInteger;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.entity.EnderDragon;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityTeleportEvent;
import org.bukkit.plugin.java.JavaPlugin;

/** Run only in a disposable JVM with -Dkreiscraft.dragon-probe=true; exits after reporting. */
public final class DragonFlightProbe extends JavaPlugin implements Listener {
    private EnderDragon dragon;
    private DragonFlightControl control;
    private int teleports;
    private boolean done;

    @Override
    public void onEnable() {
        if (!Boolean.getBoolean("kreiscraft.dragon-probe")) {
            getLogger().severe("Probe requires a disposable server with -Dkreiscraft.dragon-probe=true");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        getServer().getPluginManager().registerEvents(this, this);
        getServer().getScheduler().runTask(this, this::start);
        getServer().getScheduler().runTaskLater(this, () -> finish(false, "native tick timeout"), 600);
    }

    private void start() {
        try {
            World world = new WorldCreator("flight_probe_end").environment(World.Environment.THE_END).createWorld();
            world.setChunkForceLoaded(0, 0, true);
            world.setChunkForceLoaded(1, 0, true);
            dragon = world.spawn(new Location(world, 4, 110, 4), EnderDragon.class);
            dragon.setPhase(EnderDragon.Phase.CIRCLING);
            var handle = ((org.bukkit.craftbukkit.entity.CraftEnderDragon) dragon).getHandle();
            var original = handle.getPhaseManager().getPhase(
                    net.minecraft.world.entity.boss.enderdragon.phases.EnderDragonPhase.CHARGING_PLAYER);
            var ticks = new AtomicInteger();
            Location originalHead = handle.head.getBukkitEntity().getLocation();
            control = new NmsDragonFlight(dragon, () -> {
                int tick = ticks.incrementAndGet();
                // Approach/dive then level off: uses the real dragon tick and ordinary native movement.
                double y = 110 - Math.min(tick, 40) * 0.2;
                control.move(new Location(world, 4 + tick * 0.25, y, 4, -90, 0));
                if (tick == 80) {
                    getServer().getScheduler().runTask(this, () -> {
                        try {
                            if (dragon.getX() < 24 || dragon.getX() > 25 || teleports != 0 || !dragon.hasAI()) {
                                throw new IllegalStateException("position/teleport/AI assertion failed");
                            }
                            Location head = handle.head.getBukkitEntity().getLocation();
                            if (head.distanceSquared(originalHead) < 1
                                    || head.distanceSquared(dragon.getLocation()) > 256) {
                                throw new IllegalStateException("native multipart positions did not follow flight");
                            }
                            if (handle.getPhaseManager().getCurrentPhase().isSitting()) {
                                throw new IllegalStateException("custom phase is not airborne");
                            }
                            control.close();
                            if (handle.getPhaseManager().getPhase(
                                    net.minecraft.world.entity.boss.enderdragon.phases.EnderDragonPhase.CHARGING_PLAYER) != original
                                    || dragon.getPhase() != EnderDragon.Phase.CIRCLING) {
                                throw new IllegalStateException("native phase restoration failed");
                            }
                            finish(true, "ticks=" + ticks.get() + ", position=" + dragon.getLocation()
                                    + ", head=" + head + ", teleports=" + teleports);
                        } catch (RuntimeException error) {
                            finish(false, error.toString());
                        }
                    });
                }
            });
        } catch (RuntimeException error) {
            finish(false, error.toString());
        }
    }

    @EventHandler
    public void onTeleport(EntityTeleportEvent event) {
        if (event.getEntity().equals(dragon)) {
            teleports++;
        }
    }

    private void finish(boolean passed, String detail) {
        if (done) {
            return;
        }
        done = true;
        getLogger().info("DRAGON_FLIGHT_PROBE " + (passed ? "PASS " : "FAIL ") + detail);
        getServer().shutdown();
    }

    @Override
    public void onDisable() {
        if (control != null) {
            control.close();
        }
        if (dragon != null) {
            dragon.remove();
        }
    }
}
