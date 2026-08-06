package de.kreiscraft.modules.combat;

import java.time.Duration;
import java.util.UUID;

public interface CombatService {

    boolean isInCombat(UUID uuid);

    Duration remaining(UUID uuid);

    void tag(UUID uuid, Duration duration);

    void clear(UUID uuid);
}
