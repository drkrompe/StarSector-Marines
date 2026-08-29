package com.dillon.starsectormarines.battle.world.gen.fit;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.gen.Affordance;
import com.dillon.starsectormarines.battle.world.gen.FixtureTask;
import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.ship.RoomRecipe;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A bridge is a plot with the watch ringed round it, looking in.
 *
 * <p>That sentence is the whole of the arrangement, and each of the three claims
 * in it can fail on its own. The plot can end up somewhere other than the middle;
 * the ring can come out as a rank; and — the failure that is easy to miss and
 * makes the room read wrong rather than look wrong — every watchkeeper can end
 * up posted on the walkway <em>inboard</em> of his own console, with his back to
 * his screens and to the plot. That last one is what
 * {@link RoomFloor#place(String, int, int, Affordance)} does if it is allowed to
 * find the standing cell itself, because it prefers a cell already reserved as
 * circulation.
 *
 * <p>Asked of one room on a synthetic floor, not of a generated ship. It is a
 * fact about the fitting and the deck it fills, and a whole hull would fail it
 * for a hundred reasons belonging to the hull.
 */
class BridgeStationsRingThePlotTest {

    private record Room(RoomShape shape, int originX, int originY, RoomPose pose,
                        RoomPurpose purpose, List<Doorway> doors)
            implements FurnishableRoom { }

    /**
     * The bridge's own footprint, taken from the recipe rather than copied, so a
     * reshaped command compartment is judged as the room it became.
     */
    private static final RoomShape BRIDGE = RoomRecipe.COMMAND.shape();

    @Test
    void theWatchRingsAPlotInTheMiddle() {
        Fitted fitted = fit(BRIDGE);

        Doodad plot = fitted.covering(fitted.centreX(), fitted.centreY());
        assertNotNull(plot, "the middle of the bridge is bare, so there is nothing to ring");
        assertEquals(3, plot.footprintCellsX,
                "the plot came out as a patchwork of one-cell props rather than one object,"
                        + " which is what a hall laid out for a briefing looks like");
        assertEquals(3, plot.footprintCellsY);
        assertTrue(fitted.watchPoints() >= 6,
                "a thirteen-by-eleven bridge stands a watch of "
                        + fitted.watchPoints() + ", which is a room with consoles in it");
        assertTrue(fitted.survives(),
                "the bridge's own fill severed its circulation, so it ships as bare deck");
        assertEquals(0, fitted.dropped(),
                "a watch station was walled in by the arrangement that placed it");
    }

    /**
     * Every published point is a watch, kept from a cell that faces the fixture
     * it belongs to and looks inboard past it.
     *
     * <p>Manhattan distance to the middle is the test of "inboard", and it is
     * exact rather than approximate here because both footprints are odd on both
     * axes, so the plot is centred on a cell rather than on a seam.
     */
    @Test
    void everyWatchkeeperLooksInAtThePlot() {
        for (FixtureTask task : fit(BRIDGE).tasks()) {
            assertEquals(Affordance.WATCH, task.affordance(),
                    "the bridge published a job that is not a watch");
            int reach = Math.abs(task.fixtureX() - task.cellX())
                    + Math.abs(task.fixtureY() - task.cellY());
            assertEquals(1, reach,
                    "a watchkeeper was posted somewhere he cannot reach his own station");
        }
    }

    /** The same points, checked for which way round the watchkeeper is standing. */
    @Test
    void nobodyStandsWithTheirBackToTheirOwnConsole() {
        Fitted fitted = fit(BRIDGE);
        for (FixtureTask task : fitted.tasks()) {
            int fromCell = fitted.inboardness(task.cellX(), task.cellY());
            int fromFixture = fitted.inboardness(task.fixtureX(), task.fixtureY());
            assertTrue(fromFixture < fromCell,
                    "a watchkeeper at " + task.cellX() + "," + task.cellY()
                            + " faces outboard, so his console is between him and the bulkhead");
        }
    }

    /**
     * The watch is a quota read off the room, not a count authored in the
     * fitting.
     *
     * <p>The room the recipe actually cuts is the small case; a bridge with room
     * for further tiers of stations has to stand more of a watch without
     * anything being told how much bigger it is.
     */
    @Test
    void aBiggerBridgeStandsABiggerWatch() {
        int small = fit(BRIDGE).watchPoints();
        int large = fit(RoomShape.rectangle(25, 21)).watchPoints();

        assertTrue(large > small,
                "a 25x21 bridge stands " + large + " where a 13x11 stands " + small
                        + ", so the station count is fixed rather than sized to the room");
    }

    /**
     * A frigate's bridge is a small compartment and must still come out as a
     * bridge.
     *
     * <p>The plot's art is drawn at one size, so a room that cannot seat it has
     * to be given something rather than a hole in its middle. This is the case
     * that catches a fitting written around the art it happens to have: the ring
     * still forms, and the middle is still occupied, on a footprint the plot
     * itself does not fit.
     */
    @Test
    void aSmallBridgeIsStillABridge() {
        Fitted fitted = fit(RoomShape.rectangle(9, 7));

        assertNotNull(fitted.covering(fitted.centreX(), fitted.centreY()),
                "the small bridge has nothing in the middle for its watch to ring");
        assertTrue(fitted.watchPoints() >= 4,
                "the small bridge stands a watch of " + fitted.watchPoints());
        assertTrue(fitted.survives(), "the small bridge's own fill severed its circulation");
    }

    /**
     * The same bridge laid down turned and flipped is the same bridge.
     *
     * <p>The failure this catches is the one a canonical frame exists to prevent
     * and does not prevent by itself: an arrangement authored facing one way and
     * placed in raw room coordinates keeps its footprint and loses its contents,
     * so the plot slides off centre and the ring goes with it. A diamond hides it
     * particularly well, because a mask that turns onto itself still looks like a
     * bridge from the outside.
     */
    @Test
    void aTurnedBridgeIsTheSameBridge() {
        Fitted upright = fit(BRIDGE, RoomPose.CANONICAL);
        Fitted turned = fit(BRIDGE, new RoomPose(1, true));

        assertEquals(upright.watchPoints(), turned.watchPoints(),
                "turning the compartment changed the size of its watch");
        assertNotNull(turned.covering(turned.centreX(), turned.centreY()),
                "the turned bridge's plot is no longer in the middle of it");
        assertTrue(turned.survives(),
                "the turned bridge's own fill severed its circulation");
    }

    private record Fitted(List<Doodad> doodads, List<FixtureTask> tasks,
                          int dropped, boolean survives,
                          int centreX, int centreY) {

        int watchPoints() {
            return (int) tasks.stream().filter(t -> t.affordance() == Affordance.WATCH).count();
        }

        /**
         * The fixture covering a cell, or null. Asked by footprint rather than
         * by anchor, because the plot is one three-cell object recorded at its
         * corner and a search for a doodad standing on the middle cell would
         * miss it.
         */
        Doodad covering(int x, int y) {
            return doodads.stream()
                    .filter(d -> x >= d.cellX && x < d.cellX + d.footprintCellsX
                            && y >= d.cellY && y < d.cellY + d.footprintCellsY)
                    .findFirst().orElse(null);
        }

        /** How far inboard a cell is, as a distance the plot sits at the bottom of. */
        int inboardness(int x, int y) {
            return Math.abs(x - centreX) + Math.abs(y - centreY);
        }
    }

    private static Fitted fit(RoomShape canonical) {
        return fit(canonical, RoomPose.CANONICAL);
    }

    private static Fitted fit(RoomShape canonical, RoomPose pose) {
        RoomShape shape = canonical.posed(pose);
        int mapWidth = shape.width() + 8;
        int mapHeight = shape.height() + 8;
        NavigationGrid grid = new NavigationGrid(mapWidth, mapHeight);
        CellTopology topology = new CellTopology(mapWidth, mapHeight);
        for (int y = 0; y < mapHeight; y++) {
            for (int x = 0; x < mapWidth; x++) grid.setWalkableFloor(x, y);
        }
        GenContext ctx = new GenContext(grid, topology, new Random(7L),
                mapWidth, mapHeight, 7L);
        // One hatch on the beam, where the widest part of a diamond is, so the
        // approach has to cross every ring of the arrangement to reach the plot.
        Room room = new Room(shape, 4, 4, pose, RoomPurpose.BRIDGE,
                List.of(new Doorway(3, 4 + shape.height() / 2)));
        RoomFloor floor = new RoomFloor(ctx, room, RoomFit.STANDARD);
        new BridgeFitting().fit(floor);
        int dropped = floor.dropUnreachableWork();
        return new Fitted(List.copyOf(ctx.doodads), List.copyOf(ctx.fixtureTasks),
                dropped, floor.circulationSurvives(),
                4 + shape.width() / 2, 4 + shape.height() / 2);
    }
}
