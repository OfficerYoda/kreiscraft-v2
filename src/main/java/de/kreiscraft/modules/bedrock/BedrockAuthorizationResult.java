package de.kreiscraft.modules.bedrock;

/** Outcome of registering or removing a Pending Bedrock Authorization. */
public enum BedrockAuthorizationResult {

    /** A new, unbound authorization was registered. */
    REGISTERED,

    /** An authorization already existed for the normalized name; nothing changed. */
    ALREADY_REGISTERED,

    /** A matching authorization existed and was removed. */
    REMOVED,

    /** No authorization existed for the normalized name; nothing changed. */
    NOT_FOUND
}
