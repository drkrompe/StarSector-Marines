package com.dillon.starsectormarines.render2d;

/**
 * Per-trail config for a {@link ContrailTrail} ribbon: how long samples
 * persist, how the ribbon's width and color vary by sample age, and the
 * minimum spacing between consecutive samples before a new one is pushed.
 *
 * <p>Mirrors the knobs on vanilla's {@code styleSpec} in {@code .proj}
 * engineSlot specs — {@code contrailDuration},
 * {@code contrailWidthAddedFractionAtEnd}, {@code contrailMinSeg},
 * {@code contrailColor} — but flattened to plain floats so the same struct
 * works for engine plumes, missile smoke, wreck smoke, shuttle wash, etc.
 *
 * <p>Immutable. Build once per kind (e.g. on the {@link com.dillon.starsectormarines.battle.turret.TurretKind})
 * and share across every trail instance of that kind.
 */
public final class ContrailStyle {

    /** Sim-seconds a sample lives after being pushed. Sample fades from start→end color/width over this window, then gets dropped. */
    public final float durationSec;

    /** Ribbon half-width (one side) at age=0, in cells. Convert to pixels at draw time using the camera's cellPxSize. */
    public final float startWidthCells;
    /** Ribbon half-width at age=durationSec, in cells. Greater than start gives the dissipating-puff look; less than start gives a tapered jet. */
    public final float endWidthCells;

    public final float startR, startG, startB, startA;
    public final float endR, endG, endB, endA;

    /** Minimum distance (cells) from the previous sample before a new one is pushed. Stops zero-length segments when the source is stationary or near-stationary, and keeps the sample count bounded for fast-moving projectiles. */
    public final float minSegLenCells;
    /** Pre-squared {@link #minSegLenCells} so the per-frame push check is a single multiply-compare. */
    public final float minSegLenSqCells;

    /** When {@code true}, the {@link RibbonBatch} draws this trail with additive blending — engine plume / muzzle wash. {@code false} = normal alpha blend, used for smoke. Determines which batch instance the trail flushes into. */
    public final boolean additive;

    public ContrailStyle(float durationSec,
                         float startWidthCells, float endWidthCells,
                         float startR, float startG, float startB, float startA,
                         float endR, float endG, float endB, float endA,
                         float minSegLenCells,
                         boolean additive) {
        this.durationSec = durationSec;
        this.startWidthCells = startWidthCells;
        this.endWidthCells = endWidthCells;
        this.startR = startR; this.startG = startG; this.startB = startB; this.startA = startA;
        this.endR = endR;     this.endG = endG;     this.endB = endB;     this.endA = endA;
        this.minSegLenCells = minSegLenCells;
        this.minSegLenSqCells = minSegLenCells * minSegLenCells;
        this.additive = additive;
    }

    /**
     * Grey smoke contrail, non-additive — matches vanilla's
     * {@code "type":"SMOKE"} engineSlot style on the Locust SRM. Widens as it
     * trails to read as a dissipating puff column. Immutable; share across
     * every missile-class trail instead of allocating per shot.
     */
    public static final ContrailStyle MISSILE_SMOKE = new ContrailStyle(
            /*duration*/ 0.55f,
            /*startW*/ 0.10f, /*endW*/ 0.35f,
            /*start*/ 0.85f, 0.82f, 0.78f, 0.70f,
            /*end*/   0.40f, 0.40f, 0.40f, 0.00f,
            /*minSegLen*/ 0.08f,
            /*additive*/ false);
}
