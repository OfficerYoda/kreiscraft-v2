package de.kreiscraft.modules.status;

public record Status(String prefix, String suffix, int color) {

    /** Default status applied when no player-specific entry exists. */
    public static Status defaults() {
        return new Status("<gray>Player</gray>", "", 0xAAAAAA);
    }
}
