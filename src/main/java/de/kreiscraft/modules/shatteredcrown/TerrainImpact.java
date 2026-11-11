package de.kreiscraft.modules.shatteredcrown;

import java.time.Duration;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.FallingBlock;
import org.bukkit.util.Vector;

/** Permanent shallow ground removal; only the flying blocks belong to temporary Encounter work. */
final class TerrainImpact {
    private TerrainImpact() {}

    /** Shared Smash/Rain impact. No explosion, damage, restoration or protected-site exclusion. */
    static void apply(Location center, double radius, int depth, double lifetime, EncounterWork owner) {
        if (!Double.isFinite(radius) || radius < 1 || radius > 8 || depth < 1 || depth > 3
                || !Double.isFinite(lifetime) || lifetime < 0.5 || lifetime > 10) {
            throw new IllegalArgumentException("Terrain impact requires radius 1–8, depth 1–3 and lifetime 0.5–10s");
        }
        if (owner.isClosed()) {
            return;
        }
        EncounterWork debris = owner.child(() -> {
        });
        debris.run(() -> {
            debris.later(Duration.ofMillis((long) Math.ceil(lifetime * 1000)), debris::close);
            int launched = 0;
            for (int x = (int) Math.floor(center.getX() - radius); x <= Math.floor(center.getX() + radius); x++) {
                for (int z = (int) Math.floor(center.getZ() - radius); z <= Math.floor(center.getZ() + radius); z++) {
                    double dx = x + 0.5 - center.getX();
                    double dz = z + 0.5 - center.getZ();
                    if (dx * dx + dz * dz > radius * radius) {
                        continue;
                    }
                    Location feet = AttackSurface.column(new Location(center.getWorld(), x + 0.5, center.getY(),
                            z + 0.5), 6, 6);
                    if (feet == null) {
                        continue;
                    }
                    for (int down = 1; down <= depth; down++) {
                        if (owner.isClosed() || debris.isClosed()) {
                            return;
                        }
                        var block = feet.getBlock().getRelative(0, -down, 0);
                        if (block.isEmpty()) {
                            continue;
                        }
                        var data = block.getBlockData();
                        block.setType(Material.AIR, false);
                        // ponytail: at most 128 debris entities per impact; larger tuned craters still remove all ground.
                        if (launched++ >= 128) {
                            continue;
                        }
                        center.getWorld().spawn(block.getLocation().add(0.5, 0.5, 0.5), FallingBlock.class, entity -> {
                            debris.own(entity, "debris");
                            entity.setBlockData(data);
                            entity.setTicksLived(1); // Paper's falling-block clock: do not remove a second source block.
                            entity.setHurtEntities(false);
                            entity.setDropItem(false);
                            entity.setCancelDrop(true); // Native collision discards instead of placing/dropping.
                            entity.setVelocity(new Vector(dx * 0.08, 0.7 + (dx * dx + dz * dz) * 0.008, dz * 0.08));
                        });
                    }
                }
            }
        });
    }
}
