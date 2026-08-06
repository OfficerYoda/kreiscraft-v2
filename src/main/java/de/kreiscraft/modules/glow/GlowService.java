package de.kreiscraft.modules.glow;

import java.util.UUID;

public interface GlowService {

    boolean isGlowing(UUID playerId);

    void toggle(UUID playerId);
}
