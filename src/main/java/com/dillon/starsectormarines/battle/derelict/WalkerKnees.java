package com.dillon.starsectormarines.battle.derelict;

import java.util.Arrays;

/**
 * A derelict walker's four knees (`ruins-guardians.md`, Legs): each leg's own hit region, with its own structure, on
 * the same unit as the torso. A hit on the walker strikes a knee by a share that depends on where it comes from, and
 * then the knee whose leg reaches toward the shooter takes it instead of the torso: the front of a braced frame never
 * (its front legs are planted behind its plate), its flanks most, its rear less, and from underneath most of all. A
 * knee at zero is lost: one lost and the walker limps on three legs, slowed, and cannot grip a wall on that side or
 * brace with a front leg gone; two lost and it is downed, the torso dropped, immobile and exposed on every side.
 *
 * <p>Legs are numbered as the leg rig's hips stand ({@code DerelictLegRig}): 0 at 45 degrees from the facing (front
 * left), 1 at 135 (rear left), 2 at 225 (rear right), 3 at 315 (front right), in the battle's bearing convention.
 * Numbers are UNTUNED: they wait on the owner playing a derelict battle.
 */
public final class WalkerKnees {

    public static final int LEGS = 4;
    /** Each knee's structure. UNTUNED. */
    public static final float KNEE_STRUCTURE = 160f;
    /** Hip bearings from the facing, degrees, by leg. */
    static final float[] HIP_BEARING = {45f, 135f, 225f, 315f};
    /** Share of hits from the front that strike a knee while free (none while braced). UNTUNED. */
    public static final float FRONT_SHARE = 0.15f;
    /** Share of hits from a flank that strike a knee. UNTUNED. */
    public static final float FLANK_SHARE = 0.35f;
    /** Share of hits from the rear that strike a knee. UNTUNED. */
    public static final float REAR_SHARE = 0.25f;
    /** Share of hits from underneath the torso that strike a knee. UNTUNED. */
    public static final float UNDER_SHARE = 0.6f;
    /** Speed on three legs, as a share. UNTUNED. */
    public static final float LIMP_SPEED_SHARE = 0.6f;
    /** Damage scale on a downed walker's torso from any side: dropped, its underside and back are open. UNTUNED. */
    public static final float DOWNED_SCALE = 1.5f;
    /** How far out from the torso's centre a knee is seen, cells (about the drawn knee's). */
    public static final float KNEE_CELLS = 1.0f;
    /** Knees lost that down the walker. */
    public static final int DOWNED_AT = 2;

    private final float[] structure = new float[LEGS];

    public WalkerKnees() {
        Arrays.fill(structure, KNEE_STRUCTURE);
    }

    /**
     * The share of a hit from {@code bearing} (degrees from the facing) that strikes a knee rather than the torso.
     *
     * @param braced whether the frame stands braced (its front knees are then out of reach)
     * @param under  whether the shooter stands under the torso
     */
    public static float share(float bearing, boolean braced, boolean under) {
        if (under) return UNDER_SHARE;
        if (VaultFrameBrace.inFront(bearing)) return braced ? 0f : FRONT_SHARE;
        if (VaultFrameBrace.inRear(bearing)) return REAR_SHARE;
        return FLANK_SHARE;
    }

    /** The standing knee whose hip points nearest {@code bearing}, or -1 when none stands. */
    public int nearest(float bearing) {
        int best = -1;
        float bestGap = Float.MAX_VALUE;
        for (int i = 0; i < LEGS; i++) {
            if (lost(i)) continue;
            float gap = Math.abs(VaultFrameBrace.wrap(bearing - HIP_BEARING[i]));
            if (gap < bestGap) {
                bestGap = gap;
                best = i;
            }
        }
        return best;
    }

    /**
     * A hit of {@code damage} from {@code bearing}, given a roll in [0, 1): when the roll falls in the knee share the
     * nearest standing knee takes it all. Returns the scale left on the torso's damage, 0 when a knee took it.
     */
    public float hit(float bearing, boolean braced, boolean under, float roll, float damage) {
        if (roll >= share(bearing, braced, under)) return 1f;
        int knee = nearest(bearing);
        if (knee < 0) return 1f;
        structure[knee] = Math.max(0f, structure[knee] - damage);
        return 0f;
    }

    /** Where leg {@code leg}'s knee stands seen from above, cells: on its hip bearing, {@link #KNEE_CELLS} out. */
    public static float[] kneeAt(float x, float y, float facingDegrees, int leg) {
        double heading = Math.toRadians(facingDegrees + HIP_BEARING[leg]);
        // The battle's facing convention: heading 0 points +y, the forward vector is (-sin, cos).
        return new float[] {x - (float) Math.sin(heading) * KNEE_CELLS, y + (float) Math.cos(heading) * KNEE_CELLS};
    }

    public boolean lost(int leg) {
        return structure[leg] <= 0f;
    }

    public float structure(int leg) {
        return structure[leg];
    }

    /** The lost legs as a bit mask, bit {@code i} for leg {@code i}, as {@code DerelictLegRig.legsLost} reads it. */
    public int lostMask() {
        int mask = 0;
        for (int i = 0; i < LEGS; i++) if (lost(i)) mask |= 1 << i;
        return mask;
    }

    public int lostCount() {
        int n = 0;
        for (int i = 0; i < LEGS; i++) if (lost(i)) n++;
        return n;
    }

    /** Two knees gone: the torso is down, the walker immobile and exposed. */
    public boolean downed() {
        return lostCount() >= DOWNED_AT;
    }

    /** Whether the walker can brace: both front legs stand to plant on the walls. */
    public boolean canBrace() {
        return !lost(0) && !lost(3);
    }

    /** Whether a leg may still grip a wall: it stands, and so does the walker. */
    public boolean grips(int leg) {
        return !lost(leg) && !downed();
    }

    /** Its speed as a share: whole, limping on three legs, or downed. */
    public float speedShare() {
        int lost = lostCount();
        if (lost == 0) return 1f;
        return lost >= DOWNED_AT ? 0f : LIMP_SPEED_SHARE;
    }
}
