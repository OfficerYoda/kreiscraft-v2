package de.kreiscraft.modules.worldmgmt;

/** Reads and updates whether portal travel into the Nether and End is allowed. */
public interface DimensionAccessService {

    /** Whether portal travel into {@code dimension} is currently allowed. */
    boolean isAllowed(Dimension dimension);

    /**
     * Persists a new access value and applies it immediately.
     *
     * @return {@code true} when the value was saved, {@code false} when persistence failed
     */
    boolean setAllowed(Dimension dimension, boolean allowed);
}
