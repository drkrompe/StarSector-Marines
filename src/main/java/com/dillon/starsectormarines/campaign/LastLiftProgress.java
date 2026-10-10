package com.dillon.starsectormarines.campaign;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/** Save-owned identity ledger for Rill's twelve workers. No battle entities or runtime callbacks. */
public final class LastLiftProgress implements Serializable {
    public static final int VERSION = 1;
    public static final int COHORT_SIZE = 12;
    private int version = VERSION;
    private final String siteId;
    private int rescuedMask;
    private int deadMask;
    private int settledAttempt;
    private int activeAttempt;
    private boolean victorious;
    private int payment;

    public record Attempt(String siteId, int ordinal, List<Integer> people) implements Serializable {
        public Attempt {
            people = List.copyOf(people);
            if (siteId == null || siteId.isBlank() || ordinal < 1 || people.isEmpty()
                    || people.stream().distinct().count() != people.size()
                    || people.stream().anyMatch(i -> i < 0 || i >= COHORT_SIZE))
                throw new IllegalArgumentException("Invalid attempt identity");
        }
    }
    public record Outcome(Attempt attempt, List<Integer> rescued, List<Integer> dead,
                          boolean victory) implements Serializable {
        public Outcome {
            rescued = List.copyOf(rescued); dead = List.copyOf(dead);
            if (attempt == null || !attempt.people().containsAll(rescued) || !attempt.people().containsAll(dead)
                    || rescued.stream().distinct().count() != rescued.size()
                    || dead.stream().distinct().count() != dead.size()
                    || rescued.stream().anyMatch(dead::contains)
                    || victory && (rescued.isEmpty() || rescued.size() + dead.size() != attempt.people().size()))
                throw new IllegalArgumentException("Invalid frozen rescue outcome");
        }
    }
    public LastLiftProgress(String siteId) {
        if (siteId == null || siteId.isBlank()) throw new IllegalArgumentException("Frozen Rill site required");
        this.siteId = siteId;
    }
    public List<Integer> stranded() {
        List<Integer> people = new ArrayList<>();
        for (int i = 0; i < COHORT_SIZE; i++) if (((rescuedMask | deadMask) & (1 << i)) == 0) people.add(i);
        return List.copyOf(people);
    }
    /** Reopening an outstanding attempt retains its identity, including after a save/load. */
    public Attempt beginAttempt() {
        if (version != VERSION || isClosed()) throw new IllegalStateException("Last Lift is not available");
        if (activeAttempt == 0) activeAttempt = settledAttempt + 1;
        return new Attempt(siteId, activeAttempt, stranded());
    }
    /** Returns newly earned chapter credits. Duplicate, stale or foreign outcomes cannot pay or alter people. */
    public int settle(Outcome outcome) {
        if (outcome == null || version != VERSION || activeAttempt == 0) return 0;
        Attempt expected = new Attempt(siteId, activeAttempt, stranded());
        if (!expected.equals(outcome.attempt())) return 0;
        for (int person : outcome.rescued()) rescuedMask |= 1 << person;
        for (int person : outcome.dead()) deadMask |= 1 << person;
        settledAttempt = activeAttempt;
        activeAttempt = 0;
        victorious = outcome.victory();
        if (!victorious) return 0;
        payment = 4000 + 500 * rescuedCount();
        return payment;
    }
    public int rescuedCount() { return Integer.bitCount(rescuedMask); }
    public int deadCount() { return Integer.bitCount(deadMask); }
    public int payment() { return payment; }
    public boolean victorious() { return victorious; }
    public boolean isClosed() { return victorious || stranded().isEmpty(); }
    public String summary() {
        return rescuedCount() + " rescued / " + deadCount() + " confirmed dead / " + stranded().size() + " still stranded";
    }
}
