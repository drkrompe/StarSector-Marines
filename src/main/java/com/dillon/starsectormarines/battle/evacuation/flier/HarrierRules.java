package com.dillon.starsectormarines.battle.evacuation.flier;

/**
 * The Harrier's numbers: the Rav Bloom's flier, which hunts in packs (see {@code colony-breach-nouns.md},
 * <i>Harrier</i>). Starting values, to be felt out in play rather than settled on paper, as the Brute's are.
 *
 * <p>Distances are cells, speeds cells per second, altitude a fraction of loiter height ({@code 1} loitering,
 * near {@code 0} at the bottom of a dive).
 */
public final class HarrierRules {

    /** Hit points: two rifle hits or one flak hit. Unplated. */
    public static final float HP = 28f;
    /** One strike's bite, landed once per dive on the target the pack committed to. */
    public static final float BITE_DAMAGE = 9f;
    /** The bite's penetration: it scores a gun shield or a crew, not a tank. */
    public static final float BITE_PENETRATION = 4f;
    /** Morale drain weight of the strike: a diving flier is frightening. */
    public static final float STRIKE_MORALE = 1.4f;
    /** Body radius for hits; the drawn wings are wider than what a round has to find. */
    public static final float RADIUS = 0.35f;
    /** Walls within this of the flier do not block a line to or from it, as for a drone. */
    public static final float AIR_LOS_RADIUS = 3f;

    /** How far a pack senses prey from its centre. */
    public static final float SENSE_RANGE = 44f;
    /**
     * How far from its prey a pack loiters: past a rifle's, a machine gun's and a flak mount's reach (22-26),
     * so only a long-reach air gun (a SAM) touches a loitering pack.
     */
    public static final float STANDOFF = 30f;
    /** Radius of the loiter ring the members circle on, for a small pack; a large one widens it ({@link #ringRadius}). */
    public static final float LOITER_RADIUS = 4.5f;
    /** Arc between neighbours on the loiter ring: about a wingspan, so the ring does not stack wings. */
    public static final float RING_SPACING = 2.2f;
    /** Angular speed round the loiter ring, degrees a second. */
    public static final float LOITER_ORBIT_DEG_PER_SEC = 40f;
    /** A pack circles at least this long between dives. */
    public static final float MIN_LOITER_SECONDS = 3f;
    /** A pack commits only once this share of its members is near the loiter ring ({@link #GATHER_MARGIN}). */
    public static final float GATHER_SHARE = 0.7f;
    /** Gathered means inside the ring's radius plus this. */
    public static final float GATHER_MARGIN = 3f;

    public static final float LOITER_SPEED = 4.5f;
    public static final float DIVE_SPEED = 9f;
    public static final float PULL_UP_SPEED = 7f;
    public static final float ACCEL = 10f;
    public static final float LOITER_TURN_DEG_PER_SEC = 200f;
    public static final float DIVE_TURN_DEG_PER_SEC = 260f;

    /** A diver strikes inside this of its target's centre, plus the target's own radius. */
    public static final float STRIKE_REACH = 0.9f;
    /** A diver is at the bottom of its dive inside this distance; its altitude falls linearly to it. */
    public static final float DIVE_GLIDE_CELLS = 12f;
    /** The lowest a diver comes: just over the heads it bites. */
    public static final float DIVE_FLOOR_ALTITUDE = 0.08f;
    /** Altitude below which a flier counts as having dived, for the scene's dive-and-pull-up check. */
    public static final float LOW_ALTITUDE = 0.25f;
    /** Descent and climb rates, altitude per second. */
    public static final float DESCENT_RATE = 1.6f;
    public static final float CLIMB_RATE = 0.7f;
    /** A dive that has not landed its strikes by now is broken off. */
    public static final float MAX_DIVE_SECONDS = 7f;
    /** How long a pack flies out and climbs after a dive before it loiters again. */
    public static final float PULL_UP_SECONDS = 2.2f;

    /** Members push apart inside this distance. */
    public static final float SEPARATION_RADIUS = 1.6f;
    public static final float SEPARATION_WEIGHT = 1.6f;

    /**
     * A pack that lost at least this share of the members it dived with splits in two once it has pulled up,
     * as long as each half has {@link #SPLIT_MIN_HALF} members: a mauled pack stops diving as one.
     */
    public static final float SPLIT_LOSS_SHARE = 1f / 3f;
    public static final int SPLIT_MIN_HALF = 2;

    /** Packs released at once are cut to this size at most; a pack is 6-20 by design. */
    public static final int MAX_PACK = 20;

    /** How long a killed flier takes to fall, in seconds; presentation only. */
    public static final float FALL_SECONDS = 0.8f;

    /** Drawn altitude of a loitering flier above its ground point, in cells, for shots and shadows. */
    public static final float LOITER_HEIGHT_CELLS = 3f;
    /** An anti-air hit on a flier bursts among its packmates within at least this, even for a weapon with no splash. */
    public static final float FLAK_MIN_BURST = 1.2f;
    /** A packmate caught in a burst takes this share of the round. */
    public static final float BURST_SHARE = 0.5f;

    /** The loiter ring's radius for {@code members} fliers. */
    public static float ringRadius(int members) {
        return Math.max(LOITER_RADIUS, members * RING_SPACING / (float) (2 * Math.PI));
    }

    private HarrierRules() {}
}
