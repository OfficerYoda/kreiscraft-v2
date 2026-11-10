package de.kreiscraft.modules.shatteredcrown;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.random.RandomGenerator;

/** One shuffled round; rejected setups consume a turn rather than starving the other families. */
final class HuntRotation {
    private final RandomGenerator random;
    private final List<EncounterController.AttackFamily> remaining = new ArrayList<>();
    private final Set<EncounterController.AttackFamily> round = EnumSet.noneOf(EncounterController.AttackFamily.class);
    private EncounterController.AttackFamily last;
    private long nextWarning;

    HuntRotation(RandomGenerator random) {
        this.random = random;
    }

    EncounterController.AttackFamily next(Set<EncounterController.AttackFamily> available) {
        remaining.removeIf(attack -> !available.contains(attack));
        var added = new ArrayList<>(available.stream().filter(attack -> !round.contains(attack)).sorted().toList());
        shuffle(added);
        remaining.addAll(added); // New unlocks join the unfinished round exactly once.
        round.addAll(available);
        if (remaining.isEmpty()) {
            remaining.addAll(available.stream().sorted().toList());
            shuffle(remaining);
            round.clear();
            round.addAll(available);
        }
        if (remaining.size() > 1 && remaining.getFirst() == last) {
            var first = remaining.removeFirst();
            remaining.add(first);
        }
        last = remaining.removeFirst();
        return last;
    }

    private void shuffle(List<EncounterController.AttackFamily> attacks) {
        for (int index = attacks.size() - 1; index > 0; index--) {
            int other = random.nextInt(index + 1);
            var saved = attacks.get(index);
            attacks.set(index, attacks.get(other));
            attacks.set(other, saved);
        }
    }

    void completed(long tick, int minimumTicks, int maximumTicks) {
        nextWarning = tick + random.nextInt(minimumTicks, maximumTicks + 1);
    }

    boolean ready(long tick) {
        return tick >= nextWarning;
    }

    String queued() {
        return remaining.toString();
    }

    long remainingTicks(long tick) {
        return Math.max(0, nextWarning - tick);
    }
}
