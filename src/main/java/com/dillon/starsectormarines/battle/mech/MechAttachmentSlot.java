package com.dillon.starsectormarines.battle.mech;

/**
 * Where an attached mech stands relative to the infantry squad it follows, by its effective doctrine
 * ({@code mechs-nouns.md}, attachment). Pure geometry: the caller supplies the squad's centre and its
 * axis — the way it faces a threat, or else the way it is going — and gets the ideal point back.
 *
 * <p>Brawler leads furthest ahead along the axis, Frontline Support (Tank) screens just ahead of the
 * men, Balanced walks alongside, and Long-Range Support shadows from behind. Several followers of one
 * role spread across the axis by their rank among that role's followers, so two Brawlers do not
 * stand on one cell. With no axis yet, a follower keeps its own bearing from the squad at its role's
 * distance, so a newly attached mech closes in rather than crossing the squad to reach a side.
 */
public final class MechAttachmentSlot {

    /** Cells a Brawler leads the squad by. */
    static final float LEAD_DEPTH = 5f;
    /** Cells a Frontline Support mech screens ahead of the squad by; {@link BackstopAssignedSquad}'s front. */
    static final float SCREEN_DEPTH = BackstopAssignedSquad.FRONT_DISTANCE;
    /** Cells to the side a Balanced mech walks at. */
    static final float ALONGSIDE = 5f;
    /** Cells a Long-Range Support mech shadows the squad from. */
    static final float SHADOW_DEPTH = 7f;
    /** Cells between followers of one role, across the axis. */
    static final float FILE_SPACING = 3f;

    private MechAttachmentSlot() {}

    /**
     * The ideal point for a follower of {@code role} of rank {@code rank} among its role's followers.
     *
     * @param axisX the squad's axis as a unit vector, or {@code NaN} for none
     * @param mechX where the follower stands, read only when there is no axis
     */
    public static float[] point(MechRole role, int rank, float centreX, float centreY,
                                float axisX, float axisY, float mechX, float mechY) {
        float forward = forward(role);
        float lateral = lateral(role, rank);
        if (Float.isNaN(axisX) || Float.isNaN(axisY)) {
            float dx = mechX - centreX;
            float dy = mechY - centreY;
            float length = (float) Math.sqrt(dx * dx + dy * dy);
            if (length < 1e-3f) {
                dx = 0f;
                dy = 1f;
                length = 1f;
            }
            float radius = (float) Math.sqrt(forward * forward + lateral * lateral);
            return new float[]{centreX + dx / length * radius, centreY + dy / length * radius};
        }
        // Left-hand normal of the axis: a positive lateral stands on the squad's left.
        float leftX = -axisY;
        float leftY = axisX;
        return new float[]{centreX + axisX * forward + leftX * lateral,
                centreY + axisY * forward + leftY * lateral};
    }

    /** How far ahead of the squad (negative: behind) a role's slot stands. */
    static float forward(MechRole role) {
        if (role == null) return 0f;
        return switch (role) {
            case ASSAULT -> LEAD_DEPTH;
            case ARMORED_SUPPORT -> SCREEN_DEPTH;
            case LR_SUPPORT -> -SHADOW_DEPTH;
            case BALANCED -> 0f;
        };
    }

    /**
     * How far across the axis a follower stands. Balanced takes alternate sides, the rest file out
     * either side of the axis: rank 0 on it, then left, right, further left, further right.
     */
    static float lateral(MechRole role, int rank) {
        int r = Math.max(0, rank);
        if (role == MechRole.BALANCED) {
            float side = r % 2 == 0 ? 1f : -1f;
            return side * (ALONGSIDE + (r / 2) * FILE_SPACING);
        }
        if (r == 0) return 0f;
        float side = r % 2 == 1 ? 1f : -1f;
        return side * ((r + 1) / 2) * FILE_SPACING;
    }
}
