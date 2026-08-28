package com.dillon.starsectormarines.marine;

import java.io.Serializable;

/**
 * One squad's lifetime service record — the same per-battle telemetry
 * {@link SoldierCareer} accumulates, folded at the grain the player actually
 * names and deploys ({@code progression-nouns.md}).
 *
 * <p><b>The formation is the thing with a history.</b> This record belongs to
 * the squad, not to whoever currently fills its billets: it survives
 * replacement, and it survives a total loss and reconstitution under the same
 * squad identity. That is the deliberate choice — with rank-and-file
 * experience issued by the squad's loadout definition rather than earned by
 * individuals, the unit is what accumulates a reputation while the marines
 * rotate through it.
 *
 * <p><b>Evidence, never a dial.</b> Nothing may read these totals back as a
 * combat-quality input. A squad's fighting quality is determined by what the
 * player can see it carry; a standing that quietly made a veteran squad shoot
 * better would be exactly the hidden modifier the standing law forbids.
 *
 * <p>Lifetime totals only, for the same reason {@link SoldierCareer} keeps
 * them: a per-mission journal is a separate save-size and UI commitment.
 *
 * <p>Mutable and persisted by xstream inside {@link MarineSquad}, with the
 * usual {@code readResolve} repair so a save written before this existed loads
 * zeroed rather than null.
 */
public final class SquadCareer implements Serializable {

    private int missionsDeployed;
    private int missionsWon;
    private int marinesDeployed;
    private int casualties;
    private int roundsFired;
    private int roundsHit;
    private float damageDealt;
    private float friendlyFireDamage;
    private float damageTaken;
    private int kills;

    /** Missions this squad deployed on, won or lost. */
    public int missionsDeployed() { return missionsDeployed; }

    /** Of those, the ones that ended in victory. */
    public int missionsWon() { return missionsWon; }

    /** Billets filled across every deployment; the denominator for casualty rate. */
    public int marinesDeployed() { return marinesDeployed; }

    /** Marines who came off a battlefield wounded, missing, or dead. */
    public int casualties() { return casualties; }

    /** Primary rounds fired by everyone who has served in this squad. */
    public int roundsFired() { return roundsFired; }

    /** Of those, the ones that reached a body. */
    public int roundsHit() { return roundsHit; }

    /** Post-mitigation HP taken off hostiles. Excludes friendly fire and overkill. */
    public float damageDealt() { return damageDealt; }

    /**
     * Post-mitigation HP this squad has taken off its own side. Kept separate
     * from {@link #damageDealt()} at the telemetry seam precisely so it can be
     * reported honestly rather than netted away.
     */
    public float friendlyFireDamage() { return friendlyFireDamage; }

    /** Post-mitigation HP absorbed, from any source. */
    public float damageTaken() { return damageTaken; }

    /** Killing blows landed on hostiles. */
    public int kills() { return kills; }

    /** Lifetime landed fraction, or {@code 0} before the first round fired. */
    public float landedFraction() {
        return roundsFired > 0 ? (float) roundsHit / roundsFired : 0f;
    }

    /** Lifetime share of filled billets that became a casualty, or {@code 0} before the first deployment. */
    public float casualtyRate() {
        return marinesDeployed > 0 ? (float) casualties / marinesDeployed : 0f;
    }

    /** Folds one deploying marine's contribution into the squad record. */
    void recordMarine(boolean casualty, int roundsFired, int roundsHit,
                      float damageDealt, float friendlyFireDamage,
                      float damageTaken, int kills) {
        marinesDeployed++;
        if (casualty) casualties++;
        this.roundsFired += Math.max(0, roundsFired);
        this.roundsHit += Math.max(0, roundsHit);
        this.damageDealt += Math.max(0f, damageDealt);
        this.friendlyFireDamage += Math.max(0f, friendlyFireDamage);
        this.damageTaken += Math.max(0f, damageTaken);
        this.kills += Math.max(0, kills);
    }

    /**
     * Counts the mission itself, once, for a squad that put at least one marine
     * on the ground. Separate from {@link #recordMarine} because a mission is
     * one event however many billets the squad filled for it.
     */
    void recordMission(boolean victory) {
        missionsDeployed++;
        if (victory) missionsWon++;
    }

    private Object readResolve() {
        missionsDeployed = Math.max(0, missionsDeployed);
        missionsWon = Math.max(0, Math.min(missionsDeployed, missionsWon));
        marinesDeployed = Math.max(0, marinesDeployed);
        casualties = Math.max(0, Math.min(marinesDeployed, casualties));
        roundsFired = Math.max(0, roundsFired);
        roundsHit = Math.max(0, Math.min(roundsFired, roundsHit));
        damageDealt = Math.max(0f, damageDealt);
        friendlyFireDamage = Math.max(0f, friendlyFireDamage);
        damageTaken = Math.max(0f, damageTaken);
        kills = Math.max(0, kills);
        return this;
    }
}
