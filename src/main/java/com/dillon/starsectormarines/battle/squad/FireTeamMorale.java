package com.dillon.starsectormarines.battle.squad;

/**
 * Cohesion state for one organizational fire team. The fire team — not the
 * squad — is the unit that breaks: drain lands on the team of the marine who
 * was hit, recovery and hysteresis run per team, and a broken team peels to
 * cover while its composed siblings keep executing the squad's plan.
 *
 * <p>Keyed by the <em>organizational</em> team index
 * ({@code SquadService.fireTeamIndex}), not by the maneuver grouping
 * {@link FireTeamGroups} derives. A team that has been shot down to one
 * survivor keeps its own morale — and its own (now punishing) {@link #cap()}
 * — rather than inheriting the morale of whichever sibling absorbs it for
 * movement purposes. The two groupings answer different questions: who moves
 * together, versus who is rattled.
 *
 * <p>Owned by {@link Squad}; ticked by {@link SquadMoraleSystem} and drained
 * by {@code combat.DamageResolver}. Both writers run serially. The per-unit
 * dispatch reads {@link #broken} concurrently and must not create entries —
 * see {@link Squad#fireTeamBroken(int)}.
 */
public final class FireTeamMorale {

    /** Organizational team index this state belongs to. Stable for the battle. */
    public final int teamIndex;

    /**
     * Soft cohesion variable in {@code [0, 1]}, capped by {@link #cap()}.
     * Drains on hits, deaths, and near-misses against this team's marines;
     * recovers passively once they stop being shot at.
     */
    public float morale = 1.0f;

    /**
     * Hysteresis flag — trips below
     * {@link SquadMoraleSystem#MORALE_BROKEN_THRESHOLD} of cap and clears
     * only above the higher {@link SquadMoraleSystem#MORALE_CLEAR_THRESHOLD},
     * so a team hovering at the line doesn't flicker between peeling and
     * fighting on every replan. This is the flag the infantry dispatcher
     * reads to route the team to {@code BreakContact}.
     */
    public boolean broken = false;

    /** Sim-seconds remaining before this team can take another drain event. */
    public float drainCooldown = 0f;

    /**
     * Sim-seconds since a hit or near-miss landed on one of this team's
     * marines. Gates recovery — see
     * {@link SquadMoraleSystem#MORALE_RECOVER_AFTER_FIRE_SECONDS}. Starts
     * large so a fresh team can recover immediately.
     */
    public float timeSinceUnderFire = Float.MAX_VALUE / 2f;

    /** Live members on the most recent morale tick. */
    public int aliveMembers = 0;

    /**
     * Running peak of {@link #aliveMembers}, the denominator of {@link #cap()}.
     * Tracked as a peak rather than stamped at spawn because a marine squad
     * assembles across lifts — a team that has landed two of its four marines
     * must not read as half-strength while the shuttle is still inbound.
     */
    public int originalSize = 0;

    public FireTeamMorale(int teamIndex) {
        this.teamIndex = teamIndex;
    }

    /**
     * Ceiling on {@link #morale}, {@code aliveMembers / originalSize} — a
     * mauled team can compose itself once but never fully resets. At the
     * four-marine team size this bites harder than the old squad-wide cap
     * did: one casualty costs a quarter of the ceiling rather than an eighth.
     * That is the intended consequence of tracking cohesion where it actually
     * lives.
     */
    public float cap() {
        if (originalSize <= 0 || aliveMembers <= 0) return 1f;
        return Math.min(1f, (float) aliveMembers / originalSize);
    }
}
