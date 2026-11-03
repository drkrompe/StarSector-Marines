package com.dillon.starsectormarines.battle.vehicle;

import java.util.Arrays;

/**
 * The friendly hulls standing near a hull that is planning, as its plan must treat them ({@code convoy-nouns.md}, hull
 * contact). The rolling planner reads terrain and knows nothing of hulls, so left alone it drives a hull straight at
 * one parked on its way and leaves the meeting to {@link HullContact}. A hull that is moving will have moved by the
 * time this one gets there, so only one standing counts.
 *
 * <p>A pose is judged as the planner judges terrain, on the padded footprint and with {@link HullContact#SKIN} to
 * spare, since a plan that comes within the skin is braked by the contact it was drawn to avoid. A hull already that
 * close when the plan starts is judged on the bare footprints instead, or every way out of a tight spot would read as
 * blocked. A hull the planning one is already inside is left out: only separation can part those.
 *
 * <p>Host-thread scratch: vehicles are driven serially.
 */
public final class StandingHulls {

    /** Slower than this, cells a second, a hull is standing: a held hull creeps a tick's acceleration at a time. */
    public static final float STANDING_SPEED = .25f;
    /** Spare room around a hull standing at the end of a route that still leaves that end taken, cells. */
    static final float TAKEN_SKIN = 1f;
    private static final float SAMPLE_CELLS = .5f;

    private final float halfL, halfW, paddedHalfL, paddedHalfW;
    private final float startX, startY, startFacing;
    private final float[] contact = new float[3];
    private float[] hulls = new float[6 * 6];
    private int count;

    StandingHulls(Pose start, VehicleType type) {
        startX = start.x;
        startY = start.y;
        startFacing = start.facingDeg;
        halfL = type.visualLengthCells * .5f;
        halfW = type.visualWidthCells * .5f;
        paddedHalfL = (type.visualLengthCells + HybridAStarPlanner.PLANNER_CLEARANCE) * .5f;
        paddedHalfW = (type.visualWidthCells + HybridAStarPlanner.PLANNER_CLEARANCE) * .5f;
    }

    /** Adds a standing hull, unless the planning hull is already inside it. */
    void add(float x, float y, float facing, float lengthCells, float widthCells) {
        float otherHalfL = lengthCells * .5f, otherHalfW = widthCells * .5f;
        if (HullContact.overlap(startX, startY, startFacing, halfL, halfW, x, y, facing, otherHalfL, otherHalfW, 0f,
                contact)) return;
        boolean roomAtStart = !HullContact.overlap(startX, startY, startFacing, paddedHalfL, paddedHalfW, x, y, facing,
                otherHalfL, otherHalfW, HullContact.SKIN, contact);
        if ((count + 1) * 6 > hulls.length) hulls = Arrays.copyOf(hulls, hulls.length * 2);
        int at = count++ * 6;
        hulls[at] = x;
        hulls[at + 1] = y;
        hulls[at + 2] = facing;
        hulls[at + 3] = otherHalfL;
        hulls[at + 4] = otherHalfW;
        hulls[at + 5] = roomAtStart ? 1f : 0f;
    }

    int count() { return count; }

    /** Whether the planning hull at this pose would run into one of them. */
    boolean blocks(float x, float y, float facing) {
        for (int i = 0; i < count; i++) {
            int at = i * 6;
            boolean room = hulls[at + 5] != 0f;
            if (HullContact.overlap(x, y, facing, room ? paddedHalfL : halfL, room ? paddedHalfW : halfW,
                    hulls[at], hulls[at + 1], hulls[at + 2], hulls[at + 3], hulls[at + 4],
                    room ? HullContact.SKIN : 0f, contact)) return true;
        }
        return false;
    }

    /** Whether a plan made without them runs into one, sampled along its length. */
    boolean crossedBy(Trajectory plan) {
        float[] xs = plan.xs(), ys = plan.ys(), headings = plan.headings();
        for (int i = 1; i < plan.size(); i++) {
            float dx = xs[i] - xs[i - 1], dy = ys[i] - ys[i - 1];
            int samples = Math.max(1, (int) Math.ceil(Math.hypot(dx, dy) / SAMPLE_CELLS));
            for (int k = 1; k <= samples; k++) {
                float t = k / (float) samples;
                float facing = headings[i - 1] + NeutralPivot.signedDegrees(headings[i] - headings[i - 1]) * t;
                if (blocks(xs[i - 1] + dx * t, ys[i - 1] + dy * t, facing)) return true;
            }
        }
        return false;
    }

    /** Whether one of them stands where a hull of the planning hull's size would park at this point. */
    boolean take(float x, float y, float facing) {
        for (int i = 0; i < count; i++) {
            int at = i * 6;
            if (HullContact.overlap(x, y, facing, halfL, halfW, hulls[at], hulls[at + 1], hulls[at + 2], hulls[at + 3],
                    hulls[at + 4], TAKEN_SKIN, contact)) return true;
        }
        return false;
    }
}
