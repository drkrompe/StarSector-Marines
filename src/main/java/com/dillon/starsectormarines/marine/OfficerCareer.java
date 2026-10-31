package com.dillon.starsectormarines.marine;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/** Save-owned choices and attempt receipts. Older officers start with an empty record. */
public final class OfficerCareer implements Serializable {
    /** Ranks a single specialty can be trained to. */
    public static final int MAX_LEVEL = 3;

    private List<OfficerSpecialty> training = new ArrayList<>();
    private List<String> creditedAttempts = new ArrayList<>();
    private int victories;
    private int earnedXp;

    public int level(OfficerSpecialty specialty) {
        return (int) training.stream().filter(s -> s == specialty).count();
    }
    public int availablePoints(Rank rank) { return Math.max(0, 1 + rank.ordinal() - training.size()); }
    public boolean train(OfficerSpecialty specialty, Rank rank) {
        if (specialty == null || specialty.retired() || availablePoints(rank) == 0 || level(specialty) >= MAX_LEVEL) return false;
        training.add(specialty);
        return true;
    }
    public int deployments() { return creditedAttempts.size(); }
    public int victories() { return victories; }
    public int earnedXp() { return earnedXp; }
    public boolean record(String attempt, boolean victory, int xp) {
        if (attempt == null || creditedAttempts.contains(attempt)) return false;
        creditedAttempts.add(attempt);
        if (victory) victories++;
        earnedXp += Math.max(0, xp);
        return true;
    }
    private Object readResolve() {
        if (training == null) training = new ArrayList<>();
        // A retired specialty's points come back to spend on what is still offered.
        training.removeIf(specialty -> specialty == null || specialty.retired());
        if (creditedAttempts == null) creditedAttempts = new ArrayList<>();
        return this;
    }
}
