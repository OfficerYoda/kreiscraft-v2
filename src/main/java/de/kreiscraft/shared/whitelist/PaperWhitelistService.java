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
        // A cached Bedrock profile is authoritative regardless of the configured
        // Bedrock prefix, so dispatch on the cache before falling back to the
        // conventional "." check.
        GameProfile cached = bedrockProfiles.get(name.toLowerCase());
        if (cached != null) {
            return nms.isWhitelisted(cached);
        }
        if (isBedrockName(name)) {
            return false;
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
        // Revoke a cached Bedrock profile first so removal stays symmetric with
        // addBedrockPlayer for any configured Bedrock prefix, not just ".".
        GameProfile profile = bedrockProfiles.remove(name.toLowerCase());
        if (profile != null) {
            nms.remove(profile);
            return;
        }
        if (isBedrockName(name)) {
            return;
        }
        OfflinePlayer offline = platform.server().getOfflinePlayer(name);
        offline.setWhitelisted(false);
    }

    @Override
    public Collection<String> allNames() {
        List<String> out = new ArrayList<>();
        for (OfflinePlayer p : platform.server().getWhitelistedPlayers()) {
            if (p.getName() != null) {
                out.add(p.getName());
            }
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
