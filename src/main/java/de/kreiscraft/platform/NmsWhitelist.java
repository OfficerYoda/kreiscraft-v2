package de.kreiscraft.platform;

import com.mojang.authlib.GameProfile;
import java.util.UUID;
import net.minecraft.server.players.NameAndId;
import net.minecraft.server.players.UserWhiteList;
import net.minecraft.server.players.UserWhiteListEntry;
import org.bukkit.craftbukkit.CraftServer;

/**
 * Direct NMS access to the vanilla whitelist. Used for Bedrock players
 * (name prefix "."), whose synthetic UUIDs cannot be resolved by Mojang's
 * profile service and therefore cannot go through the Bukkit
 * {@code OfflinePlayer#setWhitelisted} path.
 *
 * <p>Not unit-tested (NMS surface).
 */
public final class NmsWhitelist {

    private final PaperPlatform platform;

    public NmsWhitelist(PaperPlatform platform) {
        this.platform = platform;
    }

    public static GameProfile profileOf(UUID uuid, String name) {
        return new GameProfile(uuid, name);
    }

    public boolean isWhitelisted(GameProfile profile) {
        return whitelist().isWhiteListed(new NameAndId(profile));
    }

    public void add(GameProfile profile) {
        UserWhiteList list = whitelist();
        NameAndId key = new NameAndId(profile);
        if (list.isWhiteListed(key)) {
            return;
        }
        list.add(new UserWhiteListEntry(key));
    }

    public void remove(GameProfile profile) {
        UserWhiteList list = whitelist();
        NameAndId key = new NameAndId(profile);
        if (!list.isWhiteListed(key)) {
            return;
        }
        list.remove(key);
    }

    private UserWhiteList whitelist() {
        return ((CraftServer) platform.server()).getServer().getPlayerList().getWhiteList();
    }
}
