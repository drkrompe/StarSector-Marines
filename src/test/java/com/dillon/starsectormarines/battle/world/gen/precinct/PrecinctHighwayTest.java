package com.dillon.starsectormarines.battle.world.gen.precinct;

import com.dillon.starsectormarines.battle.world.gen.bsp.GrownTrunkPlan;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Road in open country is a supply route or it is nothing.
 *
 * <p>Growth does not stop at a precinct's claim — arms run their full length
 * whether or not the place holds the ground they cross — so an installation
 * with a modest claim throws a street network across the wilderness around it.
 * Measured on a remote map, two thirds of all road lay outside every precinct:
 * 3086 cells, fully connected, no dead ends, serving nothing. A street grid in
 * a field.
 *
 * <p>What survives is what carries a place off the map, because a remote
 * installation is supplied from somewhere and the road out is how.
 */
class PrecinctHighwayTest {

    private static final int W = 120;
    private static final int H = 80;

    /**
     * One claim in the middle, a road from it to the left edge, and a spur that
     * goes nowhere. The spur is the wandering; the road is the supply route.
     */
    private static int[][][] fixture() {
        int[][] owner = new int[W][H];
        int[][] claim = new int[W][H];
        for (int[] column : owner) java.util.Arrays.fill(column, GrownTrunkPlan.UNOWNED);
        for (int[] column : claim) java.util.Arrays.fill(column, GrownTrunkPlan.UNOWNED);

        for (int x = 55; x <= 75; x++) {
            for (int y = 30; y <= 50; y++) claim[x][y] = 0;
        }
        // The supply road: claim edge out to the map border.
        for (int x = 0; x <= 55; x++) {
            for (int y = 39; y <= 41; y++) owner[x][y] = 0;
        }
        // The spur: off the supply road, into the country, stopping nowhere
        // near an edge.
        for (int y = 41; y <= 70; y++) {
            for (int x = 29; x <= 31; x++) owner[x][y] = 0;
        }
        return new int[][][]{owner, claim};
    }

    private static int roadCells(int[][] owner) {
        int n = 0;
        for (int[] column : owner) {
            for (int cell : column) {
                if (cell != GrownTrunkPlan.UNOWNED) n++;
            }
        }
        return n;
    }

    /** The route to the edge survives; the spur does not. */
    @Test
    void aSupplyRouteIsKeptAndAWanderingSpurIsNot() {
        int[][][] f = fixture();
        int[][] owner = f[0];
        PrecinctHighways.Result result = PrecinctHighways.prune(owner, f[1], W, H);

        assertEquals(1, result.exits(), "the one road to the map edge was not recognised");
        assertTrue(result.pruned() > 0, "the spur into open country was kept");
        for (int y = 50; y <= 70; y++) {
            assertEquals(GrownTrunkPlan.UNOWNED, owner[30][y],
                    "the spur survived at 30," + y + ", so wandering road is still kept");
        }
        assertTrue(owner[0][40] != GrownTrunkPlan.UNOWNED,
                "the supply road no longer reaches the map edge");
    }

    /**
     * A kept highway is as wide as the road it is made of.
     *
     * <p>The traced route is one cell wide because a breadth-first path is, and
     * kept as traced a five-cell road becomes a footpath that nothing drives —
     * measured before this was fixed, 193 cells of highway across five exits.
     */
    @Test
    void aHighwayStaysWideEnoughToDrive() {
        int[][][] f = fixture();
        int[][] owner = f[0];
        PrecinctHighways.prune(owner, f[1], W, H);
        int width = 0;
        for (int y = 0; y < H; y++) {
            if (owner[10][y] != GrownTrunkPlan.UNOWNED) width++;
        }
        assertTrue(width >= PrecinctBoundary.DRIVABLE_GATE_WIDTH, "the supply road is "
                + width + " cells across where it crosses the country, under the "
                + PrecinctBoundary.DRIVABLE_GATE_WIDTH + " a vehicle needs");
    }

    /** A place with no road off the map is given one. */
    @Test
    void aPlaceWithNoWayOffTheMapGetsOne() {
        int[][] owner = new int[W][H];
        int[][] claim = new int[W][H];
        for (int[] column : owner) java.util.Arrays.fill(column, GrownTrunkPlan.UNOWNED);
        for (int[] column : claim) java.util.Arrays.fill(column, GrownTrunkPlan.UNOWNED);
        for (int x = 55; x <= 75; x++) {
            for (int y = 30; y <= 50; y++) claim[x][y] = 0;
        }
        PrecinctHighways.Result result = PrecinctHighways.prune(owner, claim, W, H);
        assertTrue(result.carved(), "an installation with no road at all was left unsupplied");
        assertTrue(roadCells(owner) > 0, "a supply road was reported and none was cut");
    }

    /**
     * A place whose own streets reach the border is not given a second road.
     *
     * <p>The question is whether any road reaches the edge, not whether an
     * open-country route does. Asked the narrow way, a dense city — which has
     * streets to the border and no open country at all — had a supply road cut
     * across it on every seed.
     */
    @Test
    void aCityWhoseStreetsReachTheEdgeIsNotGivenASupplyRoad() {
        int[][] owner = new int[W][H];
        int[][] claim = new int[W][H];
        for (int[] column : owner) java.util.Arrays.fill(column, GrownTrunkPlan.UNOWNED);
        for (int[] column : claim) java.util.Arrays.fill(column, GrownTrunkPlan.UNOWNED);
        for (int x = 0; x < W; x++) {
            for (int y = 30; y <= 50; y++) {
                claim[x][y] = 0;
                owner[x][y] = 0;
            }
        }
        int before = roadCells(owner);
        PrecinctHighways.Result result = PrecinctHighways.prune(owner, claim, W, H);
        assertTrue(!result.carved(),
                "a place whose own streets already run to the border was given another road");
        assertEquals(before, roadCells(owner), "claimed road was pruned; what a place does "
                + "with its own ground is its business");
    }
}
