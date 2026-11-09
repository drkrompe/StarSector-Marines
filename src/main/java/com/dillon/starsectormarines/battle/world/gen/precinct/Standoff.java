package com.dillon.starsectormarines.battle.world.gen.precinct;

/**
 * How far from the objective an attacking force lands.
 *
 * <p>Mission vocabulary, the way {@link PrecinctPlan.Sprawl} is: a scenario
 * states how much approach it wants its marines to fight through, and the map
 * arranges itself around the answer. The distance is measured along the
 * traversal axis, between the attacker region's objective-facing side and the
 * objective precinct's claim boundary on the attacker-facing side. Read the
 * paragraph below before treating that as the distance the force walks.
 *
 * <p><b>Cells, not a fraction of the map.</b> A standoff is a walk, and a walk
 * does not scale with the map: doubling the map should not double the minutes
 * before first contact. {@link #STANDARD} is roughly the approach the 280x168
 * map had — the attacker band's far side around x=93 against an objective claim
 * edge near x=170 — which is the whole point of stating it in cells. The same
 * walk on a bigger map.
 *
 * <p><b>It is not the walk, and that is deliberate for now.</b> The gap above is
 * measured to the band's objective-facing side, while
 * {@code PrecinctLandingAreaStage} scans berths inward from the other one, so
 * the band's own depth — a third of the map — is an unstated addition to every
 * standoff. Measured at 560x336: {@link #CLOSE} states 40 cells and puts the
 * southern beachhead 149 cells from the claim and the western one 226. Reading
 * the gap from the landing side instead was built and replayed at full length,
 * and it is a materially worse battle, because the shuttles pay the slide twice
 * on every re-arm run and a beachhead on the objective's doorstep is a ferry
 * across the whole map: {@code reinforced-south} went from a marine victory at
 * tick 13,680 holding eleven compounds for 95 losses to a timeout holding nine
 * for 174, and {@code full-strength-west} stopped contesting anything at all.
 * So the number stays as it is until the arrival cadence is looked at with it.
 * See {@code conquest-560-contact.md}, which carries both measurements.
 *
 * <p><b>{@link #FAR} is not a large number, it is the absence of a slide.</b>
 * The attacker region stays exactly the band the mission stated, against the map
 * edge, which is what every precinct map did before this existed. It is the
 * value everything but Conquest passes, and it reproduces those maps to the
 * cell.
 */
public enum Standoff {

    /** A short approach: 40 cells of ground between the beachhead and the claim. */
    CLOSE(40),

    /** The measured approach of the map this model's balance was judged on. */
    STANDARD(80),

    /**
     * Whatever the stated band already gives — the beachhead on the map edge.
     *
     * <p>Carries no distance of its own, so {@link #cells()} is the largest
     * standoff there could be rather than a number to compare against.
     */
    FAR(Integer.MAX_VALUE),

    /**
     * Landed {@link #INSET_DEPTH} cells in from the map edge the band arrived
     * across, into the town rather than at its edge (owner: "we don't need to
     * keep landing zones at the edge of the map").
     *
     * <p>A depth from the edge rather than a walk to the claim, unlike
     * {@link #CLOSE} and {@link #STANDARD}: a Raid's prize is wherever the
     * search finds it, not the garrison a walk is measured against, and a
     * garrison's claim often runs down beside the arrival band, leaving no gap
     * to measure. So the band slides the depth whatever the claim, and the
     * landing stage keeps every berth off the objective's own ground instead.
     * {@link #cells()} is unused.
     */
    INSET(0);

    /** How far {@link #INSET} slides the band in from its edge. UNTUNED. */
    public static final int INSET_DEPTH = 40;

    private final int cells;

    Standoff(int cells) {
        this.cells = cells;
    }

    /**
     * How many cells of approach this asks for.
     *
     * <p>{@link Integer#MAX_VALUE} for {@link #FAR}, which no map can afford and
     * which is therefore the same answer as not sliding at all.
     */
    public int cells() {
        return cells;
    }

    /** Whether this standoff can move the attacker region at all. */
    public boolean slides() {
        return this != FAR;
    }
}
