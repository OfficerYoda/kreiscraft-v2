package de.kreiscraft.shared.whitelist;

import com.mojang.authlib.GameProfile;
import de.kreiscraft.platform.BedrockWhitelist;
import de.kreiscraft.platform.NmsWhitelist;
import de.kreiscraft.platform.PaperPlatform;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.OfflinePlayer;

public final class PaperWhitelistService implements WhitelistService {

    private final PaperPlatform platform;
    private final BedrockWhitelist nms;
    /** Bedrock name → GameProfile cache; keyed by lowercase name. */
    private final Map<String, GameProfile> bedrockProfiles = new LinkedHashMap<>();

    public PaperWhitelistService(PaperPlatform platform, BedrockWhitelist nms) {
        this.platform = platform;
        this.nms = nms;
    }

    @Override
    public boolean isWhitelisted(String name) {
        if (isBedrockName(name)) {
            GameProfile profile = bedrockProfiles.get(name.toLowerCase());
            return profile != null && nms.isWhitelisted(profile);
        }
        OfflinePlayer offline = platform.server().getOfflinePlayer(name);
        return offline.isWhitelisted();
    }

    @Override
    public void addJavaPlayer(String name) {
        OfflinePlayer offline = platform.server().getOfflinePlayer(name);
        offline.setWhitelisted(true);
    }

    @Override
    public void addBedrockPlayer(String name, UUID uuid) {
        GameProfile profile = NmsWhitelist.profileOf(uuid, name);
        bedrockProfiles.put(name.toLowerCase(), profile);
        nms.add(profile);
    }

    @Override
    public void remove(String name) {
        if (isBedrockName(name)) {
            GameProfile profile = bedrockProfiles.remove(name.toLowerCase());
            if (profile != null) {
                nms.remove(profile);
            }
            return;
        }
        OfflinePlayer offline = platform.server().getOfflinePlayer(name);
        offline.setWhitelisted(false);
    }

    @Override
    public Collection<String> allNames() {
        List<String> out = new ArrayList<>();
        for (OfflinePlayer p : platform.server().getWhitelistedPlayers()) {
            if (p.getName() != null)
                out.add(p.getName());
        }
        for (GameProfile p : bedrockProfiles.values()) {
            out.add(p.name());
        }
        return out;
    }

    private static boolean isBedrockName(String name) {
        return name != null && name.startsWith(".");
    }
}
