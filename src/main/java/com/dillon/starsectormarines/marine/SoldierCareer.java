package com.dillon.starsectormarines.marine;

import java.io.Serializable;

/**
 * One marine's lifetime service record — the campaign-side accumulation of the
 * per-battle telemetry {@code battle.sim.CombatTelemetryService} collects
 * ({@code progression-nouns.md}).
 *
 * <p><b>Lifetime totals only.</b> Per-mission history is a much bigger
 * commitment — save size, and a UI surface to justify it — and none of this
 * track's consumers need it: S4 converts these totals into experience, S8
 * puts a few of them on a roster row. If the debrief ever wants a timeline,
 * that is a follow-up, not a shape to pre-build.
 *
 * <p>Two products from one record. As a <b>reward input</b> it is what makes
 * experience earned rather than issued; as a <b>balance artifact</b> it is
 * what makes "what is the real landed-round rate at Milspec versus Service"
 * answerable from a save instead of a stopwatch.
 *
 * <p>Mutable and persisted by xstream inside {@link MarineSoldier}, with the
 * same {@code readResolve} legacy repair every other persisted marine type
 * uses: a save written before this existed loads with a zeroed record rather
 * than a null one.
 */
public final class SoldierCareer implements Serializable {

    private int missionsDeployed;
    private int missionsWon;
    private int roundsFired;
    private int roundsHit;
    private float damageDealt;
    private float friendlyFireDamage;
    private float damageTaken;
    private int kills;
    private int timesWounded;

    /** Missions this marine deployed on, won or lost. */
    public int missionsDeployed() { return missionsDeployed; }

    /** Of those, the ones that ended in victory. */
    public int missionsWon() { return missionsWon; }

    /** Primary rounds fired across every deployment. */
    public int roundsFired() { return roundsFired; }

    /** Of those, the ones that reached a body. */
    public int roundsHit() { return roundsHit; }

    /** Post-mitigation HP taken off hostiles. Excludes friendly fire and overkill. */
    public float damageDealt() { return damageDealt; }

    /**
     * Post-mitigation HP this marine has taken off their own side. Recorded
     * separately from {@link #damageDealt()}, exactly as the telemetry seam
     * keeps it, so it can be reported rather than netted away.
     */
    public float friendlyFireDamage() { return friendlyFireDamage; }

    /** Post-mitigation HP absorbed, from any source. */
    public float damageTaken() { return damageTaken; }

    /** Killing blows landed on hostiles. */
    public int kills() { return kills; }

    /** Times this marine came off a battlefield wounded. */
    public int timesWounded() { return timesWounded; }

    /** Lifetime landed fraction, or {@code 0} before the first round fired. */
    public float landedFraction() {
        return roundsFired > 0 ? (float) roundsHit / roundsFired : 0f;
    }

    /**
     * Folds one deployment into the record. Called once per mission per
     * deployed marine, by {@code MarineRoster.applySoldierOutcome}.
     *
     * <p>{@code roundsFired} and its siblings come from the mission's frozen
     * telemetry row and are zero for a marine the battle recorded nothing for
     * — a mission a marine deployed on still counts as a deployment even if
     * they never got a shot off.
     */
    void recordDeployment(boolean victory, boolean wounded,
                          int roundsFired, int roundsHit,
                          float damageDealt, float friendlyFireDamage,
                          float damageTaken, int kills) {
        missionsDeployed++;
        if (victory) missionsWon++;
        if (wounded) timesWounded++;
        this.roundsFired += Math.max(0, roundsFired);
        this.roundsHit += Math.max(0, roundsHit);
        this.damageDealt += Math.max(0f, damageDealt);
        this.friendlyFireDamage += Math.max(0f, friendlyFireDamage);
        this.damageTaken += Math.max(0f, damageTaken);
        this.kills += Math.max(0, kills);
    }

    private Object readResolve() {
        missionsDeployed = Math.max(0, missionsDeployed);
        missionsWon = Math.max(0, Math.min(missionsDeployed, missionsWon));
        roundsFired = Math.max(0, roundsFired);
        roundsHit = Math.max(0, Math.min(roundsFired, roundsHit));
        damageDealt = Math.max(0f, damageDealt);
        friendlyFireDamage = Math.max(0f, friendlyFireDamage);
        damageTaken = Math.max(0f, damageTaken);
        kills = Math.max(0, kills);
        timesWounded = Math.max(0, timesWounded);
        return this;
    }
}
