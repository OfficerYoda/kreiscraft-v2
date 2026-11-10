package de.kreiscraft.modules.shatteredcrown;

import java.util.EnumMap;
import java.util.function.BooleanSupplier;
import java.util.function.BiConsumer;
import java.util.function.Predicate;

/** Oldest owed attack wins; failed placement retries without losing its place or starving others. */
final class HuntRotation {
    enum Attack {
        DIVE, PREY, SMASH, LANDING
    }

    private final EnumMap<Attack, Double> cadence = new EnumMap<>(Attack.class);
    private final EnumMap<Attack, Double> due = new EnumMap<>(Attack.class);
    private final EnumMap<Attack, Double> retry = new EnumMap<>(Attack.class);
    private final EnumMap<Attack, Double> reported = new EnumMap<>(Attack.class);
    private final BooleanSupplier busy;
    private final Predicate<Attack> start;
    private final BiConsumer<Attack, String> report;
    private double time;

    HuntRotation(double dive, double prey, double smash, double landing, BooleanSupplier busy,
            Predicate<Attack> start, BiConsumer<Attack, String> report) {
        cadence.put(Attack.DIVE, dive);
        cadence.put(Attack.PREY, prey);
        cadence.put(Attack.SMASH, smash);
        cadence.put(Attack.LANDING, landing);
        due.putAll(cadence);
        this.busy = busy;
        this.start = start;
        this.report = report;
    }

    void tick() {
        time += 0.25;
        Attack chosen = null;
        for (Attack attack : Attack.values()) {
            if (due.get(attack) <= time && retry.getOrDefault(attack, 0.0) <= time
                    && (chosen == null || due.get(attack) < due.get(chosen))) {
                chosen = attack;
            }
        }
        if (chosen == null) {
            return;
        }
        if (busy.getAsBoolean()) {
            diagnostic(chosen, "another attack or landing is active; remains queued");
            return;
        }
        if (start.test(chosen)) {
            due.put(chosen, time + cadence.get(chosen));
            reported.remove(chosen);
        } else {
            retry.put(chosen, time + 1);
            diagnostic(chosen, "no eligible target, terrain/escape route or flight clearance; retry in 1s");
        }
    }

    String queued() {
        return java.util.Arrays.stream(Attack.values()).filter(attack -> due.get(attack) <= time)
                .map(Enum::name).collect(java.util.stream.Collectors.joining(", "));
    }

    private void diagnostic(Attack attack, String reason) {
        if (time - reported.getOrDefault(attack, -30.0) >= 30) {
            reported.put(attack, time);
            report.accept(attack, reason);
        }
    }
}
