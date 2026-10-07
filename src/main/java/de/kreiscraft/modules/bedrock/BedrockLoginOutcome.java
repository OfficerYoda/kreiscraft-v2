package de.kreiscraft.modules.bedrock;

/** Outcome of matching a Bedrock login against the registered Pending Bedrock Authorizations. */
public enum BedrockLoginOutcome {

    /** No authorization matched the observed name; the login was only recorded. */
    NOT_AUTHORIZED,

    /** An unbound authorization matched and was bound to the observed UUID. */
    BOUND,

    /** A bound authorization matched the same UUID; nothing changed. */
    ALREADY_BOUND,

    /**
     * A bound authorization matched a different UUID. The authorization was left bound to its
     * original UUID; a replacement requires explicit removal and re-registration.
     */
    CONFLICT
}
