package de.kreiscraft.modules.anticheat;

import java.util.List;
import java.util.UUID;

public interface HackLogService {

    void record(HackEntry entry);

    List<HackEntry> forPlayer(UUID uuid, HackEntry.Kind kind);
}
