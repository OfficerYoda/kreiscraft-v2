package de.kreiscraft.modules.startsequence;

import java.time.Duration;
import org.spongepowered.configurate.objectmapping.ConfigSerializable;
import org.spongepowered.configurate.objectmapping.meta.Setting;

@ConfigSerializable
public record StartSequenceConfig(Duration countdown, Double initialBorderSize, Double targetBorderSize,
        @Setting("phase-1-duration") Duration phase1Duration, @Setting("phase-2-duration") Duration phase2Duration,
        @Setting("phase-1-to-2-delay") Duration phase1To2Delay,
        @Setting("phase-2-to-instant-delay") Duration phase2ToInstantDelay) {

    public StartSequenceConfig {
        if (countdown == null)
            countdown = Duration.ofSeconds(10);
        if (initialBorderSize == null)
            initialBorderSize = 50.0;
        if (targetBorderSize == null)
            targetBorderSize = 30_000_000.0;
        if (phase1Duration == null)
            phase1Duration = Duration.ofSeconds(550_000);
        if (phase2Duration == null)
            phase2Duration = Duration.ofSeconds(300_000);
        if (phase1To2Delay == null)
            phase1To2Delay = Duration.ofMillis(7_500);
        if (phase2ToInstantDelay == null)
            phase2ToInstantDelay = Duration.ofMillis(15_000);
    }
}
