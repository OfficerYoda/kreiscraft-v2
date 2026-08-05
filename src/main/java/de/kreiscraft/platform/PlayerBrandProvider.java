package de.kreiscraft.platform;

import java.util.Optional;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;

/**
 * Reads the client-reported "brand" (vanilla, fabric, forge, plugin brand)
 * of an online player via NMS. Ports the legacy
 * {@code ((CraftPlayer) e.getPlayer()).getHandle().connection.playerBrand}
 * one-liner from the pre-refactor JoinQuitListener into a testable seam.
 *
 * <p>Not unit-tested (NMS surface).
 */
public final class PlayerBrandProvider {

    @SuppressWarnings("unused") // kept for uniformity + future needs
    private final PaperPlatform platform;

    public PlayerBrandProvider(PaperPlatform platform) {
        this.platform = platform;
    }

    /**
     * @return the brand string reported by the player's client during login,
     *         or empty if the connection is not yet ready or the field is null.
     */
    public Optional<String> brandOf(Player player) {
        if (!(player instanceof CraftPlayer craft)) {
            return Optional.empty();
        }
        var handle = craft.getHandle();
        if (handle == null || handle.connection == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(handle.connection.playerBrand);
    }
}
