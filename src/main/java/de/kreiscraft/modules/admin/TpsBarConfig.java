package de.kreiscraft.modules.admin;

import org.spongepowered.configurate.objectmapping.ConfigSerializable;

/** Color thresholds and MSPT fill endpoints, all hot-reloadable Server Configuration. */
@ConfigSerializable
public record TpsBarConfig(
        Double msptGreenMax, Double msptYellowMax,
        Double tpsGreenMin, Double tpsYellowMin,
        Integer pingGreenMax, Integer pingYellowMax,
        Double msptEmpty, Double msptFull) {

    public TpsBarConfig {
        msptGreenMax = msptGreenMax == null ? 40.0 : msptGreenMax;
        msptYellowMax = msptYellowMax == null ? 50.0 : msptYellowMax;
        tpsGreenMin = tpsGreenMin == null ? 19.0 : tpsGreenMin;
        tpsYellowMin = tpsYellowMin == null ? 15.0 : tpsYellowMin;
        pingGreenMax = pingGreenMax == null ? 50 : pingGreenMax;
        pingYellowMax = pingYellowMax == null ? 120 : pingYellowMax;
        msptEmpty = msptEmpty == null ? 0.0 : msptEmpty;
        msptFull = msptFull == null ? 50.0 : msptFull;
        requireRange(msptGreenMax, 0.0, 100_000.0);
        requireRange(msptYellowMax, 0.0, 100_000.0);
        requireRange(tpsGreenMin, 0.0, 20.0);
        requireRange(tpsYellowMin, 0.0, 20.0);
        requireRange(pingGreenMax, 0.0, 100_000.0);
        requireRange(pingYellowMax, 0.0, 100_000.0);
        requireRange(msptEmpty, 0.0, 100_000.0);
        requireRange(msptFull, 0.1, 100_000.0);
        if (msptGreenMax > msptYellowMax || tpsYellowMin > tpsGreenMin || pingGreenMax > pingYellowMax) {
            throw new IllegalArgumentException("green and yellow TPS bar thresholds must be ordered");
        }
        if (msptEmpty >= msptFull) {
            throw new IllegalArgumentException("MSPT empty must be less than MSPT full");
        }
    }

    public TpsBarConfig() {
        this(null, null, null, null, null, null, null, null);
    }

    private static void requireRange(double value, double min, double max) {
        if (!Double.isFinite(value) || value < min || value > max) {
            throw new IllegalArgumentException("TPS bar value must be between " + min + " and " + max);
        }
    }
}
