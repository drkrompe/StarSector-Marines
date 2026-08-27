package com.dillon.starsectormarines.battle.world.gen;

/**
 * One place a crew member can stand and do one thing: the standing cell, what
 * is done there, and the fixture it is done to.
 *
 * <p>A task point is <b>exclusive</b>. Three firing lanes admit three marines,
 * never four stacked on a painted marker, and that bound is honest because it is
 * the same count as the fixtures — a room cannot afford more concurrent activity
 * than it was furnished for.
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
public record TaskPoint(int cellX, int cellY, Affordance affordance,
                        int fixtureX, int fixtureY, int berth, boolean inService) {

    /** A {@link #berth} value meaning the work stands on its own, not on a machine. */
    public static final int NO_BERTH = -1;

    /** A point at a fixture, in service, serving no berth. */
    public static TaskPoint at(int cellX, int cellY, Affordance affordance,
                               int fixtureX, int fixtureY) {
        return new TaskPoint(cellX, cellY, affordance, fixtureX, fixtureY, NO_BERTH, true);
    }

    /** A point whose work is done on whatever is parked in {@code berth}. */
    public static TaskPoint servingBerth(int cellX, int cellY, int berth,
                                         int fixtureX, int fixtureY) {
        return new TaskPoint(cellX, cellY, Affordance.SERVICE,
                fixtureX, fixtureY, berth, true);
    }

    /**
     * The same point with its fixture out of service.
     *
     * <p>A wrecked gantry is not a gantry: it neither counts toward the bay's
     * capacity nor offers anyone work, and both follow from this one flag rather
     * than from two lists that can disagree.
     */
    public TaskPoint wrecked() {
        return new TaskPoint(cellX, cellY, affordance, fixtureX, fixtureY, berth, false);
    }
}
