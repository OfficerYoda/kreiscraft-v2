package de.kreiscraft.platform;

import com.mojang.authlib.GameProfile;

/**
 * Abstraction over the vanilla whitelist backend for Bedrock players.
 * Implemented by {@link NmsWhitelist} in production; mocked in tests so that
 * {@link de.kreiscraft.shared.whitelist.PaperWhitelistService} can be tested
 * without dragging NMS types onto the test classpath.
 */
public interface BedrockWhitelist {

    boolean isWhitelisted(GameProfile profile);

    /** No-op if already whitelisted. */
    void add(GameProfile profile);

    /** No-op if not whitelisted. */
    void remove(GameProfile profile);
}
