package com.dillon.starsectormarines.campaign;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/** Save-owned identity ledger for Wayline's three cargo carriers. No battle entities or runtime callbacks. */
public final class FreightRunProgress implements Serializable {
    public static final int VERSION = 1;
    public static final int COHORT_SIZE = 3;
    private int version = VERSION;
    private final String siteId;
    private int deliveredMask;
    private int lostMask;
    private int settledAttempt;
    private int activeAttempt;
    private boolean victorious;
    private int payment;

    public record Attempt(String siteId, int ordinal, List<Integer> cargo) implements Serializable {
        public Attempt {
            cargo = List.copyOf(cargo);
            if (siteId == null || siteId.isBlank() || ordinal < 1 || cargo.isEmpty()
                    || cargo.stream().distinct().count() != cargo.size()
                    || cargo.stream().anyMatch(i -> i < 0 || i >= COHORT_SIZE))
                throw new IllegalArgumentException("Invalid attempt identity");
        }
    }
    public record Outcome(Attempt attempt, List<Integer> delivered, List<Integer> lost,
                          boolean victory) implements Serializable {
        public Outcome {
            delivered = List.copyOf(delivered); lost = List.copyOf(lost);
            if (attempt == null || !attempt.cargo().containsAll(delivered) || !attempt.cargo().containsAll(lost)
                    || delivered.stream().distinct().count() != delivered.size()
                    || lost.stream().distinct().count() != lost.size()
                    || delivered.stream().anyMatch(lost::contains)
                    || victory && (delivered.size() + lost.size() != attempt.cargo().size()))
                throw new IllegalArgumentException("Invalid frozen freight outcome");
        }
    }
    public FreightRunProgress(String siteId) {
        if (siteId == null || siteId.isBlank()) throw new IllegalArgumentException("Frozen freight site required");
        this.siteId = siteId;
    }
    public boolean available() { return version == VERSION && !isClosed(); }
    public List<Integer> waiting() {
        List<Integer> cargo = new ArrayList<>();
        for (int i = 0; i < COHORT_SIZE; i++) if (((deliveredMask | lostMask) & (1 << i)) == 0) cargo.add(i);
        return List.copyOf(cargo);
    }
    /** Read-only briefing identity; opening a survey does not start an attempt. */
    public Attempt previewAttempt() {
        if (version != VERSION || isClosed()) throw new IllegalStateException("Thunder Run is not available");
        return new Attempt(siteId, activeAttempt == 0 ? settledAttempt + 1 : activeAttempt, waiting());
    }
    /** Reopening an outstanding attempt retains its identity, including after a save/load. */
    public Attempt beginAttempt() {
        if (version != VERSION || isClosed()) throw new IllegalStateException("Thunder Run is not available");
        if (activeAttempt == 0) activeAttempt = settledAttempt + 1;
        return new Attempt(siteId, activeAttempt, waiting());
    }
    /** Returns newly earned chapter credits. Duplicate, stale or foreign outcomes cannot pay or alter cargo. */
    public int settle(Outcome outcome) {
        if (outcome == null || version != VERSION || activeAttempt == 0) return 0;
        Attempt expected = new Attempt(siteId, activeAttempt, waiting());
        if (!expected.equals(outcome.attempt()) || outcome.victory() && deliveredCount() + outcome.delivered().size() == 0) return 0;
        for (int carrier : outcome.delivered()) deliveredMask |= 1 << carrier;
        for (int carrier : outcome.lost()) lostMask |= 1 << carrier;
        settledAttempt = activeAttempt;
        activeAttempt = 0;
        victorious = outcome.victory();
        if (!victorious) return 0;
        payment = 6000 + 4000 * deliveredCount();
        return payment;
    }
    public int deliveredCount() { return Integer.bitCount(deliveredMask); }
    public int lostCount() { return Integer.bitCount(lostMask); }
    public int payment() { return payment; }
    public boolean victorious() { return victorious; }
    public boolean isClosed() { return victorious || waiting().isEmpty(); }
    public String summary() {
        return deliveredCount() + " delivered / " + lostCount() + " lost / " + waiting().size() + " awaiting transport";
    }
}
