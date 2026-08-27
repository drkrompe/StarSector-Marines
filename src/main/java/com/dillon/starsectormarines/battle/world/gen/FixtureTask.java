package com.dillon.starsectormarines.battle.world.gen;

/**
 * One job a fixture affords: the cell it is done from, what is done there, and
 * the fixture it is done to.
 *
 * <p>This is the <b>authored</b> half. When a deck is hosted, every task in
 * service publishes one exclusive
 * {@link com.dillon.starsectormarines.battle.task.TaskPoint}, which is what an
 * actor actually claims — so the count of tasks a room was furnished with is the
 * bound on how many people can work in it at once. Three firing lanes admit
 * three marines, never four stacked on a painted marker.
 *
 * <p>Kept distinct from that runtime point because generation knows things the
 * claim service has no use for and no way to learn: which berth a job belongs
 * to, and whether its fixture is still in service. Both are consumed when the
 * deck is staffed, and neither survives into the claim.
 *
 * <p>The standing cell is deliberately <em>beside</em> the fixture rather than
 * on it. Nobody stands inside a workbench, and a point authored on the fixture
 * would either be unreachable or would need the fixture to stop occupying its
 * own footprint. Fittings reserve the standing cell as circulation when they
 * emit the point, so later furniture cannot take it.
 *
 * <p>Whether the point is <b>usable</b> is not settled here. A {@link #berth}
 * point affords nothing while its berth stands empty, and generation cannot know
 * what the host parks: berths are filled from a roster, so occupancy is a
 * runtime fact about this deck rather than a property of the room. Generation
 * authors the link; whoever runs the deck reads it.
 */
public record FixtureTask(int cellX, int cellY, Affordance affordance,
                        int fixtureX, int fixtureY, int berth, boolean inService) {

    /** A {@link #berth} value meaning the work stands on its own, not on a machine. */
    public static final int NO_BERTH = -1;

    /** A point at a fixture, in service, serving no berth. */
    public static FixtureTask at(int cellX, int cellY, Affordance affordance,
                               int fixtureX, int fixtureY) {
        return new FixtureTask(cellX, cellY, affordance, fixtureX, fixtureY, NO_BERTH, true);
    }

    /** A point whose work is done on whatever is parked in {@code berth}. */
    public static FixtureTask servingBerth(int cellX, int cellY, int berth,
                                         int fixtureX, int fixtureY) {
        return new FixtureTask(cellX, cellY, Affordance.SERVICE,
                fixtureX, fixtureY, berth, true);
    }

    /**
     * The same point with its fixture out of service.
     *
     * <p>A wrecked gantry is not a gantry: it neither counts toward the bay's
     * capacity nor offers anyone work, and both follow from this one flag rather
     * than from two lists that can disagree.
     */
    public FixtureTask wrecked() {
        return new FixtureTask(cellX, cellY, affordance, fixtureX, fixtureY, berth, false);
    }
}
