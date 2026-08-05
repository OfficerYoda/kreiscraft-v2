package de.kreiscraft.shared.whitelist;

import java.util.Collection;
import java.util.UUID;

/**
 * Unified whitelist facade. Java players (normal Mojang accounts) go
 * through the Bukkit API; Bedrock players (name prefix ".") go through
 * {@link de.kreiscraft.platform.BedrockWhitelist} because their synthetic
 * UUIDs cannot be resolved by the Mojang profile service.
 */
public interface WhitelistService {

    boolean isWhitelisted(String name);

    void addJavaPlayer(String name);

    void addBedrockPlayer(String name, UUID uuid);

    void remove(String name);

    Collection<String> allNames();
}
